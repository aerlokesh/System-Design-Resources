# Design a Scheduler/Calendar System (Outlook Calendar) — Hello Interview Framework

> **Question**: Architect a calendar and scheduling service like Outlook Calendar that handles recurring events, invites, time-zone differences, free/busy lookups, and real-time updates — serving hundreds of millions of users.
>
> **Asked at**: Microsoft, Google, Amazon
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
1. **Create/Edit/Delete Events**: Single and all-day events with title, time, location, description, attendees. Edit single occurrence or series.
2. **Recurring Events**: Daily, weekly, monthly, yearly patterns (RRULE). Exceptions (modify/delete single occurrence). "Every Tuesday and Thursday at 2 PM until Dec 2025."
3. **Invitations & RSVP**: Invite attendees to events. Accept/Tentative/Decline responses. Organizer sees attendance status.
4. **Free/Busy Lookup**: Query a user's availability across a time range. Find meeting times when all attendees are free (scheduling assistant).
5. **Time Zone Support**: Events stored in UTC. Display in user's local timezone. Handle DST transitions correctly.
6. **Reminders & Notifications**: Configurable reminders (15 min, 1 hour, 1 day before). Push notifications and email reminders.
7. **Calendar Views**: Day, week, month views. Efficiently fetch events for any view range.

