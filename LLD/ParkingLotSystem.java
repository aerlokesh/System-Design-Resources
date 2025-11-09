import java.util.*;
import java.time.*;

// ==================== Enums ====================
// Why Enums? Type safety, prevents invalid values, self-documenting code
// Better than magic strings/numbers that could have typos or invalid values

enum VehicleType {
    MOTORCYCLE,    // 2-wheeler
    CAR,          // 4-wheeler standard
    ELECTRIC_CAR, // Electric vehicle (may need charging spots)
    VAN,          // Larger than car
    TRUCK         // Largest vehicle
    // Easy to extend: add BUS, BICYCLE, etc. without changing existing code
}

enum ParkingSpotType {
    MOTORCYCLE,   // Small spot for motorcycles
    COMPACT,      // For small cars
    LARGE,        // For large vehicles (vans, trucks)
    HANDICAPPED,  // Accessible spots near entrance
    ELECTRIC      // Spots with charging stations
    // Enum ensures only valid spot types, compile-time checking
}

enum ParkingSpotStatus {
    AVAILABLE,    // Free to use
    OCCUPIED,     // Currently in use
    RESERVED,     // Booked but not yet occupied
    OUT_OF_SERVICE // Maintenance/blocked
}

enum PaymentStatus {
    PENDING,      // Not yet paid
    PAID,         // Payment successful
    FAILED,       // Payment declined/failed
    REFUNDED      // Payment returned
}

enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    UPI,          // Unified Payments Interface
    MOBILE_WALLET
}

// ==================== Vehicle Classes ====================
// Why Abstract Class? Vehicles share common properties (license plate, type, entry time)
// but may have different behavior (size requirements, parking spot eligibility)
// Abstract class provides: 1) Code reuse 2) Enforced structure 3) Polymorphism

abstract class Vehicle {
    protected String licensePlate;  // Unique identifier
    protected VehicleType type;
    protected LocalDateTime entryTime;

    // Constructor ensures all vehicles have required information
    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
        this.entryTime = LocalDateTime.now();  // Auto-capture entry time
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
    public LocalDateTime getEntryTime() { return entryTime; }

    // Template method - each vehicle type can specify allowed spot types
    // Polymorphism: Different behavior based on actual vehicle type
    public abstract List<ParkingSpotType> getAllowedSpotTypes();

    @Override
    public String toString() {
        return type + " (" + licensePlate + ")";
    }
}

// Concrete vehicle implementations
// Why separate classes? Single Responsibility - each handles its specific logic
// Open/Closed Principle - can add new vehicle types without modifying existing

class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE);
    }

    @Override
    public List<ParkingSpotType> getAllowedSpotTypes() {
        // Motorcycles can only park in motorcycle spots
        return Arrays.asList(ParkingSpotType.MOTORCYCLE);
    }
}

class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }

    @Override
    public List<ParkingSpotType> getAllowedSpotTypes() {
        // Cars can park in compact, large, or handicapped spots
        return Arrays.asList(
            ParkingSpotType.COMPACT,
            ParkingSpotType.LARGE,
            ParkingSpotType.HANDICAPPED
        );
    }
}

class ElectricCar extends Vehicle {
    public ElectricCar(String licensePlate) {
        super(licensePlate, VehicleType.ELECTRIC_CAR);
    }

    @Override
    public List<ParkingSpotType> getAllowedSpotTypes() {
        // Electric cars prefer electric spots but can use regular spots
        return Arrays.asList(
            ParkingSpotType.ELECTRIC,
            ParkingSpotType.COMPACT,
            ParkingSpotType.LARGE
        );
    }
}

class Van extends Vehicle {
    public Van(String licensePlate) {
        super(licensePlate, VehicleType.VAN);
    }

    @Override
    public List<ParkingSpotType> getAllowedSpotTypes() {
        // Vans need large spots only
        return Arrays.asList(ParkingSpotType.LARGE);
    }
}

class Truck extends Vehicle {
    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK);
    }

    @Override
    public List<ParkingSpotType> getAllowedSpotTypes() {
        // Trucks need large spots only
        return Arrays.asList(ParkingSpotType.LARGE);
    }
}

// ==================== Parking Spot ====================
// Represents a single parking space with type, status, and occupant

