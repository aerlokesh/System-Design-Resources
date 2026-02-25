# Design a Calendar System (Free/Busy & Scheduling) - Hello Interview Framework

> **Question**: Design a calendar system that allows users to register, manage their schedules, create events, share calendars, and view other people's availability and free/busy times. The system must support time-range queries, recurring events, permissions (free/busy vs details), and multi-device experiences.
>
> **Asked at**: Microsoft, LinkedIn
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
1. **Event CRUD**: Users create, read, update, and delete calendar events with title, description, start/end time, location, and attendees.
2. **Free/Busy Query**: Given a list of users and a time range, return their free/busy status (busy slots, not event details) — the core scheduling primitive.
3. **Calendar Sharing & Permissions**: Users share calendars with others at different permission levels: free/busy only, read details, or full edit access.
4. **Recurring Events**: Support daily, weekly, monthly, yearly recurrence patterns with exceptions (e.g., "every Tuesday except Dec 25").
5. **Multi-User Scheduling**: Find common free slots across N attendees in a given time range to suggest meeting times.
6. **Time Zone Support**: All events stored in UTC; displayed in user's local time zone. Events that span time zone changes handled correctly.

#### Nice to Have (P1)
- Real-time calendar sync across devices (phone, desktop, web).
- Event invitations with RSVP (accept/tentative/decline).
- Working hours configuration (only suggest meetings during 9-5).
- Room/resource booking (conference rooms, projectors).
- Calendar overlays (view multiple calendars simultaneously).
- Reminders and notifications (15 min before meeting).
- CalDAV/iCal protocol support for third-party client interoperability.

#### Below the Line (Out of Scope)
- Video conferencing integration (Zoom/Teams link generation).
- Task management / to-do lists.
- Email system for sending invitations.
- Full-text search across event descriptions.
- Calendar analytics (time spent in meetings reports).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Read Latency** | < 100ms P99 for single calendar view | Users expect instant calendar load |
| **Free/Busy Query Latency** | < 200ms P99 for 10 users × 1 week | Scheduling assistant must feel responsive |
| **Write Latency** | < 300ms for event creation | Acceptable for create/update operations |
| **Availability** | 99.99% for reads, 99.9% for writes | Calendar is a critical productivity tool |
| **Consistency** | Strong for event writes (no double-booking), eventual for free/busy | Cannot lose events; free/busy can lag by seconds |
| **Concurrency** | No double-booking of same time slot for same user | Optimistic locking on conflicting events |
| **Scale** | 500M users, 50B events, 10M concurrent users | Enterprise + consumer scale (Google/Outlook) |
| **Multi-device Sync** | < 5 seconds for event changes to appear on other devices | Near-real-time sync |

### Capacity Estimation

```
Users:
  Total users: 500M
  Daily active users: 100M
  Average calendars per user: 3 (personal, work, shared)
  
Events:
  Average events per user per week: 20
  Total events: 500M users × 20/week × 52 weeks × 5 years = ~50B events (historical)
  Active events (next 6 months): 500M × 20/week × 26 weeks = ~260B event instances
    (but most are recurring instances, not separate rows)
  
  New events per day: 500M (create + recurring expansions)
  Events per second: ~5,800 writes/sec sustained
  
  Average event size: 500 bytes (metadata)
  Recurring rule overhead: 100 bytes per recurring series

Reads:
  Calendar views per day: 100M DAU × 5 loads = 500M calendar reads/day
  Calendar reads per second: ~5,800 sustained, 50K peak
  Free/busy queries per day: 50M (scheduling assistant)
  Free/busy queries per second: ~580 sustained, 5K peak

Storage:
  Event metadata: 50B events × 500 bytes = ~25 TB
  Recurring rules: 2B recurring series × 100 bytes = ~200 GB
  Free/busy index: 500M users × 1 KB (pre-computed weekly bitmap) = ~500 GB
  
  Total: ~25 TB events + ~500 GB indexes
```

---

## 2️⃣ Core Entities

### Entity 1: Event
```java
public class Event {
    private final String eventId;           // UUID
    private final String calendarId;        // Which calendar this belongs to
    private final String organizerId;       // User who created the event
    private final String title;
    private final String description;
    private final Instant startTime;        // UTC
    private final Instant endTime;          // UTC
    private final String timeZone;          // IANA tz (e.g., "America/New_York")
    private final boolean allDay;           // All-day event (date-only, no time)
    private final String location;
    private final EventStatus status;       // CONFIRMED, TENTATIVE, CANCELLED
    private final String recurrenceRuleId;  // Null if single event; FK to recurrence rule
    private final Instant originalStartTime;// For recurring exceptions: which instance this modifies
    private final List<Attendee> attendees;
    private final Visibility visibility;    // PUBLIC, PRIVATE, DEFAULT
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;             // Optimistic locking for concurrency
}

public enum EventStatus {
    CONFIRMED, TENTATIVE, CANCELLED
}

public enum Visibility {
    PUBLIC,     // Anyone with calendar access sees full details
    PRIVATE,    // Only free/busy visible to shared users
    DEFAULT     // Follows calendar-level default
}
```

### Entity 2: Calendar
```java
public class Calendar {
    private final String calendarId;        // UUID
    private final String ownerId;           // User who owns this calendar
    private final String name;              // "Work", "Personal", "Team Calendar"
    private final String description;
    private final String timeZone;          // Default time zone for this calendar
    private final String color;             // Display color (#FF5733)
    private final Visibility defaultVisibility;
    private final Instant createdAt;
}
```

### Entity 3: Recurrence Rule
```java
public class RecurrenceRule {
    private final String ruleId;            // UUID
    private final String eventId;           // Master event ID
    private final RecurrenceFrequency frequency; // DAILY, WEEKLY, MONTHLY, YEARLY
    private final int interval;             // Every N days/weeks/months
    private final List<DayOfWeek> byDay;    // For weekly: [MONDAY, WEDNESDAY, FRIDAY]
    private final int byMonthDay;           // For monthly: day of month (1-31)
    private final int byMonth;              // For yearly: month (1-12)
    private final Instant until;            // End date (null = forever)
    private final int count;                // Max occurrences (0 = unlimited)
    private final List<Instant> exceptionDates; // Dates to skip (holidays, cancelled)
}

public enum RecurrenceFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
```

