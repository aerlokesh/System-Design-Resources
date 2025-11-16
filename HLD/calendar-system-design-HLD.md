# Calendar System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [Event Scheduling Flow](#event-scheduling-flow)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a scalable calendar and scheduling system (like Google Calendar, Outlook Calendar) that supports:
- Event creation, editing, and deletion
- Multiple calendar support (personal, work, shared)
- Recurring events with complex patterns
- Event invitations and RSVPs
- Availability checking and scheduling
- Reminders and notifications
- Calendar sharing and permissions
- Meeting room booking
- Time zone support
- Calendar synchronization (CalDAV)
- Integration with video conferencing (Zoom, Meet)
- Mobile and web access

### Scale Requirements
- **1 billion registered users**
- **200 million daily active users (DAU)**
- **50 billion events stored**
- **500 million events created per day**
- **1 billion calendar views per day**
- **100 million event invitations per day**
- **99.99% availability**
- **Event creation latency: < 100ms**
- **Calendar load time: < 200ms**
- **Conflict detection: < 50ms**
- **Support 100+ time zones**

---

## Functional Requirements

### Must Have (P0)

#### 1. **Event Management**
- Create single events
- Create recurring events (daily, weekly, monthly, yearly, custom)
- Edit events (title, time, location, description)
- Delete events (single or series)
- Duplicate events
- Move events (drag-and-drop)
- All-day events
- Multi-day events
- Color-coding events
- Event categories/labels

#### 2. **Calendar Views**
- Day view
- Week view
- Month view
- Year view
- Agenda/list view
- Schedule view (compact)
- Multiple calendars overlay
- Print view

#### 3. **Attendees & Invitations**
- Add attendees to events
- Send event invitations via email
- RSVP options (Yes, No, Maybe)
- Track attendee responses
- Guest list management
- Propose new time if unavailable
- View attendee availability
- Required vs optional attendees

#### 4. **Reminders & Notifications**
- Email reminders
- Push notifications
- SMS reminders
- Multiple reminders per event
- Custom reminder times (5 min, 1 hour, 1 day before)
- Notification for event changes
- Daily agenda email
- Meeting reminder popups

#### 5. **Calendar Sharing**
- Share entire calendar
- Share specific events
- Permission levels (view, edit, manage)
- Public calendars (holidays, sports)
- Subscribe to other calendars
- Team calendars
- Family calendars
- Unsubscribe from shared calendars

#### 6. **Availability & Scheduling**
- Check attendee availability
- Find meeting time that works for all
- Suggest meeting times
- Block time as busy/free
- Show as available/busy
- Working hours configuration
- Out of office settings

#### 7. **Search & Filter**
- Search events by title, location, attendees
- Filter by calendar
- Filter by date range
- Filter by attendees
- Search history
- Quick filters (today, this week, upcoming)

#### 8. **Time Zone Support**
- Display events in user's timezone
- Create events in specific timezones
- Automatic DST handling
- World clock
- Show attendee timezones
- Time zone conversion

### Nice to Have (P1)
- Meeting room booking
- Resource scheduling (projectors, cars)
- Video conferencing integration (Zoom, Meet, Teams)
- Appointment scheduling pages
- Booking links (Calendly-like)
- Calendar analytics
- Time tracking
- Goal tracking
- Weather forecast integration
- Travel time integration (maps)
- Smart scheduling suggestions
- Automatic event creation (flights, deliveries)
- Conference room displays

---

## Non-Functional Requirements

### Performance
- **Event creation**: < 100ms
- **Calendar load**: < 200ms (month view)
- **Search**: < 100ms
- **Conflict detection**: < 50ms
- **Availability check**: < 200ms
- **Sync latency**: < 1 second
- **Notification delivery**: < 5 seconds

### Scalability
- Support 1B users
- Handle 200M DAU
- Store 50B events
- 500M events created daily
- 1B calendar views per day
- Scale to 2B users in 3 years

### Availability
- **99.99% uptime** (52 minutes downtime/year)
- Multi-region deployment
- Real-time synchronization
- Offline mode support
- Graceful degradation

### Consistency
- **Strong consistency** for event CRUD operations
- **Eventual consistency** for calendar views (acceptable)
- **Conflict-free replicated data types** (CRDTs) for offline edits
- **Atomic operations** for attendee updates

### Storage
- **Event retention**: Unlimited (past events)
- **Future events**: Up to 5 years
- **Attachments**: Up to 25 MB per event
- **Backup**: Daily snapshots, 90-day retention

### Security
- **Encryption**: TLS 1.3 for transit, AES-256 at rest
- **Authentication**: OAuth 2.0, SSO
- **Authorization**: Fine-grained permissions
- **PII protection**: Encrypt event details
- **Audit logging**: All event modifications
- **Compliance**: GDPR, HIPAA, SOC 2

---

## Capacity Estimation

### Traffic Estimates
```
Registered Users: 1B
Daily Active Users (DAU): 200M (20% of total)
Monthly Active Users (MAU): 500M (50% of total)

Event Operations:
- Events created/day: 500M
- Events created/second: 500M / 86,400 ≈ 5,787 QPS
- Peak (5x): 28,935 QPS

- Events read/day: 1B (calendar views)
- Events read/second: 1B / 86,400 ≈ 11,574 QPS
- Peak (5x): 57,870 QPS

- Events updated/day: 200M (40% of creates)
- Events updated/second: 2,315 QPS
- Peak: 11,575 QPS

- Events deleted/day: 50M (10% of creates)
- Events deleted/second: 579 QPS
- Peak: 2,895 QPS

Total write QPS: 5,787 + 2,315 + 579 = 8,681 QPS
Total read QPS: 11,574 QPS
Peak total: 72,300 QPS

Notifications:
- Reminders sent/day: 1B (2 reminders × 500M events)
- Reminders/second: 11,574 QPS
- Peak: 57,870 QPS

Search Queries:
- Searches/user/day: 3
- Total searches/day: 200M × 3 = 600M
- Searches/second: 6,944 QPS
- Peak: 34,720 QPS
```

