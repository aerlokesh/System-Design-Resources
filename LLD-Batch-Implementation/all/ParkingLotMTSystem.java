import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Parking Lot System (Multi-Threaded) - HELLO Interview Framework
 * 
 * Companies: Amazon, Microsoft, Goldman Sachs, Google, Uber +13 more
 * Pattern: Strategy (pricing) + Per-floor ReentrantLock
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Strategy Pattern — swappable pricing (Hourly, Flat, Dynamic)
 * 2. ReentrantLock per floor — concurrent park/unpark without global lock
 * 3. ConcurrentHashMap — thread-safe ticket tracking
 * 4. Vehicle hierarchy — abstract Vehicle → Car, Motorcycle, Truck
 */

// ==================== Enums ====================

enum VehicleType { MOTORCYCLE, CAR, TRUCK }
enum SpotType { COMPACT, REGULAR, LARGE }

// ==================== Vehicle Hierarchy ====================

abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
    abstract SpotType getRequiredSpotType();

    @Override
    public String toString() { return type + "[" + licensePlate + "]"; }
}

class Motorcycle extends Vehicle {
    Motorcycle(String plate) { super(plate, VehicleType.MOTORCYCLE); }
    @Override SpotType getRequiredSpotType() { return SpotType.COMPACT; }
}

class Car extends Vehicle {
    Car(String plate) { super(plate, VehicleType.CAR); }
    @Override SpotType getRequiredSpotType() { return SpotType.REGULAR; }
}

class Truck extends Vehicle {
    Truck(String plate) { super(plate, VehicleType.TRUCK); }
    @Override SpotType getRequiredSpotType() { return SpotType.LARGE; }
}

// ==================== ParkingSpot ====================

class ParkingSpot {
    private final String id;
    private final SpotType type;
    private final int floor;
    private volatile Vehicle occupant;

    ParkingSpot(String id, SpotType type, int floor) {
        this.id = id; this.type = type; this.floor = floor;
    }

    public String getId() { return id; }
    public SpotType getType() { return type; }
    public int getFloor() { return floor; }
    public boolean isAvailable() { return occupant == null; }
    public Vehicle getOccupant() { return occupant; }

    boolean canFit(Vehicle v) {
        if (!isAvailable()) return false;
        return type.ordinal() >= v.getRequiredSpotType().ordinal(); // LARGE fits all
    }

    void park(Vehicle v) { this.occupant = v; }
    void release() { this.occupant = null; }

    @Override
    public String toString() {
        return id + "(" + type + ":" + (isAvailable() ? "FREE" : occupant) + ")";
    }
}

// ==================== ParkingTicket ====================

class ParkingTicket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;

    ParkingTicket(String ticketId, Vehicle vehicle, ParkingSpot spot) {
        this.ticketId = ticketId; this.vehicle = vehicle; this.spot = spot;
        this.entryTime = LocalDateTime.now();
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public double getFee() { return fee; }

    void setExitTime(LocalDateTime t) { this.exitTime = t; }
    void setFee(double f) { this.fee = f; }

    long getDurationHours() {
        LocalDateTime exit = exitTime != null ? exitTime : LocalDateTime.now();
        long mins = Duration.between(entryTime, exit).toMinutes();
        return Math.max(1, (long) Math.ceil(mins / 60.0)); // min 1 hour
    }

    @Override
    public String toString() {
        return String.format("Ticket[%s] %s @ %s | Fee=$%.2f", ticketId, vehicle, spot.getId(), fee);
    }
}

// ==================== Strategy: Pricing ====================

interface PricingStrategy {
    double calculateFee(ParkingTicket ticket);
    String getName();
}

class HourlyPricingStrategy implements PricingStrategy {
    private final Map<VehicleType, Double> hourlyRates;

    HourlyPricingStrategy() {
        hourlyRates = Map.of(
            VehicleType.MOTORCYCLE, 1.0,
            VehicleType.CAR, 2.0,
            VehicleType.TRUCK, 4.0
        );
    }

    @Override
    public double calculateFee(ParkingTicket ticket) {
        double rate = hourlyRates.getOrDefault(ticket.getVehicle().getType(), 2.0);
        return rate * ticket.getDurationHours();
    }

    @Override
    public String getName() { return "Hourly"; }
}

class FlatPricingStrategy implements PricingStrategy {
    private final double flatRate;

    FlatPricingStrategy(double flatRate) { this.flatRate = flatRate; }

    @Override
    public double calculateFee(ParkingTicket ticket) { return flatRate; }

    @Override
    public String getName() { return "Flat($" + flatRate + ")"; }
}

// ==================== ParkingFloor ====================
// Thread-safe per-floor spot management

