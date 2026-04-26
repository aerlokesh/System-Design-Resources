# Design an Enterprise Notification Service — Hello Interview Framework

> **Question**: Design a system for sending notifications (emails, push notifications, SMS) to millions of users, as might be used by Outlook, Windows, or Teams notification services — handling prioritization, deduplication, user preferences, and multi-channel delivery.
>
> **Asked at**: Microsoft, Amazon, Google, Meta
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **Multi-Channel Delivery**: Send notifications via push (APNS/FCM/WNS), email, SMS, in-app (WebSocket), and webhook. Support sending same notification across multiple channels.
2. **User Preferences**: Users configure per-channel, per-category notification preferences (e.g., "send email for @mentions but push only for DMs"). Quiet hours / Do Not Disturb.
3. **Prioritization**: Urgent notifications (security alerts, password reset) bypass quiet hours. Normal notifications respect user preferences. Low priority batched into digests.
4. **Deduplication**: Same logical event shouldn't produce duplicate notifications. Idempotent processing.
5. **Template System**: Notification content defined via templates with variables. Localization (i18n) support.
6. **Tracking & Analytics**: Track delivery status (sent, delivered, opened, clicked). Aggregate analytics per campaign/notification type.

#### Nice to Have (P1)
- Scheduled notifications (send at a specific time)
- Batching / digest (aggregate multiple events into one notification)
- A/B testing notification content
- Rate limiting per user (max N notifications per hour)
- Rich push notifications (images, action buttons)
- Notification inbox (in-app notification center with read/unread state)
- Unsubscribe management (one-click unsubscribe, compliance)

#### Below the Line (Out of Scope)
- Marketing email campaigns (separate system)
- In-app chat notifications (handled by Teams chat)
- Notification content creation UI

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Notifications/day** | 10 billion across all channels | Enterprise + consumer scale |
| **Throughput** | 500K notifications/sec peak | Burst during global events |
| **Delivery latency** | < 5 seconds for high priority, < 30s for normal | Timely delivery |
| **Availability** | 99.99% | Notifications are business-critical |
| **Delivery rate** | > 99.5% (excluding user opt-out) | High reliability |
| **At-least-once delivery** | Guarantee | Never lose a notification |
| **Deduplication window** | 5 minutes | Handle retries without spam |

### Capacity Estimation

```
Notifications:
  Total/day: 10B
  Push: 4B (40%)
  Email: 3B (30%)
  SMS: 500M (5%)
  In-app: 2B (20%)
  Webhook: 500M (5%)
  
  Avg notifications/sec: 115K
  Peak: 500K/sec

Users:
  Total users with notification preferences: 500M
  Average devices per user: 2.5
  Total device tokens: 1.25B

Templates:
  Active templates: 50K
  Languages supported: 40

Storage:
  Notification events/day: 10B × 200 bytes = 2 TB/day
  Delivery status logs: 10B × 100 bytes = 1 TB/day
  User preferences: 500M × 2 KB = 1 TB
```

---

## 2️⃣ Core Entities

```
┌────────────────┐     ┌───────────────────┐     ┌────────────────────┐
│ NotificationReq │     │ NotificationEvent  │     │ DeliveryAttempt    │
│                 │     │                    │     │                    │
│ requestId       │────▶│ eventId            │────▶│ attemptId          │
│ senderId        │     │ requestId          │     │ eventId            │
│ recipientIds[]  │     │ recipientId        │     │ channel            │
│ templateId      │     │ channel            │     │ status (pending/   │
│ variables{}     │     │ priority           │     │  sent/delivered/   │
│ channels[]      │     │ status             │     │  opened/failed)    │
│ priority        │     │ content            │     │ providerResponse   │
│ deduplicationKey│     │ scheduledAt        │     │ timestamp          │
│ scheduledAt     │     │ sentAt             │     │ retryCount         │
└────────────────┘     │ deliveredAt        │     └────────────────────┘
                        │ expiresAt          │
                        └───────────────────┘
                        
┌────────────────┐     ┌───────────────────┐     ┌────────────────────┐
│ UserPreference  │     │ DeviceToken        │     │ Template           │
│                 │     │                    │     │                    │
│ userId          │     │ tokenId            │     │ templateId         │
│ channel         │     │ userId             │     │ name               │
│ category        │     │ platform (ios/     │     │ channel            │
│ enabled         │     │  android/windows/  │     │ subject            │
│ quietHoursStart │     │  web)              │     │ bodyTemplate       │
│ quietHoursEnd   │     │ token              │     │ variables[]        │
│ timezone        │     │ lastActive         │     │ locale             │
│ frequency       │     │ appVersion         │     │ version            │
│ (immediate/     │     └───────────────────┘     └────────────────────┘
│  digest/off)    │
└────────────────┘
```