### Storage Estimates

**Events**:
```
Total events: 50B
Per event:
{
  event_id: 16 bytes (UUID)
  calendar_id: 16 bytes
  title: 200 bytes
  description: 1000 bytes
  location: 200 bytes
  start_time: 8 bytes
  end_time: 8 bytes
  timezone: 50 bytes
  recurrence_rule: 200 bytes
  organizer_id: 16 bytes
  attendees: 500 bytes (avg 5 attendees)
  reminders: 100 bytes
  metadata: 500 bytes
}
Total per event: ~3 KB

Total events: 50B × 3 KB = 150 TB
With indexes (3x): 450 TB
With replication (3x): 1.35 PB
```

**Users**:
```
Per user:
{
  user_id: 16 bytes
  email: 100 bytes
  name: 100 bytes
  timezone: 50 bytes
  preferences: 500 bytes
  calendars: 200 bytes (refs to 3-5 calendars)
}
Total per user: ~1 KB

Total users: 1B × 1 KB = 1 TB
With replication: 3 TB
```

**Calendars**:
```
Users have average 3 calendars each
Total calendars: 1B × 3 = 3B

Per calendar:
{
  calendar_id: 16 bytes
  name: 100 bytes
  description: 500 bytes
  owner_id: 16 bytes
  color: 10 bytes
  timezone: 50 bytes
  settings: 300 bytes
}
Total per calendar: ~1 KB

Total: 3B × 1 KB = 3 TB
With replication: 9 TB
```

**Event Attachments**:
```
10% of events have attachments
Attachments: 50B × 10% = 5B
Average size: 500 KB per attachment

Total: 5B × 500 KB = 2.5 PB
With replication: 7.5 PB
```

**Total Storage**:
```
Events: 1.35 PB
Users: 3 TB
Calendars: 9 TB
Attachments: 7.5 PB
Cached data: 50 TB
─────────────────────
Total: ~9 PB
```

### Bandwidth Estimates
```
Calendar View Request:
- Average month view: 30 events
- Per event data: 3 KB
- Response size: 30 × 3 KB = 90 KB
- With metadata, UI: ~150 KB per view

Daily bandwidth (calendar views):
- Views/day: 1B
- Bandwidth: 1B × 150 KB = 150 TB/day
- Per second: 150 TB / 86,400 ≈ 1.74 GB/s
- Peak (5x): 8.7 GB/s

Event Creation:
- Requests/day: 500M
- Per request: 5 KB (event data + response)
- Bandwidth: 500M × 5 KB = 2.5 TB/day
- Per second: 29 MB/s

Notifications:
- Reminders/day: 1B
- Per notification: 2 KB
- Bandwidth: 1B × 2 KB = 2 TB/day
- Per second: 23 MB/s

Peak total bandwidth: ~9 GB/s
```

### Server Estimates
```
API Servers:
- Handle CRUD operations: 72K peak QPS
- Each server: 5K QPS
- Servers needed: 15 servers
- With redundancy (4x): 60 servers

Database Servers:
- Write QPS: 43K peak
- Read QPS: 58K peak
- PostgreSQL shards: 32 shards
- Read replicas: 3 per shard
- Total: 32 + (32 × 3) = 128 servers

Cache Servers (Redis):
- Hot data: 100GB
- Nodes: 20 nodes (5GB each)
- With replication (3x): 60 nodes

Search Servers (Elasticsearch):
- Index 50B events
- Each node: 500M events
- Nodes needed: 100 nodes
- With replication (2x): 200 nodes

Notification Service:
- Handle 58K reminders/second (peak)
- Each server: 10K reminders/second
- Servers needed: 6 servers
- With redundancy (2x): 12 servers

Total: 60 + 128 + 60 + 200 + 12 = 460 servers

Cost estimation (AWS):
- API servers: 60 × $150/month = $9K/month
- Database servers: 128 × $200/month = $25.6K/month
- Redis: 60 × $100/month = $6K/month
- Elasticsearch: 200 × $150/month = $30K/month
- Notification: 12 × $80/month = $960/month
- Storage (S3): 9PB × $23/TB = $207K/month
- CDN/bandwidth: $50K/month
- Total: ~$330K/month
```

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────┐
│                      CLIENTS                               │
│  Web App | Mobile Apps (iOS/Android) | Desktop | Outlook  │
└────────────────────────┬───────────────────────────────────┘
                         │ (HTTPS - REST & CalDAV)
                         ↓
┌────────────────────────────────────────────────────────────┐
│                   CDN / LOAD BALANCER                      │
│  CloudFront / AWS ALB - SSL termination, DDoS protection  │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ↓
┌────────────────────────────────────────────────────────────┐
│                    API GATEWAY                             │
│  - Authentication & Authorization                          │
│  - Rate limiting (100 req/sec per user)                    │
│  - Request validation                                      │
│  - API versioning                                          │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┬──────────────┐
          ↓              ↓              ↓              ↓
┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
│    Event     │ │   Calendar   │ │   Search    │ │ Notification │
│   Service    │ │   Service    │ │   Service   │ │   Service    │
│              │ │              │ │             │ │              │
│ - Create     │ │ - List       │ │ - Events    │ │ - Reminders  │
│ - Update     │ │ - Share      │ │ - Attendees │ │ - Email      │
│ - Delete     │ │ - Subscribe  │ │ - Auto      │ │ - Push       │
│ - Recurrence │ │ - Sync       │ │   complete  │ │ - SMS        │
└──────┬───────┘ └──────┬───────┘ └─────┬───────┘ └──────┬───────┘
       │                │                │                │
       └────────────────┼────────────────┼────────────────┘
                        ↓                │
┌────────────────────────────────────────┼────────────────┐
│              CACHE LAYER (Redis)       │                │
│  - Hot events (today + 7 days, 1h TTL)│                │
│  - User calendars (5 min TTL)          │                │
│  - Availability cache (10 min TTL)     │                │
│  - Search results (15 min TTL)         │                │
└────────────────────────┬───────────────┼────────────────┘
                         │               │
          ┌──────────────┼───────────────┼────────┐
          ↓              ↓               ↓        ↓
┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌──────────────┐
│    Event     │ │   Calendar   │ │  User    │ │  Attachment  │
│   Database   │ │   Database   │ │  Database│ │   Storage    │
│              │ │              │ │          │ │              │
│ (PostgreSQL) │ │ (PostgreSQL) │ │(Postgres)│ │   (S3/GCS)   │
│              │ │              │ │          │ │              │
│ - Events     │ │ - Calendars  │ │ - Users  │ │ - Files      │
│ - Recurrence │ │ - Shares     │ │ - Prefs  │ │ - Images     │
│ - Attendees  │ │ - Permissions│ │ - Auth   │ │ - Documents  │
│ - Reminders  │ │ - Subscript. │ │ - Roles  │ │              │
└──────────────┘ └──────────────┘ └──────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────┐
│            MESSAGE QUEUE (Kafka / RabbitMQ)                │
│  Topics:                                                   │
│  - event.created                                           │
│  - event.updated                                           │
│  - event.deleted                                           │
│  - reminder.trigger                                        │
│  - invitation.sent                                         │
└────────────────────────┬───────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓               ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Notification │ │    Sync      │ │   Analytics  │
│   Worker     │ │   Worker     │ │    Worker    │
│              │ │              │ │              │
│ - Send email │ │ - CalDAV     │ │ - Usage      │
│ - Push notif │ │ - Webhooks   │ │ - Reports    │
│ - SMS        │ │ - Real-time  │ │ - Insights   │
└──────────────┘ └──────────────┘ └──────────────┘

┌────────────────────────────────────────────────────────────┐
│              SEARCH ENGINE (Elasticsearch)                 │
│  - Event text search                                       │
│  - Attendee search                                         │
│  - Location search                                         │
│  - Autocomplete                                            │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│           SCHEDULING INTELLIGENCE                          │
│  - Availability finder (ML-powered)                        │
│  - Smart scheduling suggestions                            │
│  - Conflict detection                                      │
│  - Time zone optimization                                  │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              THIRD-PARTY INTEGRATIONS                      │
│  - Zoom API (video conferencing)                           │
│  - Google Meet API                                         │
│  - Microsoft Teams                                         │
│  - Email providers (SMTP, SendGrid)                        │
│  - SMS providers (Twilio)                                  │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│              MONITORING & OBSERVABILITY                    │
│  Prometheus | Grafana | ELK | Datadog | PagerDuty        │
│  - Event creation rate                                     │
│  - Calendar load time                                      │
│  - Notification delivery rate                              │
│  - Sync latency                                            │
└────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **PostgreSQL for Event Storage**
   - ACID transactions for event CRUD
   - Complex queries (availability, conflicts)
   - Strong consistency required
   - Supports time-range queries efficiently
   - Sharded by calendar_id for scalability

2. **Redis for Caching**
   - Cache hot events (today + next 7 days)
   - Fast availability lookups
   - Session management
   - Rate limiting counters

3. **Elasticsearch for Search**
   - Full-text search across events
   - Autocomplete for titles, attendees
   - Fast queries (< 100ms)
   - Fuzzy matching

4. **Message Queue for Async Processing**
   - Kafka for event streaming
   - Decouple notification sending
   - Enable real-time sync
   - Audit trail

5. **CalDAV Protocol Support**
   - Standard calendar protocol
   - Interoperability with other clients
   - Sync with Apple Calendar, Outlook
   - Two-way synchronization

6. **Multi-Region Deployment**
   - Active-active across regions
   - Data replicated asynchronously
   - Conflict resolution with timestamps
   - Low latency globally

---

## Core Components

### 1. Event Service

**Purpose**: Handle all event CRUD operations

**Responsibilities**:
- Create, read, update, delete events
- Expand recurring events
- Validate event data
- Check conflicts
- Manage attendees

**API Endpoints**:

**Create Event**:
```http
POST /api/v1/events

Request:
{
  "calendar_id": "cal_abc123",
  "title": "Team Standup",
  "description": "Daily team sync meeting",
  "start": "2024-01-08T10:00:00-08:00",
  "end": "2024-01-08T10:30:00-08:00",
  "timezone": "America/Los_Angeles",
  "location": "Conference Room A",
  "attendees": [
    {
      "email": "alice@example.com",
      "role": "required",
      "status": "pending"
    },
    {
      "email": "bob@example.com",
      "role": "optional",
      "status": "pending"
    }
  ],
  "reminders": [
    {"method": "email", "minutes_before": 60},
    {"method": "push", "minutes_before": 10}
  ],
  "recurrence": {
    "frequency": "daily",
    "interval": 1,
    "days": ["mon", "tue", "wed", "thu", "fri"],
    "until": "2024-12-31"
  },
  "video_conference": {
    "provider": "zoom",
    "auto_generate": true
  }
}

Response (201 Created):
{
  "event_id": "evt_xyz789",
  "calendar_id": "cal_abc123",
  "title": "Team Standup",
  "start": "2024-01-08T10:00:00-08:00",
  "end": "2024-01-08T10:30:00-08:00",
  "attendees": [...],
  "video_conference": {
    "provider": "zoom",
    "url": "https://zoom.us/j/123456789",
    "meeting_id": "123 456 789",
    "passcode": "abc123"
  },
  "created_at": "2024-01-07T15:30:00Z"
}
```

