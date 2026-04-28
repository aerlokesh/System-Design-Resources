import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Movie Ticket Booking System (BookMyShow) - HELLO Interview Framework
 * 
 * Companies: Salesforce, Microsoft, Uber, Intuit, Walmart +5 more
 * Pattern: Observer Pattern + Two-Phase Booking (Hold → Confirm)
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Observer Pattern — decoupled notifications (Email, SMS, Waitlist)
 * 2. Two-Phase Booking — HOLD then CONFIRM with 5-min timeout
 * 3. ReentrantLock per Show — thread-safe, no double-booking
 * 4. Atomic all-or-nothing — check ALL seats before holding ANY
 */

// ==================== Enums ====================

enum SeatStatus { AVAILABLE, HELD, BOOKED, CANCELLED }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED, EXPIRED }

// ==================== Exceptions ====================

class SeatNotAvailableException extends RuntimeException {
    public SeatNotAvailableException(String seatId) {
        super("Seat " + seatId + " is not available");
    }
}

class BookingException extends RuntimeException {
    public BookingException(String msg) { super(msg); }
}

// ==================== Movie ====================

class Movie {
    private final String id;
    private final String title;
    private final int durationMins;
    private final String genre;

    public Movie(String id, String title, int durationMins, String genre) {
        this.id = id; this.title = title;
        this.durationMins = durationMins; this.genre = genre;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getDurationMins() { return durationMins; }

    @Override
    public String toString() { return title + " (" + durationMins + "min, " + genre + ")"; }
}

// ==================== Screen ====================

class Screen {
    private final String id;
    private final String name;
    private final int rows;
    private final int cols;

    public Screen(String id, String name, int rows, int cols) {
        this.id = id; this.name = name; this.rows = rows; this.cols = cols;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
}

// ==================== Seat ====================

class Seat {
    private final String seatId;   // e.g., "A1", "B5"
    private final int row;
    private final int col;
    private double price;
    private SeatStatus status;
    private String heldBy;         // userId who holds
    private long heldAt;           // timestamp of hold

    public Seat(String seatId, int row, int col, double price) {
        this.seatId = seatId; this.row = row; this.col = col;
        this.price = price; this.status = SeatStatus.AVAILABLE;
    }

    // Getters
    public String getSeatId() { return seatId; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public double getPrice() { return price; }
    public SeatStatus getStatus() { return status; }
    public String getHeldBy() { return heldBy; }
    public long getHeldAt() { return heldAt; }

    // Setters (package-private — only Show should modify)
    void setStatus(SeatStatus s) { this.status = s; }
    void setHeldBy(String userId) { this.heldBy = userId; }
    void setHeldAt(long ts) { this.heldAt = ts; }

    public boolean isAvailable() { return status == SeatStatus.AVAILABLE; }

    @Override
    public String toString() {
        return seatId + "(" + status + ")";
    }
}

// ==================== Show ====================
// Thread-safe seat management using ReentrantLock

class Show {
    private final String id;
    private final Movie movie;
    private final Screen screen;
    private final LocalDateTime showTime;
    private final Seat[][] seatGrid;
    private final Map<String, Seat> seatMap;   // O(1) lookup by seatId
    private final ReentrantLock lock = new ReentrantLock(true); // fair lock

    public Show(String id, Movie movie, Screen screen, LocalDateTime showTime, double basePrice) {
        this.id = id; this.movie = movie; this.screen = screen; this.showTime = showTime;
        this.seatGrid = new Seat[screen.getRows()][screen.getCols()];
        this.seatMap = new HashMap<>();
        initializeSeats(basePrice);
    }

    private void initializeSeats(double basePrice) {
        for (int r = 0; r < screen.getRows(); r++) {
            for (int c = 0; c < screen.getCols(); c++) {
                String seatId = (char)('A' + r) + "" + (c + 1);
                Seat seat = new Seat(seatId, r, c, basePrice);
                seatGrid[r][c] = seat;
                seatMap.put(seatId, seat);
            }
        }
    }

    /** Get all currently available seats */
    public List<Seat> getAvailableSeats() {
        lock.lock();
        try {
            List<Seat> available = new ArrayList<>();
            for (Seat[] row : seatGrid) {
                for (Seat seat : row) {
                    if (seat.isAvailable()) available.add(seat);
                }
            }
            return available;
        } finally { lock.unlock(); }
    }