class ParkingFloor {
    private final int floorNumber;
    private final List<ParkingSpot> spots;
    private final Map<SpotType, AtomicInteger> availableCounts;
    private final ReentrantLock lock = new ReentrantLock(true);

    ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.availableCounts = new ConcurrentHashMap<>();
        for (SpotType type : SpotType.values()) {
            availableCounts.put(type, new AtomicInteger(0));
        }
    }

    void addSpot(ParkingSpot spot) {
        spots.add(spot);
        availableCounts.get(spot.getType()).incrementAndGet();
    }

    /** Thread-safe: find and assign a spot */
    ParkingSpot findAndPark(Vehicle vehicle) {
        lock.lock();
        try {
            for (ParkingSpot spot : spots) {
                if (spot.canFit(vehicle)) {
                    spot.park(vehicle);
                    availableCounts.get(spot.getType()).decrementAndGet();
                    return spot;
                }
            }
            return null; // no spot on this floor
        } finally { lock.unlock(); }
    }

    /** Thread-safe: release a spot */
    void releaseSpot(ParkingSpot spot) {
        lock.lock();
        try {
            spot.release();
            availableCounts.get(spot.getType()).incrementAndGet();
        } finally { lock.unlock(); }
    }

    int getAvailableCount(SpotType type) { return availableCounts.get(type).get(); }
    int getFloorNumber() { return floorNumber; }
    int getTotalSpots() { return spots.size(); }

    String getStatus() {
        int total = spots.size();
        int used = (int) spots.stream().filter(s -> !s.isAvailable()).count();
        return String.format("Floor %d: %d/%d occupied | C:%d R:%d L:%d available",
            floorNumber, used, total,
            getAvailableCount(SpotType.COMPACT),
            getAvailableCount(SpotType.REGULAR),
            getAvailableCount(SpotType.LARGE));
    }
}

// ==================== ParkingLot (Singleton) ====================

class ParkingLot {
    private static volatile ParkingLot instance;
    private final String name;
    private final List<ParkingFloor> floors;
    private final ConcurrentHashMap<String, ParkingTicket> activeTickets;
    private final ConcurrentHashMap<String, ParkingSpot> vehicleSpots; // licensePlate → spot
    private PricingStrategy pricingStrategy;
    private final AtomicInteger ticketCounter = new AtomicInteger(0);

    private ParkingLot(String name, PricingStrategy pricing) {
        this.name = name;
        this.floors = new ArrayList<>();
        this.activeTickets = new ConcurrentHashMap<>();
        this.vehicleSpots = new ConcurrentHashMap<>();
        this.pricingStrategy = pricing;
    }

    static ParkingLot getInstance(String name, PricingStrategy pricing) {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) instance = new ParkingLot(name, pricing);
            }
        }
        return instance;
    }

    static void resetInstance() {
        synchronized (ParkingLot.class) { instance = null; }
    }

    void addFloor(ParkingFloor floor) { floors.add(floor); }
    void setPricingStrategy(PricingStrategy s) { this.pricingStrategy = s; }

    /** Park a vehicle — try each floor until spot found */
    ParkingTicket parkVehicle(Vehicle vehicle) {
        if (vehicleSpots.containsKey(vehicle.getLicensePlate())) {
            System.out.println("  ⚠️ " + vehicle + " already parked!");
            return null;
        }

        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAndPark(vehicle);
            if (spot != null) {
                String ticketId = "T-" + ticketCounter.incrementAndGet();
                ParkingTicket ticket = new ParkingTicket(ticketId, vehicle, spot);
                activeTickets.put(ticketId, ticket);
                vehicleSpots.put(vehicle.getLicensePlate(), spot);
                System.out.println("  🅿️ Parked " + vehicle + " at " + spot.getId()
                    + " (Floor " + floor.getFloorNumber() + ") → Ticket: " + ticketId);
                return ticket;
            }
        }
        System.out.println("  ❌ No spot available for " + vehicle);
        return null;
    }

    /** Unpark a vehicle — calculate fee, release spot */
    double unparkVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            System.out.println("  ⚠️ Invalid ticket: " + ticketId);
            return -1;
        }

        ticket.setExitTime(LocalDateTime.now());
        double fee = pricingStrategy.calculateFee(ticket);
        ticket.setFee(fee);

        ParkingSpot spot = ticket.getSpot();
        vehicleSpots.remove(ticket.getVehicle().getLicensePlate());

        // Find floor and release
        for (ParkingFloor floor : floors) {
            if (spot.getFloor() == floor.getFloorNumber()) {
                floor.releaseSpot(spot);
                break;
            }
        }

        System.out.printf("  🚗 Unparked %s from %s | Duration: %dh | Fee: $%.2f [%s]%n",
            ticket.getVehicle(), spot.getId(), ticket.getDurationHours(), fee, pricingStrategy.getName());
        return fee;
    }

    String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ ").append(name).append(" Status ═══\n");
        for (ParkingFloor floor : floors) {
            sb.append("  ").append(floor.getStatus()).append("\n");
        }
        sb.append("  Active tickets: ").append(activeTickets.size()).append("\n");
        return sb.toString();
    }

    boolean isFull() {
        return floors.stream().allMatch(f ->
            f.getAvailableCount(SpotType.COMPACT) == 0
            && f.getAvailableCount(SpotType.REGULAR) == 0
            && f.getAvailableCount(SpotType.LARGE) == 0);
    }
}