**Update Event**:
```http
PATCH /api/v1/events/{event_id}

Request:
{
  "title": "Team Standup (Updated)",
  "start": "2024-01-08T10:30:00-08:00",
  "update_scope": "this_and_following"  // or "only_this", "all"
}

Response (200 OK):
{
  "event_id": "evt_xyz789",
  "updated_fields": ["title", "start"],
  "notification_sent": true,
  "attendees_notified": 2
}
```

**Get Events (Calendar View)**:
```http
GET /api/v1/calendars/{calendar_id}/events?start=2024-01-01&end=2024-01-31

Response:
{
  "calendar_id": "cal_abc123",
  "events": [
    {
      "event_id": "evt_xyz789",
      "title": "Team Standup",
      "start": "2024-01-08T10:00:00-08:00",
      "end": "2024-01-08T10:30:00-08:00",
      "attendees_count": 2,
      "has_video_conference": true,
      "color": "#4285f4"
    },
    ...
  ],
  "total": 45,
  "has_more": false
}
```

**Recurring Event Handling**:
```
Storage: Store template + recurrence rule
Expansion: Generate instances on-the-fly

Template:
{
  "event_id": "evt_xyz789",
  "title": "Team Standup",
  "start_time": "10:00:00",
  "duration": 30,  // minutes
  "recurrence": "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20241231"
}

When fetching calendar view:
1. Query: Events in range [2024-01-01, 2024-01-31]
2. Find recurring event with template
3. Expand instances:
   - Jan 8 (Mon): 10:00-10:30
   - Jan 9 (Tue): 10:00-10:30
   - ...
   - Jan 31 (Wed): 10:00-10:30
4. Merge with non-recurring events
5. Return sorted list

Exceptions (edited occurrences):
- Store in separate table
- Override template values
- Link to parent via event_id + instance_date
```

**Recurrence Rule (RFC 5545 - iCalendar)**:
```
Examples:

Daily for 10 days:
FREQ=DAILY;COUNT=10

Weekly on Monday and Wednesday:
FREQ=WEEKLY;BYDAY=MO,WE

Monthly on 1st and 15th:
FREQ=MONTHLY;BYMONTHDAY=1,15

Yearly on birthday:
FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=15

Every weekday:
FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR

Last Friday of month:
FREQ=MONTHLY;BYDAY=-1FR

Complex: 2nd and 4th Tuesday:
FREQ=MONTHLY;BYDAY=2TU,4TU
```

### 2. Calendar Service

**Purpose**: Manage calendars and sharing

**Calendar Types**:
```
1. Primary Calendar
   - One per user (cannot delete)
   - Full ownership
   - Default for new events

2. Secondary Calendars
   - User-created
   - Can delete/hide
   - Examples: Work, Personal, Family

3. Shared Calendars
   - Owned by another user
   - View or edit permissions
   - Updates sync across all viewers

4. Public Calendars
   - Holidays, sports, TV shows
   - Read-only
   - Subscribable

5. Resource Calendars
   - Meeting rooms
   - Equipment
   - Vehicles
```

**Sharing & Permissions**:
```http
POST /api/v1/calendars/{calendar_id}/share

Request:
{
  "email": "colleague@example.com",
  "permission": "edit",  // view, edit, manage
  "send_notification": true
}

Response:
{
  "share_id": "share_123",
  "calendar_id": "cal_abc123",
  "shared_with": "colleague@example.com",
  "permission": "edit",
  "accepted": false
}

Permission Levels:
- VIEW: See event titles and times
- VIEW_DETAILS: See full event details
- EDIT: Create/edit/delete events
- MANAGE: Change calendar settings, share with others
- OWNER: Full control, can delete calendar
```

**Calendar Subscription**:
```
Public calendar (e.g., US Holidays):
- Publish iCal URL: https://example.com/calendars/us-holidays.ics
- Users subscribe via URL
- Read-only access
- Automatic updates (synced daily)
- Unsubscribe anytime

Implementation:
1. Generate iCal format feed
2. Cache for 24 hours
3. Clients poll for updates
4. Delta sync (only changed events)
```

### 3. Availability & Scheduling Service

**Purpose**: Find available meeting times and detect conflicts

**Check Availability**:
```http
POST /api/v1/availability/check

Request:
{
  "attendees": ["alice@example.com", "bob@example.com", "carol@example.com"],
  "duration": 60,  // minutes
  "date_range": {
    "start": "2024-01-08",
    "end": "2024-01-12"
  },
  "working_hours": {
    "start": "09:00",
    "end": "17:00",
    "days": ["mon", "tue", "wed", "thu", "fri"]
  },
  "timezone": "America/New_York"
}

Response:
{
  "available_slots": [
    {
      "start": "2024-01-08T10:00:00-05:00",
      "end": "2024-01-08T11:00:00-05:00",
      "all_available": true
    },
    {
      "start": "2024-01-08T14:00:00-05:00",
      "end": "2024-01-08T15:00:00-05:00",
      "all_available": true
    },
    {
      "start": "2024-01-09T11:00:00-05:00",
      "end": "2024-01-09T12:00:00-05:00",
      "all_available": false,
      "unavailable_attendees": ["bob@example.com"]
    }
  ],
  "best_time": "2024-01-08T10:00:00-05:00"
}
```