    /**
     * Hold seats atomically — ALL or NOTHING.
     * If any seat is not available, throws exception and holds nothing.
     */
    public boolean holdSeats(List<String> seatIds, String userId) {
        lock.lock();
        try {
            // Phase 1: Validate ALL seats available
            List<Seat> toHold = new ArrayList<>();
            for (String seatId : seatIds) {
                Seat seat = seatMap.get(seatId);
                if (seat == null) throw new BookingException("Seat " + seatId + " not found");
                if (!seat.isAvailable()) throw new SeatNotAvailableException(seatId);
                toHold.add(seat);
            }
            // Phase 2: Hold ALL seats (only reached if all available)
            long now = System.currentTimeMillis();
            for (Seat seat : toHold) {
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(userId);
                seat.setHeldAt(now);
            }
            return true;
        } finally { lock.unlock(); }
    }

    /** Confirm seats held by userId → BOOKED */
    public List<Seat> confirmSeats(String userId) {
        lock.lock();
        try {
            List<Seat> confirmed = new ArrayList<>();
            for (Seat[] row : seatGrid) {
                for (Seat seat : row) {
                    if (seat.getStatus() == SeatStatus.HELD && userId.equals(seat.getHeldBy())) {
                        seat.setStatus(SeatStatus.BOOKED);
                        seat.setHeldBy(null);
                        confirmed.add(seat);
                    }
                }
            }
            if (confirmed.isEmpty()) throw new BookingException("No held seats found for user: " + userId);
            return confirmed;
        } finally { lock.unlock(); }
    }

    /** Release seats back to AVAILABLE (cancel or timeout) */
    public void releaseSeats(List<Seat> seats) {
        lock.lock();
        try {
            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldBy(null);
                seat.setHeldAt(0);
            }
        } finally { lock.unlock(); }
    }

    /** Release all expired holds and return released seats */
    public List<Seat> releaseExpiredHolds(long timeoutMs) {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            List<Seat> expired = new ArrayList<>();
            for (Seat[] row : seatGrid) {
                for (Seat seat : row) {
                    if (seat.getStatus() == SeatStatus.HELD
                            && (now - seat.getHeldAt()) > timeoutMs) {
                        seat.setStatus(SeatStatus.AVAILABLE);
                        seat.setHeldBy(null);
                        seat.setHeldAt(0);
                        expired.add(seat);
                    }
                }
            }
            return expired;
        } finally { lock.unlock(); }
    }

    /** Display seat map */
    public String getSeatMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Show: ").append(movie.getTitle()).append(" @ ").append(showTime)
          .append(" [").append(screen.getName()).append("]\n");
        sb.append("  ");
        for (int c = 0; c < screen.getCols(); c++) sb.append(String.format("  %d ", c + 1));
        sb.append("\n");
        for (int r = 0; r < screen.getRows(); r++) {
            sb.append((char)('A' + r)).append(" ");
            for (int c = 0; c < screen.getCols(); c++) {
                Seat seat = seatGrid[r][c];
                char symbol = switch (seat.getStatus()) {
                    case AVAILABLE -> '◻';
                    case HELD      -> '◧';
                    case BOOKED    -> '◼';
                    case CANCELLED -> '◻';
                };
                sb.append(" ").append(symbol).append("  ");
            }
            sb.append("\n");
        }
        sb.append("  Legend: ◻=Available ◧=Held ◼=Booked\n");
        return sb.toString();
    }

    public String getId() { return id; }
    public Movie getMovie() { return movie; }
    public Screen getScreen() { return screen; }
}

// ==================== Booking ====================

class Booking {
    private final String bookingId;
    private final String userId;
    private final Show show;
    private final List<Seat> seats;
    private BookingStatus status;
    private final double totalPrice;
    private final LocalDateTime createdAt;

    public Booking(String bookingId, String userId, Show show, List<Seat> seats) {
        this.bookingId = bookingId; this.userId = userId;
        this.show = show; this.seats = new ArrayList<>(seats);
        this.status = BookingStatus.CONFIRMED;
        this.totalPrice = seats.stream().mapToDouble(Seat::getPrice).sum();
        this.createdAt = LocalDateTime.now();
    }

    public String getBookingId() { return bookingId; }
    public String getUserId() { return userId; }
    public Show getShow() { return show; }
    public List<Seat> getSeats() { return Collections.unmodifiableList(seats); }
    public BookingStatus getStatus() { return status; }
    public double getTotalPrice() { return totalPrice; }
    void setStatus(BookingStatus s) { this.status = s; }

    @Override
    public String toString() {
        List<String> seatIds = seats.stream().map(Seat::getSeatId).toList();
        return String.format("Booking[%s] User=%s Seats=%s Total=$%.2f Status=%s",
            bookingId, userId, seatIds, totalPrice, status);
    }
}

// ==================== Observer Pattern ====================

interface BookingObserver {
    void onBookingConfirmed(Booking booking);
    void onBookingCancelled(Booking booking);
    void onSeatsReleased(Show show, List<Seat> seats);
}