class ParkingSpot {
    private String spotId;              // Unique identifier (e.g., "L1-A-001")
    private ParkingSpotType type;
    private ParkingSpotStatus status;
    private Vehicle currentVehicle;     // null if available
    private int floor;                  // Which floor/level
    private String section;             // Section identifier (A, B, C, etc.)

    public ParkingSpot(String spotId, ParkingSpotType type, int floor, String section) {
        this.spotId = spotId;
        this.type = type;
        this.status = ParkingSpotStatus.AVAILABLE;
        this.currentVehicle = null;
        this.floor = floor;
        this.section = section;
    }

    // Getters
    public String getSpotId() { return spotId; }
    public ParkingSpotType getType() { return type; }
    public ParkingSpotStatus getStatus() { return status; }
    public Vehicle getCurrentVehicle() { return currentVehicle; }
    public int getFloor() { return floor; }
    public String getSection() { return section; }

    // Check if spot can accommodate vehicle type
    public boolean isAvailableFor(Vehicle vehicle) {
        // Must be available status AND vehicle type must match spot type
        return status == ParkingSpotStatus.AVAILABLE && 
               vehicle.getAllowedSpotTypes().contains(type);
    }

    // Occupy spot with vehicle
    public synchronized boolean assignVehicle(Vehicle vehicle) {
        if (isAvailableFor(vehicle)) {
            this.currentVehicle = vehicle;
            this.status = ParkingSpotStatus.OCCUPIED;
            return true;
        }
        return false;
    }

    // Free up spot
    public synchronized void removeVehicle() {
        this.currentVehicle = null;
        this.status = ParkingSpotStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return String.format("Spot[%s, Type=%s, Floor=%d, Section=%s, Status=%s]",
            spotId, type, floor, section, status);
    }
}

// ==================== Parking Ticket ====================
// Represents a parking session for a vehicle

class ParkingTicket {
    private String ticketId;
    private Vehicle vehicle;
    private ParkingSpot assignedSpot;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double charges;
    private PaymentStatus paymentStatus;

    public ParkingTicket(String ticketId, Vehicle vehicle, ParkingSpot spot) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.assignedSpot = spot;
        this.entryTime = LocalDateTime.now();
        this.exitTime = null;
        this.charges = 0.0;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    // Getters
    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getAssignedSpot() { return assignedSpot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public double getCharges() { return charges; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }

    // Calculate parking duration in minutes
    public long getParkingDurationMinutes() {
        LocalDateTime end = exitTime != null ? exitTime : LocalDateTime.now();
        return Duration.between(entryTime, end).toMinutes();
    }

    // Setters for exit process
    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public void setCharges(double charges) {
        this.charges = charges;
    }

    public void setPaymentStatus(PaymentStatus status) {
        this.paymentStatus = status;
    }

    @Override
    public String toString() {
        return String.format("Ticket[%s, Vehicle=%s, Spot=%s, Entry=%s, Charges=$%.2f]",
            ticketId, vehicle.getLicensePlate(), assignedSpot.getSpotId(),
            entryTime, charges);
    }
}

// ==================== Parking Strategy Interface ====================
// Why Strategy Pattern? Different parking spot selection algorithms
// Benefits: 1) Easy to swap strategies 2) Open/Closed Principle
// 3) Testable in isolation 4) Can add new strategies without changing existing code

interface ParkingStrategy {
    ParkingSpot findSpot(Vehicle vehicle, List<ParkingSpot> availableSpots);
}

// Strategy 1: Find nearest available spot (closest to entrance)
class NearestSpotStrategy implements ParkingStrategy {
    @Override
    public ParkingSpot findSpot(Vehicle vehicle, List<ParkingSpot> availableSpots) {
        // Filter spots that can accommodate this vehicle
        List<ParkingSpot> suitableSpots = new ArrayList<>();
        for (ParkingSpot spot : availableSpots) {
            if (spot.isAvailableFor(vehicle)) {
                suitableSpots.add(spot);
            }
        }

        if (suitableSpots.isEmpty()) {
            return null;
        }

        // Nearest = lowest floor, then earliest section
        suitableSpots.sort((s1, s2) -> {
            int floorCompare = Integer.compare(s1.getFloor(), s2.getFloor());
            if (floorCompare != 0) return floorCompare;
            return s1.getSection().compareTo(s2.getSection());
        });

        return suitableSpots.get(0);
    }
}

// Strategy 2: Random spot selection (load balancing across floors)
class RandomSpotStrategy implements ParkingStrategy {
    private Random random = new Random();