**Conflict Detection Algorithm**:
```
Input: New event [10:00 AM - 11:00 AM]
Check: Existing events for same attendees

Query:
SELECT * FROM events
WHERE calendar_id IN (user's calendars)
AND start_time < '2024-01-08 11:00:00'
AND end_time > '2024-01-08 10:00:00'
AND deleted = false

Overlap conditions:
1. New event starts during existing event
2. New event ends during existing event
3. New event completely contains existing event
4. Existing event completely contains new event

If overlaps found:
- Return conflict warning
- Show conflicting events
- Allow user to proceed or reschedule
```

**Smart Scheduling**:
```
Find best meeting time for all attendees:

Algorithm:
1. Fetch calendars for all attendees
2. Identify busy blocks
3. Generate all possible slots within date range
4. Score each slot:
   - All available: +100 points
   - Within working hours: +50 points
   - Morning (focus time): +20 points
   - No back-to-back meetings: +10 points
   - Preferred time (ML learned): +30 points
5. Sort by score
6. Return top 5 suggestions

ML-Enhanced:
- Learn user preferences (prefers 2 PM meetings)
- Detect patterns (always declines Friday 4 PM)
- Optimize for attendee seniority
- Minimize total travel time (if locations known)
```

### 4. Notification Service

**Purpose**: Send reminders and event notifications[ERROR] Failed to process response: The model returned the following errors: Input is too long for requested model.
**Reminder Scheduling**:
```
Reminder types:
- Email: Sent via notification service
- Push: Sent to mobile devices
- SMS: High-priority events only
- Popup: Browser notifications

Scheduling:
1. Event created with reminders
2. Calculate trigger times:
   - Event start: 10:00 AM
   - 1 day before: 10:00 AM previous day
   - 1 hour before: 9:00 AM
   - 10 min before: 9:50 AM
3. Store in reminder queue (sorted by time)
4. Cron job checks every minute
5. Trigger due reminders
6. Send via appropriate channel

Reminder Queue (Redis Sorted Set):
Key: reminders
Score: Trigger timestamp
Value: {event_id, user_id, method}

Process:
ZRANGEBYSCORE reminders 0 <current_timestamp>
→ Get all due reminders
→ Process and remove from queue
```

---

## Database Design

### PostgreSQL Schema

```sql
-- Events table
CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    calendar_id UUID NOT NULL REFERENCES calendars(calendar_id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    location VARCHAR(500),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    all_day BOOLEAN DEFAULT false,
    status VARCHAR(20) DEFAULT 'confirmed',  -- confirmed, tentative, cancelled
    visibility VARCHAR(20) DEFAULT 'default',  -- default, public, private
    organizer_id UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP,  -- Soft delete
    parent_event_id UUID,  -- For recurring event instances
    instance_date DATE  -- For recurring exceptions
);

CREATE INDEX idx_events_calendar ON events(calendar_id, start_time);
CREATE INDEX idx_events_timerange ON events(start_time, end_time);
CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_events_parent ON events(parent_event_id);

-- Recurring events
CREATE TABLE recurring_events (
    recurrence_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(event_id),
    recurrence_rule TEXT NOT NULL,  -- iCal RRULE format
    recurrence_end DATE,
    exceptions TEXT[],  -- Dates to skip
    created_at TIMESTAMP DEFAULT NOW()
);

-- Event attendees
CREATE TABLE event_attendees (
    event_id UUID REFERENCES events(event_id) ON DELETE CASCADE,
    attendee_email VARCHAR(255) NOT NULL,
    attendee_name VARCHAR(255),
    role VARCHAR(20) NOT NULL,  -- required, optional, resource
    status VARCHAR(20) DEFAULT 'pending',  -- pending, accepted, declined, maybe
    responded_at TIMESTAMP,
    PRIMARY KEY (event_id, attendee_email)
);

CREATE INDEX idx_attendees_email ON event_attendees(attendee_email, status);

-- Reminders
CREATE TABLE event_reminders (
    reminder_id SERIAL PRIMARY KEY,
    event_id UUID REFERENCES events(event_id) ON DELETE CASCADE,
    method VARCHAR(20) NOT NULL,  -- email, push, sms
    minutes_before INT NOT NULL,
    sent BOOLEAN DEFAULT false,
    sent_at TIMESTAMP
);

CREATE INDEX idx_reminders_event ON event_reminders(event_id);
CREATE INDEX idx_reminders_pending ON event_reminders(event_id, sent) WHERE sent = false;

-- Calendars
CREATE TABLE calendars (
    calendar_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(user_id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    color VARCHAR(20),
    timezone VARCHAR(100) DEFAULT 'UTC',
    is_primary BOOLEAN DEFAULT false,
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_calendars_owner ON calendars(owner_id);
CREATE INDEX idx_calendars_public ON calendars(is_public) WHERE is_public = true;

-- Calendar shares (permissions)
CREATE TABLE calendar_shares (
    share_id UUID PRIMARY KEY,
    calendar_id UUID NOT NULL REFERENCES calendars(calendar_id) ON DELETE CASCADE,
    shared_with_email VARCHAR(255) NOT NULL,
    shared_with_user_id UUID REFERENCES users(user_id),
    permission VARCHAR(20) NOT NULL,  -- view, view_details, edit, manage
    accepted BOOLEAN DEFAULT false,
    shared_by UUID NOT NULL REFERENCES users(user_id),
    shared_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(calendar_id, shared_with_email)
);

CREATE INDEX idx_shares_calendar ON calendar_shares(calendar_id);
CREATE INDEX idx_shares_user ON calendar_shares(shared_with_user_id);

-- Calendar subscriptions (public calendars)
CREATE TABLE calendar_subscriptions (
    subscription_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    calendar_id UUID NOT NULL REFERENCES calendars(calendar_id),
    subscribed_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, calendar_id)
);

-- Users
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    timezone VARCHAR(100) DEFAULT 'UTC',
    locale VARCHAR(10) DEFAULT 'en',
    working_hours_start TIME DEFAULT '09:00',
    working_hours_end TIME DEFAULT '17:00',
    working_days INT[] DEFAULT '{1,2,3,4,5}',  -- Mon-Fri
    created_at TIMESTAMP DEFAULT NOW()
);

-- Event attachments
CREATE TABLE event_attachments (
    attachment_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    filename VARCHAR(500),
    file_size BIGINT,
    mime_type VARCHAR(100),
    storage_url TEXT,
    uploaded_by UUID REFERENCES users(user_id),
    uploaded_at TIMESTAMP DEFAULT NOW()
);

-- Video conference links
CREATE TABLE video_conferences (
    conference_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,  -- zoom, meet, teams
    url TEXT NOT NULL,
    meeting_id VARCHAR(255),
    passcode VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## Event Scheduling Flow

### Creating a Recurring Meeting

```
┌─────────────────────────────────────────────────────────────────┐
│ CREATE RECURRING EVENT - End to End                             │
└─────────────────────────────────────────────────────────────────┘