### Entity 4: Calendar Share (Permission)
```java
public class CalendarShare {
    private final String shareId;
    private final String calendarId;
    private final String sharedWithUserId;  // Or group ID
    private final SharePermission permission;
    private final Instant sharedAt;
    private final String sharedBy;          // User who granted access
}

public enum SharePermission {
    FREE_BUSY_ONLY,     // Can only see free/busy status
    READ_DETAILS,       // Can see event titles, times, attendees
    READ_WRITE,         // Can create/edit/delete events
    OWNER               // Full control including sharing
}
```

### Entity 5: Attendee
```java
public class Attendee {
    private final String userId;
    private final String email;
    private final AttendeeRole role;        // ORGANIZER, REQUIRED, OPTIONAL
    private final AttendeeStatus rsvp;      // ACCEPTED, DECLINED, TENTATIVE, NEEDS_ACTION
    private final Instant respondedAt;
}

public enum AttendeeRole {
    ORGANIZER, REQUIRED, OPTIONAL, RESOURCE
}

public enum AttendeeStatus {
    NEEDS_ACTION, ACCEPTED, DECLINED, TENTATIVE
}
```

### Entity 6: Free/Busy Block
```java
public class FreeBusyBlock {
    private final String userId;
    private final Instant startTime;        // UTC
    private final Instant endTime;          // UTC
    private final BusyType type;            // BUSY, TENTATIVE, OUT_OF_OFFICE, FREE
}

public enum BusyType {
    BUSY,           // Confirmed event
    TENTATIVE,      // Tentative event
    OUT_OF_OFFICE,  // OOO
    FREE            // Available (working hours)
}
```

---

## 3️⃣ API Design

### 1. Create Event
```
POST /api/v1/calendars/{calendar_id}/events

Request:
{
  "title": "Weekly Team Standup",
  "description": "Discuss sprint progress",
  "start_time": "2025-01-13T09:00:00",
  "end_time": "2025-01-13T09:30:00",
  "time_zone": "America/New_York",
  "location": "Conference Room A",
  "attendees": [
    { "user_id": "user_456", "role": "REQUIRED" },
    { "user_id": "user_789", "role": "OPTIONAL" }
  ],
  "recurrence": {
    "frequency": "WEEKLY",
    "by_day": ["MONDAY"],
    "until": "2025-06-30T00:00:00Z"
  },
  "visibility": "DEFAULT",
  "reminders": [{ "minutes_before": 15 }]
}

Response (201 Created):
{
  "event_id": "evt_123",
  "calendar_id": "cal_abc",
  "title": "Weekly Team Standup",
  "start_time": "2025-01-13T14:00:00Z",
  "end_time": "2025-01-13T14:30:00Z",
  "recurrence_rule_id": "rrule_001",
  "created_at": "2025-01-10T10:00:00Z"
}
```

### 2. Get Events (Calendar View)
```
GET /api/v1/calendars/{calendar_id}/events?start=2025-01-13T00:00:00Z&end=2025-01-19T23:59:59Z&time_zone=America/New_York

Response (200 OK):
{
  "events": [
    {
      "event_id": "evt_123",
      "title": "Weekly Team Standup",
      "start_time": "2025-01-13T14:00:00Z",
      "end_time": "2025-01-13T14:30:00Z",
      "is_recurring": true,
      "recurrence_instance": "2025-01-13",
      "attendees": [ ... ],
      "status": "CONFIRMED"
    },
    {
      "event_id": "evt_456",
      "title": "Lunch with Alice",
      "start_time": "2025-01-14T17:00:00Z",
      "end_time": "2025-01-14T18:00:00Z",
      "is_recurring": false,
      "status": "CONFIRMED"
    }
  ],
  "time_range": { "start": "2025-01-13T00:00:00Z", "end": "2025-01-19T23:59:59Z" }
}
```

### 3. Query Free/Busy (Core Scheduling Primitive)
```
POST /api/v1/freebusy

Request:
{
  "users": ["user_123", "user_456", "user_789"],
  "time_min": "2025-01-13T00:00:00Z",
  "time_max": "2025-01-17T23:59:59Z",
  "time_zone": "America/New_York"
}

Response (200 OK):
{
  "calendars": {
    "user_123": {
      "busy": [
        { "start": "2025-01-13T14:00:00Z", "end": "2025-01-13T14:30:00Z", "type": "BUSY" },
        { "start": "2025-01-14T18:00:00Z", "end": "2025-01-14T19:00:00Z", "type": "TENTATIVE" }
      ]
    },
    "user_456": {
      "busy": [
        { "start": "2025-01-13T15:00:00Z", "end": "2025-01-13T16:00:00Z", "type": "BUSY" }
      ]
    },
    "user_789": {
      "busy": []
    }
  }
}
```

> **Key**: Free/busy returns only time blocks, NOT event details — respects privacy.

### 4. Find Common Free Slots (Scheduling Assistant)
```
POST /api/v1/scheduling/find-time

Request:
{
  "attendees": ["user_123", "user_456", "user_789"],
  "duration_minutes": 30,
  "time_min": "2025-01-13T00:00:00Z",
  "time_max": "2025-01-17T23:59:59Z",
  "time_zone": "America/New_York",
  "working_hours_only": true
}

Response (200 OK):
{
  "suggestions": [
    {
      "start": "2025-01-13T15:00:00Z",
      "end": "2025-01-13T15:30:00Z",
      "confidence": "ALL_AVAILABLE",
      "conflicts": []
    },
    {
      "start": "2025-01-14T14:00:00Z",
      "end": "2025-01-14T14:30:00Z",
      "confidence": "ALL_AVAILABLE",
      "conflicts": []
    },
    {
      "start": "2025-01-15T16:00:00Z",
      "end": "2025-01-15T16:30:00Z",
      "confidence": "SOME_TENTATIVE",
      "conflicts": [{ "user": "user_456", "type": "TENTATIVE" }]
    }
  ]
}
```

### 5. Share Calendar
```
POST /api/v1/calendars/{calendar_id}/shares

Request:
{
  "user_id": "user_456",
  "permission": "READ_DETAILS"
}

Response (201 Created):
{
  "share_id": "share_001",
  "calendar_id": "cal_abc",
  "shared_with": "user_456",
  "permission": "READ_DETAILS"
}
```

### 6. Update/Delete Event
```
PATCH /api/v1/events/{event_id}?scope=THIS_INSTANCE

Request:
{
  "title": "Standup (Moved)",
  "start_time": "2025-01-13T10:00:00",
  "end_time": "2025-01-13T10:30:00",
  "time_zone": "America/New_York"
}

Response (200 OK):
{ "event_id": "evt_123", "updated_at": "2025-01-12T10:00:00Z" }
```

> **Scope** for recurring events: `THIS_INSTANCE`, `THIS_AND_FUTURE`, `ALL_INSTANCES`.