#### Nice to Have (P1)
- Shared calendars (team calendars, room calendars)
- Room/resource booking
- Calendar delegation (assistant manages boss's calendar)
- Multiple calendars per user (personal, work, holidays)
- iCalendar import/export (.ics files)
- Meeting insights (suggested times based on habits)
- Focus time / out-of-office auto-blocking
- Integration with Teams (auto-create meeting link)

#### Below the Line (Out of Scope)
- Video conferencing (Teams meetings)
- Task management
- Email integration details

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **Users** | 400 million | Outlook.com + M365 calendar users |
| **DAU** | 150 million | Active calendar users |
| **Events created/day** | 500 million | Meetings, appointments |
| **Calendar view loads/day** | 5 billion | Users checking calendar frequently |
| **Free/busy queries/day** | 2 billion | Scheduling assistant usage |
| **Event display latency** | < 200ms for week view | Fast calendar render |
| **Free/busy latency** | < 500ms for 10 attendees | Scheduling assistant responsive |
| **Availability** | 99.99% | Calendar is business-critical |

### Capacity Estimation

```
Events:
  Total events stored: 50 billion (including recurring instances)
  Events created/day: 500M
  Average event metadata: 2 KB
  
Storage:
  Event data: 50B × 2 KB = 100 TB
  With recurrence expansion: stored as pattern + exceptions (compact)
  
API Traffic:
  Calendar view loads: 5B/day → 58K QPS avg, 200K peak
  Free/busy queries: 2B/day → 23K QPS avg, 100K peak
  Event CRUD: 500M/day → 6K QPS avg, 25K peak
  
Reminder Processing:
  Reminders/day: 2B (multiple reminders per event × attendees)
  Reminders/sec: ~23K
```

---

## 2️⃣ Core Entities

```
┌─────────────┐     ┌──────────────┐     ┌────────────────────┐
│  User        │────▶│  Calendar    │────▶│  Event              │
│              │     │               │     │                     │
│ userId       │     │ calendarId    │     │ eventId             │
│ email        │     │ userId        │     │ calendarId          │
│ timezone     │     │ name          │     │ subject             │
│ workingHours │     │ color         │     │ body                │
│              │     │ isDefault     │     │ start (UTC)         │
└─────────────┘     │ canShare      │     │ end (UTC)           │
                     │ permissions[] │     │ timeZone            │
                     └──────────────┘     │ isAllDay            │
                                           │ location            │
┌─────────────────┐                       │ organizer           │
│  Recurrence      │                       │ attendees[]         │
│  Pattern         │◀──────────────────── │ recurrence          │
│                  │                       │ isCancelled         │
│ type (daily/     │                       │ sensitivity         │
│  weekly/monthly) │                       │ showAs (free/busy/  │
│ interval         │                       │  tentative/oof)     │
│ daysOfWeek[]     │                       │ reminders[]         │
│ dayOfMonth       │                       │ seriesMasterId      │
│ startDate        │                       │ type (single/       │
│ endDate          │                       │  occurrence/        │
│ count            │                       │  exception/master)  │
│ exceptions[]     │                       │ onlineMeetingUrl    │
└─────────────────┘                       └────────────────────┘

┌─────────────┐     ┌──────────────┐
│  Attendee    │     │  Reminder    │
│              │     │               │
│ email        │     │ reminderId   │
│ name         │     │ eventId      │
│ type (req/   │     │ minutesBefore│
│  optional/   │     │ method (push/│
│  resource)   │     │  email/popup)│
│ status       │     │ fired        │
│ (accepted/   │     └──────────────┘
│  declined/   │
│  tentative/  │
│  none)       │
└─────────────┘
```

---

## 3️⃣ API Design

### Create Event
```
POST /api/v1/me/calendars/{calendarId}/events
Authorization: Bearer <token>

Request:
{
  "subject": "Sprint Planning",
  "body": { "contentType": "html", "content": "<p>Weekly sprint planning</p>" },
  "start": { "dateTime": "2025-01-15T14:00:00", "timeZone": "America/New_York" },
  "end": { "dateTime": "2025-01-15T15:00:00", "timeZone": "America/New_York" },
  "attendees": [
    { "emailAddress": { "address": "alice@contoso.com" }, "type": "required" },
    { "emailAddress": { "address": "room-a@contoso.com" }, "type": "resource" }
  ],
  "recurrence": {
    "pattern": { "type": "weekly", "interval": 1, "daysOfWeek": ["wednesday"] },
    "range": { "type": "endDate", "startDate": "2025-01-15", "endDate": "2025-12-31" }
  },
  "reminders": [{ "minutesBefore": 15 }],
  "isOnlineMeeting": true
}

Response: 201 Created
{
  "id": "evt_abc123",
  "iCalUId": "040000008200...",
  "type": "seriesMaster",
  "onlineMeeting": { "joinUrl": "https://teams.microsoft.com/l/meetup-join/..." },
  ...
}
```

### Get Calendar View (Expanded Occurrences)
```
GET /api/v1/me/calendarView?startDateTime=2025-01-13T00:00:00Z&endDateTime=2025-01-20T00:00:00Z
Authorization: Bearer <token>

Response: 200 OK
{
  "value": [
    { "id": "evt_abc123_20250115", "subject": "Sprint Planning", "start": {...}, "type": "occurrence" },
    { "id": "evt_def456", "subject": "Dentist", "start": {...}, "type": "singleInstance" },
    { "id": "evt_abc123_20250117_exception", "subject": "Sprint Planning (MOVED)", "type": "exception" }
  ]
}
```

### Find Meeting Times (Scheduling Assistant)
```
POST /api/v1/me/findMeetingTimes
Authorization: Bearer <token>

Request:
{
  "attendees": [
    { "emailAddress": { "address": "alice@contoso.com" }, "type": "required" },
    { "emailAddress": { "address": "bob@contoso.com" }, "type": "required" }
  ],
  "timeConstraint": {
    "timeslots": [
      { "start": { "dateTime": "2025-01-15T09:00:00", "timeZone": "EST" },
        "end": { "dateTime": "2025-01-17T17:00:00", "timeZone": "EST" } }
    ]
  },
  "meetingDuration": "PT1H",
  "maxCandidates": 5
}

Response: 200 OK
{
  "meetingTimeSuggestions": [
    {
      "meetingTimeSlot": { "start": {...}, "end": {...} },
      "confidence": 100.0,
      "attendeeAvailability": [
        { "attendee": "alice@contoso.com", "availability": "free" },
        { "attendee": "bob@contoso.com", "availability": "free" }
      ]
    }
  ]
}
```

---

## 4️⃣ Data Flow

### Creating a Recurring Event with Attendees

```
Organizer creates event → Calendar API
        │
        ▼
   Calendar Service:
     1. Validate event data (times, attendees, recurrence pattern)
     2. Store series master event (just the pattern, NOT individual occurrences)
        { eventId, recurrence: { weekly, Wed, until Dec 2025 }, attendees: [...] }
     3. Generate iCalendar UID for interop
     4. If isOnlineMeeting → call Teams Meeting Service → get joinUrl
     5. For each attendee:
        ├── Create invite in attendee's calendar (status: "none")
        ├── Send invite email (iCalendar .ics attachment)
        └── Publish event to attendee's notification feed
     6. Schedule reminders for organizer (15 min before first occurrence)
     7. Return created event to organizer
```

### Calendar View Query (Expanding Recurrences)

```
User opens week view (Jan 13-19) → Calendar Client
        │
        ▼
   GET /calendarView?start=Jan13&end=Jan19
        │
        ▼
   Calendar Service:
     1. Query single events in [Jan 13, Jan 19]:
        SELECT * FROM events WHERE calendarId = :cal 
          AND type = 'singleInstance' AND start < Jan19 AND end > Jan13
     
     2. Query series masters active in this range:
        SELECT * FROM events WHERE calendarId = :cal
          AND type = 'seriesMaster' AND recurrence.range overlaps [Jan13, Jan19]
     
     3. For each series master → expand recurrence pattern:
        "Weekly on Wednesday" → generates occurrence on Jan 15
        Check exceptions: is Jan 15 modified or cancelled?
          Modified → return exception event
          Cancelled → skip
          Normal → return virtual occurrence
     
     4. Merge single events + expanded occurrences → sort by start time
     5. Convert times to user's timezone
     6. Return combined list
```

---

## 5️⃣ High-Level Design

```
┌────────────────────────────────────────────────────────────────────┐
│                         CLIENTS                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ Outlook  │ │ Outlook  │ │ Outlook  │ │ 3rd Party│             │
│  │  Web     │ │ Desktop  │ │ Mobile   │ │ (CalDAV) │             │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘             │
│       └─────────────┴────────────┴─────────────┘                   │
└───────────────────────────┬────────────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────────┐
│  API Gateway (Azure Front Door + Auth)                             │
└───────────────────────┬───────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬───────────────┐
        ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Calendar     │ │ Scheduling   │ │ Reminder     │ │ Sync Service │
│ Service      │ │ Service      │ │ Service      │ │              │
│              │ │              │ │              │ │ CalDAV /     │
│ Event CRUD   │ │ Free/busy    │ │ Timer-based  │ │ EWS / Graph  │
│ View query   │ │ Find meeting │ │ trigger      │ │ ActiveSync   │
│ Recurrence   │ │ times        │ │ Push/email   │ │              │
│ expansion    │ │ Room booking │ │ reminders    │ │              │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │                │
       ▼                ▼                ▼                ▼
┌───────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐    │
│  │ Calendar DB  │  │ Free/Busy    │  │ Redis Cache          │    │
│  │ (Cosmos DB)  │  │ Cache        │  │                      │    │
│  │              │  │ (Redis)      │  │ Hot calendar views   │    │
│  │ Events       │  │              │  │ User timezone        │    │
│  │ (partition:  │  │ Per-user     │  │ Frequently accessed  │    │
│  │  calendarId) │  │ availability │  │ events               │    │
│  │ Series       │  │ bitmap       │  │                      │    │
│  │ masters      │  │              │  │                      │    │
│  └──────────────┘  └──────────────┘  └──────────────────────┘    │
└───────────────────────────────────────────────────────────────────┘
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Recurring Events — Storage vs Expansion

**Problem**: A daily standup recurring for 2 years = 520 events. If stored individually, storage explodes. If stored as pattern only, queries become complex. What's the right approach?

**Solution: Series Master + Virtual Expansion + Exception Storage**

```
Storage Model:

1. Series Master (stored):
   {
     "eventId": "evt_master_001",
     "type": "seriesMaster",
     "subject": "Daily Standup",
     "start": "2025-01-06T09:00:00Z",
     "end": "2025-01-06T09:15:00Z",
     "recurrence": {
       "pattern": { "type": "daily", "interval": 1, "daysOfWeek": ["mon","tue","wed","thu","fri"] },
       "range": { "type": "endDate", "endDate": "2026-12-31" }
     }
   }
   → 1 record instead of 520!

2. Exceptions (stored — only when an occurrence is modified/deleted):
   {
     "eventId": "evt_exc_042",
     "type": "exception",
     "seriesMasterId": "evt_master_001",
     "originalStartTime": "2025-03-15T09:00:00Z",
     "start": "2025-03-15T10:00:00Z",    // moved to 10 AM
     "end": "2025-03-15T10:15:00Z",
     "subject": "Daily Standup (Delayed)"
   }

3. Cancellations (stored):
   {
     "eventId": "evt_cancel_043",
     "type": "exception",
     "seriesMasterId": "evt_master_001",
     "originalStartTime": "2025-07-04T09:00:00Z",
     "isCancelled": true                   // July 4th — holiday
   }

4. Virtual Occurrences (computed at query time, NOT stored):
   When user requests Jan 13-19 view:
     Expand recurrence pattern → generates [Jan 13, 14, 15, 16, 17]
     Check exceptions → Jan 15 has exception (moved to 10 AM)
     Return: 4 normal occurrences + 1 exception

RRULE Expansion Algorithm (RFC 5545):
  expandRecurrence(master, rangeStart, rangeEnd):
    occurrences = []
    date = max(master.recurrence.range.startDate, rangeStart)
    while date <= min(master.recurrence.range.endDate, rangeEnd):
      if matchesPattern(date, master.recurrence.pattern):
        if not isCancelled(master.eventId, date):
          exception = getException(master.eventId, date)
          if exception: occurrences.append(exception)
          else: occurrences.append(virtualOccurrence(master, date))
      date = nextDate(date, pattern)
    return occurrences
```

### Deep Dive 2: Free/Busy Lookup at Scale

**Problem**: Scheduling assistant needs to check availability of 10 people over a week. Each person has hundreds of events. How to make this fast (<500ms)?

**Solution: Pre-Computed Free/Busy Bitmap Cache**

```
Free/Busy Representation:

For each user, maintain a bitmap of availability:
  Granularity: 15-minute slots
  One day = 96 slots (24h × 4 per hour)
  One week = 672 slots
  
  Each slot: 2 bits → 00=free, 01=tentative, 10=busy, 11=OOF
  
  One day per user: 96 × 2 bits = 192 bits = 24 bytes
  One week: 168 bytes
  One month: 720 bytes

Redis Storage:
  Key: freeBusy:{userId}:{date}
  Value: 24-byte bitmap
  
  Example: 2025-01-15
    Bits: 000000000000000000101010101010101010000000...
    (free until 9 AM, busy 9-12, free 12-1, busy 1-5, free after 5)

Free/Busy Update:
  On event create/update/delete:
    1. Determine affected time slots
    2. Update bitmap in Redis: SETBIT / BITFIELD operations
    3. Atomic: use Redis transaction for multi-slot updates
  
  On recurring event: update bitmap for visible range (next 90 days)
  Beyond 90 days: compute on-demand from series master

Scheduling Assistant Query:
  POST /findMeetingTimes
  { attendees: [alice, bob, carol], duration: 1h, range: Jan 15-17 }
  
  1. Fetch bitmaps for all 3 attendees for Jan 15-17:
     GET freeBusy:alice:20250115, freeBusy:alice:20250116, ...
     GET freeBusy:bob:20250115, ...
     GET freeBusy:carol:20250115, ...
  
  2. Bitwise AND all bitmaps:
     combined = alice & bob & carol
     Result: slots where ALL are free
  
  3. Find contiguous runs of free slots ≥ 4 (1 hour = 4 × 15min):
     Scan combined bitmap → find runs of 0000 (all free)
  
  4. Rank by preference:
     - Prefer morning slots
     - Prefer slots during working hours
     - Avoid right after lunch
  
  5. Return top 5 suggestions
  
  Total time: ~5ms (bitmap operations are O(1) per user per day)
  For 10 attendees over 5 days: ~50 Redis GETs + bitmap math = < 50ms
```

### Deep Dive 3: Time Zone Handling

**Problem**: Alice in New York creates "Sprint Planning at 2 PM" and invites Bob in London and Carol in Tokyo. When should each person see the event? What happens during DST transitions?

**Solution: Store in UTC + Original TimeZone, Display in User's Local Time**

```
Time Zone Strategy:

1. Storage: always UTC + original timezone
   {
     "start": {
       "dateTimeUtc": "2025-01-15T19:00:00Z",      // UTC
       "originalTimeZone": "America/New_York",        // IANA timezone
       "originalDateTime": "2025-01-15T14:00:00"      // for display
     }
   }
   
   UTC conversion at creation time:
     2025-01-15 14:00 EST (UTC-5) → 2025-01-15 19:00 UTC

2. Display: convert UTC to viewer's timezone
   Alice (New York, EST): 2025-01-15 14:00 EST ✓
   Bob (London, GMT): 2025-01-15 19:00 GMT
   Carol (Tokyo, JST): 2025-01-16 04:00 JST (+9)

3. DST Handling for Recurring Events:
   "Sprint Planning every Wednesday at 2 PM Eastern"
   
   Before DST: Jan 15 → 2 PM EST = 19:00 UTC
   After DST (Mar 12): Mar 19 → 2 PM EDT = 18:00 UTC
   
   Strategy: anchor to the WALL CLOCK TIME in the original timezone
   → Always "2 PM New York time" regardless of UTC offset
   → Expand recurrence using the original timezone's rules
   
   Implementation:
     Store: { pattern: weekly Wed, anchorTime: 14:00, timezone: "America/New_York" }
     Expand: for each occurrence date:
       occurrence_utc = convertToUTC("2025-03-19 14:00", "America/New_York")
       → Uses IANA tz database to determine correct UTC offset for that date

4. All-Day Events:
   "Jan 15 — Alice's Birthday"
   All-day events have NO specific time → stored as date only
   Display in viewer's local date → always shows on Jan 15 regardless of timezone
   
   Edge case: user in UTC+14 (Kiribati) vs UTC-12 (Baker Island)
   → Both see "Jan 15" because all-day events are date-anchored, not time-anchored
```