T=0ms: User creates weekly team meeting

Request:
{
  "title": "Team Standup",
  "start": "2024-01-08T10:00:00-08:00",
  "end": "2024-01-08T10:30:00-08:00",
  "recurrence": "FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=50",
  "attendees": ["alice@example.com", "bob@example.com"]
}

T=10ms: Event Service
├─ Validate request
├─ Check user has edit permission on calendar
├─ Parse recurrence rule
└─ Validate start/end times

T=20ms: Conflict Detection
├─ Query existing events for organizer
├─ Check time overlaps
├─ Find 2 conflicts on Mondays
└─ Return warning (allow user to proceed)

T=30ms: User proceeds despite conflicts

T=40ms: Create Event in Database
BEGIN TRANSACTION:
1. INSERT INTO events (master event)
2. INSERT INTO recurring_events (recurrence rule)
3. INSERT INTO event_attendees (2 attendees)
4. INSERT INTO event_reminders (default reminders)
5. Generate video conference link (Zoom API call - 200ms)
6. INSERT INTO video_conferences
COMMIT

T=250ms: Post-Creation

Tasks (async via Kafka):

1. Send Invitations:
   ├─ Generate .ics file (iCalendar format)
   ├─ Send email to alice@example.com
   ├─ Send email to bob@example.com
   └─ Include: Accept/Decline/Maybe links

2. Schedule Reminders:
   ├─ Calculate trigger times for first 10 occurrences
   ├─ Add to reminder queue (Redis sorted set)
   └─ Background job will schedule rest

3. Update Search Index:
   ├─ Index event in Elasticsearch
   └─ Enable full-text search

4. Log Event Creation:
   ├─ Audit log (who, when, what)
   └─ Analytics (event type, attendees count)

5. Sync to Mobile Devices:
   ├─ Push notification: "New event created"
   ├─ Trigger background sync
   └─ Update local calendar database

T=260ms: Response to client
{
  "event_id": "evt_abc123",
  "status": "created",
  "invitations_sent": 2,
  "reminders_scheduled": 50,
  "video_conference_url": "https://zoom.us/j/123456789"
}

Total latency: ~260ms (including Zoom API call)

Background Processing:
T+5s: Attendees receive invitation emails
T+10s: Mobile devices sync new event
T+24h: First reminder scheduled
```

### Attendee RSVP Flow

```
1. Attendee receives invitation email
2. Email contains:
   - Event details
   - Accept/Decline/Maybe buttons (links with tokens)
   - "Add to Calendar" attachment (.ics file)
   - View in browser link

3. Attendee clicks "Accept"
4. Link format: https://example.com/rsvp?token=<jwt_token>
5. Token contains: event_id, attendee_email, action

6. Server processes RSVP:
   ├─ Validate token (not expired, not used)
   ├─ Update attendee status in database:
   │  UPDATE event_attendees
   │  SET status = 'accepted', responded_at = NOW()
   │  WHERE event_id = ? AND attendee_email = ?
   ├─ Add event to attendee's calendar (if not already added)
   ├─ Send confirmation email
   └─ Notify organizer: "Alice accepted your invitation"

7. Real-time sync:
   ├─ Push update via WebSocket to organizer
   ├─ Update attendee count in UI
   └─ Update event status if all accepted

8. Response tracking:
   ├─ Accepted: 60%
   ├─ Declined: 20%
   ├─ Maybe: 10%
   ├─ No response: 10%
```

---

## Deep Dives

### 1. Time Zone Handling

**Challenge**: Users in different time zones creating and viewing events

**Solution**:

**Storage**: UTC + Original Timezone
```
Store event:
{
  "start_utc": "2024-01-08T18:00:00Z",  // UTC
  "end_utc": "2024-01-08T18:30:00Z",
  "timezone": "America/Los_Angeles",  // Original timezone
  "start_local": "2024-01-08T10:00:00-08:00"  // Display purpose
}

Benefits:
- Query in UTC (simple range queries)
- Display in user's timezone
- Preserve creator's intent
- Handle DST transitions correctly
```

**Display in User's Timezone**:
```
User A (Los Angeles, UTC-8):
- Event created: 10:00 AM PST
- Stored as: 18:00 UTC
- Displays: 10:00 AM PST

