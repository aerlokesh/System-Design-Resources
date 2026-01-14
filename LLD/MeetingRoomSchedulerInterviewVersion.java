import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Meeting Room Scheduler
 * Time to complete: 40-50 minutes
 * Focus: Interval overlap detection, booking conflicts
 */

// ==================== Meeting ====================
class Meeting {
    private final String id;
    private final String title;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String organizer;

    public Meeting(String id, String title, LocalDateTime startTime, 
                   LocalDateTime endTime, String organizer) {
        this.id = id;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.organizer = organizer;
    }

    public boolean overlapsWith(Meeting other) {
        return this.startTime.isBefore(other.endTime) && 
               other.startTime.isBefore(this.endTime);
    }

    public String getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    @Override
    public String toString() {
        return title + " [" + startTime.toLocalTime() + "-" + 
               endTime.toLocalTime() + "] by " + organizer;
    }
}

// ==================== Meeting Room ====================
class MeetingRoom {
    private final String roomId;
    private final String name;
    private final int capacity;
    private final List<Meeting> bookings;

    public MeetingRoom(String roomId, String name, int capacity) {
        this.roomId = roomId;
        this.name = name;
        this.capacity = capacity;
        this.bookings = new ArrayList<>();
    }

    public boolean isAvailable(LocalDateTime startTime, LocalDateTime endTime) {
        Meeting test = new Meeting("test", "test", startTime, endTime, "test");
        
        for (Meeting meeting : bookings) {
            if (meeting.overlapsWith(test)) {
                return false;
            }
        }
        return true;
    }

    public boolean book(Meeting meeting) {
        if (!isAvailable(meeting.getStartTime(), meeting.getEndTime())) {
            return false;
        }
        
        bookings.add(meeting);
        // Keep sorted by start time
        bookings.sort(Comparator.comparing(Meeting::getStartTime));
        return true;
    }

    public boolean cancel(String meetingId) {
        return bookings.removeIf(m -> m.getId().equals(meetingId));
    }

    public List<Meeting> getBookings() {
        return new ArrayList<>(bookings);
    }

    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }

    @Override
    public String toString() {
        return name + " (ID: " + roomId + ", Capacity: " + capacity + 
               ", Bookings: " + bookings.size() + ")";
    }
}

// ==================== Meeting Room Scheduler ====================
class MeetingRoomScheduler {
    private final Map<String, MeetingRoom> rooms;
    private int meetingCounter;

    public MeetingRoomScheduler() {
        this.rooms = new HashMap<>();
        this.meetingCounter = 1;
    }

    public void addRoom(MeetingRoom room) {
        rooms.put(room.getRoomId(), room);
        System.out.println("Added room: " + room);
    }

    public List<MeetingRoom> findAvailableRooms(LocalDateTime startTime, 
                                                 LocalDateTime endTime, 
                                                 int requiredCapacity) {
        List<MeetingRoom> available = new ArrayList<>();
        
        for (MeetingRoom room : rooms.values()) {
            if (room.getCapacity() >= requiredCapacity && 
                room.isAvailable(startTime, endTime)) {
                available.add(room);
            }
        }
        
        return available;
    }

    public Meeting bookMeeting(String roomId, String title, 
                              LocalDateTime startTime, LocalDateTime endTime, 
                              String organizer) {
        MeetingRoom room = rooms.get(roomId);
        if (room == null) {
            System.out.println("✗ Room not found: " + roomId);
            return null;
        }

        String meetingId = "M" + meetingCounter++;
        Meeting meeting = new Meeting(meetingId, title, startTime, endTime, organizer);

        if (room.book(meeting)) {
            System.out.println("✓ Booked: " + meeting + " in " + room.getName());
            return meeting;
        } else {
            System.out.println("✗ Conflict: " + title + " in " + room.getName());
            return null;
        }
    }

    public boolean cancelMeeting(String roomId, String meetingId) {
        MeetingRoom room = rooms.get(roomId);
        if (room == null) {
            return false;
        }

        boolean cancelled = room.cancel(meetingId);
        if (cancelled) {
            System.out.println("✓ Cancelled meeting: " + meetingId);
        }
        return cancelled;
    }

    public void displaySchedule(String roomId) {
        MeetingRoom room = rooms.get(roomId);
        if (room == null) {
            System.out.println("Room not found: " + roomId);
            return;
        }

        System.out.println("\n=== Schedule for " + room.getName() + " ===");
        List<Meeting> bookings = room.getBookings();
        if (bookings.isEmpty()) {
            System.out.println("No meetings scheduled");
        } else {
            for (Meeting meeting : bookings) {
                System.out.println("  " + meeting);
            }
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class MeetingRoomSchedulerInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Meeting Room Scheduler Demo ===\n");

        MeetingRoomScheduler scheduler = new MeetingRoomScheduler();

        // Add rooms
        scheduler.addRoom(new MeetingRoom("R1", "Conference Room A", 10));
        scheduler.addRoom(new MeetingRoom("R2", "Conference Room B", 20));
        scheduler.addRoom(new MeetingRoom("R3", "Small Room", 4));

        LocalDate today = LocalDate.now();

        // Book meetings
        System.out.println("\n--- Booking Meetings ---");
        scheduler.bookMeeting(
            "R1", "Sprint Planning",
            LocalDateTime.of(today, LocalTime.of(9, 0)),
            LocalDateTime.of(today, LocalTime.of(10, 0)),
            "Alice"
        );

        scheduler.bookMeeting(
            "R1", "Code Review",
            LocalDateTime.of(today, LocalTime.of(10, 30)),
            LocalDateTime.of(today, LocalTime.of(11, 30)),
            "Bob"
        );

        // Try conflicting booking
        scheduler.bookMeeting(
            "R1", "Design Discussion",
            LocalDateTime.of(today, LocalTime.of(10, 0)),  // Conflicts with Code Review
            LocalDateTime.of(today, LocalTime.of(11, 0)),
            "Charlie"
        );

        // Book in different room
        scheduler.bookMeeting(
            "R2", "Team Standup",
            LocalDateTime.of(today, LocalTime.of(9, 30)),
            LocalDateTime.of(today, LocalTime.of(10, 0)),
            "Diana"
        );

        // Display schedules
        scheduler.displaySchedule("R1");
        scheduler.displaySchedule("R2");

        // Find available rooms
        System.out.println("--- Finding Available Rooms ---");
        LocalDateTime searchStart = LocalDateTime.of(today, LocalTime.of(14, 0));
        LocalDateTime searchEnd = LocalDateTime.of(today, LocalTime.of(15, 0));
        
        List<MeetingRoom> available = scheduler.findAvailableRooms(
            searchStart, searchEnd, 5
        );
        
        System.out.println("Available rooms for " + searchStart.toLocalTime() + 
                         "-" + searchEnd.toLocalTime() + ":");
        for (MeetingRoom room : available) {
            System.out.println("  " + room);
        }

        System.out.println("\n✅ Demo complete!");
    }
}