    @Override
    public ParkingSpot findSpot(Vehicle vehicle, List<ParkingSpot> availableSpots) {
        List<ParkingSpot> suitableSpots = new ArrayList<>();
        for (ParkingSpot spot : availableSpots) {
            if (spot.isAvailableFor(vehicle)) {
                suitableSpots.add(spot);
            }
        }

        if (suitableSpots.isEmpty()) {
            return null;
        }

        // Random selection helps distribute vehicles evenly
        return suitableSpots.get(random.nextInt(suitableSpots.size()));
    }
}

// Strategy 3: Fill floor by floor (optimize search in specific floors)
class FloorByFloorStrategy implements ParkingStrategy {
    @Override
    public ParkingSpot findSpot(Vehicle vehicle, List<ParkingSpot> availableSpots) {
        // Group by floor and fill lower floors first
        Map<Integer, List<ParkingSpot>> floorMap = new HashMap<>();
        for (ParkingSpot spot : availableSpots) {
            if (spot.isAvailableFor(vehicle)) {
                floorMap.computeIfAbsent(spot.getFloor(), k -> new ArrayList<>()).add(spot);
            }
        }

        if (floorMap.isEmpty()) {
            return null;
        }

        // Get lowest floor with available spots
        int lowestFloor = Collections.min(floorMap.keySet());
        List<ParkingSpot> floorSpots = floorMap.get(lowestFloor);
        return floorSpots.get(0);
    }
}

// ==================== Pricing Strategy ====================
// Why separate pricing? Single Responsibility + Easy to modify rates
// Can implement hourly, daily, flat rate, surge pricing, etc.

interface PricingStrategy {
    double calculateCharges(ParkingTicket ticket);
}

// Hourly pricing implementation
class HourlyPricingStrategy implements PricingStrategy {
    private Map<VehicleType, Double> hourlyRates;

    public HourlyPricingStrategy() {
        this.hourlyRates = new HashMap<>();
        // Different rates for different vehicle types
        hourlyRates.put(VehicleType.MOTORCYCLE, 1.0);
        hourlyRates.put(VehicleType.CAR, 2.0);
        hourlyRates.put(VehicleType.ELECTRIC_CAR, 1.5);  // Discounted rate
        hourlyRates.put(VehicleType.VAN, 3.0);
        hourlyRates.put(VehicleType.TRUCK, 4.0);
    }

    @Override
    public double calculateCharges(ParkingTicket ticket) {
        long minutes = ticket.getParkingDurationMinutes();
        double hours = Math.ceil(minutes / 60.0);  // Round up to nearest hour
        double hourlyRate = hourlyRates.get(ticket.getVehicle().getType());
        return hours * hourlyRate;
    }
}

// ==================== Payment Processing ====================
// Abstract payment processing - can add multiple payment methods

class Payment {
    private String paymentId;
    private double amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private LocalDateTime paymentTime;

    public Payment(String paymentId, double amount, PaymentMethod method) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
        this.paymentTime = null;
    }

    // Simulate payment processing
    public boolean processPayment() {
        // In real system: integrate with payment gateway
        // For demo: assume payment succeeds
        this.status = PaymentStatus.PAID;
        this.paymentTime = LocalDateTime.now();
        return true;
    }

    public String getPaymentId() { return paymentId; }
    public double getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getPaymentTime() { return paymentTime; }
}

// ==================== Display Board ====================
// Shows available spots per floor/type (Observer pattern could extend this)

class DisplayBoard {
    private String boardId;
    private int floor;

    public DisplayBoard(String boardId, int floor) {
        this.boardId = boardId;
        this.floor = floor;
    }

    public void showAvailableSpots(Map<ParkingSpotType, Integer> availableSpots) {
        System.out.println("\n===== Display Board - Floor " + floor + " =====");
        for (Map.Entry<ParkingSpotType, Integer> entry : availableSpots.entrySet()) {
            System.out.println(entry.getKey() + " spots: " + entry.getValue());
        }
        System.out.println("=====================================\n");
    }
}