// ==================== Main Demo ====================

public class ParkingLotMTSystem {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  Parking Lot (Multi-Threaded) - Strategy + Locking   ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        ParkingLot.resetInstance();
        ParkingLot lot = ParkingLot.getInstance("Downtown Garage", new HourlyPricingStrategy());

        // Setup: 2 floors, mixed spots
        ParkingFloor floor1 = new ParkingFloor(1);
        floor1.addSpot(new ParkingSpot("1-C1", SpotType.COMPACT, 1));
        floor1.addSpot(new ParkingSpot("1-C2", SpotType.COMPACT, 1));
        floor1.addSpot(new ParkingSpot("1-R1", SpotType.REGULAR, 1));
        floor1.addSpot(new ParkingSpot("1-R2", SpotType.REGULAR, 1));
        floor1.addSpot(new ParkingSpot("1-L1", SpotType.LARGE, 1));

        ParkingFloor floor2 = new ParkingFloor(2);
        floor2.addSpot(new ParkingSpot("2-R1", SpotType.REGULAR, 2));
        floor2.addSpot(new ParkingSpot("2-R2", SpotType.REGULAR, 2));
        floor2.addSpot(new ParkingSpot("2-L1", SpotType.LARGE, 2));

        lot.addFloor(floor1);
        lot.addFloor(floor2);

        System.out.println(lot.getStatus());

        // ── Scenario 1: Park various vehicles ──
        System.out.println("━━━ Scenario 1: Park vehicles ━━━");
        ParkingTicket t1 = lot.parkVehicle(new Motorcycle("MOTO-001"));
        ParkingTicket t2 = lot.parkVehicle(new Car("CAR-001"));
        ParkingTicket t3 = lot.parkVehicle(new Car("CAR-002"));
        ParkingTicket t4 = lot.parkVehicle(new Truck("TRUCK-001"));
        System.out.println();
        System.out.println(lot.getStatus());

        // ── Scenario 2: Unpark with fee calculation ──
        System.out.println("━━━ Scenario 2: Unpark with fees ━━━");
        if (t1 != null) lot.unparkVehicle(t1.getTicketId());
        if (t2 != null) lot.unparkVehicle(t2.getTicketId());
        System.out.println();
        System.out.println(lot.getStatus());

        // ── Scenario 3: Switch pricing strategy ──
        System.out.println("━━━ Scenario 3: Switch to Flat pricing ━━━");
        lot.setPricingStrategy(new FlatPricingStrategy(5.0));
        ParkingTicket t5 = lot.parkVehicle(new Car("CAR-003"));
        if (t5 != null) lot.unparkVehicle(t5.getTicketId());
        System.out.println();

        // ── Scenario 4: Duplicate parking attempt ──
        System.out.println("━━━ Scenario 4: Edge cases ━━━");
        lot.parkVehicle(new Car("CAR-002")); // already parked!
        lot.unparkVehicle("T-999");          // invalid ticket
        System.out.println();

        // ── Scenario 5: Multi-threaded concurrent parking ──
        System.out.println("━━━ Scenario 5: Concurrent parking (10 threads) ━━━");
        // Reset for clean test
        ParkingLot.resetInstance();
        ParkingLot lot2 = ParkingLot.getInstance("Thread Test Garage", new HourlyPricingStrategy());

        ParkingFloor testFloor = new ParkingFloor(1);
        for (int i = 1; i <= 5; i++) {
            testFloor.addSpot(new ParkingSpot("S" + i, SpotType.REGULAR, 1));
        }
        lot2.addFloor(testFloor);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger parked = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        List<String> ticketIds = Collections.synchronizedList(new ArrayList<>());

        // 10 cars try to park in 5 spots
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    ParkingTicket t = lot2.parkVehicle(new Car("THREAD-" + idx));
                    if (t != null) {
                        parked.incrementAndGet();
                        ticketIds.add(t.getTicketId());
                    } else {
                        rejected.incrementAndGet();
                    }
                } finally { latch.countDown(); }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("\n  Results: Parked=" + parked.get() + " | Rejected=" + rejected.get());
        System.out.println("  Expected: Parked=5, Rejected=5 → "
            + (parked.get() == 5 ? "✅ CORRECT" : "⚠️ CHECK"));
        System.out.println(lot2.getStatus());

        // Unpark all
        System.out.println("  Unparking all:");
        for (String tid : ticketIds) {
            lot2.unparkVehicle(tid);
        }
        System.out.println(lot2.getStatus());

        System.out.println("✅ All parking lot scenarios complete.");
    }
}