---

## 3️⃣ API Design

### Send Notification
```
POST /api/v1/notifications/send
Authorization: Bearer <service_token>

Request:
{
  "templateId": "new_message_mention",
  "recipients": [
    { "userId": "user_123" },
    { "userId": "user_456" },
    { "groupId": "team_engineering" }   // expands to group members
  ],
  "variables": {
    "senderName": "Alice",
    "channelName": "#general",
    "messagePreview": "Hey team, check out the new design..."
  },
  "channels": ["push", "email"],        // preferred channels
  "priority": "normal",                  // urgent | high | normal | low
  "deduplicationKey": "msg_789_mention", // prevent duplication
  "expiresAt": "2025-01-15T11:00:00Z"   // optional TTL
}

Response: 202 Accepted
{
  "requestId": "req_abc123",
  "status": "queued",
  "recipientCount": 42
}
```

### Get Delivery Status
```
GET /api/v1/notifications/req_abc123/status

Response: 200 OK
{
  "requestId": "req_abc123",
  "totalRecipients": 42,
  "delivered": 38,
  "pending": 2,
  "failed": 2,
  "channels": {
    "push": { "sent": 40, "delivered": 36, "opened": 12 },
    "email": { "sent": 42, "delivered": 38, "opened": 8 }
  }
}
```

---

## 4️⃣ Data Flow

```
Producer Service (Teams, Outlook, etc.)
        │
        ▼
   POST /notifications/send → Notification API
        │
        ├── 1. Validate request, resolve group → individual users
        ├── 2. Dedup check: has deduplicationKey been seen in last 5 min?
        │       Redis: SETNX dedup:{key} 1 EX 300
        │       If already exists → return 200 (idempotent, skip)
        ├── 3. Enqueue to priority queue (Kafka / Service Bus)
        │
        ▼
   Notification Worker (consumes from queue)
        │
        ├── For each recipient:
        │   ├── 1. Load user preferences (Redis cache → DB fallback)
        │   │       Is this category enabled? Which channels?
        │   ├── 2. Check quiet hours (user timezone):
        │   │       Quiet hours? → hold for later (unless urgent)
        │   ├── 3. Rate limit check: too many notifications recently?
        │   │       Redis: INCR ratelimit:{userId}:{hour} → if > 50, throttle
        │   ├── 4. Render content from template + variables + locale
        │   ├── 5. Route to channel-specific delivery service:
        │   │
        │   ├──▶ Push Delivery Service
        │   │     ├── Lookup device tokens (Redis)
        │   │     ├── Send to APNS / FCM / WNS
        │   │     └── Handle token refresh / invalid token cleanup
        │   │
        │   ├──▶ Email Delivery Service
        │   │     ├── Render HTML email from template
        │   │     ├── Send via SMTP (internal email service)
        │   │     └── Track opens via tracking pixel
        │   │
        │   ├──▶ SMS Delivery Service
        │   │     ├── Send via SMS gateway (Twilio / Azure Comm Services)
        │   │     └── Handle delivery receipts
        │   │
        │   ├──▶ In-App Delivery Service
        │   │     ├── Push via WebSocket (if user online)
        │   │     └── Store in notification inbox (read later)
        │   │
        │   └──▶ Webhook Delivery Service
        │         ├── POST to registered URL
        │         └── Retry with exponential backoff on failure
        │
        └── 6. Log delivery event → Analytics pipeline (Kafka → ClickHouse)
```

---

## 5️⃣ High-Level Design