---

## 4️⃣ Data Flow

### Flow 1: Creating an Event (Write Path)
```
1. User creates event via POST /api/v1/calendars/{cal_id}/events
   ↓
2. Calendar Service:
   a. Validate: check user owns calendar or has WRITE permission
   b. Time zone conversion: convert local time → UTC for storage
   c. Conflict check: query user's existing events in time range
      → If conflict found: return 409 Conflict (or warn if configured)
   d. Store event in Events DB (PostgreSQL)
   e. If recurring: store RecurrenceRule, link to master event
   ↓
3. Async side-effects (via event bus):
   a. Update Free/Busy index for organizer
   b. Send invitations to attendees (email/push notification)
   c. Update Free/Busy index for attendees (tentative until RSVP)
   d. Notify connected devices for real-time sync
   ↓
4. Return 201 Created with event details
```

### Flow 2: Free/Busy Query (Read Path — Hot Path)
```
1. Client calls POST /api/v1/freebusy with user list + time range
   ↓
2. Free/Busy Service:
   a. Permission check: does requester have free/busy access to each user?
   b. For each requested user:
      Option A (fast path): Read from pre-computed Free/Busy cache (Redis)
        → Key: freebusy:{user_id}:{date}
        → Value: list of busy blocks for that date
        → O(1) lookup per user per day
      
      Option B (fallback): Compute on-the-fly from Events DB
        → Query events for user in time range (including recurring expansion)
        → Convert to busy blocks
        → Cache the result
   ↓
3. Merge: collect all busy blocks, group by user
   ↓
4. Return free/busy data (without event details)
```

### Flow 3: Recurring Event Expansion
```
1. User queries calendar for Jan 13-19 week view
   ↓
2. Event Service:
   a. Fetch single (non-recurring) events: WHERE start_time BETWEEN ? AND ?
   b. Fetch recurring series active in this range:
      → Get all RecurrenceRules WHERE series_start <= range_end 
        AND (series_end IS NULL OR series_end >= range_start)
   c. For each recurring rule: EXPAND instances in the query range
      → Apply frequency, interval, byDay rules
      → Subtract exception dates
      → Generate virtual event instances (not stored in DB)
   ↓
3. Merge single events + expanded recurring instances
   ↓
4. Sort by start_time, return to client
```

### Flow 4: Multi-Device Sync (Real-Time)
```
1. Event created/updated on Device A
   ↓
2. Calendar Service stores event, publishes to Kafka "calendar-changes" topic
   ↓
3. Sync Service:
   a. Consumes change event
   b. Looks up all devices registered for this user
   c. Push notification via WebSocket (if online) or push notification (if offline)
   ↓
4. Device B receives change → updates local calendar view
   Latency: < 5 seconds end-to-end
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│                                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐               │
│  │ Web App   │  │ iOS App   │  │ Android  │  │ CalDAV/iCal  │               │
│  │           │  │           │  │ App      │  │ Clients      │               │
│  └─────┬─────┘  └─────┬────┘  └─────┬─────┘  └──────┬───────┘               │
│        │              │              │               │                       │
└────────┼──────────────┼──────────────┼───────────────┼───────────────────────┘
         │              │              │               │
         └──────────────┼──────────────┼───────────────┘
                        │  REST / WebSocket
                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY / LOAD BALANCER                            │
│                                                                               │
│  • Auth (OAuth2 / JWT)     • Rate limiting                                   │
│  • Request routing         • WebSocket upgrade for real-time sync            │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                │               │               │
          ┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────────┐
          │ CALENDAR    │ │ FREE/BUSY  │ │ SCHEDULING     │
          │ SERVICE     │ │ SERVICE    │ │ ASSISTANT      │
          │             │ │            │ │                │
          │ • Event CRUD│ │ • Query    │ │ • Find common  │
          │ • Recurring │ │   free/busy│ │   free slots   │
          │   expansion │ │ • Per-user │ │ • Suggest times│
          │ • Permissions││   busy     │ │ • Working hours│
          │ • Sharing   │ │   blocks   │ │                │
          │             │ │            │ │ Pods: 10-20    │
          │ Pods: 50-100│ │ Pods: 30-50│ └────────────────┘
          └──────┬──────┘ └──────┬─────┘
                 │               │
    ┌────────────┼───────────────┼────────────────┐
    │            │               │                │
    ▼            ▼               ▼                ▼
┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐
│ EVENT    │ │ FREE/BUSY│ │ CALENDAR     │ │ USER PROFILE │
│ STORE    │ │ CACHE    │ │ & PERMISSION │ │ CACHE        │
│          │ │          │ │ STORE        │ │              │
│PostgreSQL│ │ Redis    │ │ PostgreSQL   │ │ Redis        │
│ Sharded  │ │ Cluster  │ │              │ │              │
│ by       │ │          │ │ • calendars  │ │ • Time zones │
│ calendar │ │ Per-user │ │ • shares     │ │ • Working hrs│
│ _id      │ │ daily    │ │ • permissions│ │ • Preferences│
│          │ │ busy     │ │              │ │              │
│ ~25 TB   │ │ blocks   │ │ ~500 GB      │ │ ~10 GB       │
│          │ │ ~500 GB  │ │              │ │              │
└──────────┘ └──────────┘ └──────────────┘ └──────────────┘

                │
                │ Kafka: "calendar-changes"
                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KAFKA / EVENT BUS                                    │
│                                                                               │
│  Topic: "calendar-changes"  (event create/update/delete)                     │
│  Topic: "freebusy-updates"  (free/busy index invalidation)                   │
│  Topic: "invitations"       (attendee invitation events)                     │
│  Topic: "reminders"         (scheduled reminder triggers)                    │
└──────────┬──────────────────────────┬────────────────────────────────────────┘
           │                          │
     ┌─────▼──────────────┐    ┌─────▼──────────────┐
     │ FREE/BUSY INDEX    │    │ SYNC SERVICE        │
     │ UPDATER            │    │                     │
     │                    │    │ • Consume calendar   │
     │ • Consume event    │    │   change events      │
     │   changes          │    │ • Push to connected  │
     │ • Recompute        │    │   devices via        │
     │   free/busy blocks │    │   WebSocket / Push   │
     │ • Update Redis     │    │                     │
     │   cache            │    │ Pods: 20-50         │
     │                    │    └─────────────────────┘
     │ Pods: 10-20        │
     └────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                     NOTIFICATION & REMINDER SERVICE                           │
│                                                                               │
│  • Scheduled reminders (15 min before meeting)                               │
│  • Email invitations to external attendees                                   │
│  • Push notifications for RSVP changes                                       │
│  • Backed by: delayed message queue (SQS / Kafka delay)                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **Calendar Service** | Event CRUD, recurring expansion, permissions, sharing | Java on K8s | 50-100 pods |
| **Free/Busy Service** | Query busy blocks for users, aggregation | Go on K8s | 30-50 pods (read-heavy) |
| **Scheduling Assistant** | Find common free slots across N users | Java on K8s | 10-20 pods |
| **Event Store** | Source of truth for events and recurrence rules | PostgreSQL (sharded by calendar_id) | ~25 TB, sharded |
| **Free/Busy Cache** | Pre-computed daily busy blocks per user | Redis Cluster | ~500 GB |
| **Calendar/Permission Store** | Calendars, shares, permissions | PostgreSQL | ~500 GB |
| **Free/Busy Index Updater** | Recompute free/busy cache on event changes | Kafka consumer (Go) | 10-20 pods |
| **Sync Service** | Real-time multi-device sync via WebSocket/push | Go/Rust on K8s | 20-50 pods |
| **Kafka** | Event streaming for async processing | Apache Kafka | 10+ brokers |

---

## 6️⃣ Deep Dives

### Deep Dive 1: Time Modeling & Event Storage Schema

**The Problem**: Calendar events span time ranges, can recur, can be all-day, and must support efficient time-range queries. The schema must handle: "give me all events between Jan 13 and Jan 19 for this calendar."

```sql
-- Events table: one row per single event OR per recurring series master
CREATE TABLE events (
    event_id          UUID PRIMARY KEY,
    calendar_id       UUID NOT NULL,
    organizer_id      UUID NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    start_time        TIMESTAMP WITH TIME ZONE NOT NULL,  -- UTC
    end_time          TIMESTAMP WITH TIME ZONE NOT NULL,  -- UTC
    original_tz       VARCHAR(50) NOT NULL,                -- IANA timezone
    all_day           BOOLEAN DEFAULT FALSE,
    location          VARCHAR(500),
    status            VARCHAR(20) DEFAULT 'CONFIRMED',
    visibility        VARCHAR(20) DEFAULT 'DEFAULT',
    
    -- Recurrence
    recurrence_rule_id UUID,                               -- FK to recurrence_rules
    is_recurring_master BOOLEAN DEFAULT FALSE,             -- True for the "template" event
    recurring_master_id UUID,                              -- For exceptions: points to master
    original_start     TIMESTAMP WITH TIME ZONE,           -- Which instance this exception modifies
    
    -- Optimistic locking
    version           BIGINT DEFAULT 1,
    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP DEFAULT NOW(),
    
    -- Indexing
    CONSTRAINT idx_calendar_time UNIQUE (calendar_id, start_time, event_id)
);

