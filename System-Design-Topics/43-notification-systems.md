# 🎯 Topic 43: Notification Systems

> **System Design Interview — Deep Dive**
> A comprehensive guide covering push notifications (APNS/FCM), email, SMS, in-app notifications, notification fanout, delivery guarantees, rate limiting, user preferences, template management, and how to articulate notification system decisions in system design interviews.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Notification Channels](#notification-channels)
3. [Architecture Overview](#architecture-overview)
4. [Push Notifications (APNS / FCM)](#push-notifications-apns--fcm)
5. [Email Notifications](#email-notifications)
6. [SMS Notifications](#sms-notifications)
7. [In-App Notifications](#in-app-notifications)
8. [Notification Fanout — One Event, Multiple Users](#notification-fanout--one-event-multiple-users)
9. [User Preferences and Opt-Out](#user-preferences-and-opt-out)
10. [Rate Limiting and Throttling](#rate-limiting-and-throttling)
11. [Template Management](#template-management)
12. [Delivery Guarantees and Retry](#delivery-guarantees-and-retry)
13. [Real-World Examples](#real-world-examples)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

A **notification system** delivers messages to users across multiple channels — push, email, SMS, in-app — based on events, preferences, and priority. The challenge is delivering the right message to the right user through the right channel at the right time, at scale.

```
Event: "Your order has shipped!"

Notification pipeline:
  1. Order service publishes event to Kafka: {order_id, user_id, status: "shipped"}
  2. Notification service consumes event
  3. Looks up user preferences: push=ON, email=ON, SMS=OFF
  4. Applies rate limiting: User hasn't been notified in last 5 minutes ✓
  5. Renders templates: Push body, email HTML, subject line
  6. Dispatches:
     → APNS/FCM for push notification
     → SES/SendGrid for email
  7. Tracks delivery status: delivered, opened, clicked
```

---

## Notification Channels

| Channel | Latency | Delivery Rate | Cost | Best For |
|---|---|---|---|---|
| **Push (APNS/FCM)** | 0.5-5 sec | 60-90% (device on, app installed) | Free (platform fees) | Time-sensitive alerts |
| **Email** | 1-30 sec | 95%+ (delivery), 20-30% (open) | $0.0001-0.001/email | Detailed content, receipts |
| **SMS** | 1-10 sec | 95%+ delivery | $0.01-0.05/SMS | Critical alerts, 2FA |
| **In-App** | Instant (if online) | 100% (when user opens app) | Free | Feature announcements |
| **WebSocket** | Instant | 100% (if connected) | Free (infrastructure) | Real-time updates |

```
Priority cascade (for critical notifications):
  1. Try push notification first (fastest, free)
  2. If device is offline → send email as backup
  3. If extremely urgent (fraud alert) → also send SMS

Channel selection logic:
  if notification.priority == CRITICAL:
      send(PUSH + EMAIL + SMS)
  elif notification.priority == HIGH:
      send(PUSH + EMAIL)
  elif notification.priority == NORMAL:
      send(PUSH)
  elif notification.priority == LOW:
      send(IN_APP_ONLY)  # Show next time user opens app
```

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                 NOTIFICATION SYSTEM ARCHITECTURE               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Event Sources (Kafka topics):                               │
│  ├── order-events: Order shipped, delivered, cancelled       │
│  ├── payment-events: Payment succeeded, failed               │
│  ├── social-events: New follower, mention, like              │
│  └── system-events: Security alert, maintenance              │
│       │                                                      │
│       ▼                                                      │
│  Notification Service (Flink / Consumer):                    │
│  ├── Event → Notification mapping (which events → which notif)│
│  ├── User preference lookup (DynamoDB/Redis)                 │
│  ├── Rate limiting check (Redis sliding window)              │
│  ├── Template rendering (Handlebars/Mustache)                │
│  ├── Deduplication (Redis: SET notif:{hash} NX EX 3600)     │
│  └── Channel routing (push vs email vs SMS vs in-app)        │
│       │                                                      │
│       ▼                                                      │
│  Channel Dispatchers (separate queues per channel):          │
│  ├── Push Queue → APNS / FCM (batch API)                    │
│  ├── Email Queue → SES / SendGrid (SMTP/API)                │
│  ├── SMS Queue → Twilio / SNS (API)                         │
│  └── In-App Queue → Write to DynamoDB + WebSocket push      │
│       │                                                      │
│       ▼                                                      │
│  Delivery Tracking:                                          │
│  ├── Delivery receipts (push delivered, email bounced)       │
│  ├── Open/click tracking (email pixel, push tap)             │
│  └── Analytics: sent/delivered/opened/clicked rates          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Push Notifications (APNS / FCM)

### How Push Works

```
iOS (APNS — Apple Push Notification Service):
  App → registers with APNS → gets device token
  Device token stored in our database: {user_id, device_token, platform: "ios"}
  
  Send notification:
    POST to APNS with device_token + payload
    Payload: {"aps": {"alert": {"title": "Order Shipped!", "body": "..."}}}
  
  APNS delivers to device (even if app is closed)
  If device is offline: APNS stores and delivers when online (up to 30 days)

Android (FCM — Firebase Cloud Messaging):
  Similar flow: App registers → gets registration token
  POST to FCM API with token + payload
  FCM delivers to device

Device token management:
  User may have multiple devices (phone + tablet + watch)
  Each device has its own token
  On notification: Send to ALL user's registered device tokens
  
  Token invalidation:
    App uninstalled → token becomes invalid
    APNS/FCM returns error → remove invalid token from database
    Periodic cleanup: Check and remove stale tokens
```

### Batch Sending

```
Sending to 1M users one-by-one = 1M API calls → too slow!

FCM batch: Up to 500 tokens per request
  1M users / 500 per batch = 2,000 API calls (much faster)

FCM topic messaging:
  Subscribe users to topic: "order_updates"
  Send one message to topic → FCM delivers to ALL subscribers
  Use for: Broadcast notifications (new feature, maintenance)

Parallel dispatchers:
  10 push workers × 100 requests/sec = 1,000 batches/sec
  × 500 tokens/batch = 500,000 deliveries/sec
  1M notifications in ~2 seconds
```

---

## Email Notifications

### Email Delivery Pipeline

```
Notification service → Email queue (SQS/Kafka) → Email worker → SES/SendGrid

Email worker:
  1. Pull from queue
  2. Render HTML template with user data
  3. Submit to email provider API (SES, SendGrid, Mailgun)
  4. Track delivery status (delivered, bounced, spam-reported)

Deliverability best practices:
  ✅ SPF, DKIM, DMARC records configured
  ✅ Warm-up sending IP (gradually increase volume)
  ✅ Handle bounces: Remove hard-bounced emails immediately
  ✅ Unsubscribe link in every email (CAN-SPAM compliance)
  ✅ Dedicated sending domain (notifications@mail.yourapp.com)
  
  Bounce types:
    Hard bounce: Email doesn't exist → remove from list permanently
    Soft bounce: Mailbox full → retry 3 times, then suppress temporarily

Cost at scale:
  SES: $0.10 per 1,000 emails = $100 for 1M emails
  SendGrid: $0.20-0.50 per 1,000 (more features, better deliverability)
```

---

## SMS Notifications

```
Most expensive channel — use sparingly:

Use cases:
  ✅ 2FA/OTP: "Your verification code is 847291"
  ✅ Critical security: "Suspicious login from new device"
  ✅ Time-critical: "Your ride is arriving in 2 minutes"
  ❌ Marketing (use email/push instead — 100x cheaper)

Provider: Twilio, AWS SNS, MessageBird
  Cost: $0.0075 per SMS (US), $0.05+ international
  1M SMS = $7,500 (vs $100 for 1M emails)

SMS rate limiting (mandatory):
  Max 1 SMS per minute per user
  Max 5 SMS per hour per user
  Max 10 SMS per day per user
  Exceeding → fall back to push/email
```

---

## In-App Notifications

```
Stored in database, displayed when user opens app:

DynamoDB schema:
  PK: user_id
  SK: timestamp#notification_id
  Attributes: type, title, body, is_read, action_url, created_at
  TTL: 30 days (auto-expire old notifications)

Query: "Get user's unread notifications"
  PK = user_id, SK > 0, filter is_read = false
  → Returns unread notifications sorted by time (newest first)

Real-time delivery (for online users):
  If user has active WebSocket connection:
    Push notification payload through WebSocket → appears instantly
  If user is offline:
    Store in DynamoDB → user sees it when they open the app

Notification badge count:
  Redis: INCR unread_count:{user_id} (on new notification)
         DECR unread_count:{user_id} (on mark-as-read)
  API: GET unread_count:{user_id} → display badge "3"
  
  Reconciliation: Periodically recount from DynamoDB
    (Redis count can drift due to crashes)
```

---

## Notification Fanout — One Event, Multiple Users

### Small Fanout (< 1,000 recipients)

```
"Alice posted a new tweet" → notify 800 followers

  Event: {type: "new_tweet", user_id: "alice", tweet_id: "123"}
  
  Notification service:
    1. Fetch followers: SELECT follower_id FROM follows WHERE following_id = "alice"
    2. For each follower: Create notification + dispatch to preferred channel
    3. Push to per-channel queues (push_queue, email_queue)
  
  800 followers → 800 push notifications → dispatched in < 1 second
```

### Large Fanout (> 10,000 recipients)

```
"Taylor Swift posted a new tweet" → notify 50M followers

  Problem: Creating 50M notifications synchronously → takes hours!

  Solution: Async fanout via Kafka
    1. Event published to Kafka "celebrity_posts" topic
    2. Fanout service reads follower list in batches (10K per batch)
    3. For each batch: Publish 10K notification tasks to "notifications" topic
    4. Notification workers consume in parallel (1,000 workers)
    
    50M notifications / 1,000 workers / 500 per second = ~100 seconds total
  
  Optimization for celebrities:
    Don't fan-out at notification time → fan-out at read time (pull model)
    Store: "Taylor Swift posted tweet 123 at 14:30"
    When follower opens app: Check if Taylor posted since last visit → show notification
    
    50M fan-out → 0 fan-out at write time, per-user lookup at read time
    (Same hybrid push/pull as Topic 7: Push vs Pull Fanout)
```

---

## User Preferences and Opt-Out

### Preference Schema

```
DynamoDB / PostgreSQL:

{
  user_id: "user_123",
  preferences: {
    "order_updates":    { push: true, email: true, sms: false },
    "marketing":        { push: false, email: true, sms: false },
    "security_alerts":  { push: true, email: true, sms: true },  // Can't opt out of security
    "social":           { push: true, email: false, sms: false },
    "quiet_hours":      { start: "22:00", end: "08:00", timezone: "America/New_York" }
  }
}

Notification service checks BEFORE dispatching:
  1. Is this notification type enabled for this channel? → If not, skip
  2. Is user in quiet hours? → If yes, defer to morning (unless CRITICAL)
  3. Has user unsubscribed from email? → If yes, skip email channel
  
  Security alerts: Always sent regardless of preferences (regulatory requirement)
```

### Quiet Hours

```
User preference: No notifications between 10 PM and 8 AM

Implementation:
  if notification.priority != CRITICAL:
      user_local_time = convert_to_timezone(now(), user.timezone)
      if user.quiet_hours.start <= user_local_time <= user.quiet_hours.end:
          schedule_for_later(notification, user.quiet_hours.end)
          return  # Don't send now
  
  CRITICAL notifications (fraud, security): Bypass quiet hours always.
  
  Deferred notifications: Sent as batch at end of quiet hours
    "While you were away: 3 order updates, 2 social notifications"
```

---

## Rate Limiting and Throttling

```
Prevent notification fatigue:

Per-user limits:
  Max 10 push notifications per hour
  Max 3 emails per day (non-transactional)
  Max 5 SMS per day

Implementation (Redis sliding window):
  key = "notif_rate:{user_id}:{channel}:{window}"
  ZADD key {timestamp} {notification_id}
  ZREMRANGEBYSCORE key 0 {timestamp - window_size}
  count = ZCARD key
  if count >= limit: SKIP (rate limited)
  
  Also: Global rate limits to provider APIs
    SES: 50 emails/sec (soft limit, can increase)
    FCM: 5,000 requests/sec per project
    Twilio: 1 SMS/sec per phone number (buy more numbers for higher throughput)

Throttling during spikes:
  Black Friday: 10x normal notification volume
  Without throttling: FCM rate limit hit → notifications delayed/dropped
  With throttling: Queue absorbs spike, dispatchers process at sustainable rate
```

---

## Template Management

```
Separate content from code:

Template:
  "Hi {{user_name}}, your order #{{order_id}} has been {{status}}!"
  
  Push: Short version (max 178 chars for iOS)
  Email: Full HTML template with header, body, footer, unsubscribe link
  SMS: Ultra-short (max 160 chars)

Template storage: DynamoDB or PostgreSQL
  {template_id: "order_shipped", channel: "push", 
   locale: "en", body: "Your order #{{order_id}} is on its way!"}
  {template_id: "order_shipped", channel: "push",
   locale: "es", body: "Tu pedido #{{order_id}} está en camino!"}

Localization:
  Look up user's locale → select matching template
  Fallback: English if locale not available

A/B testing:
  Template variants: A = "Your order shipped!" B = "📦 Order on its way!"
  Track open/click rates per variant → auto-select winner
```

---

## Delivery Guarantees and Retry

```
At-least-once delivery:
  If push fails → retry with exponential backoff
  If email bounces → retry (soft bounce) or remove (hard bounce)
  If SMS fails → retry once, then fall back to push

Deduplication:
  SET notif:{user_id}:{event_hash} NX EX 3600
  If already set → skip (already sent this notification for this event)
  Prevents: User getting "Order Shipped" twice for same order

Delivery tracking:
  Push: FCM/APNS delivery receipts (delivered, not delivered)
  Email: SES delivery notifications (delivered, bounced, complaint)
  SMS: Twilio delivery callbacks (delivered, undelivered, failed)
  
  Store in analytics: {notification_id, channel, status, timestamp}
  Dashboard: Sent → Delivered → Opened → Clicked funnel per notification type
```

---

## Real-World Examples

### Uber — Ride Notifications

```
Channels: Push (primary), SMS (backup for critical)
  "Your driver is arriving" → Push (time-sensitive)
  "Receipt for your trip" → Email (detailed content)
  "Suspicious login" → Push + SMS (critical)
  
  Rate limiting: Max 20 push per ride lifecycle
  Latency: < 5 seconds from event to push delivery
```

### Instagram — Social Notifications

```
"alice liked your photo" → Push + In-App
  Batching: "alice and 5 others liked your photo" (collapse after 3 likes)
  
  Celebrity fan-out: Pull model (don't push to 500M followers)
  Normal users: Push model (fan-out to 500 followers)
  
  Quiet hours: Deferred batch at 8 AM local time
```

### Amazon — Order Notifications

```
Channels: Email (primary), Push (secondary), SMS (OTP only)
  "Order confirmed" → Email + Push
  "Order shipped" → Email + Push + tracking link
  "Delivered" → Email + Push
  
  Template: Localized to 20+ languages
  Rate limiting: Max 1 notification per order status change
```

---

## Interview Talking Points & Scripts

### Architecture

> *"I'd use a notification service that consumes events from Kafka, looks up user preferences in Redis, applies rate limiting, renders channel-specific templates, and dispatches to per-channel queues. Push notifications go through APNS/FCM, emails through SES, SMS through Twilio. Each channel has its own worker pool and retry policy. Delivery status is tracked in DynamoDB for analytics."*

### Fanout

> *"For a celebrity with 50M followers, I'd use the pull model — store the event and let followers discover it when they open the app. For normal users with 500 followers, I'd use push fanout — create 500 notification records and dispatch immediately. This hybrid approach handles both cases without overwhelming the system."*

---

## Common Interview Mistakes

### ❌ Mistake 1: One-size-fits-all channel
**Better**: Multiple channels with priority cascade. Push first, email backup, SMS for critical only.

### ❌ Mistake 2: No rate limiting
**Better**: Per-user limits (10 push/hour, 3 email/day). Per-provider limits (SES 50/sec).

### ❌ Mistake 3: Not mentioning user preferences
**Better**: Users control which notification types and channels they receive. Quiet hours deferred.

### ❌ Mistake 4: Synchronous notification sending
**Better**: Async via queues. Event → Kafka → notification workers → channel dispatchers.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│              NOTIFICATION SYSTEMS CHEAT SHEET                 │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  CHANNELS: Push (APNS/FCM), Email (SES), SMS (Twilio), In-App│
│  PRIORITY: Critical→Push+Email+SMS. Normal→Push. Low→In-App │
│                                                              │
│  ARCHITECTURE:                                               │
│    Kafka → Notification Service → Per-Channel Queues → APIs  │
│    Preference lookup → Rate limit check → Template render    │
│                                                              │
│  FANOUT:                                                     │
│    Small (< 1K): Push model (fan-out at write time)          │
│    Large (> 10K): Pull model (fan-out at read time)          │
│    Celebrity: Hybrid (store event, followers pull on open)   │
│                                                              │
│  USER PREFERENCES:                                           │
│    Per notification type × per channel (push/email/sms)      │
│    Quiet hours: Defer non-critical to morning                │
│    Security alerts: Always sent (can't opt out)              │
│                                                              │
│  RATE LIMITING:                                              │
│    Per user: 10 push/hour, 3 email/day, 5 SMS/day           │
│    Per provider: SES 50/sec, FCM 5K/sec, Twilio 1/sec/number│
│                                                              │
│  DELIVERY: At-least-once + dedup key. Retry with backoff.    │
│  TRACKING: Sent → Delivered → Opened → Clicked funnel        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 7: Push vs Pull Fanout** — Hybrid fanout for notifications
- **Topic 37: WebSocket & Real-Time** — In-app real-time notifications
- **Topic 42: Job Scheduling** — Scheduled/deferred notifications
- **Topic 15: Rate Limiting** — Per-user notification throttling

---

*This document is part of the System Design Interview Deep Dive series.*