```
┌────────────────────────────────────────────────────────────────────┐
│  PRODUCER SERVICES                                                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  Teams  │ │ Outlook │ │ OneDrive│ │ Windows │ │ Azure   │   │
│  │  Chat   │ │  Email  │ │  Sync   │ │ Update  │ │ Alerts  │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   │
│       └────────────┴──────────┴────────────┴────────────┘        │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│                    NOTIFICATION PLATFORM                            │
│                                                                    │
│  ┌───────────────┐  ┌──────────────────────────────────────┐     │
│  │ Notification   │  │ Priority Queues (Kafka/Service Bus)  │     │
│  │ API Gateway    │──▶│                                      │     │
│  │                │  │ ┌────────┐ ┌────────┐ ┌──────────┐  │     │
│  │ Validate       │  │ │Urgent  │ │Normal  │ │Low/Digest│  │     │
│  │ Dedup          │  │ │(0 lag) │ │(< 5s)  │ │(batched) │  │     │
│  │ Enqueue        │  │ └────┬───┘ └────┬───┘ └────┬─────┘  │     │
│  └───────────────┘  └──────┼──────────┼──────────┼─────────┘     │
│                             ▼          ▼          ▼               │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │          NOTIFICATION WORKERS (auto-scaled)               │    │
│  │                                                          │    │
│  │  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  │    │
│  │  │ Preference   │  │ Template      │  │ Rate Limiter │  │    │
│  │  │ Engine       │  │ Renderer      │  │              │  │    │
│  │  │              │  │               │  │ Per-user     │  │    │
│  │  │ User prefs   │  │ i18n          │  │ Per-channel  │  │    │
│  │  │ Quiet hours  │  │ Variables     │  │ Global       │  │    │
│  │  │ Channel      │  │ Personalize   │  │              │  │    │
│  │  │ selection    │  │               │  │              │  │    │
│  │  └──────────────┘  └───────────────┘  └──────────────┘  │    │
│  └──────────────────────────────────────────────────────────┘    │
│                             │                                     │
│  ┌──────────┬───────────────┼──────────────┬──────────────┐      │
│  ▼          ▼               ▼              ▼              ▼      │
│ ┌────────┐ ┌────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│ │ Push   │ │ Email  │ │   SMS    │ │ In-App   │ │ Webhook  │  │
│ │Delivery│ │Delivery│ │ Delivery │ │ Delivery │ │ Delivery │  │
│ │        │ │        │ │          │ │          │ │          │  │
│ │APNS    │ │SMTP    │ │Twilio/   │ │WebSocket │ │HTTP POST │  │
│ │FCM     │ │Service │ │Azure CS  │ │+ Inbox   │ │+ retry   │  │
│ │WNS     │ │        │ │          │ │          │ │          │  │
│ └────────┘ └────────┘ └──────────┘ └──────────┘ └──────────┘  │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                        DATA LAYER                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐    │
│  │ Redis    │ │ Cosmos DB│ │ Kafka    │ │ ClickHouse        │    │
│  │          │ │          │ │          │ │ (Analytics)        │    │
│  │ Prefs    │ │ Events   │ │ Queues   │ │                   │    │
│  │ Tokens   │ │ Delivery │ │ Delivery │ │ Delivery metrics  │    │
│  │ Dedup    │ │ logs     │ │ logs     │ │ Open/click rates  │    │
│  │ Rate lim │ │ Templates│ │          │ │ Channel perf      │    │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Priority Queue & Delivery Guarantees

**Problem**: Urgent security notifications must be delivered instantly, while low-priority marketing digests can wait. How to ensure priority ordering while guaranteeing at-least-once delivery?

**Solution: Multi-Lane Priority Queues with Dead Letter Handling**

```
Queue Architecture:

Three priority lanes (separate Kafka topics):
  1. URGENT (P0): security alerts, password reset, 2FA codes
     → Consumer group with max parallelism, 0 lag tolerance
     → SLA: delivered within 2 seconds
     
  2. NORMAL (P1): new message, mention, reply
     → Standard consumer group
     → SLA: delivered within 10 seconds
     
  3. LOW (P2): activity digest, weekly summary
     → Batching consumer (aggregate before send)
     → SLA: within 1 hour (batched into digest)

