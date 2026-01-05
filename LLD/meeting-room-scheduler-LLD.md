# Meeting Room Scheduler - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Booking State Machine](#booking-state-machine)
5. [Class Design](#class-design)
6. [Conflict Detection & Resolution](#conflict-detection--resolution)
7. [Complete Implementation](#complete-implementation)
8. [Extensibility](#extensibility)
9. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Meeting Room Scheduler?**

A meeting room scheduler is a system that manages the booking of meeting rooms in an office or organization. Users can search for available rooms, book them for specific time slots, view their bookings, and cancel if needed. The system prevents double-booking and handles conflicts intelligently.

**Real-World Examples:**
- Microsoft Outlook Calendar (Room Booking)
- Google Calendar (Room Resources)
- Office 365 Room Finder
- Workplace by Facebook
- WeWork Room Booking

**Core Workflow:**
```
User Searches Rooms → Filters by Capacity/Amenities → Checks Availability
    ↓
Books Room → System Validates No Conflict → Booking Confirmed
    ↓
Meeting Time → Room Used → Booking Completed
    ↓
Can Cancel/Modify → System Updates → Room Available Again
```

**Key Challenges:**
- **Double-Booking Prevention:** Concurrent bookings for same slot
- **Conflict Detection:** Overlapping time slots
- **Search Efficiency:** Find rooms by capacity, location, amenities
- **Recurring Meetings:** Handle daily/weekly/monthly recurrence
- **Cancellation:** Handle cancellation and waitlists
- **Overbooking:** Handle no-shows and last-minute cancellations
- **Resource Constraints:** Room capacity, equipment availability

---

## Requirements

### Functional Requirements

1. **Room Management**
   - Add/remove/update meeting rooms
   - Room properties (capacity, location, floor, building)
   - Amenities (projector, whiteboard, video conferencing)
   - Mark rooms as unavailable (maintenance)

2. **Booking Operations**
   - Search available rooms by time slot
   - Filter by capacity, location, amenities
   - Book room for specific time slot
   - View all bookings for a room
   - View user's bookings

3. **Conflict Prevention**
   - Check for overlapping bookings
   - Atomic booking operations
   - Prevent double-booking
   - Handle concurrent booking attempts

4. **Recurring Meetings**
   - Book recurring slots (daily, weekly, monthly)
   - Modify/cancel recurring series
   - Handle exceptions in series

5. **Booking Management**
   - Modify booking (time, room)
   - Cancel booking
   - Auto-cancel for no-shows
   - Waitlist for popular rooms

6. **Notifications**
   - Booking confirmation
   - Reminder before meeting
   - Cancellation notification
   - Room availability alerts

### Non-Functional Requirements

1. **Performance**
   - Search rooms: < 500ms
   - Book room: < 1 second
   - Support 1000+ concurrent bookings

2. **Consistency**
   - No double-bookings
   - Atomic booking operations
   - Strong consistency for availability

3. **Scalability**
   - Thousands of rooms
   - Millions of bookings
   - Hundreds of thousands of users

4. **Availability**
   - 99.9% uptime
   - Handle peak hours (morning booking rush)

### Out of Scope

- Calendar integration (Outlook, Google Calendar)
- Video conferencing integration
- Catering and equipment booking
- Payment/billing for external bookings
- Mobile app implementation
- Analytics and reporting

---

## Core Entities and Relationships

### Key Entities

1. **MeetingRoom**
   - Room ID, name, capacity
   - Location (building, floor, room number)
   - Amenities (projector, whiteboard, etc.)
   - Status (Available, Unavailable, Maintenance)

2. **Booking**
   - Booking ID
   - Room, User (organizer)
   - Start time, end time
   - Title, description
   - Attendees
   - Status (Confirmed, Cancelled, Completed)
   - Recurrence pattern (if recurring)

3. **User**
   - User ID, name, email
   - Department
   - Bookings history

4. **TimeSlot**
   - Start time, end time
   - Helper for time calculations

5. **RecurrencePattern**
   - Frequency (Daily, Weekly, Monthly)
   - End condition (count or end date)
   - Days of week (for weekly)

### Entity Relationships

```
┌─────────────┐      books        ┌──────────────┐
│    User     │──────────────────►│   Booking    │
└─────────────┘                   └──────┬───────┘
                                         │
                                         │ for
                                         ▼
                                  ┌──────────────┐
                                  │ MeetingRoom  │
                                  └──────────────┘

┌──────────────┐     has          ┌──────────────┐
│   Booking    │◄─────────────────│  Recurrence  │
└──────────────┘                  │   Pattern    │
                                  └──────────────┘

┌──────────────┐    overlaps?     ┌──────────────┐
│   TimeSlot   │◄────────────────►│   TimeSlot   │
└──────────────┘                  └──────────────┘
```

---

## Booking State Machine

### Booking States

```
                  create booking
┌──────────┐   ──────────────────►  ┌────────────┐
│   NONE   │                        │  PENDING   │
└──────────┘                        └──────┬─────┘
                                           │
                                    no conflict
                                           │
                                           ▼
                                    ┌────────────┐
                             ┌──────│ CONFIRMED  │
                             │      └──────┬─────┘
                             │             │
                       cancelled           │ meeting time
                             │             │
                             │             ▼
                             │      ┌────────────┐
                             └─────►│ COMPLETED  │
                                    └────────────┘
                                           │
                                           │ no-show
                                           ▼
                                    ┌────────────┐
                                    │ CANCELLED  │
                                    └────────────┘
```

---

## Class Design

### 1. MeetingRoom

```java
/**
 * Meeting room with capacity and amenities
 */
public class MeetingRoom {
    private final String id;
    private final String name;
    private final int capacity;
    private final String building;
    private final int floor;
    private final Set<String> amenities;
    private RoomStatus status;
    
    public enum RoomStatus {
        AVAILABLE, UNAVAILABLE, MAINTENANCE
    }
    
    public MeetingRoom(String id, String name, int capacity, 
                       String building, int floor) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.building = building;
        this.floor = floor;
        this.amenities = new HashSet<>();
        this.status = RoomStatus.AVAILABLE;
    }
    
    public void addAmenity(String amenity) {
        amenities.add(amenity);
    }
    
    public boolean hasAmenity(String amenity) {
        return amenities.contains(amenity);
    }
    
    // Getters and setters
}
```

### 2. TimeSlot

```java
/**
 * Time slot for booking
 */
public class TimeSlot {
    private final long startTime;
    private final long endTime;
    
    public TimeSlot(long startTime, long endTime) {
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Start must be before end");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    /**
     * Check if this slot overlaps with another
     */
    public boolean overlaps(TimeSlot other) {
        return this.startTime < other.endTime && other.startTime < this.endTime;
    }
    
    /**
     * Get duration in minutes
     */
    public long getDurationMinutes() {
        return (endTime - startTime) / (60 * 1000);
    }
    
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    
    @Override
    public String toString() {
        return String.format("[%d - %d]", startTime, endTime);
    }
}
```

### 3. Booking

```java
/**
 * Meeting room booking
 */
public class Booking {
    private final String id;
    private final String roomId;
    private final String organizerId;
    private final TimeSlot timeSlot;
    private String title;
    private String description;
    private final List<String> attendees;
    private BookingStatus status;
    private final RecurrencePattern recurrence;
    private final long createdAt;
    
    public enum BookingStatus {
        PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
    }
    
    public Booking(String id, String roomId, String organizerId, 
                   TimeSlot timeSlot, String title) {
        this(id, roomId, organizerId, timeSlot, title, null);
    }
    
    public Booking(String id, String roomId, String organizerId,
                   TimeSlot timeSlot, String title, RecurrencePattern recurrence) {
        this.id = id;
        this.roomId = roomId;
        this.organizerId = organizerId;
        this.timeSlot = timeSlot;
        this.title = title;
        this.attendees = new ArrayList<>();
        this.status = BookingStatus.PENDING;
        this.recurrence = recurrence;
        this.createdAt = System.currentTimeMillis();
    }
    
    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }
    
    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
    
    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }
    
    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.PENDING;
    }
    
    // Getters
}
```

### 4. RecurrencePattern

```java
/**
 * Recurrence pattern for recurring meetings
 */
public class RecurrencePattern {
    private final RecurrenceType type;
    private final int interval; // Every N days/weeks/months
    private final Set<DayOfWeek> daysOfWeek; // For weekly recurrence
    private final RecurrenceEnd end;
    
    public enum RecurrenceType {
        DAILY, WEEKLY, MONTHLY
    }
    
    public static class RecurrenceEnd {
        private final Integer occurrences; // End after N occurrences
        private final Long endDate; // End by specific date
        
        public RecurrenceEnd(Integer occurrences) {
            this.occurrences = occurrences;
            this.endDate = null;
        }
        
        public RecurrenceEnd(long endDate) {
            this.occurrences = null;
            this.endDate = endDate;
        }
    }
    
    public RecurrencePattern(RecurrenceType type, int interval, RecurrenceEnd end) {
        this.type = type;
        this.interval = interval;
        this.daysOfWeek = new HashSet<>();
        this.end = end;
    }
    
    /**
     * Generate next occurrence time slots
     */
    public List<TimeSlot> generateOccurrences(TimeSlot firstSlot, int maxOccurrences) {
        List<TimeSlot> slots = new ArrayList<>();
        slots.add(firstSlot);
        
        long currentStart = firstSlot.getStartTime();
        long duration = firstSlot.getEndTime() - firstSlot.getStartTime();
        
        for (int i = 1; i < maxOccurrences; i++) {
            long nextStart = calculateNextOccurrence(currentStart);
            if (shouldEnd(nextStart, i)) {
                break;
            }
            
            slots.add(new TimeSlot(nextStart, nextStart + duration));
            currentStart = nextStart;
        }
        
        return slots;
    }
    
    private long calculateNextOccurrence(long current) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(current);
        
        switch (type) {
            case DAILY:
                cal.add(Calendar.DAY_OF_MONTH, interval);
                break;
            case WEEKLY:
                cal.add(Calendar.WEEK_OF_YEAR, interval);
                break;
            case MONTHLY:
                cal.add(Calendar.MONTH, interval);
                break;
        }
        
        return cal.getTimeInMillis();
    }
    
    private boolean shouldEnd(long time, int occurrenceCount) {
        if (end.occurrences != null && occurrenceCount >= end.occurrences) {
            return true;
        }
        if (end.endDate != null && time >= end.endDate) {
            return true;
        }
        return false;
    }
}
```

---

## Conflict Detection & Resolution

### Overlap Detection

```java
public class ConflictDetector {
    /**
     * Check if two time slots overlap
     */
    public boolean hasConflict(TimeSlot slot1, TimeSlot slot2) {
        return slot1.overlaps(slot2);
    }
    
    /**
     * Find all conflicting bookings for a room
     */
    public List<Booking> findConflicts(String roomId, TimeSlot proposedSlot,
                                       List<Booking> existingBookings) {
        return existingBookings.stream()
            .filter(b -> b.getRoomId().equals(roomId))
            .filter(Booking::isActive)
            .filter(b -> b.getTimeSlot().overlaps(proposedSlot))
            .collect(Collectors.toList());
    }
}
```

### Atomic Booking

```java
public class AtomicBookingManager {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Atomically check and book room
     * Prevents race conditions
     */
    public boolean tryBook(MeetingRoom room, TimeSlot slot, 
                          List<Booking> existingBookings) {
        lock.writeLock().lock();
        try {
            // Check for conflicts
            boolean hasConflict = existingBookings.stream()
                .filter(Booking::isActive)
                .anyMatch(b -> b.getTimeSlot().overlaps(slot));
            
            if (hasConflict) {
                return false;
            }
            
            // Book the room
            // (actual booking creation happens here)
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

---

## Complete Implementation

### MeetingRoomScheduler - Main Service

```java
/**
 * Main meeting room scheduler service
 */
public class MeetingRoomScheduler {
    private final Map<String, MeetingRoom> rooms;
    private final Map<String, Booking> bookings;
    private final Map<String, User> users;
    private final AtomicInteger bookingIdCounter;
    private final ReadWriteLock bookingLock;
    
    public MeetingRoomScheduler() {
        this.rooms = new ConcurrentHashMap<>();
        this.bookings = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.bookingIdCounter = new AtomicInteger(1);
        this.bookingLock = new ReentrantReadWriteLock();
    }
    
    // Room Management
    public void addRoom(MeetingRoom room) {
        rooms.put(room.getId(), room);
    }
    
    public void registerUser(User user) {
        users.put(user.getId(), user);
    }
    
    // Search Available Rooms
    public List<MeetingRoom> searchAvailableRooms(TimeSlot timeSlot, 
                                                   int minCapacity,
                                                   Set<String> requiredAmenities) {
        return rooms.values().stream()
            .filter(r -> r.getStatus() == MeetingRoom.RoomStatus.AVAILABLE)
            .filter(r -> r.getCapacity() >= minCapacity)
            .filter(r -> r.getAmenities().containsAll(requiredAmenities))
            .filter(r -> isRoomAvailable(r.getId(), timeSlot))
            .collect(Collectors.toList());
    }
    
    private boolean isRoomAvailable(String roomId, TimeSlot timeSlot) {
        return bookings.values().stream()
            .filter(b -> b.getRoomId().equals(roomId))
            .filter(Booking::isActive)
            .noneMatch(b -> b.getTimeSlot().overlaps(timeSlot));
    }
    
    // Create Booking
    public Booking createBooking(String userId, String roomId, 
                                 TimeSlot timeSlot, String title) {
        bookingLock.writeLock().lock();
        try {
            // Check room exists and is available
            MeetingRoom room = rooms.get(roomId);
            if (room == null || room.getStatus() != MeetingRoom.RoomStatus.AVAILABLE) {
                throw new IllegalArgumentException("Room not available");
            }
            
            // Check for conflicts
            if (!isRoomAvailable(roomId, timeSlot)) {
                throw new IllegalStateException("Time slot already booked");
            }
            
            // Create booking
            String bookingId = "BOOK-" + bookingIdCounter.getAndIncrement();
            Booking booking = new Booking(bookingId, roomId, userId, timeSlot, title);
            booking.confirm();
            
            bookings.put(bookingId, booking);
            
            User user = users.get(userId);
            if (user != null) {
                user.addBooking(booking);
            }
            
            return booking;
        } finally {
            bookingLock.writeLock().unlock();
        }
    }
    
    // Create Recurring Booking
    public List<Booking> createRecurringBooking(String userId, String roomId,
                                                TimeSlot firstSlot, String title,
                                                RecurrencePattern pattern) {
        List<Booking> createdBookings = new ArrayList<>();
        List<TimeSlot> occurrences = pattern.generateOccurrences(firstSlot, 52); // Max 52
        
        for (TimeSlot slot : occurrences) {
            try {
                Booking booking = createBooking(userId, roomId, slot, title);
                createdBookings.add(booking);
            } catch (Exception e) {
                // Skip conflicting slots
                System.out.println("Skipped slot due to conflict: " + slot);
            }
        }
        
        return createdBookings;
    }
    
    // Cancel Booking
    public boolean cancelBooking(String bookingId, String userId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null) {
            return false;
        }
        
        // Only organizer can cancel
        if (!booking.getOrganizerId().equals(userId)) {
            return false;
        }
        
        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            return false;
        }
        
        booking.cancel();
        return true;
    }
    
    // Modify Booking
    public boolean modifyBooking(String bookingId, String userId, TimeSlot newSlot) {
        bookingLock.writeLock().lock();
        try {
            Booking booking = bookings.get(bookingId);
            if (booking == null || !booking.getOrganizerId().equals(userId)) {
                return false;
            }
            
            // Check new slot is available
            bookings.values().stream()
                .filter(b -> !b.getId().equals(bookingId)) // Exclude current booking
                .filter(b -> b.getRoomId().equals(booking.getRoomId()))
                .filter(Booking::isActive)
                .forEach(b -> {
                    if (b.getTimeSlot().overlaps(newSlot)) {
                        throw new IllegalStateException("New slot conflicts");
                    }
                });
            
            // Modify booking
            booking.updateTimeSlot(newSlot);
            return true;
        } finally {
            bookingLock.writeLock().unlock();
        }
    }
    
    // View Bookings
    public List<Booking> getUserBookings(String userId) {
        return bookings.values().stream()
            .filter(b -> b.getOrganizerId().equals(userId))
            .filter(Booking::isActive)
            .sorted(Comparator.comparingLong(b -> b.getTimeSlot().getStartTime()))
            .collect(Collectors.toList());
    }
    
    public List<Booking> getRoomBookings(String roomId, long fromTime, long toTime) {
        TimeSlot querySlot = new TimeSlot(fromTime, toTime);
        
        return bookings.values().stream()
            .filter(b -> b.getRoomId().equals(roomId))
            .filter(Booking::isActive)
            .filter(b -> b.getTimeSlot().overlaps(querySlot))
            .sorted(Comparator.comparingLong(b -> b.getTimeSlot().getStartTime()))
            .collect(Collectors.toList());
    }
    
    public MeetingRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    public Booking getBooking(String bookingId) {
        return bookings.get(bookingId);
    }
}
```

---

## Extensibility

### 1. Waitlist Management

```java
public class WaitlistManager {
    private final Map<String, Queue<WaitlistEntry>> roomWaitlists;
    
    static class WaitlistEntry {
        String userId;
        TimeSlot desiredSlot;
        int requiredCapacity;
        long timestamp;
    }
    
    public void addToWaitlist(String roomId, String userId, TimeSlot slot) {
        roomWaitlists.computeIfAbsent(roomId, k -> new PriorityQueue<>())
                     .offer(new WaitlistEntry(userId, slot, timestamp));
    }
    
    public void notifyWaitlist(String roomId, TimeSlot availableSlot) {
        Queue<WaitlistEntry> waitlist = roomWaitlists.get(roomId);
        if (waitlist == null) return;
        
        // Notify users on waitlist that room is available
        for (WaitlistEntry entry : waitlist) {
            if (entry.desiredSlot.overlaps(availableSlot)) {
                notifyUser(entry.userId, roomId, availableSlot);
            }
        }
    }
}
```

### 2. Room Recommendations

```java
public class RoomRecommendation {
    public List<MeetingRoom> recommendRooms(int attendeeCount, String preferredBuilding,
                                           Set<String> requiredAmenities) {
        return rooms.stream()
            .filter(r -> r.getCapacity() >= attendeeCount)
            .filter(r -> r.getCapacity() <= attendeeCount * 1.5) // Not too big
            .filter(r -> r.getAmenities().containsAll(requiredAmenities))
            .sorted(Comparator
                .comparing((MeetingRoom r) -> r.getBuilding().equals(preferredBuilding))
                .reversed()
                .thenComparingInt(MeetingRoom::getCapacity))
            .collect(Collectors.toList());
    }
}
```

### 3. No-Show Tracking

```java
public class NoShowTracker {
    private final Map<String, Integer> userNoShowCounts;
    private static final int MAX_NO_SHOWS = 3;
    
    public void recordNoShow(String userId) {
        int count = userNoShowCounts.merge(userId, 1, Integer::sum);
        
        if (count >= MAX_NO_SHOWS) {
            // Restrict user's booking privileges
            restrictUser(userId);
        }
    }
    
    public void recordAttendance(String userId) {
        // Reset counter on successful attendance
        userNoShowCounts.put(userId, 0);
    }
}
```

### 4. Smart Scheduling Assistant

```java
public class SchedulingAssistant {
    /**
     * Find best available time slot for all attendees
     */
    public TimeSlot findBestSlot(List<String> attendeeIds, 
                                 long durationMinutes,
                                 long searchStart,
                                 long searchEnd) {
        // Get all attendees' bookings
        Map<String, List<Booking>> attendeeBookings = new HashMap<>();
        for (String attendeeId : attendeeIds) {
            attendeeBookings.put(attendeeId, getUserBookings(attendeeId));
        }
        
        // Find common free slots
        List<TimeSlot> freeSlots = findCommonFreeSlots(
            attendeeBookings, durationMinutes, searchStart, searchEnd);
        
        // Return first available slot
        return freeSlots.isEmpty() ? null : freeSlots.get(0);
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- Single or recurring meetings?
- Conflict handling (reject or waitlist)?
- Maximum booking duration?
- Cancellation policy?
- Room capacity requirements?

**2. Identify Core Entities (5 minutes)**
- MeetingRoom (properties, amenities)
- Booking (time slot, room, user)
- TimeSlot (overlap detection)
- RecurrencePattern (optional)

**3. Design Conflict Prevention (10 minutes)**
- TimeSlot overlap logic
- Atomic booking with locks
- Show concurrent booking scenario

**4. Implement Core Logic (20 minutes)**
- Room search with filters
- Booking creation with conflict check
- Cancellation logic
- View bookings

**5. Discuss Extensions (5 minutes)**
- Recurring meetings
- Waitlist
- Recommendations
- Analytics

### Common Interview Questions

**Q1: "How do you prevent double-booking?"**

**A:** Use locks and atomic operations:
```java
public Booking createBooking(...) {
    lock.writeLock().lock();
    try {
        // 1. Check for conflicts
        if (hasConflict(roomId, timeSlot)) {
            throw new Exception("Conflict");
        }
        
        // 2. Create booking
        Booking booking = new Booking(...);
        bookings.put(booking.getId(), booking);
        
        return booking;
    } finally {
        lock.writeLock().unlock();
    }
}
```

**Q2: "How to check if two time slots overlap?"**

**A:** Simple interval overlap logic:
```java
boolean overlaps(TimeSlot a, TimeSlot b) {
    return a.start < b.end && b.start < a.end;
}

// Examples:
// [10-12] and [11-13] → overlaps (11 < 12 && 10 < 13)
// [10-12] and [12-14] → no overlap (10 < 14 but 12 >= 12)
```

**Q3: "How to handle recurring meetings?"**

**A:** Generate series of time slots:
```java
// Weekly meeting for 10 weeks
RecurrencePattern pattern = new RecurrencePattern(
    RecurrenceType.WEEKLY,
    1, // Every week
    new RecurrenceEnd(10) // 10 occurrences
);

List<TimeSlot> slots = pattern.generateOccurrences(firstSlot, 10);

// Book each slot
for (TimeSlot slot : slots) {
    try {
        createBooking(userId, roomId, slot, title);
    } catch (ConflictException e) {
        // Skip conflicting slots or notify user
    }
}
```

**Q4: "What if room is booked but user doesn't show up?"**

**A:** Track no-shows and implement policies:
```java
// After meeting end time + grace period
public void checkAndMarkNoShow(Booking booking) {
    long now = System.currentTimeMillis();
    long gracePeriod = 15 * 60 * 1000; // 15 minutes
    
    if (now > booking.getTimeSlot().getEndTime() + gracePeriod) {
        if (!wasRoomUsed(booking)) {
            booking.setStatus(BookingStatus.NO_SHOW);
            noShowTracker.recordNoShow(booking.getOrganizerId());
        }
    }
}
```

**Q5: "How to handle time zone differences?"**

**A:** Store all times in UTC, convert for display:
```java
class TimeSlot {
    private final long startTimeUTC; // Always UTC
    private final long endTimeUTC;
    
    public long getStartTime(TimeZone tz) {
        return convertFromUTC(startTimeUTC, tz);
    }
}

// Booking creation
public Booking book(String userId, String roomId, 
                   long startLocal, long endLocal, TimeZone userTz) {
    long startUTC = convertToUTC(startLocal, userTz);
    long endUTC = convertToUTC(endLocal, userTz);
    
    TimeSlot slot = new TimeSlot(startUTC, endUTC);
    return createBooking(userId, roomId, slot, title);
}
```

### Design Patterns Used

1. **Strategy Pattern:** Different search/filter strategies
2. **State Pattern:** Booking status management
3. **Observer Pattern:** Notifications for booking changes
4. **Factory Pattern:** Creating bookings and time slots
5. **Singleton Pattern:** MeetingRoomScheduler instance

### Expected Level Performance

**Junior Engineer:**
- Basic entities (Room, Booking, User)
- Simple booking create/cancel
- Basic conflict detection
- Single-shot bookings

**Mid-Level Engineer:**
- Complete CRUD operations
- Efficient conflict detection
- Atomic booking with locks
- Search with filters
- Recurring meetings (basic)

**Senior Engineer:**
- Production-ready with all features
- Recurring meetings with patterns
- Waitlist management
- Smart recommendations
- No-show tracking
- Performance optimization
- Time zone handling

### Key Trade-offs

**1. Locking Granularity**
- **Global lock:** Simple, but low concurrency
- **Per-room lock:** Better concurrency, more complex
- **Optimistic locking:** Best performance, requires retry logic

**2. Recurring Meetings**
- **Create all:** Simple, but many DB entries
- **Template + instances:** Complex, but efficient
- **Our choice:** Create all for simplicity in LLD

**3. Search Performance**
- **Linear scan:** Simple O(n), slow for large datasets
- **Index by time:** Fast O(log n), more memory
- **Our choice:** Linear for LLD, mention indexing

**4. Conflict Resolution**
- **Reject:** Simple, poor UX
- **Waitlist:** Better UX, more complex
- **Suggest alternatives:** Best UX, most complex

---

## Summary

This Meeting Room Scheduler demonstrates:

1. **Conflict Prevention:** Atomic booking with locks prevents double-booking
2. **Time Slot Overlap:** Efficient algorithm for detecting conflicts
3. **Recurring Meetings:** Pattern-based recurrence with flexible end conditions
4. **Thread Safety:** ReadWriteLock for concurrent bookings
5. **Search & Filter:** Find rooms by capacity, amenities, location
6. **Booking Lifecycle:** Complete state machine with cancellation

**Key Takeaways:**
- **Atomic operations** prevent double-booking
- **TimeSlot overlap detection** is core algorithm
- **ReadWriteLock** allows concurrent reads, exclusive writes
- **Recurring patterns** handle series bookings
- **State machine** manages booking lifecycle

**Related Systems:**
- Calendar scheduling (Google Calendar, Outlook)
- Resource booking (equipment, vehicles)
- Appointment scheduling (doctors, services)
- Hotel room booking

---

*Last Updated: January 5, 2026*
*Difficulty Level: Medium*
*Key Focus: Concurrency, Time Slot Conflicts, Atomic Operations*
*Companies: Microsoft, Google, Office Automation*
*Interview Success Rate: High with proper conflict detection*