User B (New York, UTC-5):
- Same event
- Stored as: 18:00 UTC
- Displays: 1:00 PM EST

User C (London, UTC+0):
- Same event
- Stored as: 18:00 UTC
- Displays: 6:00 PM GMT
```

**Daylight Saving Time (DST)**:
```
Problem: Recurring event crosses DST boundary

Event: "Weekly meeting at 10 AM PST"
Created: Jan 1, 2024 (PST = UTC-8)

March 10, 2024: DST starts (PST → PDT, UTC-7)

Before DST (Jan - early March):
- 10:00 AM PST = 18:00 UTC

After DST (mid March - Oct):
- 10:00 AM PDT = 17:00 UTC ← Changed!

Solution: Store with timezone, not fixed offset
- Start: 10:00 AM America/Los_Angeles
- System automatically adjusts UTC conversion
- User always sees 10:00 AM local time
- UTC value changes based on DST
```

### 2. Recurring Event Edge Cases

**Case 1: Editing Single Instance**:
```
Series: Mon/Wed/Fri standup, 10 AM

User edits Wednesday Jan 10:
- Change time: 10 AM → 2 PM
- Change title: "Team Standup" → "Extended Team Sync"

Storage:
1. Master event remains unchanged
2. Create exception:
   INSERT INTO events (
     parent_event_id = 'evt_123',
     instance_date = '2024-01-10',
     start_time = '14:00',
     title = 'Extended Team Sync'
   )

When fetching calendar:
1. Expand master event (generates all instances)
2. Query exceptions
3. Override specific instances with exception data
4. Return merged result
```

**Case 2: Editing All Future Instances**:
```
Change all future meetings starting from Jan 15:

Approach 1: Truncate and create new series
1. Update master event: recurrence_end = 'Jan 12'
2. Create new master event: start from Jan 15
3. New recurrence rule

Approach 2: Exceptions for all
- Impractical (too many exception records)

Chosen: Approach 1 (more efficient)
```

**Case 3: Deleting Instance**:
```
Delete Wednesday Jan 10 from series:

Solution: Add to exceptions list
UPDATE recurring_events
SET exceptions = array_append(exceptions, '2024-01-10')
WHERE event_id = 'evt_123'

When expanding:
- Generate instances
- Skip dates in exceptions array
- Return filtered list
```

### 3. Conflict Resolution (Multi-Device Sync)

**Scenario**: User edits same event on phone and laptop while offline

**Problem**: Divergent versions
```
Initial state (in cloud):
Event: "Meeting at 2 PM"

User offline on phone:
- Edits to "Meeting at 3 PM"
- Changes stored locally
- No sync yet

User offline on laptop:
- Edits to "Meeting at 2:30 PM"
- Changes stored locally
- No sync yet

Both come online → Conflict!
```

**Resolution Strategy: Last-Write-Wins (LWW)**:
```
Each modification has timestamp:
- Phone edit: 2024-01-08T10:15:30Z
- Laptop edit: 2024-01-08T10:17:45Z

Server receives both:
1. Phone syncs first → Update applied
2. Laptop syncs later → Overwrites (later timestamp)
3. Result: "Meeting at 2:30 PM" (laptop version wins)

Timestamp stored: updated_at field
Conflict detection: Compare timestamps
Winner: Latest timestamp
```

**Advanced: Operational Transform (OT)**:
```
For field-level merging:

Phone changes:
- Title: "Meeting" → "Team Meeting"
- Time: 2 PM → 3 PM

Laptop changes:
- Location: "" → "Room A"
- Time: 2 PM → 2:30 PM

Merge result:
- Title: "Team Meeting" (from phone)
- Time: 2:30 PM (laptop wins - later timestamp)
- Location: "Room A" (from laptop)

Implementation: CRDTs (Conflict-free Replicated Data Types)
- Each field independently versioned
- Automatic merging without conflicts
- Used by Google Docs, can apply to Calendar
```

### 4. Scaling Event Queries

**Challenge**: Load month view with 100+ events efficiently

**Naive Approach**:
```sql
SELECT * FROM events
WHERE calendar_id = 'cal_123'
AND start_time >= '2024-01-01'
AND start_time < '2024-02-01'
AND deleted_at IS NULL
ORDER BY start_time

Problem:
- Fetches all event data (including large descriptions)
- May return 100+ events
- Slow for users with many events
```

**Optimized Approach**:
```sql
-- Step 1: Fetch minimal data for UI
SELECT 
  event_id,
  title,
  start_time,
  end_time,
  color,
  has_attendees,
  has_video_conference
FROM events
WHERE calendar_id = 'cal_123'
AND start_time >= '2024-01-01'
AND start_time < '2024-02-01'
AND deleted_at IS NULL
ORDER BY start_time
LIMIT 500

-- Step 2: Load details on-demand (when user clicks event)
-- Lazy loading pattern

Benefits:
- Smaller response size (20 KB vs 300 KB)
- Faster query (fewer columns)
- Faster rendering
- Load details only when needed
```

**Caching Strategy**:
```
Cache key: calendar_view:{calendar_id}:{year}:{month}

1. Check Redis:
   GET calendar_view:cal_123:2024:01
   
2. If cache hit:
   - Return cached data
   - TTL: 5 minutes
   - Fast response (< 10ms)

3. If cache miss:
   - Query database
   - Expand recurring events
   - Cache result
   - Return to client

Cache invalidation:
- On event create/update/delete
- DEL calendar_view:cal_123:*
- Next request rebuilds cache