-- Time-range query index (the critical query)
CREATE INDEX idx_events_calendar_range ON events (calendar_id, start_time, end_time);

-- For recurring series: need to find active series for any given range
CREATE INDEX idx_events_recurring ON events (calendar_id, is_recurring_master, start_time)
    WHERE is_recurring_master = TRUE;

-- Recurrence rules: RFC 5545 (iCalendar) compatible
CREATE TABLE recurrence_rules (
    rule_id           UUID PRIMARY KEY,
    event_id          UUID NOT NULL REFERENCES events(event_id),
    frequency         VARCHAR(10) NOT NULL,  -- DAILY, WEEKLY, MONTHLY, YEARLY
    interval_val      INT DEFAULT 1,         -- Every N frequency units
    by_day            VARCHAR(50),           -- "MO,WE,FR" for weekly
    by_month_day      INT,                   -- 1-31 for monthly
    by_month          INT,                   -- 1-12 for yearly
    until_time        TIMESTAMP WITH TIME ZONE, -- End date (null = forever)
    count             INT DEFAULT 0,          -- Max occurrences (0 = unlimited)
    exception_dates   TIMESTAMP[] DEFAULT '{}' -- Dates to skip
);
```

**Time-range query pattern**:
```java
public class EventQueryService {

    /**
     * Two-phase query for "events in this week":
     * 
     * Phase 1: Fetch SINGLE events in range
     *   SELECT * FROM events 
     *   WHERE calendar_id = ? 
     *     AND start_time < ?  (range_end)
     *     AND end_time > ?    (range_start)
     *     AND is_recurring_master = FALSE
     *   ORDER BY start_time;
     * 
     * Phase 2: Fetch RECURRING SERIES active in range, then EXPAND
     *   SELECT e.*, r.* FROM events e
     *   JOIN recurrence_rules r ON e.recurrence_rule_id = r.rule_id
     *   WHERE e.calendar_id = ?
     *     AND e.is_recurring_master = TRUE
     *     AND e.start_time <= ?   (range_end — series started before range ends)
     *     AND (r.until_time IS NULL OR r.until_time >= ?)  (range_start — series hasn't ended)
     * 
     * Phase 3: For each recurring series, EXPAND in-memory
     *   → Compute instances using frequency/interval/byDay rules
     *   → Filter to query range
     *   → Subtract exception_dates
     *   → Apply per-instance overrides (exceptions stored as separate events)
     */
    
    public List<Event> getEventsInRange(String calendarId, Instant rangeStart, Instant rangeEnd) {
        // Phase 1: Single events
        List<Event> singles = eventRepo.findSingleEventsInRange(calendarId, rangeStart, rangeEnd);
        
        // Phase 2: Recurring series masters
        List<Event> recurringMasters = eventRepo.findRecurringMastersActiveInRange(
            calendarId, rangeStart, rangeEnd);
        
        // Phase 3: Expand recurring instances
        List<Event> expandedInstances = new ArrayList<>();
        for (Event master : recurringMasters) {
            RecurrenceRule rule = recurrenceRepo.findByEventId(master.getEventId());
            List<Event> instances = recurrenceExpander.expand(master, rule, rangeStart, rangeEnd);
            expandedInstances.addAll(instances);
        }
        
        // Phase 4: Apply exceptions (overrides for specific instances)
        List<Event> exceptions = eventRepo.findExceptionsForMasters(
            recurringMasters.stream().map(Event::getEventId).toList());
        applyExceptions(expandedInstances, exceptions);
        
        // Merge and sort
        List<Event> allEvents = new ArrayList<>();
        allEvents.addAll(singles);
        allEvents.addAll(expandedInstances);
        allEvents.sort(Comparator.comparing(Event::getStartTime));
        
        return allEvents;
    }
}
```

---

### Deep Dive 2: Recurring Event Expansion — The Hardest Part

**The Problem**: A recurring event "every Monday 9-9:30am" created on Jan 1 generates infinite future instances. We can't store each instance as a row (infinite storage). We must expand on-the-fly at query time.

```java
public class RecurrenceExpander {