class EmailNotifier implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking b) {
        System.out.println("    📧 Email: Booking confirmed! " + b.getBookingId()
            + " for " + b.getShow().getMovie().getTitle());
    }
    @Override
    public void onBookingCancelled(Booking b) {
        System.out.println("    📧 Email: Booking cancelled. " + b.getBookingId());
    }
    @Override
    public void onSeatsReleased(Show s, List<Seat> seats) {
        System.out.println("    📧 Email: " + seats.size() + " seats released for "
            + s.getMovie().getTitle());
    }
}

class SMSNotifier implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking b) {
        List<String> seatIds = b.getSeats().stream().map(Seat::getSeatId).toList();
        System.out.println("    📱 SMS: Your tickets " + seatIds + " confirmed! Total: $"
            + String.format("%.2f", b.getTotalPrice()));
    }
    @Override
    public void onBookingCancelled(Booking b) {
        System.out.println("    📱 SMS: Booking " + b.getBookingId() + " cancelled.");
    }
    @Override
    public void onSeatsReleased(Show s, List<Seat> seats) {
        // SMS doesn't notify on seat releases (opt-in only)
    }
}

class WaitlistNotifier implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking b) { /* no-op */ }
    @Override
    public void onBookingCancelled(Booking b) {
        System.out.println("    📋 Waitlist: Seats available after cancellation for "
            + b.getShow().getMovie().getTitle() + "!");
    }
    @Override
    public void onSeatsReleased(Show s, List<Seat> seats) {
        List<String> seatIds = seats.stream().map(Seat::getSeatId).toList();
        System.out.println("    📋 Waitlist: Seats " + seatIds + " now available for "
            + s.getMovie().getTitle());
    }
}

// ==================== BookingService (Singleton) ====================
// Orchestrates booking flow, manages observers

class BookingService {
    private static volatile BookingService instance;

    private final Map<String, Movie> movies = new ConcurrentHashMap<>();
    private final Map<String, Show> shows = new ConcurrentHashMap<>();
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    private final List<BookingObserver> observers = new CopyOnWriteArrayList<>();

    private static final long HOLD_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private int bookingCounter = 0;

    private BookingService() {}

    public static BookingService getInstance() {
        if (instance == null) {
            synchronized (BookingService.class) {
                if (instance == null) instance = new BookingService();
            }
        }
        return instance;
    }

    public static void resetInstance() {
        synchronized (BookingService.class) { instance = null; }
    }

    // ─── Observer Management ───

    public void addObserver(BookingObserver observer) { observers.add(observer); }
    public void removeObserver(BookingObserver observer) { observers.remove(observer); }

    private void notifyBookingConfirmed(Booking b) {
        for (BookingObserver o : observers) o.onBookingConfirmed(b);
    }
    private void notifyBookingCancelled(Booking b) {
        for (BookingObserver o : observers) o.onBookingCancelled(b);
    }
    private void notifySeatsReleased(Show s, List<Seat> seats) {
        if (!seats.isEmpty()) {
            for (BookingObserver o : observers) o.onSeatsReleased(s, seats);
        }
    }

    // ─── Movie & Show Management ───

    public void addMovie(Movie movie) { movies.put(movie.getId(), movie); }

    public Show addShow(String movieId, Screen screen, LocalDateTime showTime, double basePrice) {
        Movie movie = movies.get(movieId);
        if (movie == null) throw new BookingException("Movie not found: " + movieId);
        String showId = "show-" + (shows.size() + 1);
        Show show = new Show(showId, movie, screen, showTime, basePrice);
        shows.put(showId, show);
        return show;
    }

    // ─── Booking Flow ───

    /** Step 1: View available seats */
    public List<Seat> getAvailableSeats(String showId) {
        Show show = getShow(showId);
        return show.getAvailableSeats();
    }

    /** Step 2: Hold seats (5-min timeout) */
    public boolean holdSeats(String showId, List<String> seatIds, String userId) {
        Show show = getShow(showId);
        System.out.println("  🔒 Holding seats " + seatIds + " for user " + userId);
        return show.holdSeats(seatIds, userId);
    }

    /** Step 3: Confirm booking */
    public Booking confirmBooking(String showId, String userId) {
        Show show = getShow(showId);
        List<Seat> confirmedSeats = show.confirmSeats(userId);

        String bookingId = "BK-" + (++bookingCounter);
        Booking booking = new Booking(bookingId, userId, show, confirmedSeats);
        bookings.put(bookingId, booking);

        System.out.println("  ✅ " + booking);
        notifyBookingConfirmed(booking);
        return booking;
    }