Partial cache:
- Cache only hot months (current + next 2)
- Don't cache past events (rarely accessed)
```

---

## Scalability & Reliability

### Database Sharding

**Shard by Calendar ID**:
```
Shard count: 32
Routing: hash(calendar_id) % 32

Benefits:
- All events for a calendar co-located
- Efficient range queries
- No cross-shard queries for calendar views

Shard distribution:
- 3B calendars / 32 shards = 93.75M calendars per shard
- Each shard manages ~1.56B events

Rebalancing:
- Add shards as data grows
- Consistent hashing for minimal data movement
```

**Read Replicas**:
```
Configuration:
- 1 master (writes)
- 3 replicas per shard (reads)
- Replica lag: < 1 second

Read distribution:
- 80% read traffic
- Load balanced across replicas
- Writes always to master
- Async replication to replicas
```

### High Availability

**Multi-Region Deployment**:
```
3 Regions: US, Europe, Asia

Active-Active:
- All regions accept writes
- Async replication between regions
- Conflict resolution via timestamps
- Local reads (low latency)

Failover:
- Health checks every 10 seconds
- Region failure detected in 30 seconds
- Traffic rerouted to healthy region
- RTO: < 2 minutes
```

**Disaster Recovery**:
```
Backup Strategy:
- Daily snapshots to S3
- Point-in-time recovery (7 days)
- Cross-region replication
- Test restore monthly

Recovery Scenarios:
1. Single server failure:
   - Auto-failover to replica
   - Time: < 30 seconds
   - Data loss: None

2. Complete database failure:
   - Restore from backup
   - RTO: 4 hours
   - RPO: 1 hour (last backup)

3. Region failure:
   - Failover to another region
   - RTO: 2 minutes
   - RPO: 0 (replicated)
```

### Monitoring

**Key Metrics**:
```
Performance:
- Event creation latency (p95): < 100ms
- Calendar load time (p95): < 200ms
- Search latency (p95): < 100ms
- Availability check time: < 200ms

Reliability:
- API error rate: < 0.1%
- Database connection pool usage: < 80%
- Cache hit rate: > 90%
- Notification delivery rate: > 99%

Business:
- Daily active users
- Events created per day
- Calendar views per day
- Feature adoption rates
```

---

## Trade-offs & Alternatives

### 1. Storage: PostgreSQL vs NoSQL

**PostgreSQL** (Chosen):
```
Pros:
+ Strong consistency (ACID)
+ Complex queries (time ranges, joins)
+ Transactions (atomic updates)
+ Mature and battle-tested
+ Rich indexing

Cons:
- Vertical scaling limits
- Sharding complexity
- Write throughput limits

Use when: Need ACID, complex queries
```

**MongoDB**:
```
Pros:
+ Flexible schema
+ Horizontal scaling
+ Document model (fits events well)

Cons:
- Eventual consistency
- No ACID across collections
- Complex queries harder

Use when: Need flexibility, high writes
```

**Cassandra**:
```
Pros:
+ Write-optimized
+ Linear scalability
+ Multi-datacenter

Cons:
- No joins
- No transactions
- Complex time-range queries

Use when: Write-heavy, simple queries
```

### 2. Recurring Events: Expand vs Store Instances

**Expand on-the-fly** (Chosen):
```
Pros:
+ Storage efficient (1 template vs 100 instances)
+ Easy to edit series
+ Flexible recurrence rules

Cons:
- CPU cost for expansion
- Complex query logic
- Slow for long series

Use when: Moderate recurrence (< 100 instances)
```

**Store All Instances**:
```
Pros:
+ Simple queries (just SELECT)
+ Fast calendar loading
+ Easy to edit individual instances

Cons:
- Storage waste (100x more)
- Slow to create series
- Hard to edit all instances

Use when: Short recurrence or simple rules
```

### 3. Real-time Sync: WebSocket vs Polling

**WebSocket** (Chosen):
```
Pros:
+ Real-time updates (instant)
+ Lower latency
+ Bidirectional
+ Server can push changes

Cons:
- Persistent connections (resource intensive)
- Complex to scale
- Firewall issues

Use when: Need real-time collaboration
```

**Polling**:
```
Pros:
+ Simple to implement
+ No persistent connections
+ Works everywhere

Cons:
- Higher latency (polling interval)
- Wasted requests (no changes)
- Higher server load

Use when: Real-time not critical
```

### 4. Availability Check: Eager vs Lazy

**Lazy (Chosen)**:
```
On-demand when user requests:
+ No wasted computation
+ Always fresh data
- Slower (200ms query)
- Higher database load during peak

Use when: Feature not frequently used
```

**Eager (Pre-computed)**:
```
Pre-compute daily availability:
+ Instant response (cached)
+ Lower latency
- Wasted computation
- May be stale

Use when: Feature heavily used
```

---

## Conclusion

This calendar system design provides:

**Functionality**:
- Complete event lifecycle (CRUD, recurring, attendees)
- Advanced scheduling (availability, conflicts)
- Rich sharing and permissions
- Multi-device synchronization
- Integration with video conferencing

**Performance**:
- <100ms event creation
- <200ms calendar loading
- <50ms conflict detection
- 95% cache hit rate

**Scale**:
- 1B users, 200M DAU
- 50B events stored
- 500M events created daily
- 72K peak QPS
- 9PB total storage

**Reliability**:
- 99.99% availability
- Multi-region active-active
- Disaster recovery (RTO: 4 hours)
- Conflict-free sync
- Comprehensive monitoring

The system balances feature richness, performance, and scalability while providing a seamless experience across devices and time zones.

---

**Document Status**: Complete ✓