    /**
     * Expansion strategies:
     * 
     * Option A: Lazy expansion at read time (chosen)
     *   - Store only the master event + recurrence rule
     *   - On query: expand instances within the query range
     *   - Pros: no storage for future instances; handles infinite recurrence
     *   - Cons: CPU cost at read time; must be fast (< 10ms per series)
     * 
     * Option B: Materialized instances (pre-compute N months ahead)
     *   - Store actual event rows for next 6 months
     *   - Batch job regenerates when approaching the horizon
     *   - Pros: fast reads (no expansion needed); simpler queries
     *   - Cons: storage explosion (20 events/week × 26 weeks = 520 rows per series)
     * 
     * Option C: Hybrid (chosen for free/busy)
     *   - Lazy expansion for calendar view (accuracy)
     *   - Materialized free/busy blocks in Redis (performance)
     *   - Best of both worlds
     */
    
    public List<Event> expand(Event master, RecurrenceRule rule, 
                               Instant rangeStart, Instant rangeEnd) {
        List<Event> instances = new ArrayList<>();
        
        // Start from the master's start time
        ZonedDateTime current = master.getStartTime()
            .atZone(ZoneId.of(master.getTimeZone()));
        Duration eventDuration = Duration.between(master.getStartTime(), master.getEndTime());
        
        int count = 0;
        int maxCount = rule.getCount() > 0 ? rule.getCount() : Integer.MAX_VALUE;
        
        while (count < maxCount) {
            Instant instanceStart = current.toInstant();
            Instant instanceEnd = instanceStart.plus(eventDuration);
            
            // Stop if past the range or past the "until" date
            if (instanceStart.isAfter(rangeEnd)) break;
            if (rule.getUntil() != null && instanceStart.isAfter(rule.getUntil())) break;
            
            // Check if this instance is in the query range
            if (instanceEnd.isAfter(rangeStart) && instanceStart.isBefore(rangeEnd)) {
                // Check if this date is in the exception list
                if (!rule.getExceptionDates().contains(instanceStart)) {
                    Event instance = master.toBuilder()
                        .startTime(instanceStart)
                        .endTime(instanceEnd)
                        .originalStartTime(instanceStart)  // For exception identification
                        .build();
                    instances.add(instance);
                }
            }
            
            // Advance to next occurrence
            current = advanceByRule(current, rule);
            count++;
        }
        
        return instances;
    }
    
    private ZonedDateTime advanceByRule(ZonedDateTime current, RecurrenceRule rule) {
        return switch (rule.getFrequency()) {
            case DAILY -> current.plusDays(rule.getInterval());
            case WEEKLY -> advanceWeekly(current, rule);
            case MONTHLY -> current.plusMonths(rule.getInterval());
            case YEARLY -> current.plusYears(rule.getInterval());
        };
    }
    
    /**
     * DST (Daylight Saving Time) handling:
     * 
     * "Every Monday at 9am New York time" should ALWAYS be at 9am local.
     * - In winter: 9am EST = 14:00 UTC
     * - In summer: 9am EDT = 13:00 UTC
     * 
     * Solution: store original timezone, expand in local time, convert to UTC
     * → ZonedDateTime handles DST transitions automatically
     * → A meeting "at 9am" stays "at 9am" regardless of DST changes
     */
}
```

**Recurring exception handling** (editing "this instance only"):
```
When user edits a single instance of a recurring event:

1. Create a NEW event row with:
   - recurring_master_id = master's event_id
   - original_start = the original instance's start time
   - The modified fields (new title, new time, etc.)

2. Add the original instance's start time to exception_dates on the recurrence rule

3. On expansion:
   - Expand generates the instance at the original time
   - Exception date removes it
   - The override event (with original_start) replaces it

Result: "Every Monday at 9am" with Tuesday Jan 14 moved to 10am:
  - Master: RRULE:FREQ=WEEKLY;BYDAY=MO
  - Exception: 2025-01-13 (the Monday that was moved)
  - Override event: start=2025-01-14T10:00, original_start=2025-01-13T09:00
```

---

### Deep Dive 3: Free/Busy Index — Pre-Computed for Fast Scheduling

**The Problem**: The scheduling assistant must query free/busy for 10 users across 5 days in < 200ms. Computing free/busy on-the-fly from events (including recurring expansion) for 10 users is too slow.

```java
public class FreeBusyIndexService {

    /**
     * Pre-computed Free/Busy cache in Redis:
     * 
     * Key: freebusy:{user_id}:{date}  (e.g., freebusy:user_123:2025-01-13)
     * Value: JSON array of busy blocks
     *   [
     *     { "start": 1736773200, "end": 1736775000, "type": "BUSY" },
     *     { "start": 1736798400, "end": 1736802000, "type": "TENTATIVE" }
     *   ]
     * TTL: 7 days (re-computed on any event change)
     * 
     * Population:
     *   - On event create/update/delete → publish to "freebusy-updates" topic
     *   - Free/Busy Index Updater consumer:
     *     1. Fetch all events for user on affected dates
     *     2. Expand recurring events
     *     3. Merge overlapping busy blocks
     *     4. Write to Redis
     * 
     * Query performance:
     *   10 users × 5 days = 50 Redis GET operations
     *   With pipelining: 1 round-trip, 50 keys → < 5ms
     *   Total with processing: < 20ms
     */
    
    // Query free/busy from cache (hot path)
    public Map<String, List<FreeBusyBlock>> queryFreeBusy(
            List<String> userIds, Instant rangeStart, Instant rangeEnd) {
        
        Map<String, List<FreeBusyBlock>> result = new HashMap<>();
        
        // Generate all date keys for the range
        List<LocalDate> dates = getDatesBetween(rangeStart, rangeEnd);
        
        // Batch fetch from Redis (pipelined)
        List<String> keys = new ArrayList<>();
        for (String userId : userIds) {
            for (LocalDate date : dates) {
                keys.add("freebusy:" + userId + ":" + date);
            }
        }
        
        List<String> values = redis.mget(keys);
        
        // Parse and group by user
        int i = 0;
        for (String userId : userIds) {
            List<FreeBusyBlock> blocks = new ArrayList<>();
            for (LocalDate date : dates) {
                String json = values.get(i++);
                if (json != null) {
                    blocks.addAll(parseBlocks(json));
                }
            }
            result.put(userId, blocks);
        }
        
        return result;
    }
    