    /** Cancel a booking — release seats */
    public boolean cancelBooking(String bookingId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null) throw new BookingException("Booking not found: " + bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED)
            throw new BookingException("Already cancelled: " + bookingId);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.getShow().releaseSeats(booking.getSeats());

        System.out.println("  ❌ Cancelled: " + bookingId);
        notifyBookingCancelled(booking);
        return true;
    }

    /** Cleanup expired holds (called by timer) */
    public void cleanupExpiredHolds() {
        for (Show show : shows.values()) {
            List<Seat> released = show.releaseExpiredHolds(HOLD_TIMEOUT_MS);
            notifySeatsReleased(show, released);
        }
    }

    /** Display seat map for a show */
    public String getSeatMap(String showId) {
        return getShow(showId).getSeatMap();
    }

    private Show getShow(String showId) {
        Show show = shows.get(showId);
        if (show == null) throw new BookingException("Show not found: " + showId);
        return show;
    }
}

// ==================== Main Demo ====================

public class MovieTicketBookingSystem {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Movie Ticket Booking System - Observer + Two-Phase Book ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");

        BookingService.resetInstance();
        BookingService service = BookingService.getInstance();

        // Register observers
        service.addObserver(new EmailNotifier());
        service.addObserver(new SMSNotifier());
        service.addObserver(new WaitlistNotifier());

        // Setup: Movie + Screen + Show
        Movie movie = new Movie("m1", "Inception", 148, "Sci-Fi");
        service.addMovie(movie);

        Screen screen = new Screen("s1", "Screen 1", 5, 8); // 5 rows × 8 cols = 40 seats
        Show show = service.addShow("m1", screen, LocalDateTime.of(2026, 5, 1, 19, 30), 15.00);
        String showId = show.getId();

        // ── Scenario 1: Happy path — hold and confirm ──
        System.out.println("━━━ Scenario 1: Happy Path (Hold → Confirm) ━━━");
        System.out.println(service.getSeatMap(showId));

        List<Seat> available = service.getAvailableSeats(showId);
        System.out.println("  Available seats: " + available.size());

        service.holdSeats(showId, List.of("A1", "A2", "A3"), "user-alice");
        System.out.println(service.getSeatMap(showId));

        Booking booking1 = service.confirmBooking(showId, "user-alice");
        System.out.println(service.getSeatMap(showId));

        // ── Scenario 2: Concurrent conflict ──
        System.out.println("━━━ Scenario 2: Concurrent Booking Conflict ━━━");
        service.holdSeats(showId, List.of("B1", "B2"), "user-bob");
        System.out.println("  Bob holds B1, B2");

        try {
            service.holdSeats(showId, List.of("B1"), "user-charlie"); // same seat!
        } catch (SeatNotAvailableException e) {
            System.out.println("  ✓ Charlie blocked: " + e.getMessage());
        }

        Booking booking2 = service.confirmBooking(showId, "user-bob");
        System.out.println();

        // ── Scenario 3: Cancellation + waitlist notification ──
        System.out.println("━━━ Scenario 3: Cancellation (Observer notifies waitlist) ━━━");
        service.cancelBooking(booking1.getBookingId());
        System.out.println();

        // Verify seats released
        available = service.getAvailableSeats(showId);
        System.out.println("  Available after cancel: " + available.size());
        System.out.println(service.getSeatMap(showId));

        // ── Scenario 4: Multi-threaded concurrent booking ──
        System.out.println("━━━ Scenario 4: Multi-threaded Concurrent Booking ━━━");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(3);

        // 3 users try to book same seats C1,C2 simultaneously
        String[] users = {"user-dave", "user-eve", "user-frank"};
        for (String user : users) {
            executor.submit(() -> {
                try {
                    service.holdSeats(showId, List.of("C1", "C2"), user);
                    Booking b = service.confirmBooking(showId, user);
                    System.out.println("  🎉 " + user + " booked successfully!");
                } catch (Exception e) {
                    System.out.println("  ✗ " + user + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { }
        executor.shutdown();

        System.out.println();
        System.out.println(service.getSeatMap(showId));

        // ── Scenario 5: Error handling ──
        System.out.println("━━━ Scenario 5: Error Handling ━━━");
        try {
            service.holdSeats("invalid-show", List.of("A1"), "user-x");
        } catch (BookingException e) {
            System.out.println("  ✓ Invalid show: " + e.getMessage());
        }

        try {
            service.holdSeats(showId, List.of("Z99"), "user-x");
        } catch (BookingException e) {
            System.out.println("  ✓ Invalid seat: " + e.getMessage());
        }

        try {
            service.cancelBooking("BK-999");
        } catch (BookingException e) {
            System.out.println("  ✓ Invalid booking: " + e.getMessage());
        }

        System.out.println("\n✅ All scenarios complete.");
    }
}
