# Notification System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Notification Delivery Flow](#notification-delivery-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable, reliable notification system that enables:
- Multi-channel delivery (Push, Email, SMS, In-app, Webhook)
- Real-time and scheduled notifications
- Priority-based delivery (Critical, High, Medium, Low)
- Template management with personalization
- User preference management (opt-in/opt-out)
- Rate limiting per user and per channel
- Delivery tracking and analytics
- Retry mechanism with exponential backoff
- Support for transactional and marketing notifications

### Scale Requirements
- **100 million daily active users (DAU)**
- **1 billion notifications per day**
- **Peak load: 10K notifications per second**
- **Avg delivery latency: < 2 seconds**
- **Critical notifications: < 500ms**
- **99.95% availability**
- **No notification loss (at-least-once delivery)**
- **Support 50+ notification types**

---

## Functional Requirements

### Must Have (P0)

#### 1. **Multi-Channel Support**
- Push notifications (iOS APNs, Android FCM, Web Push)
- Email notifications (transactional, marketing)
- SMS notifications (OTP, alerts)
- In-app notifications (bell icon, notification center)
- Webhook notifications (system-to-system)

#### 2. **Notification Sending**
- Send single notification to one user
- Send bulk notifications to millions of users
- Schedule notifications for future delivery
- Send recurring notifications (daily, weekly)
- Send time-zone aware notifications
- Cancel scheduled notifications

#### 3. **Template Management**
- Create notification templates
- Support multiple languages (i18n)
- Variable substitution (personalization)
- Template versioning
- A/B testing support
- Rich content (images, buttons, actions)

#### 4. **User Preferences**
- Opt-in/opt-out per channel
- Notification frequency limits
- Quiet hours (Do Not Disturb)
- Category-based preferences (marketing, alerts, social)
- Global unsubscribe

#### 5. **Priority Management**
- CRITICAL: Security alerts, payment failures (< 500ms)
- HIGH: OTPs, order confirmations (< 1s)
- MEDIUM: Promotions, updates (< 5s)
- LOW: Newsletters, tips (< 1 minute)

#### 6. **Delivery Tracking**
- Track notification states:
  - QUEUED: In processing queue
  - SENT: Sent to provider (APNs/FCM/SMTP)
  - DELIVERED: Confirmed delivery
  - FAILED: Delivery failed
  - CLICKED: User clicked notification
  - DISMISSED: User dismissed notification
- Delivery receipts and webhooks
- Real-time status updates

#### 7. **Rate Limiting**
- Per user rate limits (max 10 notifications/hour)
- Per channel rate limits
- Respect provider limits (APNs: 1000 QPS, FCM: 100K QPS)
- Throttling during peak hours

### Nice to Have (P1)
- Notification grouping/stacking
- Smart delivery (send when user most likely to engage)
- Notification retry with exponential backoff
- Dead letter queue for failed notifications
- Campaign management
- Segmentation (send to specific user groups)
- Analytics dashboard (delivery rate, click rate, conversion)
- Message preview before sending
- Notification history per user
- Compliance (GDPR, CAN-SPAM)

---

## Non-Functional Requirements

### Performance
- **Notification processing**: < 100ms (enqueue)
- **Critical notification delivery**: < 500ms (end-to-end)
- **Normal notification delivery**: < 2 seconds (p95)
- **Bulk send throughput**: 1M notifications in 10 minutes
- **API latency**: < 50ms (send notification endpoint)

### Scalability
- Support 100M DAU
- Handle 1 billion notifications/day (~11.5K/second average)
- Peak capacity: 10x average (100K/second)
- Scale to 10B notifications/day in 2 years
- Support 1000+ concurrent bulk campaigns