    // Re-compute free/busy for a user on a specific date (called on event change)
    public void recomputeForDate(String userId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        // Get all events for this user on this date (across all their calendars)
        List<String> calendarIds = calendarService.getCalendarIdsForUser(userId);
        List<Event> events = new ArrayList<>();
        for (String calId : calendarIds) {
            events.addAll(eventQueryService.getEventsInRange(calId, dayStart, dayEnd));
        }
        
        // Convert events to busy blocks
        List<FreeBusyBlock> blocks = events.stream()
            .filter(e -> e.getStatus() != EventStatus.CANCELLED)
            .map(e -> new FreeBusyBlock(
                userId, e.getStartTime(), e.getEndTime(),
                e.getStatus() == EventStatus.TENTATIVE ? BusyType.TENTATIVE : BusyType.BUSY))
            .toList();
        
        // Merge overlapping blocks
        List<FreeBusyBlock> merged = mergeOverlapping(blocks);
        
        // Write to Redis
        String key = "freebusy:" + userId + ":" + date;
        redis.setex(key, Duration.ofDays(7), serialize(merged));
    }
}
```

---

### Deep Dive 4: Concurrency — Preventing Double-Booking

**The Problem**: Two users simultaneously try to book the same conference room at 2pm, or a user creates two overlapping events. We must prevent double-booking while maintaining performance.

```java
public class ConcurrencyControl {

    /**
     * Two layers of concurrency protection:
     * 
     * Layer 1: OPTIMISTIC LOCKING (per-event)
     *   - Each event has a version column
     *   - UPDATE events SET ... WHERE event_id = ? AND version = ?
     *   - If version mismatch → 409 Conflict (another user modified it)
     *   - Handles: concurrent edits to the SAME event
     * 
     * Layer 2: OVERLAP CHECK (per-user/resource time slot)
     *   - Before creating an event, check for overlapping events:
     *     SELECT COUNT(*) FROM events
     *     WHERE calendar_id = ? AND start_time < ? AND end_time > ?
     *     AND status != 'CANCELLED'
     *   - If overlap found → warn user or reject (configurable)
     *   - For RESOURCES (conference rooms): strict rejection (no double-booking)
     *   
     *   Race condition: two creates check simultaneously, both see no overlap
     *   Solution: SERIALIZABLE isolation for resource booking
     *   OR: advisory lock on (calendar_id, time_slot)
     */
    
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Event createEventWithConflictCheck(String calendarId, Event event) {
        // Check for overlaps
        int overlaps = eventRepo.countOverlapping(
            calendarId, event.getStartTime(), event.getEndTime());
        
        if (overlaps > 0 && isResourceCalendar(calendarId)) {
            throw new ConflictException("Time slot already booked for this resource");
        }
        
        // No conflict → create
        return eventRepo.save(event);
    }
    
    // For user calendars: warn but allow (users can have overlapping events)
    // For resource calendars: strict rejection
    
    /**
     * Alternative: Advisory locking for high-contention resources
     * 
     * SELECT pg_advisory_xact_lock(hashtext(calendar_id || ':' || time_slot));
     * -- Now we have exclusive access to this calendar+timeslot
     * -- Check overlap, create if clear
     * -- Lock released on transaction commit
     * 
     * This serializes only conflicting bookings (same room, same time)
     * Non-conflicting bookings proceed in parallel
     */
}
```

---

### Deep Dive 5: Time Zones — The Subtle Complexity

**The Problem**: A user in New York creates "Every Monday 9am" and shares the calendar with a colleague in London. The London user sees "Every Monday 2pm". When DST changes, New York shifts but the meeting should still be "9am local New York time."

```java
public class TimeZoneHandler {

    /**
     * Time zone strategy:
     * 
     * STORAGE: Always UTC in the database
     *   start_time: 2025-01-13T14:00:00Z (UTC)
     *   original_tz: "America/New_York"
     * 
     * DISPLAY: Convert to viewer's timezone
     *   New York viewer: 2025-01-13 09:00 AM EST
     *   London viewer:   2025-01-13 02:00 PM GMT
     *   Tokyo viewer:    2025-01-13 11:00 PM JST
     * 
     * RECURRING EVENTS: Expand in the CREATOR'S timezone, then convert to UTC
     *   "Every Monday 9am New York" during DST transition:
     *   - Jan 13 (EST): 9am EST = 14:00 UTC
     *   - Mar 10 (EDT): 9am EDT = 13:00 UTC  ← UTC time CHANGES, local stays 9am
     *   
     *   If we stored recurring rules in UTC, the meeting would drift by 1 hour
     *   during DST transitions. By expanding in local time first, then converting,
     *   the meeting is always at 9am New York time.
     * 
     * ALL-DAY EVENTS: Date-only, no time component
     *   Stored as: date = 2025-01-13 (no timezone)
     *   Displayed as: "January 13" regardless of viewer's timezone
     *   → All-day events span midnight-to-midnight in the CREATOR'S timezone
     *   → For free/busy: block the entire day in the creator's timezone
     * 
     * FLOATING vs ABSOLUTE times:
     *   - "Flight departing at 3pm" = ABSOLUTE (specific timezone)
     *   - "Lunch at noon" = could be FLOATING (same local time everywhere)
     *   - We store all times as ABSOLUTE (UTC + original_tz)
     */
    
    public Instant localToUtc(LocalDateTime localTime, String timeZone) {
        ZoneId zone = ZoneId.of(timeZone);
        return localTime.atZone(zone).toInstant();
    }
    
    public LocalDateTime utcToLocal(Instant utcTime, String timeZone) {
        ZoneId zone = ZoneId.of(timeZone);
        return utcTime.atZone(zone).toLocalDateTime();
    }
}
```

---

### Deep Dive 6: Scheduling Assistant — Finding Common Free Slots

**The Problem**: Given N attendees, a desired meeting duration, and a time range, find all time slots where everyone is available. Must respect working hours and return ranked suggestions.

```java
public class SchedulingAssistantService {

    /**
     * Algorithm: Interval sweep line
     * 
     * 1. Fetch free/busy for all N attendees in the time range
     * 2. Merge all busy blocks into a unified busy timeline
     * 3. Find gaps (free slots) of sufficient duration
     * 4. Filter by working hours
     * 5. Rank by preference (earlier in week, within core hours, fewer conflicts)
     * 
     * Time complexity: O(B log B) where B = total busy blocks across all users
     * Typical: 10 users × 20 events/week = 200 blocks → trivial
     */
    
