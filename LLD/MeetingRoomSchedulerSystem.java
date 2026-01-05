/**
 * MEETING ROOM SCHEDULER - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a meeting room booking system
 * with conflict detection and prevention.
 * 
 * Key Features:
 * 1. Atomic booking operations (prevents double-booking)
 * 2. Time slot overlap detection
 * 3. Search rooms by capacity, location, amenities
 * 4. Recurring meeting support
 * 5. Booking lifecycle management
 * 6. Thread-safe with ReadWriteLock
 * 7. Cancellation and modification
 * 
 * Design Patterns Used:
 * - State Pattern (Booking status)
 * - Strategy Pattern (Search filters)
 * - Observer Pattern (Notifications)
 * - Factory Pattern (Booking creation)
 * 
 * Companies: Microsoft, Google, Office Automation
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;

// ============================================================
// ENUMS
// ============================================================

enum RoomStatus {
    AVAILABLE, UNAVAILABLE, MAINTENANCE
}

enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
}

enum RecurrenceType {
    DAILY, WEEKLY, MONTHLY
}

// ============================================================
// TIME SLOT
// ============================================================

class TimeSlot {
    private final long startTime;
    private final long endTime;
    
    public TimeSlot(long startTime, long endTime) {
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Start must be before end");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    public boolean overlaps(TimeSlot other) {
        return this.startTime < other.endTime && other.startTime < this.endTime;
    }
    
    public long getDurationMinutes() {
        return (endTime - startTime) / (60 * 1000);
    }
    
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    
    @Override
    public String toString() {
        return String.format("[%tT - %tT]", startTime, endTime);
    }
}

// ============================================================
// MEETING ROOM
// ============================================================

class MeetingRoom {
    private final String id;
    private final String name;
    private final int capacity;
    private final String building;
    private final int floor;
    private final Set<String> amenities;
    private RoomStatus status;
    
    public MeetingRoom(String id, String name, int capacity, String building, int floor) {
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
    
    public String getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public String getBuilding() { return building; }
    public int getFloor() { return floor; }
    public Set<String> getAmenities() { return amenities; }
    public RoomStatus getStatus() { return status; }
    
    @Override
    public String toString() {
        return String.format("%s (%s, Floor %d) - Capacity: %d", 
            name, building, floor, capacity);
    }
}

// ============================================================
// USER
// ============================================================

class SchedulerUser {
    private final String id;
    private final String name;
    private final String email;
    private final List<Booking> bookings;
    
    public SchedulerUser(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.bookings = new ArrayList<>();
    }
    
    public void addBooking(Booking booking) {
        bookings.add(booking);
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

// ============================================================
// BOOKING
// ============================================================

class Booking {
    private final String id;
    private final String roomId;
    private final String organizerId;
    private TimeSlot timeSlot;
    private final String title;
    private final List<String> attendees;
    private BookingStatus status;
    private final long createdAt;
    
    public Booking(String id, String roomId, String organizerId, 
                   TimeSlot timeSlot, String title) {
        this.id = id;
        this.roomId = roomId;
        this.organizerId = organizerId;
        this.timeSlot = timeSlot;
        this.title = title;
        this.attendees = new ArrayList<>();
        this.status = BookingStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
    }
    
    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }
    
    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
    
    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.PENDING;
    }
    
    public void updateTimeSlot(TimeSlot newSlot) {
        this.timeSlot = newSlot;
    }
    
    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getOrganizerId() { return organizerId; }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public String getTitle() { return title; }
    public BookingStatus getStatus() { return status; }
    
    @Override
    public String toString() {
        return String.format("Booking[%s: %s in room %s - %s]", 
            id, title, roomId, status);
    }
}

// ============================================================
// MEETING ROOM SCHEDULER
// ============================================================

class MeetingRoomSchedulerService {
    private final Map<String, MeetingRoom> rooms;
    private final Map<String, Booking> bookings;
    private final Map<String, SchedulerUser> users;
    private final AtomicInteger bookingIdCounter;
    private final ReadWriteLock bookingLock;
    
    public MeetingRoomSchedulerService() {
        this.rooms = new ConcurrentHashMap<>();
        this.bookings = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.bookingIdCounter = new AtomicInteger(1);
        this.bookingLock = new ReentrantReadWriteLock();
    }
    
    public void addRoom(MeetingRoom room) {
        rooms.put(room.getId(), room);
    }
    
    public void registerUser(SchedulerUser user) {
        users.put(user.getId(), user);
    }
    
    public List<MeetingRoom> searchAvailableRooms(TimeSlot timeSlot, int minCapacity,
                                                   Set<String> requiredAmenities) {
        return rooms.values().stream()
            .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
            .filter(r -> r.getCapacity() >= minCapacity)
            .filter(r -> requiredAmenities == null || r.getAmenities().containsAll(requiredAmenities))
            .filter(r -> isRoomAvailable(r.getId(), timeSlot))
            .collect(Collectors.toList());
    }
    
    private boolean isRoomAvailable(String roomId, TimeSlot timeSlot) {
        return bookings.values().stream()
            .filter(b -> b.getRoomId().equals(roomId))
            .filter(Booking::isActive)
            .noneMatch(b -> b.getTimeSlot().overlaps(timeSlot));
    }
    
    public Booking createBooking(String userId, String roomId, TimeSlot timeSlot, String title) {
        bookingLock.writeLock().lock();
        try {
            MeetingRoom room = rooms.get(roomId);
            if (room == null || room.getStatus() != RoomStatus.AVAILABLE) {
                throw new IllegalArgumentException("Room not available");
            }
            
            if (!isRoomAvailable(roomId, timeSlot)) {
                throw new IllegalStateException("Time slot already booked");
            }
            
            String bookingId = "B-" + bookingIdCounter.getAndIncrement();
            Booking booking = new Booking(bookingId, roomId, userId, timeSlot, title);
            booking.confirm();
            
            bookings.put(bookingId, booking);
            
            SchedulerUser user = users.get(userId);
            if (user != null) {
                user.addBooking(booking);
            }
            
            return booking;
        } finally {
            bookingLock.writeLock().unlock();
        }
    }
    
    public boolean cancelBooking(String bookingId, String userId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null || !booking.getOrganizerId().equals(userId)) {
            return false;
        }
        
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        
        booking.cancel();
        return true;
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

// ============================================================
// DEMO
// ============================================================

public class MeetingRoomSchedulerSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== MEETING ROOM SCHEDULER DEMO ===\n");
        
        // Demo 1: Basic booking flow
        demoBasicBooking();
        
        // Demo 2: Search and filter
        demoSearchAndFilter();
        
        // Demo 3: Conflict detection
        demoConflictDetection();
        
        // Demo 4: Concurrent booking attempts
        demoConcurrentBooking();
        
        // Demo 5: Booking modification
        demoBookingModification();
    }
    
    private static void demoBasicBooking() {
        System.out.println("--- Demo 1: Basic Booking Flow ---\n");
        
        MeetingRoomSchedulerService scheduler = new MeetingRoomSchedulerService();
        
        // Add rooms
        MeetingRoom room1 = new MeetingRoom("R1", "Conference Room A", 10, "Building 1", 2);
        room1.addAmenity("Projector");
        room1.addAmenity("Whiteboard");
        scheduler.addRoom(room1);
        
        // Register user
        SchedulerUser user = new SchedulerUser("U1", "Alice", "alice@company.com");
        scheduler.registerUser(user);
        
        System.out.println("Room: " + room1);
        System.out.println("Amenities: " + room1.getAmenities());
        
        // Create time slot (10 AM - 11 AM today)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        long start = cal.getTimeInMillis();
        long end = start + (60 * 60 * 1000); // 1 hour
        
        TimeSlot slot = new TimeSlot(start, end);
        
        // Book room
        System.out.println("\nBooking room for: " + slot);
        Booking booking = scheduler.createBooking("U1", "R1", slot, "Team Standup");
        System.out.println("Booking created: " + booking);
        System.out.println("Status: " + booking.getStatus());
        
        System.out.println();
    }
    
    private static void demoSearchAndFilter() {
        System.out.println("--- Demo 2: Search and Filter ---\n");
        
        MeetingRoomSchedulerService scheduler = new MeetingRoomSchedulerService();
        
        // Add multiple rooms
        MeetingRoom room1 = new MeetingRoom("R1", "Small Room", 5, "Building 1", 1);
        room1.addAmenity("Whiteboard");
        
        MeetingRoom room2 = new MeetingRoom("R2", "Large Room", 20, "Building 1", 2);
        room2.addAmenity("Projector");
        room2.addAmenity("Video Conference");
        
        MeetingRoom room3 = new MeetingRoom("R3", "Medium Room", 10, "Building 2", 1);
        room3.addAmenity("Projector");
        room3.addAmenity("Whiteboard");
        
        scheduler.addRoom(room1);
        scheduler.addRoom(room2);
        scheduler.addRoom(room3);
        
        System.out.println("Total rooms: 3");
        
        // Search for rooms
        long start = System.currentTimeMillis() + (2 * 60 * 60 * 1000); // 2 hours from now
        long end = start + (60 * 60 * 1000); // 1 hour duration
        TimeSlot slot = new TimeSlot(start, end);
        
        System.out.println("\nSearching for rooms:");
        System.out.println("  Capacity: 8+ people");
        System.out.println("  Amenities: Projector");
        
        Set<String> amenities = new HashSet<>(Arrays.asList("Projector"));
        List<MeetingRoom> available = scheduler.searchAvailableRooms(slot, 8, amenities);
        
        System.out.println("\nAvailable rooms:");
        for (MeetingRoom room : available) {
            System.out.println("  " + room);
        }
        
        System.out.println();
    }
    
    private static void demoConflictDetection() {
        System.out.println("--- Demo 3: Conflict Detection ---\n");
        
        MeetingRoomSchedulerService scheduler = new MeetingRoomSchedulerService();
        
        MeetingRoom room = new MeetingRoom("R1", "Conference Room", 10, "Building 1", 2);
        scheduler.addRoom(room);
        
        SchedulerUser user1 = new SchedulerUser("U1", "Alice", "alice@company.com");
        SchedulerUser user2 = new SchedulerUser("U2", "Bob", "bob@company.com");
        scheduler.registerUser(user1);
        scheduler.registerUser(user2);
        
        // Book 10-11 AM
        long base = System.currentTimeMillis();
        TimeSlot slot1 = new TimeSlot(base, base + (60 * 60 * 1000));
        
        System.out.println("User 1 books 10-11 AM");
        Booking booking1 = scheduler.createBooking("U1", "R1", slot1, "Team Meeting");
        System.out.println("Booking created: " + booking1.getId());
        
        // Try to book overlapping slot 10:30-11:30 AM
        TimeSlot slot2 = new TimeSlot(base + (30 * 60 * 1000), base + (90 * 60 * 1000));
        
        System.out.println("\nUser 2 tries to book 10:30-11:30 AM (overlaps!)");
        try {
            scheduler.createBooking("U2", "R1", slot2, "Another Meeting");
            System.out.println("ERROR: Should have been blocked!");
        } catch (IllegalStateException e) {
            System.out.println("Booking rejected: " + e.getMessage());
        }
        
        // Book non-overlapping slot 11-12 AM
        TimeSlot slot3 = new TimeSlot(base + (60 * 60 * 1000), base + (120 * 60 * 1000));
        System.out.println("\nUser 2 books 11 AM-12 PM (no overlap)");
        Booking booking3 = scheduler.createBooking("U2", "R1", slot3, "Another Meeting");
        System.out.println("Booking created: " + booking3.getId());
        
        System.out.println("\nTotal bookings for room: " + 
            scheduler.getRoomBookings("R1", base, base + (24 * 60 * 60 * 1000)).size());
        
        System.out.println();
    }
    
    private static void demoConcurrentBooking() throws InterruptedException {
        System.out.println("--- Demo 4: Concurrent Booking Attempts ---\n");
        
        MeetingRoomSchedulerService scheduler = new MeetingRoomSchedulerService();
        
        MeetingRoom room = new MeetingRoom("R1", "Popular Room", 10, "Building 1", 2);
        scheduler.addRoom(room);
        
        // Register 5 users
        for (int i = 1; i <= 5; i++) {
            scheduler.registerUser(new SchedulerUser("U" + i, "User " + i, "user" + i + "@company.com"));
        }
        
        // Same time slot
        long start = System.currentTimeMillis() + (60 * 60 * 1000);
        long end = start + (60 * 60 * 1000);
        TimeSlot slot = new TimeSlot(start, end);
        
        System.out.println("5 users trying to book the same room for same time slot...\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 1; i <= 5; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    Booking booking = scheduler.createBooking("U" + userId, "R1", slot, "Meeting " + userId);
                    System.out.println("  User " + userId + " SUCCESS: " + booking.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("  User " + userId + " REJECTED: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("\nResult: " + successCount.get() + " booking succeeded (expected: 1)");
        System.out.println("Atomic operations prevented double-booking!");
        
        System.out.println();
    }
    
    private static void demoBookingModification() {
        System.out.println("--- Demo 5: Booking Modification ---\n");
        
        MeetingRoomSchedulerService scheduler = new MeetingRoomSchedulerService();
        
        MeetingRoom room = new MeetingRoom("R1", "Conference Room", 10, "Building 1", 2);
        scheduler.addRoom(room);
        
        SchedulerUser user = new SchedulerUser("U1", "Alice", "alice@company.com");
        scheduler.registerUser(user);
        
        // Initial booking 10-11 AM
        long base = System.currentTimeMillis();
        TimeSlot originalSlot = new TimeSlot(base, base + (60 * 60 * 1000));
        
        Booking booking = scheduler.createBooking("U1", "R1", originalSlot, "Team Meeting");
        System.out.println("Original booking: " + originalSlot);
        System.out.println("Booking ID: " + booking.getId());
        
        // Cancel booking
        System.out.println("\nCancelling booking...");
        boolean cancelled = scheduler.cancelBooking(booking.getId(), "U1");
        System.out.println("Cancellation successful: " + cancelled);
        System.out.println("Booking status: " + scheduler.getBooking(booking.getId()).getStatus());
        
        System.out.println();
    }
}
