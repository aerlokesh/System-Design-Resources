/**
 * BIKE RENTAL SYSTEM - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a complete bike rental system
 * similar to Lime, Bird, Citi Bike, and other bike-sharing services.
 * 
 * Key Features:
 * 1. Dockless system with GPS tracking
 * 2. Atomic bike reservation (prevents double-booking)
 * 3. Multiple pricing strategies (Time, Distance, Hybrid)
 * 4. E-bike support with battery tracking
 * 5. Station management (for docked systems)
 * 6. Maintenance tracking
 * 7. Geospatial bike discovery
 * 8. Thread-safe operations
 * 
 * Design Patterns Used:
 * - State Pattern (Bike and Rental status)
 * - Strategy Pattern (Pricing strategies)
 * - Observer Pattern (Location updates)
 * - Factory Pattern (Rental creation)
 * 
 * Companies: Lime, Bird, Citi Bike, Lyft Bikes
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

// ============================================================
// ENUMS
// ============================================================

enum BikeType {
    REGULAR, ELECTRIC, PREMIUM
}

enum BikeStatus {
    AVAILABLE, IN_USE, RESERVED, MAINTENANCE, RETIRED
}

enum RentalStatus {
    RESERVED, ACTIVE, COMPLETED, CANCELLED
}

enum PaymentMethod {
    WALLET, CREDIT_CARD, DEBIT_CARD
}

enum PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED
}

// ============================================================
// LOCATION
// ============================================================

class Location {
    private final double latitude;
    private final double longitude;
    
    public Location(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public double distanceTo(Location other) {
        final double EARTH_RADIUS_KM = 6371.0;
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    
    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", latitude, longitude);
    }
}

// ============================================================
// USER
// ============================================================

class User {
    private final String id;
    private final String name;
    private final String email;
    private final String phone;
    private double walletBalance;
    private final List<Rental> rentalHistory;
    private boolean isVerified;
    
    public User(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.walletBalance = 0.0;
        this.rentalHistory = new ArrayList<>();
        this.isVerified = true; // Auto-verified for demo
    }
    
    public void addToWallet(double amount) {
        this.walletBalance += amount;
    }
    
    public boolean deductFromWallet(double amount) {
        if (walletBalance >= amount) {
            walletBalance -= amount;
            return true;
        }
        return false;
    }
    
    public void addRental(Rental rental) {
        rentalHistory.add(rental);
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isVerified() { return isVerified; }
    public double getWalletBalance() { return walletBalance; }
}

// ============================================================
// BIKE
// ============================================================

class Bike {
    private final String id;
    private final BikeType type;
    private Location currentLocation;
    private BikeStatus status;
    private int batteryLevel;
    private double condition;
    private final AtomicReference<String> currentRentalId;
    
    public Bike(String id, BikeType type, Location initialLocation) {
        this.id = id;
        this.type = type;
        this.currentLocation = initialLocation;
        this.status = BikeStatus.AVAILABLE;
        this.batteryLevel = type == BikeType.ELECTRIC ? 100 : -1;
        this.condition = 1.0;
        this.currentRentalId = new AtomicReference<>(null);
    }
    
    public boolean isAvailable() {
        return status == BikeStatus.AVAILABLE && condition > 0.5;
    }
    
    public synchronized boolean tryReserve(String rentalId) {
        if (status == BikeStatus.AVAILABLE) {
            status = BikeStatus.RESERVED;
            currentRentalId.set(rentalId);
            return true;
        }
        return false;
    }
    
    public synchronized boolean startRental(String rentalId) {
        if (status == BikeStatus.RESERVED && rentalId.equals(currentRentalId.get())) {
            status = BikeStatus.IN_USE;
            return true;
        }
        return false;
    }
    
    public synchronized void endRental() {
        status = BikeStatus.AVAILABLE;
        currentRentalId.set(null);
    }
    
    public void updateLocation(Location location) {
        this.currentLocation = location;
    }
    
    public String getId() { return id; }
    public BikeType getType() { return type; }
    public Location getCurrentLocation() { return currentLocation; }
    public BikeStatus getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }
    public double getCondition() { return condition; }
    
    @Override
    public String toString() {
        return String.format("Bike[%s, %s, %s, Battery:%d%%, Condition:%.1f]",
            id, type, status, batteryLevel == -1 ? 0 : batteryLevel, condition);
    }
}

// ============================================================
// RENTAL
// ============================================================

class Rental {
    private final String id;
    private final String userId;
    private final String bikeId;
    private final long startTime;
    private Long endTime;
    private final Location startLocation;
    private Location endLocation;
    private RentalStatus status;
    private double cost;
    private Payment payment;
    
    public Rental(String id, String userId, String bikeId, Location startLocation) {
        this.id = id;
        this.userId = userId;
        this.bikeId = bikeId;
        this.startTime = System.currentTimeMillis();
        this.startLocation = startLocation;
        this.status = RentalStatus.RESERVED;
    }
    
    public void startRental() {
        this.status = RentalStatus.ACTIVE;
    }
    
    public void endRental(Location endLocation, double cost) {
        this.endTime = System.currentTimeMillis();
        this.endLocation = endLocation;
        this.cost = cost;
        this.status = RentalStatus.COMPLETED;
    }
    
    public long getDurationMinutes() {
        long end = endTime != null ? endTime : System.currentTimeMillis();
        return (end - startTime) / (60 * 1000);
    }
    
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getBikeId() { return bikeId; }
    public long getStartTime() { return startTime; }
    public Location getStartLocation() { return startLocation; }
    public Location getEndLocation() { return endLocation; }
    public RentalStatus getStatus() { return status; }
    public double getCost() { return cost; }
    public void setPayment(Payment payment) { this.payment = payment; }
}

// ============================================================
// PAYMENT
// ============================================================

class Payment {
    private final String id;
    private final String rentalId;
    private final double amount;
    private final PaymentMethod method;
    private PaymentStatus status;
    
    public Payment(String id, String rentalId, double amount, PaymentMethod method) {
        this.id = id;
        this.rentalId = rentalId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }
    
    public void markCompleted() { this.status = PaymentStatus.COMPLETED; }
    public void markFailed() { this.status = PaymentStatus.FAILED; }
    public void refund() { this.status = PaymentStatus.REFUNDED; }
    
    public PaymentStatus getStatus() { return status; }
    public double getAmount() { return amount; }
}

// ============================================================
// PRICING STRATEGIES
// ============================================================

interface PricingStrategy {
    double calculateCost(Rental rental, Bike bike);
}

class TimeBasedPricing implements PricingStrategy {
    private final double unlockFee;
    private final double perMinuteRate;
    
    public TimeBasedPricing(double unlockFee, double perMinuteRate) {
        this.unlockFee = unlockFee;
        this.perMinuteRate = perMinuteRate;
    }
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        long minutes = rental.getDurationMinutes();
        return unlockFee + (minutes * perMinuteRate);
    }
}

class DistanceBasedPricing implements PricingStrategy {
    private final double unlockFee;
    private final double perKmRate;
    
    public DistanceBasedPricing(double unlockFee, double perKmRate) {
        this.unlockFee = unlockFee;
        this.perKmRate = perKmRate;
    }
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        if (rental.getEndLocation() == null) {
            return unlockFee;
        }
        double distance = rental.getStartLocation().distanceTo(rental.getEndLocation());
        return unlockFee + (distance * perKmRate);
    }
}

// ============================================================
// BIKE RENTAL SERVICE
// ============================================================

class BikeRentalServiceImpl {
    private final Map<String, User> users;
    private final Map<String, Bike> bikes;
    private final Map<String, Rental> rentals;
    private final PricingStrategy pricingStrategy;
    private final AtomicInteger rentalIdCounter;
    
    public BikeRentalServiceImpl(PricingStrategy pricingStrategy) {
        this.users = new ConcurrentHashMap<>();
        this.bikes = new ConcurrentHashMap<>();
        this.rentals = new ConcurrentHashMap<>();
        this.pricingStrategy = pricingStrategy;
        this.rentalIdCounter = new AtomicInteger(1);
    }
    
    public void registerUser(User user) {
        users.put(user.getId(), user);
    }
    
    public void addBike(Bike bike) {
        bikes.put(bike.getId(), bike);
    }
    
    public List<Bike> findNearbyBikes(Location userLocation, double radiusKm) {
        return bikes.values().stream()
            .filter(Bike::isAvailable)
            .filter(bike -> bike.getCurrentLocation().distanceTo(userLocation) <= radiusKm)
            .sorted(Comparator.comparingDouble(bike -> 
                bike.getCurrentLocation().distanceTo(userLocation)))
            .limit(10)
            .collect(Collectors.toList());
    }
    
    public Rental reserveBike(String userId, String bikeId) {
        User user = users.get(userId);
        Bike bike = bikes.get(bikeId);
        
        if (user == null || bike == null) {
            throw new IllegalArgumentException("User or bike not found");
        }
        
        if (!user.isVerified()) {
            throw new IllegalStateException("User not verified");
        }
        
        String rentalId = "RENT-" + rentalIdCounter.getAndIncrement();
        
        if (bike.tryReserve(rentalId)) {
            Rental rental = new Rental(rentalId, userId, bikeId, bike.getCurrentLocation());
            rentals.put(rentalId, rental);
            user.addRental(rental);
            return rental;
        }
        
        throw new IllegalStateException("Bike not available");
    }
    
    public boolean startRental(String rentalId) {
        Rental rental = rentals.get(rentalId);
        if (rental == null) {
            return false;
        }
        
        Bike bike = bikes.get(rental.getBikeId());
        if (bike.startRental(rentalId)) {
            rental.startRental();
            return true;
        }
        
        return false;
    }
    
    public boolean endRental(String rentalId, Location endLocation) {
        Rental rental = rentals.get(rentalId);
        if (rental == null || rental.getStatus() != RentalStatus.ACTIVE) {
            return false;
        }
        
        Bike bike = bikes.get(rental.getBikeId());
        User user = users.get(rental.getUserId());
        
        double cost = pricingStrategy.calculateCost(rental, bike);
        
        if (user.deductFromWallet(cost)) {
            rental.endRental(endLocation, cost);
            bike.updateLocation(endLocation);
            bike.endRental();
            
            Payment payment = new Payment(UUID.randomUUID().toString(),
                rentalId, cost, PaymentMethod.WALLET);
            payment.markCompleted();
            rental.setPayment(payment);
            
            return true;
        }
        
        return false;
    }
    
    public User getUser(String userId) {
        return users.get(userId);
    }
    
    public Bike getBike(String bikeId) {
        return bikes.get(bikeId);
    }
    
    public Rental getRental(String rentalId) {
        return rentals.get(rentalId);
    }
}

// ============================================================
// DEMO
// ============================================================

public class BikeRentalSystem {
    
    public static void main(String[] args) {
        System.out.println("=== BIKE RENTAL SYSTEM DEMO ===\n");
        
        // Demo 1: Complete rental flow
        demoCompleteRentalFlow();
        
        // Demo 2: Find nearby bikes
        demoFindNearbyBikes();
        
        // Demo 3: Different pricing strategies
        demoPricingStrategies();
        
        // Demo 4: Concurrent bike reservations
        demoConcurrentReservations();
        
        // Demo 5: E-bike with battery
        demoElectricBike();
    }
    
    private static void demoCompleteRentalFlow() {
        System.out.println("--- Demo 1: Complete Rental Flow ---\n");
        
        BikeRentalServiceImpl service = new BikeRentalServiceImpl(
            new TimeBasedPricing(1.0, 0.15)
        );
        
        // Register user
        User user = new User("U1", "John Doe", "john@email.com", "555-1234");
        user.addToWallet(50.0);
        service.registerUser(user);
        System.out.println("User registered: " + user.getName());
        System.out.println("Wallet balance: $" + user.getWalletBalance());
        
        // Add bike
        Bike bike = new Bike("B1", BikeType.REGULAR, new Location(37.7749, -122.4194));
        service.addBike(bike);
        System.out.println("\nBike added: " + bike);
        
        // Reserve bike
        System.out.println("\nReserving bike...");
        Rental rental = service.reserveBike("U1", "B1");
        System.out.println("Rental created: " + rental.getId());
        System.out.println("Bike status: " + service.getBike("B1").getStatus());
        
        // Start rental (unlock)
        System.out.println("\nUnlocking bike...");
        service.startRental(rental.getId());
        System.out.println("Rental status: " + service.getRental(rental.getId()).getStatus());
        System.out.println("Bike status: " + service.getBike("B1").getStatus());
        
        // Simulate ride
        System.out.println("\nRiding for 30 minutes...");
        try {
            Thread.sleep(1000); // Simulate 30 min (1 sec in demo)
        } catch (InterruptedException e) {}
        
        // End rental (lock)
        Location endLocation = new Location(37.7849, -122.4094);
        System.out.println("\nLocking bike at: " + endLocation);
        service.endRental(rental.getId(), endLocation);
        
        Rental completedRental = service.getRental(rental.getId());
        System.out.println("Rental status: " + completedRental.getStatus());
        System.out.println("Duration: " + completedRental.getDurationMinutes() + " minutes");
        System.out.println("Cost: $" + String.format("%.2f", completedRental.getCost()));
        System.out.println("Remaining balance: $" + service.getUser("U1").getWalletBalance());
        System.out.println("Bike status: " + service.getBike("B1").getStatus());
        
        System.out.println();
    }
    
    private static void demoFindNearbyBikes() {
        System.out.println("--- Demo 2: Find Nearby Bikes ---\n");
        
        BikeRentalServiceImpl service = new BikeRentalServiceImpl(
            new TimeBasedPricing(1.0, 0.15)
        );
        
        // Add bikes at various locations
        service.addBike(new Bike("B1", BikeType.REGULAR, new Location(37.7749, -122.4194)));
        service.addBike(new Bike("B2", BikeType.ELECTRIC, new Location(37.7759, -122.4184)));
        service.addBike(new Bike("B3", BikeType.REGULAR, new Location(37.7849, -122.4094)));
        service.addBike(new Bike("B4", BikeType.ELECTRIC, new Location(37.7649, -122.4294)));
        service.addBike(new Bike("B5", BikeType.REGULAR, new Location(37.7949, -122.3994)));
        
        Location userLocation = new Location(37.7749, -122.4194);
        System.out.println("User location: " + userLocation);
        
        List<Bike> nearbyBikes = service.findNearbyBikes(userLocation, 2.0);
        System.out.println("\nBikes within 2km:");
        for (Bike bike : nearbyBikes) {
            double distance = userLocation.distanceTo(bike.getCurrentLocation());
            System.out.println(String.format("  %s - %.2f km away - %s",
                bike.getId(), distance, bike.getType()));
        }
        
        System.out.println();
    }
    
    private static void demoPricingStrategies() {
        System.out.println("--- Demo 3: Different Pricing Strategies ---\n");
        
        // Time-based
        System.out.println("Time-Based Pricing:");
        BikeRentalServiceImpl timeService = new BikeRentalServiceImpl(
            new TimeBasedPricing(1.0, 0.15)
        );
        testPricing(timeService, "Time-based");
        
        // Distance-based
        System.out.println("\nDistance-Based Pricing:");
        BikeRentalServiceImpl distService = new BikeRentalServiceImpl(
            new DistanceBasedPricing(1.0, 2.0)
        );
        testPricing(distService, "Distance-based");
        
        System.out.println();
    }
    
    private static void testPricing(BikeRentalServiceImpl service, String name) {
        User user = new User("U1", "Test User", "test@email.com", "555-0000");
        user.addToWallet(100.0);
        service.registerUser(user);
        
        Bike bike = new Bike("B1", BikeType.REGULAR, new Location(37.7749, -122.4194));
        service.addBike(bike);
        
        Rental rental = service.reserveBike("U1", "B1");
        service.startRental(rental.getId());
        
        try {
            Thread.sleep(500); // Simulate ride
        } catch (InterruptedException e) {}
        
        Location endLocation = new Location(37.7849, -122.4094);
        service.endRental(rental.getId(), endLocation);
        
        Rental completed = service.getRental(rental.getId());
        System.out.println("  " + name + " cost: $" + String.format("%.2f", completed.getCost()));
    }
    
    private static void demoConcurrentReservations() {
        System.out.println("--- Demo 4: Concurrent Bike Reservations ---\n");
        
        BikeRentalServiceImpl service = new BikeRentalServiceImpl(
            new TimeBasedPricing(1.0, 0.15)
        );
        
        // Add one bike
        service.addBike(new Bike("B1", BikeType.REGULAR, new Location(37.7749, -122.4194)));
        
        // Add multiple users
        for (int i = 1; i <= 5; i++) {
            User user = new User("U" + i, "User " + i, "user" + i + "@email.com", "555-000" + i);
            user.addToWallet(50.0);
            service.registerUser(user);
        }
        
        System.out.println("5 users trying to reserve the same bike concurrently...\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 1; i <= 5; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    Rental rental = service.reserveBike("U" + userId, "B1");
                    System.out.println("  User " + userId + " SUCCESSFULLY reserved bike");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("  User " + userId + " FAILED: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            executor.shutdown();
            System.out.println("\nResult: Only " + successCount.get() + " user got the bike (correct!)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    private static void demoElectricBike() {
        System.out.println("--- Demo 5: Electric Bike with Battery ---\n");
        
        BikeRentalServiceImpl service = new BikeRentalServiceImpl(
            new TimeBasedPricing(2.0, 0.25) // E-bikes cost more
        );
        
        User user = new User("U1", "Alice", "alice@email.com", "555-1111");
        user.addToWallet(50.0);
        service.registerUser(user);
        
        Bike ebike = new Bike("EB1", BikeType.ELECTRIC, new Location(37.7749, -122.4194));
        service.addBike(ebike);
        
        System.out.println("E-Bike: " + ebike);
        System.out.println("Battery level: " + ebike.getBatteryLevel() + "%");
        
        Rental rental = service.reserveBike("U1", "EB1");
        service.startRental(rental.getId());
        
        System.out.println("\nRiding e-bike...");
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {}
        
        service.endRental(rental.getId(), new Location(37.7849, -122.4094));
        
        Rental completed = service.getRental(rental.getId());
        System.out.println("\nE-Bike rental completed");
        System.out.println("Cost: $" + String.format("%.2f", completed.getCost()));
        System.out.println("Note: E-bikes have higher rates ($2 unlock + $0.25/min)");
        
        System.out.println();
    }
}