    public List<TimeSlot> findCommonFreeSlots(
            List<String> attendeeIds, int durationMinutes,
            Instant rangeStart, Instant rangeEnd, boolean workingHoursOnly) {
        
        // Step 1: Fetch free/busy for all attendees
        Map<String, List<FreeBusyBlock>> allBusy = 
            freeBusyService.queryFreeBusy(attendeeIds, rangeStart, rangeEnd);
        
        // Step 2: Merge all busy blocks into sorted list of (start, end) intervals
        List<long[]> mergedBusy = new ArrayList<>();
        for (List<FreeBusyBlock> blocks : allBusy.values()) {
            for (FreeBusyBlock block : blocks) {
                mergedBusy.add(new long[]{ 
                    block.getStartTime().toEpochMilli(), 
                    block.getEndTime().toEpochMilli() 
                });
            }
        }
        mergedBusy.sort(Comparator.comparingLong(a -> a[0]));
        
        // Merge overlapping intervals
        List<long[]> merged = mergeIntervals(mergedBusy);
        
        // Step 3: Find gaps between busy blocks
        List<TimeSlot> freeSlots = new ArrayList<>();
        long current = rangeStart.toEpochMilli();
        long durationMs = durationMinutes * 60_000L;
        
        for (long[] busy : merged) {
            if (busy[0] - current >= durationMs) {
                // Gap found — split into duration-sized slots
                addSlots(freeSlots, current, busy[0], durationMs);
            }
            current = Math.max(current, busy[1]);
        }
        // Check remaining gap after last busy block
        if (rangeEnd.toEpochMilli() - current >= durationMs) {
            addSlots(freeSlots, current, rangeEnd.toEpochMilli(), durationMs);
        }
        
        // Step 4: Filter by working hours (if requested)
        if (workingHoursOnly) {
            freeSlots = filterByWorkingHours(freeSlots, attendeeIds);
        }
        
        // Step 5: Rank suggestions
        freeSlots.sort(Comparator.comparingDouble(this::scoreSlot).reversed());
        
        return freeSlots.stream().limit(10).toList();  // Top 10 suggestions
    }
    
    private double scoreSlot(TimeSlot slot) {
        // Prefer: earlier in the week, within 10am-4pm, not on Friday afternoon
        double score = 1.0;
        int hour = slot.getStart().atZone(ZoneOffset.UTC).getHour();
        if (hour >= 10 && hour <= 15) score += 0.5;  // Core hours bonus
        if (slot.getStart().atZone(ZoneOffset.UTC).getDayOfWeek() == DayOfWeek.FRIDAY 
            && hour >= 15) score -= 0.3;  // Friday afternoon penalty
        return score;
    }
}
```

---

### Deep Dive 7: Calendar Sharing & Permission Model

**The Problem**: Calendar sharing requires fine-grained permissions. User A shares their calendar with User B at "free/busy only" — B can see when A is busy but not what events they have. User C has "read details" — can see titles. User D has "read/write" — can create events on A's calendar.

```java
public class PermissionService {

    /**
     * Permission model (hierarchical):
     * 
     * OWNER > READ_WRITE > READ_DETAILS > FREE_BUSY_ONLY
     * 
     * Permission enforcement points:
     * 
     * 1. Event API (GET /calendars/{id}/events):
     *    - OWNER, READ_WRITE, READ_DETAILS: return full event details
     *    - FREE_BUSY_ONLY: return ONLY busy blocks (time ranges, no titles)
     *    - No permission: 403 Forbidden
     * 
     * 2. Event API (POST /calendars/{id}/events):
     *    - OWNER, READ_WRITE: allowed
     *    - READ_DETAILS, FREE_BUSY_ONLY: 403 Forbidden
     * 
     * 3. Free/Busy API (POST /freebusy):
     *    - ANY permission level (including FREE_BUSY_ONLY): return busy blocks
     *    - Organization-level default: everyone in org can see free/busy
     *    - No relationship: 403 (unless org-wide free/busy is enabled)
     * 
     * 4. Calendar share management:
     *    - Only OWNER can add/remove shares
     *    - OWNER can grant up to READ_WRITE (not OWNER)
     */
    
    public boolean canViewEventDetails(String requesterId, String calendarId) {
        SharePermission permission = getEffectivePermission(requesterId, calendarId);
        return permission == SharePermission.OWNER 
            || permission == SharePermission.READ_WRITE
            || permission == SharePermission.READ_DETAILS;
    }
    
    public boolean canViewFreeBusy(String requesterId, String calendarOwner) {
        // Check explicit calendar share
        for (String calId : calendarService.getCalendarIds(calendarOwner)) {
            SharePermission perm = getEffectivePermission(requesterId, calId);
            if (perm != null) return true;  // Any permission level grants free/busy
        }
        
        // Check org-wide free/busy policy
        return orgService.isSameOrg(requesterId, calendarOwner) 
            && orgService.isOrgFreeBusyEnabled(calendarOwner);
    }
    
    private SharePermission getEffectivePermission(String userId, String calendarId) {
        // Check: is user the calendar owner?
        Calendar cal = calendarService.getCalendar(calendarId);
        if (cal.getOwnerId().equals(userId)) return SharePermission.OWNER;
        
        // Check: explicit share
        CalendarShare share = shareRepo.findByCalendarAndUser(calendarId, userId);
        if (share != null) return share.getPermission();
        
        // Check: group-based share (user is in a group that has access)
        List<String> userGroups = groupService.getGroupsForUser(userId);
        for (String groupId : userGroups) {
            CalendarShare groupShare = shareRepo.findByCalendarAndGroup(calendarId, groupId);
            if (groupShare != null) return groupShare.getPermission();
        }
        
        return null;  // No permission
    }
}
```

---

### Deep Dive 8: Multi-Device Sync — Real-Time Calendar Updates

**The Problem**: A user creates an event on their phone. Within seconds, the event should appear on their laptop's calendar app, their tablet, and any shared calendar viewers.

```java
public class CalendarSyncService {

