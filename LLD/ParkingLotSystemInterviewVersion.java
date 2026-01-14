import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Parking Lot System
 * Time to complete: 45-60 minutes
 * Focus: Core parking/unparking with polymorphism
 */

// ==================== Enums ====================
enum VehicleType {
    CAR, MOTORCYCLE, TRUCK
}

enum SpotType {
    COMPACT, REGULAR, LARGE
}

// ==================== Vehicle Classes ====================
abstract class Vehicle {
    protected String licensePlate;
    protected VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public abstract SpotType getRequiredSpotType();

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }

    @Override
    public String toString() {
        return type + "(" + licensePlate + ")";
    }
}

class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }

    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.REGULAR;
    }
}

class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) {
        super(licensePlate, VehicleType.MOTORCYCLE);
    }

    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.COMPACT;
    }
}

class Truck extends Vehicle {
    public Truck(String licensePlate) {
        super(licensePlate, VehicleType.TRUCK);
    }

    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.LARGE;
    }
}

// ==================== Parking Spot ====================
class ParkingSpot {
    private final String spotId;
    private final SpotType type;
    private boolean occupied;
    private Vehicle currentVehicle;

    public ParkingSpot(String spotId, SpotType type) {
        this.spotId = spotId;
        this.type = type;
        this.occupied = false;
    }

    public boolean canFitVehicle(Vehicle vehicle) {
        return !occupied && type.ordinal() >= vehicle.getRequiredSpotType().ordinal();
    }

    public boolean parkVehicle(Vehicle vehicle) {
        if (!canFitVehicle(vehicle)) {
            return false;
        }
        this.occupied = true;
        this.currentVehicle = vehicle;
        return true;
    }

    public void removeVehicle() {
        this.occupied = false;
        this.currentVehicle = null;
    }

    public String getSpotId() { return spotId; }
    public SpotType getType() { return type; }
    public boolean isOccupied() { return occupied; }

    @Override
    public String toString() {
        return spotId + "(" + type + ")" + (occupied ? "[X]" : "[ ]");
    }
}

// ==================== Parking Ticket ====================
class ParkingTicket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;

    public ParkingTicket(String ticketId, Vehicle vehicle, ParkingSpot spot) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = LocalDateTime.now();
    }

    public double calculateFee(double hourlyRate) {
        this.exitTime = LocalDateTime.now();
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        long hours = (minutes + 59) / 60;  // Round up
        this.fee = hours * hourlyRate;
        return fee;
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }

    @Override
    public String toString() {
        return "Ticket[" + ticketId + ", " + vehicle + ", " + spot.getSpotId() + "]";
    }
}

// ==================== Parking Lot (Singleton) ====================
class ParkingLot {
    private static ParkingLot instance;
    private final List<ParkingSpot> spots;
    private final Map<String, ParkingTicket> activeTickets;
    private int ticketCounter;

    private ParkingLot() {
        this.spots = new ArrayList<>();
        this.activeTickets = new HashMap<>();
        this.ticketCounter = 1;
    }

    public static ParkingLot getInstance() {
        if (instance == null) {
            instance = new ParkingLot();
        }
        return instance;
    }

    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
    }

    public ParkingTicket parkVehicle(Vehicle vehicle) {
        SpotType required = vehicle.getRequiredSpotType();

        // Find available spot
        for (ParkingSpot spot : spots) {
            if (spot.canFitVehicle(vehicle)) {
                spot.parkVehicle(vehicle);
                
                String ticketId = "T" + ticketCounter++;
                ParkingTicket ticket = new ParkingTicket(ticketId, vehicle, spot);
                activeTickets.put(ticketId, ticket);
                
                System.out.println("✓ Parked: " + vehicle + " at " + spot.getSpotId());
                return ticket;
            }
        }

        System.out.println("✗ No space for: " + vehicle);
        return null;
    }

    public double unparkVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            System.out.println("✗ Invalid ticket: " + ticketId);
            return 0;
        }

        ticket.getSpot().removeVehicle();
        double hourlyRate = 10.0;  // Simple flat rate
        double fee = ticket.calculateFee(hourlyRate);

        System.out.println("✓ Unparked: " + ticket.getVehicle() + 
                         " from " + ticket.getSpot().getSpotId() + 
                         " Fee: $" + fee);
        return fee;
    }

    public void displayStatus() {
        System.out.println("\n=== Parking Lot Status ===");
        System.out.println("Total spots: " + spots.size());
        System.out.println("Occupied: " + activeTickets.size());
        System.out.println("Available: " + (spots.size() - activeTickets.size()));
        
        System.out.println("\nSpots:");
        for (ParkingSpot spot : spots) {
            System.out.println("  " + spot);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class ParkingLotSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Parking Lot Demo ===\n");

        ParkingLot lot = ParkingLot.getInstance();

        // Add spots
        lot.addSpot(new ParkingSpot("C1", SpotType.COMPACT));
        lot.addSpot(new ParkingSpot("C2", SpotType.COMPACT));
        lot.addSpot(new ParkingSpot("R1", SpotType.REGULAR));
        lot.addSpot(new ParkingSpot("R2", SpotType.REGULAR));
        lot.addSpot(new ParkingSpot("L1", SpotType.LARGE));

        lot.displayStatus();

        // Park vehicles
        System.out.println("--- Parking Vehicles ---");
        ParkingTicket t1 = lot.parkVehicle(new Motorcycle("BIKE-001"));
        ParkingTicket t2 = lot.parkVehicle(new Car("CAR-001"));
        ParkingTicket t3 = lot.parkVehicle(new Car("CAR-002"));
        ParkingTicket t4 = lot.parkVehicle(new Truck("TRUCK-001"));

        lot.displayStatus();

        // Simulate parking time
        System.out.println("[Simulating 2 hours...]");
        Thread.sleep(100);

        // Unpark vehicles
        System.out.println("\n--- Unparking Vehicles ---");
        lot.unparkVehicle(t1.getTicketId());
        lot.unparkVehicle(t2.getTicketId());

        lot.displayStatus();

        System.out.println("✅ Demo complete!");
    }
}
