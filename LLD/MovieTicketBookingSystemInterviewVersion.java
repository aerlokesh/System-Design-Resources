import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Movie Ticket Booking System
 * Time to complete: 45-60 minutes
 * Focus: Seat selection, booking management, concurrency
 */

// ==================== Seat ====================
class Seat {
    private final String seatNumber;
    private final String row;
    private final int column;
    private boolean isBooked;
    private String bookedBy;

    public Seat(String seatNumber, String row, int column) {
        this.seatNumber = seatNumber;
        this.row = row;
        this.column = column;
        this.isBooked = false;
    }

    public synchronized boolean book(String userId) {
        if (isBooked) {
            return false;
        }
        isBooked = true;
        bookedBy = userId;
        return true;
    }

    public synchronized void release() {
        isBooked = false;
        bookedBy = null;
    }

    public boolean isAvailable() {
        return !isBooked;
    }

    public String getSeatNumber() { return seatNumber; }

    @Override
    public String toString() {
        return seatNumber + (isBooked ? "[X]" : "[ ]");
    }
}

// ==================== Show ====================
class Show {
    private final String showId;
    private final String movieName;
    private final LocalDateTime showTime;
    private final List<Seat> seats;
    private final double ticketPrice;

    public Show(String showId, String movieName, LocalDateTime showTime, 
                int totalSeats, double ticketPrice) {
        this.showId = showId;
        this.movieName = movieName;
        this.showTime = showTime;
        this.ticketPrice = ticketPrice;
        this.seats = new ArrayList<>();

        // Create seats (A1-A10, B1-B10, etc.)
        String[] rows = {"A", "B", "C", "D"};
        int seatsPerRow = totalSeats / rows.length;
        
        for (String row : rows) {
            for (int i = 1; i <= seatsPerRow; i++) {
                String seatNum = row + i;
                seats.add(new Seat(seatNum, row, i));
            }
        }
    }

    public List<Seat> getAvailableSeats() {
        List<Seat> available = new ArrayList<>();
        for (Seat seat : seats) {
            if (seat.isAvailable()) {
                available.add(seat);
            }
        }
        return available;
    }

    public Seat getSeat(String seatNumber) {
        for (Seat seat : seats) {
            if (seat.getSeatNumber().equals(seatNumber)) {
                return seat;
            }
        }
        return null;
    }

    public String getShowId() { return showId; }
    public String getMovieName() { return movieName; }
    public LocalDateTime getShowTime() { return showTime; }
    public double getTicketPrice() { return ticketPrice; }

    @Override
    public String toString() {
        return movieName + " @ " + showTime.toLocalTime() + 
               " (" + getAvailableSeats().size() + "/" + seats.size() + " available)";
    }
}

// ==================== Booking ====================
class Booking {
    private final String bookingId;
    private final Show show;
    private final List<Seat> seats;
    private final String userId;
    private final LocalDateTime bookingTime;
    private final double totalAmount;

    public Booking(String bookingId, Show show, List<Seat> seats, String userId) {
        this.bookingId = bookingId;
        this.show = show;
        this.seats = seats;
        this.userId = userId;
        this.bookingTime = LocalDateTime.now();
        this.totalAmount = seats.size() * show.getTicketPrice();
    }

    public String getBookingId() { return bookingId; }
    public List<Seat> getSeats() { return seats; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Booking[").append(bookingId).append(", ");
        sb.append(show.getMovieName()).append(", Seats: ");
        for (Seat seat : seats) {
            sb.append(seat.getSeatNumber()).append(" ");
        }
        sb.append(", Amount: $").append(totalAmount).append("]");
        return sb.toString();
    }
}

// ==================== Movie Ticket Booking Service ====================
class MovieTicketBookingService {
    private final Map<String, Show> shows;
    private final Map<String, Booking> bookings;
    private int bookingCounter;

    public MovieTicketBookingService() {
        this.shows = new HashMap<>();
        this.bookings = new HashMap<>();
        this.bookingCounter = 1;
    }

    public void addShow(Show show) {
        shows.put(show.getShowId(), show);
        System.out.println("✓ Added show: " + show);
    }

    public Show getShow(String showId) {
        return shows.get(showId);
    }

    public Booking bookTickets(String showId, List<String> seatNumbers, String userId) {
        Show show = shows.get(showId);
        if (show == null) {
            System.out.println("✗ Show not found: " + showId);
            return null;
        }

        List<Seat> seatsToBook = new ArrayList<>();
        
        // Verify all seats are available
        for (String seatNum : seatNumbers) {
            Seat seat = show.getSeat(seatNum);
            if (seat == null) {
                System.out.println("✗ Seat not found: " + seatNum);
                return null;
            }
            if (!seat.isAvailable()) {
                System.out.println("✗ Seat already booked: " + seatNum);
                return null;
            }
            seatsToBook.add(seat);
        }

        // Book all seats
        for (Seat seat : seatsToBook) {
            if (!seat.book(userId)) {
                // Rollback if any seat fails
                for (Seat bookedSeat : seatsToBook) {
                    if (bookedSeat != seat) {
                        bookedSeat.release();
                    }
                }
                System.out.println("✗ Booking failed for: " + seat.getSeatNumber());
                return null;
            }
        }

        // Create booking
        String bookingId = "B" + bookingCounter++;
        Booking booking = new Booking(bookingId, show, seatsToBook, userId);
        bookings.put(bookingId, booking);

        System.out.println("✓ Booking successful: " + booking);
        return booking;
    }

    public boolean cancelBooking(String bookingId) {
        Booking booking = bookings.remove(bookingId);
        if (booking == null) {
            System.out.println("✗ Booking not found: " + bookingId);
            return false;
        }

        // Release seats
        for (Seat seat : booking.getSeats()) {
            seat.release();
        }

        System.out.println("✓ Cancelled booking: " + bookingId);
        return true;
    }

    public void displaySeating(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            System.out.println("✗ Show not found");
            return;
        }

        System.out.println("\n=== Seating for " + show.getMovieName() + " ===");
        System.out.println("Show time: " + show.getShowTime().toLocalTime());
        System.out.println("Available: " + show.getAvailableSeats().size());
        
        System.out.println("\nSeats:");
        int count = 0;
        for (Seat seat : show.getAvailableSeats()) {
            System.out.print(seat + " ");
            count++;
            if (count % 10 == 0) System.out.println();
        }
        System.out.println("\n");
    }
}

// ==================== Demo ====================
public class MovieTicketBookingSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Movie Ticket Booking Demo ===\n");

        MovieTicketBookingService service = new MovieTicketBookingService();

        // Add shows
        LocalDate today = LocalDate.now();
        Show show1 = new Show(
            "S1",
            "Avengers: Endgame",
            LocalDateTime.of(today, LocalTime.of(18, 0)),
            40,
            15.0
        );

        service.addShow(show1);
        service.displaySeating("S1");

        // Book tickets
        System.out.println("--- Booking Tickets ---");
        Booking b1 = service.bookTickets(
            "S1",
            Arrays.asList("A1", "A2", "A3"),
            "user1"
        );

        Booking b2 = service.bookTickets(
            "S1",
            Arrays.asList("B5", "B6"),
            "user2"
        );

        // Try to book already booked seat
        service.bookTickets(
            "S1",
            Arrays.asList("A1", "A4"),  // A1 already booked
            "user3"
        );

        service.displaySeating("S1");

        // Cancel booking
        System.out.println("--- Cancelling Booking ---");
        service.cancelBooking(b1.getBookingId());

        service.displaySeating("S1");

        System.out.println("✅ Demo complete!");
    }
}