// ==================== Parking Floor ====================
// Represents one floor/level of the parking lot

class ParkingFloor {
    private int floorNumber;
    private List<ParkingSpot> spots;
    private DisplayBoard displayBoard;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.displayBoard = new DisplayBoard("DB-Floor-" + floorNumber, floorNumber);
    }

    public int getFloorNumber() { return floorNumber; }
    public List<ParkingSpot> getSpots() { return spots; }

    // Add parking spot to this floor
    public void addParkingSpot(ParkingSpot spot) {
        spots.add(spot);
    }

    // Get available spots by type
    public Map<ParkingSpotType, Integer> getAvailableSpotsByType() {
        Map<ParkingSpotType, Integer> available = new HashMap<>();
        for (ParkingSpotType type : ParkingSpotType.values()) {
            available.put(type, 0);
        }

        for (ParkingSpot spot : spots) {
            if (spot.getStatus() == ParkingSpotStatus.AVAILABLE) {
                available.put(spot.getType(), available.get(spot.getType()) + 1);
            }
        }

        return available;
    }

    // Update display board
    public void updateDisplayBoard() {
        displayBoard.showAvailableSpots(getAvailableSpotsByType());
    }
}

// ==================== Entry/Exit Gates ====================
// Manages vehicle entry and exit

class EntranceGate {
    private String gateId;

    public EntranceGate(String gateId) {
        this.gateId = gateId;
    }

    public String getGateId() { return gateId; }

    public void openGate() {
        System.out.println("Entrance Gate " + gateId + " opened");
    }

    public void closeGate() {
        System.out.println("Entrance Gate " + gateId + " closed");
    }
}

class ExitGate {
    private String gateId;

    public ExitGate(String gateId) {
        this.gateId = gateId;
    }

    public String getGateId() { return gateId; }

    public void openGate() {
        System.out.println("Exit Gate " + gateId + " opened");
    }

    public void closeGate() {
        System.out.println("Exit Gate " + gateId + " closed");
    }
}

// ==================== Parking Lot (Singleton) ====================
// Why Singleton? Only one parking lot instance should manage the entire system
// Central coordination point for all operations

class ParkingLot {
    private static ParkingLot instance;
    private String name;
    private String address;
    private List<ParkingFloor> floors;
    private List<EntranceGate> entranceGates;
    private List<ExitGate> exitGates;
    private Map<String, ParkingTicket> activeTickets;  // ticketId -> ticket
    private ParkingStrategy parkingStrategy;
    private PricingStrategy pricingStrategy;
    private int ticketCounter;

    // Private constructor - Singleton pattern
    private ParkingLot(String name, String address) {
        this.name = name;
        this.address = address;
        this.floors = new ArrayList<>();
        this.entranceGates = new ArrayList<>();
        this.exitGates = new ArrayList<>();
        this.activeTickets = new HashMap<>();
        this.parkingStrategy = new NearestSpotStrategy();  // Default strategy
        this.pricingStrategy = new HourlyPricingStrategy(); // Default pricing
        this.ticketCounter = 0;
    }

    // Thread-safe singleton getter
    public static synchronized ParkingLot getInstance(String name, String address) {
        if (instance == null) {
            instance = new ParkingLot(name, address);
        }
        return instance;
    }

    // Add infrastructure
    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public void addEntranceGate(EntranceGate gate) {
        entranceGates.add(gate);
    }

    public void addExitGate(ExitGate gate) {
        exitGates.add(gate);
    }

    // Set strategies (Strategy Pattern - runtime flexibility)
    public void setParkingStrategy(ParkingStrategy strategy) {
        this.parkingStrategy = strategy;
    }

    public void setPricingStrategy(PricingStrategy strategy) {
        this.pricingStrategy = strategy;
    }