    /**
     * Sync architecture:
     * 
     * 1. Change Detection: Every event write publishes to Kafka "calendar-changes"
     *    Payload: { calendar_id, event_id, change_type, timestamp, affected_users }
     * 
     * 2. Change Distribution:
     *    For ONLINE users (WebSocket connected):
     *      → Push change notification immediately via WebSocket
     *      → Client fetches updated event data
     *    
     *    For OFFLINE users (mobile, desktop apps):
     *      → Store pending changes in sync queue
     *      → On next app open: client sends "give me changes since timestamp X"
     *      → Server returns delta of changes
     * 
     * 3. Delta Sync API:
     *    GET /api/v1/sync?since=2025-01-10T10:00:00Z&calendars=cal_abc,cal_def
     *    Response: list of created/updated/deleted events since that timestamp
     *    
     *    Client maintains a local sync token (last sync timestamp)
     *    On each sync: fetch deltas, apply locally, update sync token
     * 
     * 4. Conflict Resolution:
     *    Last-write-wins with version number
     *    If client sends an update with stale version → 409 Conflict
     *    Client re-fetches latest version, merges, retries
     */
    
    // Delta sync endpoint
    public SyncResponse getChangesSince(String userId, Instant since, 
                                         List<String> calendarIds) {
        List<EventChange> changes = new ArrayList<>();
        
        for (String calId : calendarIds) {
            // Fetch events modified after 'since' timestamp
            List<Event> modified = eventRepo.findModifiedAfter(calId, since);
            for (Event event : modified) {
                ChangeType type = event.getCreatedAt().isAfter(since) 
                    ? ChangeType.CREATED 
                    : ChangeType.UPDATED;
                changes.add(new EventChange(event, type));
            }
            
            // Fetch deleted event IDs
            List<String> deletedIds = eventRepo.findDeletedAfter(calId, since);
            for (String id : deletedIds) {
                changes.add(new EventChange(id, ChangeType.DELETED));
            }
        }
        
        return new SyncResponse(changes, Instant.now());  // New sync token = now
    }
}
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Event storage** | PostgreSQL sharded by calendar_id | Time-range queries with B-tree index; strong consistency for writes; sharding by calendar isolates users |
| **Recurring events** | Lazy expansion at read time (master + rule) | No storage for infinite future instances; expand in query range; handles DST via timezone-aware expansion |
| **Free/busy cache** | Pre-computed Redis (per-user, per-day) | 10-user × 5-day query = 50 Redis MGET < 5ms; updated async on event changes; trades freshness for speed |
| **Concurrency** | Optimistic locking (version) + advisory locks for resources | Version column prevents lost updates; advisory locks serialize resource booking without global lock |
| **Time zones** | Store UTC + original timezone; expand recurring in local time | Prevents DST drift for recurring events; "9am New York" stays 9am regardless of DST |
| **Permissions** | Hierarchical: owner > read-write > read-details > free/busy | Privacy: free/busy hides event details; org-wide free/busy default reduces configuration |
| **Multi-device sync** | Kafka + WebSocket (online) + delta sync API (offline) | Real-time for active users; catch-up sync for offline devices; last-write-wins conflict resolution |
| **Scheduling assistant** | Interval sweep line on merged busy blocks | O(B log B) for all busy blocks; filter by working hours; rank by preference (core hours, day of week) |

## Interview Talking Points

1. **"Recurring events: lazy expansion at read time with master + rule pattern"** — Store one master event + RFC 5545 recurrence rule. Expand instances in-memory within the query range. Handle exceptions via separate event rows linked to master by original_start. DST-safe: expand in creator's timezone, then convert to UTC.

2. **"Pre-computed free/busy index in Redis"** — Key = `freebusy:{user}:{date}`, value = JSON busy blocks. Updated async via Kafka on any event change. 10-user × 5-day scheduling query = 50 Redis MGETs in one pipeline < 5ms. Trades seconds of staleness for sub-200ms scheduling queries.

3. **"Optimistic locking + advisory locks for double-booking prevention"** — Event version column prevents lost updates (concurrent edits). PostgreSQL advisory locks serialize resource/room bookings for the same timeslot. User calendars allow overlapping events (warn, don't block).

4. **"Time zone handling: UTC storage + original timezone for recurring expansion"** — All times stored in UTC. Recurring events expanded in creator's local timezone (prevents DST drift). "9am New York" → 14:00 UTC in winter, 13:00 UTC in summer. All-day events are date-only, displayed in creator's timezone.

5. **"Calendar permission model: free/busy vs details vs edit"** — Four levels (free/busy only → read details → read/write → owner). Free/busy API strips event details — returns only time blocks. Org-wide free/busy default so colleagues can schedule without explicit sharing.

6. **"Scheduling assistant: sweep-line algorithm on merged busy intervals"** — Merge all attendees' busy blocks → find gaps ≥ desired duration → filter by working hours → rank by preference. O(B log B) where B = total busy blocks. Returns top 10 suggestions with conflict annotations.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Google Calendar / Outlook** | Identical problem | Our design covers core scheduling primitives |
| **Meeting Room Booking (Calendly)** | Same time-slot problem | Calendly is external scheduling with public availability; we're internal calendar |
| **Job Scheduler** | Same time-based scheduling | Job scheduler runs automated tasks; calendar schedules human meetings |
| **Notification System** | Same reminder pattern | Reminders are time-triggered notifications; shared delivery mechanism |
| **Event-Driven Architecture** | Same event bus pattern | Calendar changes propagate via Kafka like any event-driven system |
| **Collaborative Editing (Google Docs)** | Same real-time sync | Docs uses OT/CRDT for text; calendar uses last-write-wins for events |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **Event store** | PostgreSQL (sharded) | Spanner / CockroachDB | Spanner: global distribution, strong consistency without manual sharding |
| **Free/busy cache** | Redis | DynamoDB / Memcached | DynamoDB: serverless, auto-scaling; Memcached: simpler for flat key-value |
| **Recurrence expansion** | Lazy (at read time) | Materialized instances | Materialize if read latency is critical and storage is cheap |
| **Sync mechanism** | Kafka + WebSocket | Firebase / Pusher | Firebase: managed real-time sync, great for mobile |
| **Concurrency control** | Optimistic locking + advisory locks | Distributed locks (Redis/ZooKeeper) | Redis locks: for cross-shard resource booking |
| **Scheduling algorithm** | Sweep-line interval merge | Constraint solver (CP-SAT) | CP-SAT: for complex multi-room, multi-constraint scheduling |
| **Time zone library** | java.time / IANA tz database | Joda-Time / Moment.js | Joda-Time: legacy Java; Moment.js: frontend only |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Google Calendar API Design (RFC 5545 iCalendar)
- RFC 5545: Internet Calendaring and Scheduling (iCalendar)
- Recurring Event Expansion Algorithms
- Time Zone Best Practices (IANA tz database)
- Interval Scheduling / Sweep Line Algorithm
- Optimistic Locking & Advisory Locks in PostgreSQL
- CalDAV Protocol (RFC 4791)
- Microsoft Outlook Calendar Architecture