### Availability
- **99.95% uptime** (~4 hours downtime/year)
- Multi-region deployment (US, EU, Asia)
- Graceful degradation (if email fails, don't block push)
- No notification loss (durability guarantee)
- Retry failed deliveries automatically

### Reliability
- **Delivery guarantee**: At-least-once delivery
- **Ordering**: Best-effort (not strictly ordered)
- **Idempotency**: Duplicate sends handled gracefully
- **Data persistence**: Notifications stored for 90 days
- **Audit trail**: Complete history of all notifications

### Security
- **Authentication**: API keys, OAuth 2.0
- **Authorization**: Role-based access control (RBAC)
- **Encryption**: TLS for all connections
- **PII handling**: Encrypt user data at rest
- **Rate limiting**: Prevent abuse
- **Token management**: Secure storage of device tokens

### Consistency
- **Eventual consistency** for delivery status
- **Strong consistency** for user preferences
- **Idempotent** operations (same request = same result)

---

## Capacity Estimation

### Traffic Estimates
```
Daily Active Users (DAU): 100M
Notifications per user per day: 10
Total notifications/day: 100M × 10 = 1B

Notifications/second (average): 1B / 86,400 ≈ 11,574 QPS
Notifications/second (peak - 10x): 115,740 QPS ≈ 116K QPS

Distribution by channel:
- Push: 50% → 500M/day → 5,787 QPS
- Email: 30% → 300M/day → 3,472 QPS
- SMS: 10% → 100M/day → 1,157 QPS
- In-app: 8% → 80M/day → 926 QPS
- Webhook: 2% → 20M/day → 231 QPS

Bulk campaigns: 20% of total → 200M notifications
Single notifications: 80% → 800M notifications
```

### Storage Estimates

**Notification Metadata**:
```
Per notification:
{
  notification_id: 16 bytes (UUID)
  user_id: 16 bytes
  template_id: 16 bytes
  channel: 10 bytes
  priority: 10 bytes
  status: 10 bytes
  created_at: 8 bytes (timestamp)
  delivered_at: 8 bytes
  content_hash: 32 bytes
  metadata: 200 bytes (JSON)
}
Total per notification: ~320 bytes

Daily storage: 1B × 320 bytes = 320 GB/day
Monthly: 320 GB × 30 = 9.6 TB/month
With 90-day retention: 28.8 TB

With replication (3x): 86.4 TB
```

**Notification Content** (Templates):
```
Templates: 1000 templates
Per template:
- Template content: 2 KB
- Translations (10 languages): 20 KB
- Media references: 500 bytes
Total per template: ~23 KB

Total template storage: 1000 × 23 KB = 23 MB (negligible)
```

**User Preferences**:
```
Users: 100M
Per user preference:
{
  user_id: 16 bytes
  email_enabled: 1 byte
  push_enabled: 1 byte
  sms_enabled: 1 byte
  quiet_hours: 20 bytes
  categories_preferences: 100 bytes
  device_tokens: 500 bytes (iOS + Android + Web)
}
Total per user: ~640 bytes

Total storage: 100M × 640 bytes = 64 GB
With replication: 192 GB
```

**Device Tokens**:
```
Active devices: 200M (2 devices per user on average)
Token size: 200 bytes (FCM token)
Total: 200M × 200 bytes = 40 GB
With replication: 120 GB
```

**Total Storage**:
```
Notification metadata (90 days): 86.4 TB
User preferences: 192 GB = 0.19 TB
Device tokens: 120 GB = 0.12 TB
Templates: 0.02 TB
─────────────────────────────────
Total: ~87 TB
```

### Bandwidth Estimates
```
Notification payload average size:
- Push: 1 KB (title + body + data)
- Email: 50 KB (HTML + images)
- SMS: 200 bytes (160 chars)
- In-app: 500 bytes
- Webhook: 2 KB

Weighted average:
(0.5 × 1KB) + (0.3 × 50KB) + (0.1 × 0.2KB) + (0.08 × 0.5KB) + (0.02 × 2KB)
= 0.5 + 15 + 0.02 + 0.04 + 0.04 = 15.6 KB per notification

Daily bandwidth:
Outgoing: 1B × 15.6 KB = 15.6 TB/day
15.6 TB / 86,400 seconds ≈ 180 MB/second

Peak (10x): 1.8 GB/second
```

### Memory Requirements (Caching)
```
Active user sessions (online users): 10M
Session data per user: 1 KB (preferences, device tokens)
Total: 10M × 1 KB = 10 GB

Notification queue (in-flight): 1M notifications
Queue entry size: 2 KB (notification + metadata)
Total: 1M × 2 KB = 2 GB

Template cache: 1000 templates × 23 KB = 23 MB

Rate limiter counters:
Users: 100M × 64 bytes (Redis counter) = 6.4 GB

Total memory needed: ~20 GB
Distributed across Redis cluster: 10 nodes × 2 GB per node
```

### Server Estimates
```
API Servers (stateless):
  - Handle send requests: 116K QPS
  - Each server: 5K QPS capacity
  - Servers needed: 116K / 5K = 23 servers
  - With redundancy (3x): 70 servers

Workers (notification processing):
  - Process 11.5K notifications/second
  - Each worker: 500 notifications/second
  - Workers needed: 11.5K / 500 = 23 workers
  - With redundancy (2x): 50 workers

Delivery Workers (per channel):
  - Push: 5,787 QPS → 12 workers
  - Email: 3,472 QPS → 7 workers
  - SMS: 1,157 QPS → 3 workers
  - In-app: 926 QPS → 2 workers
  - Webhook: 231 QPS → 1 worker
  Total delivery workers: 50 workers

Total servers: 70 + 50 + 50 = 170 servers

Cost estimation (AWS):
- API servers: 70 × $100/month = $7K/month
- Workers: 100 × $80/month = $8K/month
- Database (RDS + Cassandra): $15K/month
- Message Queue (Kafka): $5K/month
- Redis: $3K/month
- Third-party APIs (FCM, SNS, SES): $20K/month
- Total: ~$58K/month
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      CLIENTS                               │
│  Mobile Apps | Web Apps | Backend Services | Admin Panel  │
└────────────────────────┬───────────────────────────────────┘
                         │ (HTTPS)
                         ↓
┌────────────────────────────────────────────────────────────┐
│                   CDN / LOAD BALANCER                      │
│  CloudFront / AWS ALB - SSL termination, DDoS protection  │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│                    API GATEWAY                             │
│  - Authentication (API keys, JWT)                          │
│  - Rate limiting (per API key)                             │
│  - Request validation                                      │
│  - Routing                                                 │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Notification │ │   Template   │ │  Preference  │
│   Service    │ │   Service    │ │   Service    │
│  (Stateless) │ │  (Stateless) │ │  (Stateless) │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       └────────────────┼────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────┐
│              MESSAGE QUEUE (Kafka Cluster)                 │
│  Topics:                                                   │
│  - notifications.high_priority (partition by user_id)      │
│  - notifications.medium_priority                           │
│  - notifications.low_priority                              │
│  - notifications.bulk (partition by campaign_id)           │
│  - notifications.status_update                             │
└────────────────────────┬───────────────────────────────────┘
                         │
              ┌──────────┼──────────┐
              ↓          ↓           ↓
┌──────────────────┐ ┌────────────────┐ ┌──────────────────┐
│ Priority Queue   │ │ Bulk Campaign  │ │ Scheduler        │
│ Worker           │ │ Worker         │ │ (Cron jobs)      │
│ (High priority)  │ │ (Fan-out)      │ │                  │
└─────────┬────────┘ └────────┬───────┘ └──────────────────┘
          │                   │
          └───────────────────┼───────────────────┐
                              ↓                   ↓
                    ┌─────────────────┐  ┌──────────────────┐
                    │ Rate Limiter    │  │  User Preference │
                    │ (Redis)         │  │  Check (Cache)   │
                    └─────────────────┘  └──────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ↓               ↓               ↓
    ┌─────────────────┐ ┌─────────────┐ ┌─────────────┐
    │ Push Delivery   │ │   Email     │ │    SMS      │
    │ Worker (APNs/   │ │   Worker    │ │   Worker    │
    │ FCM)            │ │   (SES/SG)  │ │   (Twilio)  │
    └────────┬────────┘ └──────┬──────┘ └──────┬──────┘
             │                 │                │
             ↓                 ↓                ↓
    ┌────────────────────────────────────────────────────┐
    │        THIRD-PARTY PROVIDERS                       │
    │  APNs | FCM | SendGrid | SES | Twilio | SNS      │
    └────────────────────────────────────────────────────┘
                              │
                              ↓ (Delivery status webhooks)
                    ┌─────────────────┐
                    │  Webhook Handler│
                    │  (Status update)│
                    └─────────────────┘
                              │
                              ↓
┌────────────────────────────────────────────────────────────┐
│                CACHE LAYER (Redis Cluster)                 │
│  - User preferences (hash, 1 hour TTL)                     │
│  - Device tokens (string, 7 day TTL)                       │
│  - Rate limit counters (sorted set, sliding window)        │
│  - Template cache (string, 24 hour TTL)                    │
│  - Notification queue status (bitmap)                      │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│Notification  │ │   User       │ │  Analytics   │
│     DB       │ │   DB         │ │     DB       │
│ (Cassandra)  │ │  (PostgreSQL)│ │ (ClickHouse) │
│              │ │              │ │              │
│ - Notif logs │ │ - Users      │ │ - Events     │
│ - Status     │ │ - Preferences│ │ - Metrics    │
│ - History    │ │ - Devices    │ │ - Reports    │
└──────────────┘ └──────────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────┐
│           OBJECT STORAGE (S3 / Cloud Storage)              │
│  - Template assets (images, videos)                        │
│  - Email attachments                                       │
│  - Export data (notification history)                      │
│  - Compliance archives                                     │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                    │
│  Prometheus | Grafana | ELK | Jaeger | PagerDuty         │
│  - Delivery rate metrics                                   │
│  - Provider performance                                    │
│  - Queue depth monitoring                                  │
│  - Alert on failures                                       │
└────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Kafka for Message Queue**
   - High throughput (1M+ messages/sec)
   - Durability (no notification loss)
   - Partition by priority for ordering
   - Consumer groups for parallel processing
   - Replay capability for failures

2. **Cassandra for Notification Logs**
   - Write-heavy workload (11.5K writes/sec)
   - Time-series data (notifications by time)
   - Fast writes, eventual consistency acceptable
   - Efficient range queries (user's last N notifications)
   - Linear scalability

3. **Redis for Caching & Rate Limiting**
   - In-memory speed for preferences
   - Sliding window rate limiting
   - Device token cache
   - Sub-millisecond lookups
   - Pub/Sub for real-time updates

4. **PostgreSQL for User Data**
   - User profiles and preferences need ACID
   - Complex queries (user segmentation)
   - Strong consistency required
   - Relatively small dataset
   - Read replicas for scaling

5. **Priority Queues**
   - Separate queues by priority
   - Critical notifications processed first
   - Prevents low-priority from blocking high-priority
   - SLA guarantees per priority level

---

## Core Components

### 1. Notification Service (API Layer)

**Purpose**: Entry point for sending notifications

**Responsibilities**:
- Accept notification requests
- Validate request payload
- Check authentication & authorization
- Generate notification ID
- Enrich notification with metadata
- Route to appropriate queue
- Return acknowledgment

**API Endpoints**:

**Send Single Notification**:
```http
POST /api/v1/notifications/send

Request:
{
  "user_id": "user_12345",
  "template_id": "welcome_email",
  "channel": ["email", "push"],
  "priority": "HIGH",
  "data": {
    "user_name": "Alice",
    "verification_code": "123456"
  },
  "schedule_at": null,  // Send immediately
  "expire_at": "2024-01-08T12:00:00Z"  // Expire if not delivered
}

Response:
{
  "notification_id": "notif_abc123",
  "status": "QUEUED",
  "estimated_delivery": "2024-01-08T10:30:02Z",
  "channels_queued": ["email", "push"]
}
```

**Send Bulk Notification**:
```http
POST /api/v1/notifications/bulk

Request:
{
  "campaign_id": "campaign_xyz",
  "template_id": "promo_offer",
  "channel": "push",
  "priority": "MEDIUM",
  "recipients": [
    {"user_id": "user_1", "data": {"name": "Alice", "discount": "20%"}},
    {"user_id": "user_2", "data": {"name": "Bob", "discount": "15%"}},
    // ... up to 100K users
  ],
  "schedule_at": "2024-01-08T15:00:00Z"
}

Response:
{
  "campaign_id": "campaign_xyz",
  "total_recipients": 100000,
  "status": "SCHEDULED",
  "estimated_completion": "2024-01-08T15:10:00Z"
}
```

**Request Validation**:
```
1. Check API key / JWT token
2. Validate user_id exists
3. Check template_id exists
4. Validate channel supported
5. Check payload size < 10KB
6. Verify rate limit not exceeded
7. Validate schedule_at format and time
8. Check user preferences (not opted-out)

Validation time: < 10ms
```

**Processing Flow**:
```
1. Request arrives at API Gateway
2. Route to Notification Service instance
3. Validate request (10ms)
4. Generate notification_id (Snowflake)
5. Check user preferences (Redis cache, 1ms)
6. If opted-out: Return 200 with skipped status
7. If opted-in:
   a. Enrich with metadata
   b. Publish to Kafka topic based on priority
   c. Return 202 Accepted with notification_id
8. Total latency: < 50ms
```

**Idempotency**:
```
Problem: Client retries may create duplicate notifications

Solution: Idempotency key

Client sends:
{
  "idempotency_key": "client_generated_uuid",
  "user_id": "user_123",
  ...
}

Server:
1. Check Redis: GET idempotency:client_generated_uuid
2. If exists: Return cached response (duplicate)
3. If not exists:
   - Process notification
   - Cache response: SETEX idempotency:key 86400 response
   - Return response
   
Idempotency window: 24 hours
```

### 2. Template Service

**Purpose**: Manage notification templates and render content

**Template Structure**:
```json
{
  "template_id": "order_confirmation",
  "name": "Order Confirmation",
  "version": "2.0",
  "channels": {
    "email": {
      "subject": "Order {{order_id}} Confirmed",
      "html_body": "<html>...<h1>Thanks {{user_name}}</h1>...</html>",
      "text_body": "Thanks {{user_name}}, your order {{order_id}}...",
      "from": "orders@example.com",
      "reply_to": "support@example.com"
    },
    "push": {
      "title": "Order Confirmed!",
      "body": "Hi {{user_name}}, order {{order_id}} is confirmed",
      "icon": "https://cdn.example.com/order_icon.png",
      "click_action": "app://orders/{{order_id}}",
      "data": {
        "order_id": "{{order_id}}",
        "deep_link": "app://orders/{{order_id}}"
      }
    },
    "sms": {
      "body": "Order {{order_id}} confirmed. Track: https://ex.co/o/{{order_id}}"
    }
  },
  "locales": {
    "en": {...},
    "es": {...},
    "fr": {...}
  },
  "metadata": {
    "category": "transactional",
    "tags": ["order", "confirmation"],
    "created_by": "admin_user",
    "created_at": "2024-01-01T00:00:00Z"
  }
}
```

**Template Rendering**:
```
Input:
- template_id: "order_confirmation"
- channel: "email"
- locale: "en"
- data: {"user_name": "Alice", "order_id": "ORD-12345"}

Process:
1. Fetch template from cache (Redis) or DB
2. Select correct locale (default to "en")
3. Select channel-specific template
4. Replace variables using template engine (Handlebars/Mustache)
   "Hi {{user_name}}" → "Hi Alice"
   "Order {{order_id}}" → "Order ORD-12345"
5. Validate rendered output (no broken links, valid HTML)
6. Return rendered content

Output:
{
  "subject": "Order ORD-12345 Confirmed",
  "html_body": "<html>...<h1>Thanks Alice</h1>...</html>",
  "from": "orders@example.com"
}

Rendering time: < 5ms (cached template)
```

**Template Versioning**:
```
Templates are immutable once published

Version flow:
1. Create new template (draft)
2. Test with sample data
3. Publish → Version 1.0
4. Use in production

When updates needed:
1. Create new version (draft)
2. A/B test: 10% v2.0, 90% v1.0
3. Monitor metrics (open rate, click rate)
4. If v2.0 better: Promote to default
5. Deprecate v1.0 after 30 days

Active versions tracked:
template:order_confirmation:active_versions = [1.0, 2.0]
```

### 3. Preference Service

**Purpose**: Manage user notification preferences

**Preference Model**:
```json
{
  "user_id": "user_12345",
  "channels": {
    "email": {
      "enabled": true,
      "verified": true,
      "address": "alice@example.com"
    },
    "push": {
      "enabled": true,
      "devices": [
        {
          "device_id": "device_ios_abc",
          "token": "apns_token_xyz",
          "platform": "ios",
          "registered_at": "2024-01-01T00:00:00Z"
        },
        {
          "device_id": "device_android_def",
          "token": "fcm_token_xyz",
          "platform": "android",
          "registered_at": "2024-01-02T00:00:00Z"
        }
      ]
    },
    "sms": {
      "enabled": false,
      "phone_number": "+1234567890",
      "verified": true
    }
  },
  "categories": {
    "marketing": {
      "email": false,
      "push": false,
      "sms": false
    },
    "transactional": {
      "email": true,
      "push": true,
      "sms": true
    },
    "social": {
      "email": true,
      "push": true,
      "sms": false
    },
    "alerts": {
      "email": true,
      "push": true,
      "sms": true
    }
  },
  "quiet_hours": {
    "enabled": true,
    "start": "22:00",
    "end": "08:00",
    "timezone": "America/New_York",
    "except_critical": true
  },
  "frequency_limits": {
    "marketing": {
      "max_per_day": 2,
      "max_per_week": 5
    }
  },
  "global_unsubscribe": false,
  "updated_at": "2024-01-08T10:00:00Z"
}
```

**Preference Check Flow**:
```
Before sending notification:

1. Fetch user preferences (Redis cache or PostgreSQL)
2. Check global_unsubscribe:
   - If true: Skip notification
3. Check channel enabled:
   - If false: Skip for that channel
4. Check category preferences:
   - If category disabled: Skip
5. Check quiet hours:
   - If in quiet hours and not critical: Skip or delay
6. Check frequency limits:
   - If limit exceeded: Skip or queue for later
7. If all checks pass: Proceed with delivery

Check time: < 2ms (cached)
```

**Device Token Management**:
```
Registration:
POST /api/v1/preferences/devices

{
  "user_id": "user_123",
  "device_id": "device_abc",
  "platform": "ios",
  "token": "apns_token_xyz"
}

Server actions:
1. Validate token format
2. Store in database
3. Cache in Redis: 
   SET device:user_123:device_abc token:apns_token_xyz EX 604800
4. Associate with user_id

Token refresh:
- APNs tokens rarely change
- FCM tokens may change (app update)
- Client sends updated token on change
- Server updates database and cache

Token invalidation:
- User uninstalls app: Provider webhook notifies
- Server marks token as invalid
- Next send attempt fails: Remove from database
```

### 4. Priority Queue Worker

**Purpose**: Process notifications by priority

**Priority Levels & SLA**:
```
CRITICAL (Queue: notifications.critical)
- Use case: Security alerts, fraud alerts, payment failures
- SLA: < 500ms end-to-end
- Examples: "Your account was accessed from new device"
- Processing: Highest priority, skip all throttling

HIGH (Queue: notifications.high_priority)
- Use case: OTPs, order confirmations, delivery updates
- SLA: < 1 second
- Examples: "Your OTP is 123456"
- Processing: High priority, minimal throttling

MEDIUM (Queue: notifications.medium_priority)
- Use case: Promotions, feature updates, reminders
- SLA: < 5 seconds
- Examples: "20% off your next purchase"
- Processing: Normal priority, standard throttling

LOW (Queue: notifications.low_priority)
- Use case: Newsletters, tips, recommendations
- SLA: < 1 minute
- Examples: "Weekly digest of your activity"
- Processing: Low priority, aggressive throttling
```

**Worker Architecture**:
```
Each priority level has dedicated workers:

Critical Queue:
  - 10 dedicated workers
  - Process immediately, no batching
  - No rate limiting
  - Direct delivery (bypass caching)

High Priority Queue:
  - 20 workers
  - Small batches (10 notifications)
  - Light rate limiting (100/sec per user)
  - Fast-path delivery

Medium Priority Queue:
  - 30 workers
  - Medium batches (100 notifications)
  - Standard rate limiting (10/hour per user)
  - Normal delivery

Low Priority Queue:
  - 10 workers
  - Large batches (1000 notifications)
  - Aggressive rate limiting (2/day per user)
  - Delayed delivery during off-peak
```

**Processing Flow**:
```
1. Worker polls Kafka partition
2. Fetch batch of notifications (size depends on priority)
3. For each notification:
   a. Check user preferences (Redis cache)
   b. Check rate limit (Redis sliding window)
   c. Render template
   d. Route to channel-specific delivery worker
4. Publish to delivery queue
5. Update status: QUEUED → PROCESSING
6. Commit Kafka offset

Processing time: 10-50ms per notification
```

### 5. Rate Limiter

**Purpose**: Prevent notification spam, respect user limits

**Implementation**: Redis Sliding Window

**Algorithm**:
```
Key: rate_limit:{user_id}:{channel}:{category}
Type: Sorted Set
Score: Timestamp
Value: notification_id

Add notification:
  ZADD rate_limit:user_123:push:marketing <timestamp> notif_abc

Count in window (last hour):
  ZCOUNT rate_limit:user_123:push:marketing <now-3600> <now>

Remove old entries:
  ZREMRANGEBYSCORE rate_limit:user_123:push:marketing 0 <now-3600>

Check limit:
  IF count >= limit THEN reject ELSE allow
```

**Example**:
```
User limit: 10 push notifications/hour

Current time: 14:00
Window: 13:00 - 14:00

Notifications sent:
13:10 - notif_1
13:25 - notif_2
13:45 - notif_3
14:00 - notif_4 (current)

Count: 4 notifications in last hour
Limit: 10
Result: ALLOW (4 < 10)

At 15:00:
Window: 14:00 - 15:00
Only notif_4 in window (others expired)
Count: 1
```

**Multi-Level Limits**:
```
1. Per user per channel:
   - Push: 10/hour
   - Email: 5/hour
   - SMS: 2/hour

2. Per category:
   - Marketing: 2/day
   - Transactional: unlimited
   - Social: 20/day

3. Global (all channels):
   - Max 20/hour across all channels

Check order:
  1. Check global limit
  2. Check channel limit
  3. Check category limit
  If any exceeded: REJECT
```

**Provider Limits**:
```
APNs: 1000 requests/second per certificate
FCM: 100,000 messages/second
SES: 14 messages/second (default, can increase)
Twilio SMS: 100 messages/second

Implementation: Token bucket algorithm
- Each provider has bucket
- Tokens refilled per second
- Request consumes token
- If no tokens: Wait or queue
```

### 6. Delivery Workers

**Push Notification Worker (APNs/FCM)**:

**APNs Flow** (iOS):
```
1. Receive notification from queue
2. Fetch device token from cache
3. Build APNs payload:
   {
     "aps": {
       "alert": {
         "title": "Order Confirmed",
         "body": "Your order #12345 is confirmed"
       },
       "badge": 5,
       "sound": "default",
       "category": "ORDER_CATEGORY"
     },
     "custom_data": {
       "order_id": "12345",
       "deep_link": "app://orders/12345"
     }
   }

4. Send to APNs via HTTP/2:
   POST https://api.push.apple.com/3/device/<device_token>
   Headers:
     - apns-topic: com.example.app
     - apns-priority: 10 (high) or 5 (low)
     - apns-expiration: <timestamp>

5. Handle response:
   - 200 Success: Update status DELIVERED
   - 410 Unregistered: Remove token
   - 400 Bad request: Log error, mark FAILED
   - 429 Too many requests: Retry with backoff

Total latency: 100-300ms
```

**FCM Flow** (Android):
```
1. Receive notification from queue
2. Fetch device token from cache
3. Build FCM payload:
   {
     "message": {
       "token": "fcm_device_token",
       "notification": {
         "title": "Order Confirmed",
         "body": "Your order #12345 is confirmed",
         "image": "https://cdn.example.com/order.png"
       },
       "data": {
         "order_id": "12345",
         "click_action": "OPEN_ORDER"
       },
       "android": {
         "priority": "high",
         "ttl": "3600s"
       }
     }
   }

4. Send to FCM:
   POST https://fcm.googleapis.com/v1/projects/<project>/messages:send
   Headers:
     - Authorization: Bearer <oauth_token>

5. Handle response:
   - Success: Update status DELIVERED
   - NOT_FOUND: Token invalid, remove
   - INVALID_ARGUMENT: Log error
   - QUOTA_EXCEEDED: Backoff and retry

Total latency: 50-200ms
```

**Connection Pooling**:
```
Maintain persistent connections to APNs/FCM:
- Pool size: 10 connections per worker
- Reuse connections (HTTP/2 multiplexing)
- Auto-reconnect on failure
- Health checks every 30 seconds

Benefits:
- Reduce SSL handshake overhead
- Higher throughput (1000 messages/sec per connection)
- Lower latency (no connection setup delay)
```

**Email Worker**:

**SendGrid/SES Flow**:
```
1. Receive email notification from queue
2. Fetch user email from cache/DB
3. Render HTML template
4. Build email:
   {
     "from": "noreply@example.com",
     "to": "user@example.com",
     "subject": "Your order is confirmed",
     "html": "<html>...</html>",
     "text": "Plain text version...",
     "reply_to": "support@example.com",
     "headers": {
       "X-Campaign-ID": "campaign_123",
       "X-Notification-ID": "notif_abc"
     }
   }

5. Send via SendGrid API:
   POST https://api.sendgrid.com/v3/mail/send
   Headers:
     - Authorization: Bearer <api_key>
     - Content-Type: application/json

6. Handle response:
   - 202 Accepted: Email queued by SendGrid
   - 400: Invalid request
   - 429: Rate limit exceeded

7. Track delivery via webhooks:
   - delivered: Email reached inbox
   - bounced: Invalid email / full inbox
   - opened: User opened email (tracking pixel)
   - clicked: User clicked link
```

**Email Deliverability**:
```
Best practices:
- SPF, DKIM, DMARC records configured
- Warm up new IP addresses slowly
- Monitor bounce rate (< 5%)
- Honor unsubscribe immediately
- Include unsubscribe link in all marketing emails
- Authenticate sender domain
- Use dedicated IP for transactional emails
- Monitor sender reputation (avoid spam complaints)
```

**SMS Worker**:

**Twilio Flow**:
```
1. Receive SMS notification from queue
2. Fetch phone number (E.164 format: +1234567890)
3. Build SMS payload:
   {
     "from": "+1234567890",  // Twilio number
     "to": "+1987654321",     // User number
     "body": "Your OTP is 123456. Valid for 5 minutes.",
     "statusCallback": "https://api.example.com/sms/status"
   }

4. Send via Twilio API:
   POST https://api.twilio.com/2010-04-01/Accounts/<account>/Messages.json
   Auth: Basic <account_sid>:<auth_token>

5. Handle response:
   - 201 Created: SMS queued
   - Message SID returned for tracking

6. Track status via webhook:
   - queued → sent → delivered
   - failed → undelivered

Latency: 1-5 seconds (depends on carrier)
Cost: $0.0075 per SMS (US)
```

### 7. Webhook Handler

**Purpose**: Receive delivery status from providers

**Webhook Endpoints**:
```
POST /webhooks/apns/status
POST /webhooks/fcm/status
POST /webhooks/sendgrid/events
POST /webhooks/twilio/status
```

**Processing Flow**:
```
1. Provider sends webhook:
   {
     "notification_id": "notif_abc",
     "status": "delivered",
     "timestamp": "2024-01-08T10:30:15Z",
     "device_token": "token_xyz"
   }

2. Validate webhook signature (HMAC)
3. Update notification status in Cassandra
4. Update analytics (ClickHouse)
5. If status = FAILED:
   - Check retry policy
   - If retries remaining: Re-queue
   - Else: Move to dead letter queue
6. Publish status update event (Kafka)
7. Return 200 OK

Processing time: < 10ms
```

**Status Tracking**:
```
States:
QUEUED → SENT → DELIVERED → CLICKED
              ↓
            FAILED → RETRY → DELIVERED
                   ↓
                DEAD_LETTER

Transitions stored in Cassandra:
notification_status_history table
```

### 8. Scheduler Service

**Purpose**: Handle scheduled and recurring notifications

**Implementation**: Quartz Scheduler + Kafka

**Scheduling Flow**:
```
Schedule future notification:
1. Client sends schedule_at: "2024-01-10T09:00:00Z"
2. Store in PostgreSQL scheduled_notifications table
3. Quartz scheduler polls every minute
4. At scheduled time:
   - Fetch due notifications
   - Publish to Kafka (appropriate priority queue)
   - Mark as triggered
5. Notification processed normally

Storage:
{
  "schedule_id": "sched_123",
  "notification_id": "notif_abc",
  "schedule_at": "2024-01-10T09:00:00Z",
  "timezone": "America/New_York",
  "recurring": false,
  "triggered": false
}
```

**Recurring Notifications**:
```
Daily reminder:
{
  "schedule_id": "sched_456",
  "template_id": "daily_reminder",
  "user_id": "user_123",
  "cron": "0 9 * * *",  // Every day at 9 AM
  "timezone": "America/New_York",
  "enabled": true
}

Quartz Job:
- Executes at cron time
- Generates new notification
- Publishes to Kafka
- Repeats next day
```

**Timezone Handling**:
```
Challenge: Send "9 AM notification" to users in different timezones

Solution: Store user timezone, schedule per timezone

Example:
- User A (NYC, UTC-5): Schedule for 14:00 UTC
- User B (London, UTC+0): Schedule for 09:00 UTC
- User C (Tokyo, UTC+9): Schedule for 00:00 UTC

Quartz triggers at these times in UTC
```

---

## Database Design

### 1. Notification Database (Cassandra)

**Why Cassandra?**
- Write-heavy (11.5K writes/second)
- Time-series data (notifications by timestamp)
- Horizontal scalability
- Multi-datacenter replication
- Tunable consistency

**Schema**:

```cql
-- Notifications by user (main table)
CREATE TABLE notifications_by_user (
    user_id uuid,
    created_at timestamp,
    notification_id timeuuid,
    template_id uuid,
    channel text,
    priority text,
    status text,
    title text,
    body text,
    data text,  -- JSON
    delivered_at timestamp,
    clicked_at timestamp,
    PRIMARY KEY ((user_id), created_at, notification_id)
) WITH CLUSTERING ORDER BY (created_at DESC, notification_id DESC);

-- Notifications by ID (lookup table)
CREATE TABLE notifications_by_id (
    notification_id timeuuid PRIMARY KEY,
    user_id uuid,
    template_id uuid,
    channel text,
    priority text,
    status text,
    title text,
    body text,
    data text,
    created_at timestamp,
    sent_at timestamp,
    delivered_at timestamp,
    clicked_at timestamp,
    failed_reason text
);

-- Status history
CREATE TABLE notification_status_history (
    notification_id timeuuid,
    status text,
    timestamp timestamp,
    details text,
    PRIMARY KEY ((notification_id), timestamp)
) WITH CLUSTERING ORDER BY (timestamp ASC);

-- Bulk campaigns
CREATE TABLE campaign_notifications (
    campaign_id uuid,
    user_id uuid,
    notification_id timeuuid,
    status text,
    created_at timestamp,
    PRIMARY KEY ((campaign_id), user_id)
);
```

**Partitioning Strategy**:
```
notifications_by_user: Partition by user_id
- All user's notifications co-located
- Efficient pagination (ORDER BY created_at DESC)
- Limit: LIMIT 50 for recent notifications

notifications_by_id: Partition by notification_id
- Single partition lookups (fast)
- Used for status updates, click tracking

Replication Factor: 3
Consistency Level: QUORUM (write), ONE (read)
Compaction Strategy: Time Window (TWCS)
TTL: 90 days (auto-delete old notifications)
```

### 2. User Database (PostgreSQL)

**Schema**:

```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    phone_number VARCHAR(20),
    timezone VARCHAR(50) DEFAULT 'UTC',
    language VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(user_id),
    email_enabled BOOLEAN DEFAULT true,
    push_enabled BOOLEAN DEFAULT true,
    sms_enabled BOOLEAN DEFAULT false,
    quiet_hours_enabled BOOLEAN DEFAULT false,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    global_unsubscribe BOOLEAN DEFAULT false,
    preferences_json JSONB,  -- Flexible schema
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE category_preferences (
    user_id UUID REFERENCES users(user_id),
    category VARCHAR(50),
    channel VARCHAR(20),
    enabled BOOLEAN DEFAULT true,
    PRIMARY KEY (user_id, category, channel)
);

CREATE TABLE device_tokens (
    device_id VARCHAR(100) PRIMARY KEY,
    user_id UUID REFERENCES users(user_id),
    platform VARCHAR(20),  -- ios, android, web
    token TEXT,
    registered_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    is_valid BOOLEAN DEFAULT true
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
CREATE INDEX idx_device_tokens_valid ON device_tokens(user_id, is_valid) WHERE is_valid = true;

CREATE TABLE templates (
    template_id UUID PRIMARY KEY,
    name VARCHAR(100) UNIQUE,
    category VARCHAR(50),
    version VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    template_json JSONB,  -- Full template structure
    created_by UUID,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_templates_name_version ON templates(name, version);

CREATE TABLE scheduled_notifications (
    schedule_id UUID PRIMARY KEY,
    notification_data JSONB,
    schedule_at TIMESTAMP,
    timezone VARCHAR(50),
    recurring BOOLEAN DEFAULT false,
    cron_expression VARCHAR(100),
    triggered BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_scheduled_due ON scheduled_notifications(schedule_at) 
    WHERE triggered = false;
```

**Sharding Strategy**:
```
Shard by user_id (mod 16 shards)

Shard routing:
  shard_id = hash(user_id) % 16

Benefits:
- User data co-located in same shard
- Efficient joins within shard
- Scale to billions of users

Cross-shard queries:
- Avoid when possible
- Use read replicas for analytics
```

### 3. Analytics Database (ClickHouse)

**Purpose**: Fast analytical queries on notification metrics

**Schema**:

```sql
CREATE TABLE notification_events (
    event_id UUID,
    notification_id UUID,
    user_id UUID,
    template_id UUID,
    channel String,
    priority String,
    event_type String,  -- sent, delivered, clicked, failed
    timestamp DateTime,
    campaign_id Nullable(UUID),
    metadata String  -- JSON
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, user_id, notification_id);

-- Metrics
CREATE MATERIALIZED VIEW notification_metrics_hourly
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, channel, priority)
AS SELECT
    toStartOfHour(timestamp) as hour,
    channel,
    priority,
    countIf(event_type = 'sent') as sent_count,
    countIf(event_type = 'delivered') as delivered_count,
    countIf(event_type = 'clicked') as clicked_count,
    countIf(event_type = 'failed') as failed_count
FROM notification_events
GROUP BY hour, channel, priority;
```

**Query Examples**:
```sql
-- Delivery rate last 24 hours
SELECT 
    channel,
    sent_count,
    delivered_count,
    (delivered_count * 100.0 / sent_count) as delivery_rate
FROM notification_metrics_hourly
WHERE hour >= now() - INTERVAL 24 HOUR
GROUP BY channel;

-- Click-through rate by template
SELECT
    template_id,
    countIf(event_type = 'delivered') as delivered,
    countIf(event_type = 'clicked') as clicked,
    (clicked * 100.0 / delivered) as ctr
FROM notification_events
WHERE timestamp >= now() - INTERVAL 7 DAY
GROUP BY template_id
ORDER BY ctr DESC;
```

---

## Notification Delivery Flow

### End-to-End Flow: Order Confirmation

```
User places order → Need to send confirmation

┌─────────────────────────────────────────────────┐
│ T=0ms: Order Service triggers notification      │
└─────────────────────────────────────────────────┘

POST /api/v1/notifications/send
{
  "user_id": "user_alice_123",
  "template_id": "order_confirmation",
  "channel": ["email", "push"],
  "priority": "HIGH",
  "data": {
    "order_id": "ORD-98765",
    "order_total": "$129.99",
    "user_name": "Alice"
  }
}

┌─────────────────────────────────────────────────┐
│ T=5ms: Notification Service                     │
└─────────────────────────────────────────────────┘

1. Validate request
2. Generate notification_id: notif_abc123
3. Check user preferences (Redis cache):
   GET user:alice:preferences
   → email: enabled, push: enabled ✓

4. Check rate limit (Redis):
   ZCOUNT rate_limit:alice:HIGH 1704672000 1704672060
   → 2 notifications in last minute (< 10 limit) ✓

5. Publish to Kafka high_priority queue
6. Return 202 Accepted

Response:
{
  "notification_id": "notif_abc123",
  "status": "QUEUED",
  "channels_queued": ["email", "push"]
}

┌─────────────────────────────────────────────────┐
│ T=10ms: Priority Queue Worker                   │
└─────────────────────────────────────────────────┘

1. Consume from Kafka high_priority queue
2. Fetch template from cache:
   GET template:order_confirmation:2.0
   
3. Render for email channel:
   Subject: "Order ORD-98765 Confirmed"
   Body: "Hi Alice, your order for $129.99..."
   
4. Render for push channel:
   Title: "Order Confirmed!"
   Body: "Hi Alice, order ORD-98765 confirmed"

5. Split into two delivery tasks:
   - Email delivery task
   - Push delivery task

6. Publish to delivery queues

┌─────────────────────────────────────────────────┐
│ T=50ms: Email Worker                            │
└─────────────────────────────────────────────────┘

1. Consume email task
2. Fetch user email: alice@example.com (from cache)
3. Build email payload
4. Send via SendGrid API
5. SendGrid returns 202 Accepted
6. Update status: SENT
7. Write to Cassandra (async)

┌─────────────────────────────────────────────────┐
│ T=100ms: Push Worker                            │
└─────────────────────────────────────────────────┘

1. Consume push task
2. Fetch device tokens:
   - iOS: apns_token_xyz
   - Android: fcm_token_abc
   
3. Send to APNs (iOS):
   - Build payload
   - POST to APNs
   - Response: 200 OK
   
4. Send to FCM (Android):
   - Build payload
   - POST to FCM
   - Response: Success
   
5. Update status: DELIVERED
6. Write to Cassandra

┌─────────────────────────────────────────────────┐
│ T=2000ms: SendGrid delivers email                │
└─────────────────────────────────────────────────┘

1. SendGrid delivers to inbox
2. Webhook callback:
   POST /webhooks/sendgrid/events
   {
     "event": "delivered",
     "notification_id": "notif_abc123",
     "timestamp": 1704672002
   }

3. Update Cassandra: status = DELIVERED
4. Publish analytics event

┌─────────────────────────────────────────────────┐
│ T=60000ms: User opens app, sees push             │
└─────────────────────────────────────────────────┘

1. User taps notification
2. App opens, tracks click
3. Sends analytics:
   POST /api/v1/notifications/notif_abc123/click
   
4. Update Cassandra: clicked_at = NOW()
5. Publish analytics event

Total latency:
- API response: 5ms
- Push delivered: 100ms
- Email delivered: 2 seconds
- User engagement tracked: Real-time
```

---

## Deep Dives

### 1. Handling Provider Failures

**Scenario**: APNs is down, can't deliver push notifications

**Detection**:
```
1. Push worker makes request to APNs
2. Connection timeout (30 seconds)
3. Or error response: 503 Service Unavailable
4. Worker detects failure
```

**Handling Strategy**:
```
Circuit Breaker Pattern:

States:
- CLOSED: Normal operation
- OPEN: Provider failed, stop sending
- HALF_OPEN: Testing if recovered

Flow:
1. APNs fails 5 times in 1 minute
2. Circuit breaker opens
3. Stop sending to APNs for 5 minutes
4. Queue notifications for retry
5. After 5 minutes: Try one request (half-open)
6. If success: Close circuit, resume
7. If failure: Open again, wait longer (10 min)

Benefits:
- Prevent cascading failures
- Give provider time to recover
- Don't lose notifications (queued for retry)
```

**Fallback Strategy**:
```
Multi-provider approach:

Primary: APNs
Fallback: SNS (AWS) → APNs

If APNs direct fails:
1. Route through AWS SNS
2. SNS handles retry logic
3. More reliable (AWS infrastructure)

Cost trade-off:
- Direct APNs: Free
- SNS → APNs: $0.50 per million
- Use fallback only when needed
```

### 2. Bulk Campaign Optimization

**Challenge**: Send 10M push notifications in 10 minutes

**Naive Approach** (slow):
```
For each user:
  1. Check preferences (DB query)
  2. Fetch device token (DB query)
  3. Render template
  4. Send to FCM
  
Total: 10M × 4 operations = 40M operations
Time: 10M / 1000 QPS = 10,000 seconds = 2.7 hours ❌
```

**Optimized Approach**:
```
1. Pre-warming:
   - Load all user preferences into Redis
   - Cache device tokens
   - Render template once (same for all)

2. Batching:
   - Process 1000 users at a time
   - Parallel processing (100 workers)
   - Each worker handles 100K users
   
3. Fan-out:
   - Kafka partitioned by user_id
   - Each partition processed independently
   - 100 partitions = 100 parallel streams
   
4. Provider batching:
   - FCM supports 500 tokens per request
   - Batch users by device token
   - Send in bulk (massive speedup)
   
Time: 10M / (100 workers × 1000 QPS) = 100 seconds ✓
```

**Fan-out Architecture**:
```
Campaign Coordinator:
1. Receive bulk send request
2. Query database for recipient user_ids
3. Split into chunks of 10K users
4. Publish each chunk to Kafka
5. Return immediately (async processing)

Workers (100 instances):
1. Consume chunks from Kafka
2. For each chunk:
   - Batch preference checks (Redis MGET)
   - Batch template rendering
   - Group by device platform
   - Send in batches to providers
3. Track progress in Redis
4. Update campaign status

Progress tracking:
  Key: campaign:campaign_xyz:progress
  Value: {sent: 5000000, failed: 1000, in_progress: 4999000}
  
Client polls: GET campaign:campaign_xyz:progress
```

### 3. Preventing Duplicate Notifications

**Problem**: Network retry may cause duplicates

**Solution 1: Idempotency Keys** (Client-side)
```
Client generates UUID:
{
  "idempotency_key": "client_uuid_12345",
  "user_id": "user_123",
  ...
}

Server checks Redis:
GET idempotency:client_uuid_12345

If exists:
  → Return cached response (duplicate prevented)
If not:
  → Process notification
  → Cache response (TTL: 24 hours)
  → Return response
```

**Solution 2: Notification Hash** (Server-side)
```
Generate hash from notification content:
hash = SHA256(user_id + template_id + data + timestamp_minute)

Before sending:
  Check Redis: EXISTS notification_hash:<hash>
  
If exists:
  → Skip (duplicate)
If not:
  → Send notification
  → Set Redis: SETEX notification_hash:<hash> 300 1
  → Expires in 5 minutes

Prevents duplicates within 5-minute window
```

**Solution 3: Provider Deduplication**
```
FCM has built-in deduplication:
- Use "collapse_key" in payload
- FCM keeps only latest message with same key
- Older messages replaced

APNs:
- Use "apns-collapse-id" header
- Similar behavior to FCM

Benefit: Even if we send duplicates, provider filters
```

### 4. Smart Delivery Timing

**Goal**: Send notifications when user most likely to engage

**User Behavior Analysis**:
```
Track user activity patterns:
- When user opens app (hourly distribution)
- When user clicks notifications (success rate by hour)
- User's timezone and sleep schedule

Store in profile:
{
  "user_id": "user_123",
  "optimal_send_times": [9, 12, 18, 20],  // Hours in user TZ
  "never_send": [0, 1, 2, 3, 4, 5, 6, 7],  // Sleep hours
  "best_hour": 18,  // 6 PM has highest engagement
  "timezone": "America/New_York"
}
```

**Smart Scheduling**:
```
Non-critical notification:
1. Check current time in user's timezone
2. If in optimal window: Send immediately
3. If not: Schedule for next optimal time
4. Example:
   - Current: 2 AM user time
   - Next optimal: 9 AM (7 hours later)
   - Schedule for 9 AM

Critical notification:
- Always send immediately (ignore optimization)
```

**Machine Learning Model**:
```
Features:
- User demographics (age, location)
- Historical engagement rate
- Time of day, day of week
- Notification type
- Recent activity patterns

Model predicts:
- Probability of user engaging
- If < 20%: Delay to better time
- If > 60%: Send immediately

Update model weekly based on new data
```

### 5. Notification Grouping/Stacking

**Problem**: User gets bombarded with notifications

**Solution**: Group related notifications

**Example - Social App**:
```
Separate notifications:
- "Alice liked your photo" (10:00 AM)
- "Bob liked your photo" (10:02 AM)
- "Charlie liked your photo" (10:05 AM)
→ User gets 3 notifications in 5 minutes

Grouped notification:
- "Alice, Bob, and Charlie liked your photo" (10:05 AM)
→ User gets 1 notification
```

**Implementation**:
```
1. Hold notifications for N seconds (e.g., 60 seconds)
2. Group similar notifications:
   - Same type (likes, comments, follows)
   - Same target (same photo, same post)
3. After N seconds:
   - If 1 notification: Send individual
   - If 2-3: "Alice and 2 others liked your photo"
   - If 4+: "Alice and 3 others liked your photo"
4. Update notification if user already received:
   - Replace old notification with grouped one
   - Use APNs collapse-id / FCM collapse-key

Benefits:
- Reduce notification fatigue
- Improve user experience
- Lower provider costs
```

**Stacking on Device**:
```
iOS/Android handle stacking automatically:
- Notifications from same app grouped
- Show count: "3 new notifications"
- Expand to see details

We provide grouping key:
APNs: "thread-id": "photo_likes_12345"
FCM: "notification.tag": "photo_likes_12345"

Same thread-id = stacked together
```

### 6. A/B Testing Notifications

**Goal**: Test which notification performs better

**Setup**:
```
Template variants:
- Variant A: "Your order is confirmed! 🎉"
- Variant B: "Order confirmed - arriving in 2 days"
- Variant C: "Great news! Your order #12345 is confirmed"

Distribution: 33% / 33% / 34%
```

**Implementation**:
```
1. Create template with variants:
   {
     "template_id": "order_confirmation_test",
     "variants": [
       {"id": "A", "title": "...", "weight": 33},
       {"id": "B", "title": "...", "weight": 33},
       {"id": "C", "title": "...", "weight": 34}
     ]
   }

2. When sending notification:
   - Hash user_id to get consistent variant
   - Always show same variant to same user
   - Track which variant used

3. Collect metrics:
   - Click-through rate per variant
   - Conversion rate per variant
   - Uninstall rate per variant

4. After 10K notifications:
   - Statistical significance test
   - Winner: Variant with highest CTR
   - Promote to default template
```

**Analysis**:
```
Results after 1 week:
Variant A: 15% CTR, 5% conversion
Variant B: 12% CTR, 6% conversion ← Winner (better conversion)
Variant C: 14% CTR, 4% conversion

Choose B: Higher conversion = more business value
```

---

## Scalability & Reliability

### Horizontal Scaling

**Stateless Services** (Easy to scale):
```
Auto-scaling rules:
- Scale up: CPU > 70% for 5 minutes
- Scale down: CPU < 30% for 15 minutes
- Min instances: 10 per region
- Max instances: 200 per region

Services:
- Notification Service: Scale based on request rate
- Template Service: Scale based on render requests
- Preference Service: Scale based on lookup rate
- Delivery Workers: Scale based on queue depth
```

**Worker Scaling**:
```
Kafka Consumer Groups enable horizontal scaling:

Current: 10 workers in consumer group
Queue depth: 100K notifications
Processing: 1K/second

If queue depth > 50K for 5 minutes:
  → Add 10 more workers
  → Kafka rebalances partitions
  → Now 20 workers processing
  → Processing: 2K/second
  → Queue drains in 50 seconds

Scale down when queue < 10K
```

### Database Scaling

**Cassandra**:
```
Horizontal scaling (add nodes):
Current: 30 nodes, 90TB storage, 30K writes/sec

To add capacity:
1. Add new nodes (10 nodes)
2. Cassandra streams data automatically
3. Rebalancing time: 2-4 hours
4. No downtime

Result: 40 nodes, 120TB storage, 40K writes/sec
```

**PostgreSQL**:
```
Vertical: Upgrade instance (db.r5.2xlarge → db.r5.4xlarge)
Horizontal: Read replicas

Read replicas:
- Master: All writes
- 5 replicas: Reads (preferences, templates)
- Replica lag: < 1 second
- Route reads to nearest replica

Connection pooling:
- PgBouncer: 1000 connections → 100 DB connections
- Reduces DB load
```

**Redis Cluster**:
```
Current: 10 nodes, 20GB memory, 100K ops/sec

Add capacity:
1. Add new nodes to cluster
2. Redis Cluster reshards automatically
3. 16,384 hash slots distributed evenly
4. No downtime

Result: 20 nodes, 40GB memory, 200K ops/sec
```

### Multi-Region Deployment

**Active-Active Configuration**:
```
3 Regions: US-East, EU-West, AP-South

Each region:
- Full stack (API, workers, databases)
- Processes notifications independently
- Writes to local Cassandra
- Cassandra replicates across regions

User routing:
- DNS routes to nearest region
- WebSockets connect to local region
- Notifications processed locally
- Low latency (< 100ms)

Benefits:
- Lower latency
- Regional failover
- Compliance (data residency)
```

**Cross-Region Replication**:
```
Cassandra:
- NetworkTopologyStrategy
- RF=3 per region (9 total replicas)
- LOCAL_QUORUM writes (2/3 in local region)
- Async replication to other regions

PostgreSQL:
- Logical replication to other regions
- Read replicas in each region
- Master-master not recommended (conflicts)

Redis:
- Independent per region (no replication)
- Eventually consistent (acceptable)
```

### High Availability

**Failure Scenarios**:

**1. API Server Failure**:
```
Detection: Load balancer health check fails

Impact: That server stops receiving traffic

Mitigation:
- Load balancer removes from rotation
- Traffic distributed to healthy servers
- Auto-scaling adds replacement

Recovery time: < 30 seconds
```

**2. Worker Failure**:
```
Detection: Kafka consumer group heartbeat timeout

Impact: Notifications not processed from assigned partitions

Mitigation:
- Kafka rebalances partitions to healthy workers
- Unprocessed messages handled by other workers
- No message loss (Kafka durability)

Recovery time: < 10 seconds (rebalance)
```

**3. Database Failure**:
```
Cassandra node failure:
- Hinted handoff queues writes
- Read from replica nodes
- Automatic repair when node returns
- No downtime

PostgreSQL master failure:
- Promote read replica to master
- Update DNS/connection string
- Downtime: 1-2 minutes (failover)

Redis node failure:
- Redis Cluster promotes replica
- Automatic failover
- Downtime: < 5 seconds
```

**4. Provider Failure (APNs/FCM down)**:
```
Detection: Circuit breaker opens (5 failures)

Impact: Push notifications not delivered

Mitigation:
- Queue notifications for retry
- Switch to fallback provider (SNS)
- Alert engineering team
- Update status page

Recovery:
- Circuit breaker retries every 5 minutes
- When provider recovers: Resume
- Drain retry queue
```

**5. Entire Region Failure**:
```
Detection: All health checks fail in region

Impact: Users in that region can't send notifications

Mitigation:
- DNS failover to another region
- Users routed to next nearest region
- Slightly higher latency
- No data loss (Cassandra replicated)

Recovery time: 2-5 minutes (DNS TTL)
```

### Monitoring & Alerting

**Key Metrics**:
```
System Health:
- API latency (p50, p95, p99)
- Queue depth per priority
- Worker CPU/memory usage
- Database connections
- Cache hit ratio

Delivery Metrics:
- Notifications sent/sec
- Delivery success rate
- Provider latency
- Failed deliveries
- Retry queue size

Business Metrics:
- Click-through rate
- Conversion rate
- Unsubscribe rate
- User engagement
```

**Alerts**:
```
P0 (Page immediately):
- API error rate > 5%
- Critical queue depth > 100K
- Provider circuit breaker open
- Database down
- Region unavailable

P1 (Alert on-call):
- API latency p95 > 500ms
- Delivery success rate < 95%
- Queue processing lag > 5 minutes
- Cache hit ratio < 80%

P2 (Notify during business hours):
- Increasing unsubscribe rate
- Template rendering errors
- Slow queries detected
```

**Dashboards**:
```
Operations Dashboard:
- Real-time throughput
- Queue depths
- Worker health
- Database performance
- Provider status

Business Dashboard:
- Delivery rates by channel
- Click-through rates
- Campaign performance
- User engagement trends
- Cost breakdown
```

### Disaster Recovery

**Backup Strategy**:
```
Cassandra:
- Daily snapshots to S3
- Point-in-time recovery (7 days)
- Cross-region replication
- Test restore monthly

PostgreSQL:
- Continuous WAL archiving
- Daily snapshots
- 30-day retention
- Test restore weekly

Redis:
- RDB snapshots every 6 hours
- AOF enabled (every second)
- Replication to standby
```

**Recovery Procedures**:
```
Data Loss Scenarios:

1. Accidental deletion:
   - Restore from backup
   - Time: 1-4 hours
   - Data loss: Up to last backup

2. Region failure:
   - Failover to another region
   - Time: 2-5 minutes
   - No data loss (replication)

3. Database corruption:
   - Restore from snapshot
   - Replay WAL/AOF
   - Time: 2-8 hours
   - Data loss: Minimal

Recovery Time Objective (RTO): 4 hours
Recovery Point Objective (RPO): 15 minutes
```

---

## Trade-offs & Alternatives

### 1. Message Queue: Kafka vs Alternatives

**Kafka** (Chosen):
```
Pros:
+ High throughput (1M+ msg/sec)
+ Durability (disk-backed)
+ Replay capability
+ Consumer groups (scaling)
+ Order guarantees per partition

Cons:
- Complex to operate
- Higher latency (disk writes)
- Resource intensive
- Steeper learning curve

Use when: High volume, need durability, replay
```

**RabbitMQ**:
```
Pros:
+ Easier to operate
+ Lower latency (memory-first)
+ Rich routing features
+ Better for small-medium scale

Cons:
- Lower throughput (100K msg/sec)
- Memory-bound (not durable by default)
- No replay
- Complex at large scale

Use when: Lower volume, need routing flexibility
```

**Amazon SQS**:
```
Pros:
+ Fully managed (no ops)
+ Unlimited throughput
+ Auto-scaling
+ Pay per use

Cons:
- Higher cost at scale
- Max 120K messages in-flight
- No strict ordering (FIFO limited)
- Less control

Use when: Want managed service, moderate volume
```

### 2. Database: Cassandra vs Alternatives

**Cassandra** (Chosen for notifications):
```
Pros:
+ Write-optimized
+ Linear scalability
+ Multi-datacenter
+ Time-series friendly
+ No single point of failure

Cons:
- Eventual consistency
- No joins
- Difficult to model
- Higher operational complexity

Use when: High write volume, time-series data
```

**MongoDB**:
```
Pros:
+ Flexible schema
+ Rich query language
+ Easier to learn
+ Good for varied data

Cons:
- Single master (write bottleneck)
- Sharding complex
- Not optimized for time-series
- Consistency issues historically

Use when: Need flexible schema, complex queries
```

**PostgreSQL with TimescaleDB**:
```
Pros:
+ SQL familiarity
+ ACID guarantees
+ Rich features
+ Time-series extension

Cons:
- Vertical scaling limits
- Sharding not native
- Single master writes
- Higher cost at scale

Use when: Need SQL, strong consistency, moderate scale
```

### 3. Caching: Redis vs Alternatives

**Redis** (Chosen):
```
Pros:
+ In-memory speed (< 1ms)
+ Rich data structures
+ Pub/Sub support
+ Atomic operations
+ Cluster mode

Cons:
- Memory expensive
- Data loss on crash (without AOF)
- Single-threaded per shard

Use when: Need speed, atomic operations, pub/sub
```

**Memcached**:
```
Pros:
+ Simple, fast
+ Multi-threaded
+ Lower memory overhead
+ Easy to scale

Cons:
- Only key-value (no data structures)
- No persistence
- No pub/sub
- Limited atomic operations

Use when: Simple caching, high concurrency
```

**DynamoDB**:
```
Pros:
+ Fully managed
+ Unlimited scale
+ Durable storage
+ No memory limits

Cons:
- Higher latency (5-10ms)
- More expensive
- No pub/sub
- Eventual consistency

Use when: Need durability, unlimited scale
```

### 4. Push vs Pull for Delivery Status

**Push (Webhooks)** (Chosen):
```
Pros:
+ Real-time updates
+ Lower API calls
+ Efficient
+ Provider-initiated

Cons:
- Requires webhook endpoint
- Security concerns (validate signatures)
- Retry logic needed
- Can be unreliable

Use when: Need real-time status, have infrastructure
```

**Pull (Polling)**:
```
Pros:
+ Simple to implement
+ No webhook infrastructure
+ Control over rate
+ More reliable

Cons:
- Higher latency
- Wasted API calls (empty polls)
- Higher cost
- Not real-time

Use when: Simple setup, don't need real-time
```

### 5. Synchronous vs Asynchronous Processing

**Asynchronous** (Chosen):
```
Pros:
+ Fast API response
+ Handle spikes (queue buffers)
+ Retry on failure
+ Scale independently

Cons:
- More complex
- Eventual consistency
- Harder to debug
- Need message queue

Use when: High volume, need resilience
```

**Synchronous**:
```
Pros:
+ Simple to implement
+ Immediate feedback
+ Easier to debug
+ Strong consistency

Cons:
- Slow API response
- Can't handle spikes
- Blocks on provider failure
- Tight coupling

Use when: Low volume, need immediate confirmation
```

### 6. Multi-Tenant vs Single-Tenant

**Multi-Tenant** (Chosen):
```
Pros:
+ Resource sharing (cost-effective)
+ Easier to maintain
+ Faster feature rollout
+ Lower operational overhead

Cons:
- Noisy neighbor problem
- Less customization
- Security isolation concerns
- One bug affects all

Use when: SaaS product, many small customers
```

**Single-Tenant**:
```
Pros:
+ Full isolation
+ Dedicated resources
+ Custom configurations
+ Better security

Cons:
- Higher cost per customer
- More operational complexity
- Slower updates
- Resource waste

Use when: Enterprise customers, compliance needs
```

---

## Interview Questions & Answers

### Q1: How do you handle 10M notifications in 1 minute (spike)?

**Answer**:
```
1. Queue-based architecture:
   - API immediately queues to Kafka (< 50ms response)
   - Returns accepted status
   - Processing happens async

2. Auto-scaling workers:
   - Monitor queue depth
   - If > threshold: Scale up workers
   - Kafka rebalances load automatically

3. Priority queues:
   - Critical notifications: Separate queue, fast-track
   - Bulk notifications: Lower priority queue

4. Provider batching:
   - Group 500 users per FCM request
   - Parallel sending (100 workers)
   - Complete in 2-3 minutes

5. Rate limiting:
   - Throttle low-priority notifications
   - Ensure critical ones go through
```

### Q2: How do you prevent notification spam?

**Answer**:
```
1. Rate limiting (Redis sliding window):
   - Per user: 10 notifications/hour
   - Per category: Marketing 2/day
   - Global: 20/hour all channels

2. User preferences:
   - Opt-in/opt-out per channel
   - Quiet hours (Do Not Disturb)
   - Category preferences

3. Intelligent grouping:
   - Group similar notifications
   - "3 people liked your photo" vs 3 separate

4. Smart timing:
   - Send when user likely to engage
   - ML model predicts best time
   - Delay non-critical notifications

5. Frequency capping:
   - Maximum notifications per week
   - Respect user-defined limits
```

### Q3: How do you ensure exactly-once delivery?

**Answer**:
```
Trick question: We don't! We guarantee at-least-once.

Why:
- Network failures may cause retries
- Distributed system = no perfect guarantee
- Cost of exactly-once too high

Instead:
1. Idempotent operations:
   - Use notification_id as dedup key
   - Client ignores duplicates

2. Provider deduplication:
   - FCM collapse-key
   - APNs collapse-id
   - Last message wins

3. Client-side deduplication:
   - Track received notification IDs
   - Ignore if already seen

4. Acceptable trade-off:
   - Better to deliver twice than zero
   - Duplicates rare in practice
   - User impact minimal
```

### Q4: How do you handle different time zones for scheduled notifications?

**Answer**:
```
1. Store user timezone in profile:
   - "America/New_York" (IANA format)

2. Schedule in UTC:
   - User wants 9 AM their time
   - NYC (UTC-5): Schedule for 14:00 UTC
   - London (UTC+0): Schedule for 09:00 UTC

3. Quartz scheduler:
   - Jobs trigger at specific UTC times
   - Process users in that timezone
   - Separate job per timezone/hour

4. Optimization:
   - Group users by timezone
   - Batch send for each group
   - Reduce scheduler overhead

5. Edge cases:
   - Daylight saving time
   - Use IANA database (handles DST)
   - Update schedules on DST transitions
```

### Q5: How do you monitor notification system health?

**Answer**:
```
1. Golden Signals:
   - Latency: API response time, delivery time
   - Traffic: Notifications/sec
   - Errors: Failure rate per channel
   - Saturation: Queue depth, CPU, memory

2. Business Metrics:
   - Delivery rate: 95%+ target
   - Click-through rate: Track by template
   - Unsubscribe rate: < 1%
   - Provider performance

3. Alerts (PagerDuty):
   - P0: API down, provider outage
   - P1: High latency, low delivery rate
   - P2: Increasing unsubscribes

4. Dashboards (Grafana):
   - Real-time throughput
   - Queue depths by priority
   - Provider latency
   - Regional distribution

5. Distributed Tracing (Jaeger):
   - End-to-end request tracking
   - Identify bottlenecks
   - Debug failures
```

---

## Conclusion

This notification system design handles 1 billion daily notifications with:
- **High scalability**: 116K peak QPS with auto-scaling
- **Multi-channel support**: Push, Email, SMS, In-app, Webhooks
- **Reliability**: At-least-once delivery, automatic retries
- **Low latency**: < 500ms for critical, < 2s for normal
- **User control**: Comprehensive preference management
- **Analytics**: Full tracking and reporting

Key design principles:
1. **Asynchronous processing**: Queue-based for resilience
2. **Priority-based delivery**: Critical notifications first
3. **Rate limiting**: Prevent spam, respect limits
4. **Horizontal scaling**: Add capacity by adding nodes
5. **Provider abstraction**: Switch providers seamlessly
6. **Observability**: Comprehensive monitoring and alerts

The system can scale to 10B notifications/day by adding more workers and database nodes without architectural changes.