    // Get all available spots across all floors
    private List<ParkingSpot> getAllAvailableSpots() {
        List<ParkingSpot> available = new ArrayList<>();
        for (ParkingFloor floor : floors) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getStatus() == ParkingSpotStatus.AVAILABLE) {
                    available.add(spot);
                }
            }
        }
        return available;
    }

    // Vehicle entry process
    public synchronized ParkingTicket parkVehicle(Vehicle vehicle, EntranceGate gate) {
        System.out.println("\n--- Vehicle Entry ---");
        System.out.println("Vehicle: " + vehicle);
        System.out.println("Entrance: " + gate.getGateId());

        // Find suitable parking spot using current strategy
        List<ParkingSpot> availableSpots = getAllAvailableSpots();
        ParkingSpot spot = parkingStrategy.findSpot(vehicle, availableSpots);

        if (spot == null) {
            System.out.println("ERROR: No available parking spot for " + vehicle.getType());
            return null;
        }

        // Assign vehicle to spot
        if (!spot.assignVehicle(vehicle)) {
            System.out.println("ERROR: Failed to assign vehicle to spot");
            return null;
        }

        // Generate ticket
        String ticketId = "TICKET-" + (++ticketCounter);
        ParkingTicket ticket = new ParkingTicket(ticketId, vehicle, spot);
        activeTickets.put(ticketId, ticket);

        // Open gate
        gate.openGate();
        System.out.println("Assigned: " + spot);
        System.out.println("Ticket: " + ticketId);
        gate.closeGate();

        return ticket;
    }

    // Vehicle exit process
    public synchronized Payment unparkVehicle(String ticketId, PaymentMethod method, ExitGate gate) {
        System.out.println("\n--- Vehicle Exit ---");
        System.out.println("Ticket: " + ticketId);

        ParkingTicket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            System.out.println("ERROR: Invalid ticket ID");
            return null;
        }

        // Set exit time and calculate charges
        ticket.setExitTime(LocalDateTime.now());
        double charges = pricingStrategy.calculateCharges(ticket);
        ticket.setCharges(charges);

        System.out.println("Duration: " + ticket.getParkingDurationMinutes() + " minutes");
        System.out.println("Charges: $" + String.format("%.2f", charges));

        // Process payment
        String paymentId = "PAY-" + System.currentTimeMillis();
        Payment payment = new Payment(paymentId, charges, method);

        if (payment.processPayment()) {
            ticket.setPaymentStatus(PaymentStatus.PAID);
            ticket.getAssignedSpot().removeVehicle();
            activeTickets.remove(ticketId);

            gate.openGate();
            System.out.println("Payment successful!");
            System.out.println("Spot freed: " + ticket.getAssignedSpot().getSpotId());
            gate.closeGate();

            return payment;
        } else {
            System.out.println("ERROR: Payment failed");
            return null;
        }
    }

    // Display status of all floors
    public void displayStatus() {
        System.out.println("\n========== Parking Lot Status ==========");
        System.out.println("Name: " + name);
        System.out.println("Address: " + address);
        System.out.println("Total Floors: " + floors.size());
        System.out.println("Active Tickets: " + activeTickets.size());

        for (ParkingFloor floor : floors) {
            floor.updateDisplayBoard();
        }
    }

    // Get occupancy statistics
    public void showOccupancyReport() {
        int totalSpots = 0;
        int occupiedSpots = 0;

        for (ParkingFloor floor : floors) {
            totalSpots += floor.getSpots().size();
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getStatus() == ParkingSpotStatus.OCCUPIED) {
                    occupiedSpots++;
                }
            }
        }

        double occupancyRate = totalSpots > 0 ? (occupiedSpots * 100.0 / totalSpots) : 0;

        System.out.println("\n===== Occupancy Report =====");
        System.out.println("Total Spots: " + totalSpots);
        System.out.println("Occupied: " + occupiedSpots);
        System.out.println("Available: " + (totalSpots - occupiedSpots));
        System.out.println("Occupancy Rate: " + String.format("%.1f%%", occupancyRate));
        System.out.println("============================\n");
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete parking lot operations

