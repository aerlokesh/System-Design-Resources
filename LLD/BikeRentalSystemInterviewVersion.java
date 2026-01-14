import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Bike Rental System
 * Time to complete: 40-50 minutes
 * Focus: State pattern, rental management
 */

// ==================== Bike Status ====================
enum BikeStatus {
    AVAILABLE, RENTED, MAINTENANCE
}

enum BikeType {
    REGULAR, ELECTRIC, MOUNTAIN
}

// ==================== Bike ====================
class Bike {
    private final String bikeId;
    private final BikeType type;
    private BikeStatus status;
    private final double hourlyRate;

    public Bike(String bikeId, BikeType type, double hourlyRate) {
        this.bikeId = bikeId;
        this.type = type;
        this.status = BikeStatus.AVAILABLE;
        this.hourlyRate = hourlyRate;
    }

    public boolean isAvailable() {
        return status == BikeStatus.AVAILABLE;
    }

    public void rent() {
        status = BikeStatus.RENTED;
    }

    public void returnBike() {
        status = BikeStatus.AVAILABLE;
    }

    public String getBikeId() { return bikeId; }
    public BikeType getType() { return type; }
    public BikeStatus getStatus() { return status; }
    public double getHourlyRate() { return hourlyRate; }

    @Override
    public String toString() {
        return bikeId + " (" + type + ") - $" + hourlyRate + "/hr [" + status + "]";
    }
}

// ==================== Rental ====================
class Rental {
    private final String rentalId;
    private final Bike bike;
    private final String userId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private double totalCost;

    public Rental(String rentalId, Bike bike, String userId) {
        this.rentalId = rentalId;
        this.bike = bike;
        this.userId = userId;
        this.startTime = LocalDateTime.now();
    }

    public double complete() {
        this.endTime = LocalDateTime.now();
        long minutes = Duration.between(startTime, endTime).toMinutes();
        double hours = Math.ceil(minutes / 60.0);
        this.totalCost = hours * bike.getHourlyRate();
        return totalCost;
    }

    public String getRentalId() { return rentalId; }
    public Bike getBike() { return bike; }
    public String getUserId() { return userId; }

    @Override
    public String toString() {
        return "Rental[" + rentalId + ", Bike=" + bike.getBikeId() + 
               ", User=" + userId + ", Cost=$" + totalCost + "]";
    }
}

// ==================== Bike Rental Service ====================
class BikeRentalService {
    private final List<Bike> bikes;
    private final Map<String, Rental> activeRentals;
    private int rentalCounter;

    public BikeRentalService() {
        this.bikes = new ArrayList<>();
        this.activeRentals = new HashMap<>();
        this.rentalCounter = 1;
    }

    public void addBike(Bike bike) {
        bikes.add(bike);
        System.out.println("✓ Added bike: " + bike);
    }

    public List<Bike> getAvailableBikes(BikeType type) {
        List<Bike> available = new ArrayList<>();
        for (Bike bike : bikes) {
            if (bike.isAvailable() && (type == null || bike.getType() == type)) {
                available.add(bike);
            }
        }
        return available;
    }

    public Rental rentBike(String bikeId, String userId) {
        Bike bike = findBike(bikeId);
        
        if (bike == null) {
            System.out.println("✗ Bike not found: " + bikeId);
            return null;
        }

        if (!bike.isAvailable()) {
            System.out.println("✗ Bike not available: " + bikeId);
            return null;
        }

        bike.rent();
        String rentalId = "R" + rentalCounter++;
        Rental rental = new Rental(rentalId, bike, userId);
        activeRentals.put(rentalId, rental);

        System.out.println("✓ Rented: " + bike.getBikeId() + " to " + userId + " [" + rentalId + "]");
        return rental;
    }

    public double returnBike(String rentalId) {
        Rental rental = activeRentals.remove(rentalId);
        
        if (rental == null) {
            System.out.println("✗ Rental not found: " + rentalId);
            return 0;
        }

        rental.getBike().returnBike();
        double cost = rental.complete();

        System.out.println("✓ Returned: " + rental.getBike().getBikeId() + 
                         " Cost: $" + String.format("%.2f", cost));
        return cost;
    }

    private Bike findBike(String bikeId) {
        for (Bike bike : bikes) {
            if (bike.getBikeId().equals(bikeId)) {
                return bike;
            }
        }
        return null;
    }

    public void displayStatus() {
        System.out.println("\n=== Bike Rental Status ===");
        System.out.println("Total bikes: " + bikes.size());
        System.out.println("Active rentals: " + activeRentals.size());
        
        System.out.println("\nBikes:");
        for (Bike bike : bikes) {
            System.out.println("  " + bike);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class BikeRentalSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Bike Rental Demo ===\n");

        BikeRentalService service = new BikeRentalService();

        // Add bikes
        service.addBike(new Bike("B001", BikeType.REGULAR, 5.0));
        service.addBike(new Bike("B002", BikeType.ELECTRIC, 10.0));
        service.addBike(new Bike("B003", BikeType.MOUNTAIN, 8.0));
        service.addBike(new Bike("B004", BikeType.REGULAR, 5.0));

        service.displayStatus();

        // Rent bikes
        System.out.println("--- Renting Bikes ---");
        Rental r1 = service.rentBike("B001", "user1");
        Rental r2 = service.rentBike("B002", "user2");

        service.displayStatus();

        // Try to rent already rented bike
        service.rentBike("B001", "user3");

        // Simulate rental time
        System.out.println("\n[Simulating 2 hours of rental...]");
        Thread.sleep(100);

        // Return bikes
        System.out.println("\n--- Returning Bikes ---");
        service.returnBike(r1.getRentalId());
        service.returnBike(r2.getRentalId());

        service.displayStatus();

        System.out.println("✅ Demo complete!");
    }
}