At-Least-Once Delivery:
  Producer → Kafka (with acks=all) → Consumer
  
  Consumer processing:
    1. Read message from Kafka
    2. Process (render, deliver to channel provider)
    3. If delivery succeeds → commit offset
    4. If delivery fails → DON'T commit → message will be redelivered
    5. After 3 retries → move to Dead Letter Queue (DLQ)
  
  DLQ handling:
    - Alert ops team
    - Auto-retry DLQ messages every 30 minutes
    - After 24 hours in DLQ → mark as permanently failed, log

Exactly-Once Semantics (at the application level):
  Each notification has eventId (UUID)
  Before delivering:
    Check Redis: SETNX delivered:{eventId}:{channel} 1 EX 86400
    If already set → skip (already delivered)
  This prevents duplicate deliveries even if Kafka redelivers
```

### Deep Dive 2: Smart Channel Selection & Fallback

**Problem**: A user has desktop (online), mobile (in pocket), and email. How do we pick the right channel and fall back if delivery fails?

**Solution: Cascading Channel Strategy with Device-Aware Routing**

```
Channel Selection Algorithm:

Input:
  - User preference for this notification category
  - User's current online/offline status per device
  - Notification priority

Decision Tree:
  1. If user opted out of this category → DON'T SEND (respect preference)
  
  2. If priority == URGENT:
     → Send on ALL enabled channels simultaneously
     → Bypass quiet hours
  
  3. If user is ONLINE (WebSocket connected):
     → Send in-app notification first
     → Wait 3 seconds for "seen" acknowledgment
     → If not seen → send push notification
     → If push fails → send email (after 5 min delay)
  
  4. If user is OFFLINE:
     → Send push notification
     → If push fails (token expired) → send email
     → Store in notification inbox (for when user opens app)
  
  5. For SMS (expensive, intrusive):
     → Only for URGENT + user has SMS enabled
     → Only if push + email failed

Fallback Chain:
  In-App → Push → Email → SMS (each with timeout before fallback)
  
  Timeline:
  T+0s:  Send in-app (WebSocket push)
  T+3s:  No read receipt → send push notification
  T+30s: Push not delivered (no FCM receipt) → send email
  T+5m:  For urgent only: still no engagement → send SMS

Device Token Management:
  On app install: register token → POST /devices
  On token refresh: update token → PUT /devices/{id}
  On uninstall: FCM/APNS returns "invalid token" → delete from registry
  
  Stale token cleanup:
    If device not active for 90 days → mark as inactive
    If 3 consecutive push failures → remove token
```

### Deep Dive 3: Notification Digest & Batching

**Problem**: A user gets 50 notifications in an hour from various sources. Sending 50 individual emails/pushes is spam. How to intelligently batch?

**Solution: Time-Window Aggregation with Personalized Digest**

```
Digest Pipeline:

Low-priority notifications enter digest queue:
  Kafka topic: "notifications.digest"
  
  Digest Worker:
    1. Aggregate by (userId, category, timeWindow)
    2. Time windows: 15 min, 1 hour, daily (configurable per user)
    3. When window closes → render digest

Example Digest:
  Individual events:
    - Alice replied to your comment in #design
    - Bob mentioned you in #engineering  
    - 5 new messages in #general
    - Carol shared a file with you
    
  Rendered as single notification:
  ┌─────────────────────────────────────┐
  │ 📬 You have 8 new activities        │
  │                                     │
  │ 💬 Alice replied to your comment    │
  │ 📢 Bob mentioned you in #engineering│
  │ 📄 Carol shared "Q1 Report" with you│
  │ + 5 more messages in #general       │
  │                                     │
  │ [View all in Teams]                 │
  └─────────────────────────────────────┘

Implementation:
  Redis sorted set per user:
    ZADD digest:{userId}:{category} {timestamp} {eventJson}
  
  Digest scheduler (cron every 15 min):
    For each user with pending digest events:
      1. ZRANGEBYSCORE → get all events in window
      2. Group by source (channel, sender, type)
      3. Render digest template with aggregated data
      4. Send via preferred channel (usually email)
      5. ZREMRANGEBYSCORE → clear processed events
      
  Smart grouping:
    "3 messages from Alice" instead of 3 separate items
    "12 new messages in 4 channels" as summary line
```