public class ParkingLotSystem {
    public static void main(String[] args) {
        // Initialize Parking Lot (Singleton)
        ParkingLot parkingLot = ParkingLot.getInstance("Downtown Parking", "123 Main Street");

        // Create 3 floors
        ParkingFloor floor1 = new ParkingFloor(1);
        ParkingFloor floor2 = new ParkingFloor(2);
        ParkingFloor floor3 = new ParkingFloor(3);

        // Add parking spots to Floor 1 (mixed types)
        floor1.addParkingSpot(new ParkingSpot("L1-A-001", ParkingSpotType.MOTORCYCLE, 1, "A"));
        floor1.addParkingSpot(new ParkingSpot("L1-A-002", ParkingSpotType.MOTORCYCLE, 1, "A"));
        floor1.addParkingSpot(new ParkingSpot("L1-B-001", ParkingSpotType.COMPACT, 1, "B"));
        floor1.addParkingSpot(new ParkingSpot("L1-B-002", ParkingSpotType.COMPACT, 1, "B"));
        floor1.addParkingSpot(new ParkingSpot("L1-C-001", ParkingSpotType.LARGE, 1, "C"));
        floor1.addParkingSpot(new ParkingSpot("L1-D-001", ParkingSpotType.ELECTRIC, 1, "D"));

        // Add parking spots to Floor 2
        floor2.addParkingSpot(new ParkingSpot("L2-A-001", ParkingSpotType.COMPACT, 2, "A"));
        floor2.addParkingSpot(new ParkingSpot("L2-A-002", ParkingSpotType.COMPACT, 2, "A"));
        floor2.addParkingSpot(new ParkingSpot("L2-B-001", ParkingSpotType.LARGE, 2, "B"));
        floor2.addParkingSpot(new ParkingSpot("L2-C-001", ParkingSpotType.HANDICAPPED, 2, "C"));

        // Add parking spots to Floor 3
        floor3.addParkingSpot(new ParkingSpot("L3-A-001", ParkingSpotType.LARGE, 3, "A"));
        floor3.addParkingSpot(new ParkingSpot("L3-A-002", ParkingSpotType.LARGE, 3, "A"));

        // Add floors to parking lot
        parkingLot.addFloor(floor1);
        parkingLot.addFloor(floor2);
        parkingLot.addFloor(floor3);

        // Add gates
        parkingLot.addEntranceGate(new EntranceGate("ENTRANCE-1"));
        parkingLot.addEntranceGate(new EntranceGate("ENTRANCE-2"));
        parkingLot.addExitGate(new ExitGate("EXIT-1"));
        parkingLot.addExitGate(new ExitGate("EXIT-2"));

        System.out.println("========== Parking Lot System Demo ==========\n");

        // Initial status
        parkingLot.displayStatus();

        // ===== Simulate vehicle parking =====

        // 1. Park a motorcycle
        Vehicle motorcycle1 = new Motorcycle("BIKE-001");
        ParkingTicket ticket1 = parkingLot.parkVehicle(
            motorcycle1,
            new EntranceGate("ENTRANCE-1")
        );

        // 2. Park a car
        Vehicle car1 = new Car("CAR-001");
        ParkingTicket ticket2 = parkingLot.parkVehicle(
            car1,
            new EntranceGate("ENTRANCE-1")
        );

        // 3. Park an electric car
        Vehicle electricCar1 = new ElectricCar("ECAR-001");
        ParkingTicket ticket3 = parkingLot.parkVehicle(
            electricCar1,
            new EntranceGate("ENTRANCE-2")
        );

        // 4. Park a truck
        Vehicle truck1 = new Truck("TRUCK-001");
        ParkingTicket ticket4 = parkingLot.parkVehicle(
            truck1,
            new EntranceGate("ENTRANCE-2")
        );

        // Display status after parking
        parkingLot.displayStatus();
        parkingLot.showOccupancyReport();

        // ===== Simulate waiting time (for realistic charges) =====
        System.out.println("\n[Simulating 2 hours of parking...]");
        // In real scenario, vehicles would stay parked

        // ===== Simulate vehicle exit =====

        // 1. Motorcycle exits
        Payment payment1 = parkingLot.unparkVehicle(
            ticket1.getTicketId(),
            PaymentMethod.CASH,
            new ExitGate("EXIT-1")
        );

        // 2. Car exits
        Payment payment2 = parkingLot.unparkVehicle(
            ticket2.getTicketId(),
            PaymentMethod.CREDIT_CARD,
            new ExitGate("EXIT-1")
        );

        // Display final status
        parkingLot.displayStatus();
        parkingLot.showOccupancyReport();

        System.out.println("\n========== Demo Complete ==========");
    }
}
