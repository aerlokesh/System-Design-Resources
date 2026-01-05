# Bike Rental System - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [System Types](#system-types)
4. [Core Entities and Relationships](#core-entities-and-relationships)
5. [Rental State Machine](#rental-state-machine)
6. [Class Design](#class-design)
7. [Complete Implementation](#complete-implementation)
8. [Extensibility](#extensibility)
9. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Bike Rental System?**

A bike rental system allows users to rent bikes for short-term use, typically for urban transportation. Users can find available bikes nearby, unlock them, ride to their destination, and return them at designated locations or anywhere (depending on system type).

**Real-World Examples:**
- **Docked Systems:** Citi Bike (NYC), Santander Cycles (London), Vélib' (Paris)
- **Dockless Systems:** Lime, Bird, Spin, Jump
- **Hybrid Systems:** Combination of both

**Core Workflow:**
```
User Registration → Find Nearby Bike → Unlock Bike → Ride
    ↓
Return to Station/Park Anywhere → Lock Bike → Calculate Payment
    ↓
Payment Processed → User Rated → Bike Available for Next User
```

**Key Challenges:**
- **Bike Availability:** Track real-time bike locations and status
- **Concurrent Rentals:** Prevent double-booking of bikes
- **Pricing:** Calculate based on time, distance, or hybrid
- **Geospatial Queries:** Find bikes/stations within radius
- **Maintenance Tracking:** Identify bikes needing repair
- **Station Capacity:** Manage dock availability (for docked systems)
- **Rebalancing:** Move bikes from full to empty stations

---

## Requirements

### Functional Requirements

1. **User Management**
   - Register users with payment method
   - User authentication and profile
   - View rental history
   - Wallet/credit management

2. **Bike Management**
   - Add bikes to system
   - Track bike location (GPS)
   - Track bike status (Available, In-Use, Maintenance)
   - Update bike condition
   - Battery level (for e-bikes)

3. **Station Management** (for docked systems)
   - Add/remove stations
   - Track available docks
   - Notify when station is full/empty
   - Station capacity management

4. **Rental Operations**
   - Find nearby bikes/stations
   - Reserve bike (optional, time-limited)
   - Unlock bike (start rental)
   - Track ongoing rental
   - Lock bike (end rental)
   - Handle dropped rentals

5. **Pricing & Payment**
   - Calculate rental cost (time-based, distance-based, or hybrid)
   - Process payment from wallet/card
   - Handle insufficient funds
   - Apply promotions/discounts

6. **Maintenance**
   - Report bike issues
   - Mark bike for maintenance
   - Track maintenance history
   - Return bike to service

### Non-Functional Requirements

1. **Performance**
   - Find bikes: < 1 second
   - Unlock operation: < 3 seconds
   - Support 10,000+ concurrent rentals

2. **Scalability**
   - Handle millions of users
   - Thousands of bikes
   - Hundreds of stations

3. **Availability**
   - 99.9% uptime
   - Offline mode for unlock/lock

4. **Consistency**
   - No double-booking of bikes
   - Accurate billing
   - Real-time availability updates

### Out of Scope

- Mobile app UI/UX
- Bike hardware (locks, GPS, IoT)
- Route navigation
- Social features (group rides, leaderboards)
- Bike manufacturing and procurement
- Insurance and liability
- Multi-city operations

---

## System Types

### 1. Docked System (Station-Based)

**Characteristics:**
- Bikes must be returned to specific stations
- Fixed docking points
- Station capacity management

**Examples:** Citi Bike, Santander Cycles

**Pros:**
- ✅ Organized bike placement
- ✅ Easier maintenance
- ✅ Predictable availability

**Cons:**
- ❌ Less flexible for users
- ❌ Requires station infrastructure
- ❌ Rebalancing needed

### 2. Dockless System (Free-Floating)

**Characteristics:**
- Bikes can be left anywhere (within service area)
- GPS-tracked locations
- No fixed stations

**Examples:** Lime, Bird, Spin

**Pros:**
- ✅ Maximum flexibility
- ✅ No station infrastructure needed
- ✅ Can expand easily

**Cons:**
- ❌ Bikes may cluster in inconvenient locations
- ❌ Harder to maintain order
- ❌ More complex geospatial queries

### 3. Hybrid System

**Characteristics:**
- Preferred return locations (stations) but not mandatory
- Incentives for station returns (discount)
- Best of both worlds

**Our Design:** Focus on dockless for LLD interviews (more interesting geospatial problems)

---

## Core Entities and Relationships

### Key Entities

1. **User**
   - User ID, name, email, phone
   - Wallet balance
   - Rental history
   - Verification status

2. **Bike**
   - Bike ID, type (regular, electric)
   - Current location (GPS)
   - Status (Available, In-Use, Maintenance, Reserved)
   - Battery level (e-bikes)
   - Condition rating

3. **Station** (for docked systems)
   - Station ID, name, location
   - Total docks
   - Available docks
   - Bikes at station

4. **Rental**
   - Rental ID
   - User, Bike
   - Start time, end time
   - Start location, end location
   - Cost
   - Status

5. **Payment**
   - Payment ID, rental ID
   - Amount, method
   - Status, timestamp

6. **MaintenanceRecord**
   - Issue description
   - Reported by user/system
   - Status, assigned technician

### Entity Relationships

```
┌─────────────┐
│    User     │
└──────┬──────┘
       │ creates
       ▼
┌─────────────┐      uses        ┌──────────────┐
│   Rental    │◄─────────────────│     Bike     │
└──────┬──────┘                  └──────┬───────┘
       │                                │
       │ generates                      │ located at
       ▼                                │ (optional)
┌─────────────┐                         ▼
│   Payment   │                  ┌──────────────┐
└─────────────┘                  │   Station    │
                                 └──────────────┘

┌──────────────┐     has         ┌──────────────┐
│     Bike     │◄────────────────│ Maintenance  │
└──────────────┘                 │   Record     │
                                 └──────────────┘
```

---

## Rental State Machine

### Rental States

```
┌──────────┐    find & reserve    ┌──────────┐
│   NONE   │─────────────────────►│ RESERVED │
└──────────┘                      └─────┬────┘
                                        │
                                   unlock bike
                                        │
                                        ▼
                                  ┌──────────┐
                           ┌──────│ ACTIVE   │
                           │      └─────┬────┘
                           │            │
                      reservation       │ lock bike
                       expires          │
                           │            ▼
                           │      ┌──────────┐
                           └─────►│COMPLETED │
                                  └──────────┘
```

### Bike States

```
┌────────────┐    user unlocks    ┌──────────┐
│ AVAILABLE  │───────────────────►│  IN_USE  │
└─────┬──────┘                    └─────┬────┘
      │                                 │
      │                            user locks
      │                                 │
      │                                 ▼
      │                           ┌──────────┐
      │                           │AVAILABLE │
      │                           └──────────┘
      │
      │ issue reported
      │
      ▼
┌────────────┐    fixed      ┌──────────┐
│MAINTENANCE │◄──────────────│AVAILABLE │
└────────────┘               └──────────┘
```

---

## Class Design

### 1. User

```java
/**
 * User who rents bikes
 */
public class User {
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
        this.isVerified = false;
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
    
    // Getters and setters
}
```

### 2. Bike

```java
/**
 * Bike entity with location and status
 */
public class Bike {
    private final String id;
    private final BikeType type;
    private Location currentLocation;
    private BikeStatus status;
    private int batteryLevel; // 0-100 for e-bikes, -1 for regular
    private double condition; // 0.0-1.0 (1.0 = perfect)
    private String currentRentalId;
    
    public enum BikeType {
        REGULAR, ELECTRIC, PREMIUM
    }
    
    public enum BikeStatus {
        AVAILABLE, IN_USE, RESERVED, MAINTENANCE, RETIRED
    }
    
    public Bike(String id, BikeType type, Location initialLocation) {
        this.id = id;
        this.type = type;
        this.currentLocation = initialLocation;
        this.status = BikeStatus.AVAILABLE;
        this.batteryLevel = type == BikeType.ELECTRIC ? 100 : -1;
        this.condition = 1.0;
    }
    
    public boolean isAvailable() {
        return status == BikeStatus.AVAILABLE && condition > 0.5;
    }
    
    public synchronized boolean tryReserve(String rentalId) {
        if (status == BikeStatus.AVAILABLE) {
            status = BikeStatus.RESERVED;
            currentRentalId = rentalId;
            return true;
        }
        return false;
    }
    
    public synchronized boolean startRental(String rentalId) {
        if (status == BikeStatus.RESERVED && rentalId.equals(currentRentalId)) {
            status = BikeStatus.IN_USE;
            return true;
        }
        return false;
    }
    
    public synchronized void endRental() {
        status = BikeStatus.AVAILABLE;
        currentRentalId = null;
    }
    
    public void updateLocation(Location location) {
        this.currentLocation = location;
    }
    
    // Getters and setters
}
```

### 3. Station (for docked systems)

```java
/**
 * Bike station with docking slots
 */
public class Station {
    private final String id;
    private final String name;
    private final Location location;
    private final int totalDocks;
    private final Set<String> dockedBikeIds;
    private final ReadWriteLock lock;
    
    public Station(String id, String name, Location location, int totalDocks) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.totalDocks = totalDocks;
        this.dockedBikeIds = new HashSet<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    public int getAvailableDocks() {
        lock.readLock().lock();
        try {
            return totalDocks - dockedBikeIds.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean dockBike(String bikeId) {
        lock.writeLock().lock();
        try {
            if (dockedBikeIds.size() >= totalDocks) {
                return false; // Station full
            }
            return dockedBikeIds.add(bikeId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public boolean undockBike(String bikeId) {
        lock.writeLock().lock();
        try {
            return dockedBikeIds.remove(bikeId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int getDockedBikeCount() {
        lock.readLock().lock();
        try {
            return dockedBikeIds.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Getters
}
```

### 4. Rental

```java
/**
 * Rental transaction
 */
public class Rental {
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
    
    public enum RentalStatus {
        RESERVED, ACTIVE, COMPLETED, CANCELLED
    }
    
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
    
    // Getters and setters
}
```

### 5. Pricing Strategy

```java
/**
 * Strategy interface for pricing
 */
public interface PricingStrategy {
    double calculateCost(Rental rental, Bike bike);
}

/**
 * Time-based pricing
 */
public class TimeBasedPricing implements PricingStrategy {
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

/**
 * Distance-based pricing
 */
public class DistanceBasedPricing implements PricingStrategy {
    private final double unlockFee;
    private final double perKmRate;
    
    public DistanceBasedPricing(double unlockFee, double perKmRate) {
        this.unlockFee = unlockFee;
        this.perKmRate = perKmRate;
    }
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        double distance = rental.getStartLocation()
            .distanceTo(rental.getEndLocation());
        return unlockFee + (distance * perKmRate);
    }
}

/**
 * Hybrid pricing (time + distance)
 */
public class HybridPricing implements PricingStrategy {
    private final double unlockFee;
    private final double perMinuteRate;
    private final double perKmRate;
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        long minutes = rental.getDurationMinutes();
        double distance = rental.getStartLocation()
            .distanceTo(rental.getEndLocation());
        
        return unlockFee + (minutes * perMinuteRate) + (distance * perKmRate);
    }
}
```

---

## Complete Implementation

### BikeRentalSystem - Main Service

```java
/**
 * Main bike rental service
 */
public class BikeRentalSystem {
    private final Map<String, User> users;
    private final Map<String, Bike> bikes;
    private final Map<String, Station> stations; // Optional for docked systems
    private final Map<String, Rental> rentals;
    private final PricingStrategy pricingStrategy;
    private final AtomicInteger rentalIdCounter;
    
    public BikeRentalSystem(PricingStrategy pricingStrategy) {
        this.users = new ConcurrentHashMap<>();
        this.bikes = new ConcurrentHashMap<>();
        this.stations = new ConcurrentHashMap<>();
        this.rentals = new ConcurrentHashMap<>();
        this.pricingStrategy = pricingStrategy;
        this.rentalIdCounter = new AtomicInteger(1);
    }
    
    // User Management
    public void registerUser(User user) {
        users.put(user.getId(), user);
    }
    
    public void addBike(Bike bike) {
        bikes.put(bike.getId(), bike);
    }
    
    public void addStation(Station station) {
        stations.put(station.getId(), station);
    }
    
    // Find Nearby Bikes
    public List<Bike> findNearbyBikes(Location userLocation, double radiusKm) {
        return bikes.values().stream()
            .filter(Bike::isAvailable)
            .filter(bike -> bike.getCurrentLocation().distanceTo(userLocation) <= radiusKm)
            .sorted(Comparator.comparingDouble(bike -> 
                bike.getCurrentLocation().distanceTo(userLocation)))
            .limit(10)
            .collect(Collectors.toList());
    }
    
    // Reserve Bike
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
        
        // Try to reserve bike (atomic operation)
        if (bike.tryReserve(rentalId)) {
            Rental rental = new Rental(rentalId, userId, bikeId, 
                                      bike.getCurrentLocation());
            rentals.put(rentalId, rental);
            
            // Schedule reservation timeout (e.g., 5 minutes)
            scheduleReservationTimeout(rental, 5 * 60 * 1000);
            
            return rental;
        }
        
        throw new IllegalStateException("Bike not available");
    }
    
    // Start Rental (Unlock)
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
    
    // End Rental (Lock)
    public boolean endRental(String rentalId, Location endLocation) {
        Rental rental = rentals.get(rentalId);
        if (rental == null || rental.getStatus() != Rental.RentalStatus.ACTIVE) {
            return false;
        }
        
        Bike bike = bikes.get(rental.getBikeId());
        User user = users.get(rental.getUserId());
        
        // Calculate cost
        double cost = pricingStrategy.calculateCost(rental, bike);
        
        // Process payment
        if (user.deductFromWallet(cost)) {
            rental.endRental(endLocation, cost);
            bike.updateLocation(endLocation);
            bike.endRental();
            
            // Create payment record
            Payment payment = new Payment(UUID.randomUUID().toString(),
                rentalId, cost, Payment.PaymentMethod.WALLET);
            payment.markCompleted();
            rental.setPayment(payment);
            
            return true;
        }
        
        return false; // Insufficient funds
    }
    
    // Maintenance
    public void reportIssue(String bikeId, String issue, String reportedBy) {
        Bike bike = bikes.get(bikeId);
        if (bike != null) {
            bike.setStatus(Bike.BikeStatus.MAINTENANCE);
            
            MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID().toString(),
                bikeId,
                issue,
                reportedBy
            );
            
            // Store maintenance record
            System.out.println("Bike " + bikeId + " marked for maintenance: " + issue);
        }
    }
    
    // Station Operations (for docked systems)
    public List<Station> findNearbyStations(Location location, double radiusKm) {
        return stations.values().stream()
            .filter(s => s.getLocation().distanceTo(location) <= radiusKm)
            .sorted(Comparator.comparingDouble(s -> 
                s.getLocation().distanceTo(location)))
            .collect(Collectors.toList());
    }
    
    private void scheduleReservationTimeout(Rental rental, long timeoutMs) {
        // In production, use ScheduledExecutorService
        // For simplicity, not implemented here
    }
}
```

---

## Extensibility

### 1. Dynamic Pricing (Surge/Off-Peak)

```java
public class DynamicPricingStrategy implements PricingStrategy {
    private final TimeBasedPricing baseStrategy;
    private final Map<Integer, Double> hourlyMultipliers;
    
    public DynamicPricingStrategy() {
        this.baseStrategy = new TimeBasedPricing(1.0, 0.15);
        this.hourlyMultipliers = new HashMap<>();
        
        // Peak hours (8-10 AM, 5-7 PM): 1.5x
        hourlyMultipliers.put(8, 1.5);
        hourlyMultipliers.put(9, 1.5);
        hourlyMultipliers.put(17, 1.5);
        hourlyMultipliers.put(18, 1.5);
        
        // Off-peak (11 PM - 6 AM): 0.8x
        for (int hour = 23; hour <= 24; hour++) hourlyMultipliers.put(hour, 0.8);
        for (int hour = 0; hour <= 6; hour++) hourlyMultipliers.put(hour, 0.8);
    }
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        double baseCost = baseStrategy.calculateCost(rental, bike);
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(rental.getStartTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        
        double multiplier = hourlyMultipliers.getOrDefault(hour, 1.0);
        return baseCost * multiplier;
    }
}
```

### 2. Subscription Plans

```java
public class SubscriptionPlan {
    private final String id;
    private final String name;
    private final double monthlyFee;
    private final int freeMinutesPerRide;
    private final double discountRate;
    
    public SubscriptionPlan(String id, String name, double monthlyFee,
                            int freeMinutes, double discountRate) {
        this.id = id;
        this.name = name;
        this.monthlyFee = monthlyFee;
        this.freeMinutesPerRide = freeMinutes;
        this.discountRate = discountRate;
    }
}

public class SubscriptionPricingStrategy implements PricingStrategy {
    private final TimeBasedPricing baseStrategy;
    private final Map<String, SubscriptionPlan> userSubscriptions;
    
    @Override
    public double calculateCost(Rental rental, Bike bike) {
        SubscriptionPlan plan = userSubscriptions.get(rental.getUserId());
        
        if (plan == null) {
            return baseStrategy.calculateCost(rental, bike);
        }
        
        long minutes = rental.getDurationMinutes();
        long chargeableMinutes = Math.max(0, minutes - plan.freeMinutesPerRide);
        
        double baseCost = 1.0 + (chargeableMinutes * 0.15);
        return baseCost * (1.0 - plan.discountRate);
    }
}
```

### 3. Geofencing (Service Area)

```java
public class Geofence {
    private final List<Location> boundaryPoints;
    private final String name;
    
    public boolean contains(Location point) {
        // Ray casting algorithm for point-in-polygon
        // Simplified version
        return isInsideBoundary(point, boundaryPoints);
    }
    
    private boolean isInsideBoundary(Location point, List<Location> boundary) {
        // Implement ray-casting algorithm
        // Returns true if point is inside polygon
        return true; // Simplified
    }
}

public class GeofencedBikeSystem extends BikeRentalSystem {
    private final Geofence serviceArea;
    
    @Override
    public boolean endRental(String rentalId, Location endLocation) {
        if (!serviceArea.contains(endLocation)) {
            // Charge penalty for parking outside service area
            chargePenalty(rentalId, 25.0);
            return false;
        }
        
        return super.endRental(rentalId, endLocation);
    }
}
```

### 4. Bike Rebalancing System

```java
public class BikeRebalancer {
    private final BikeRentalSystem system;
    
    public List<RebalancingTask> generateRebalancingTasks() {
        List<RebalancingTask> tasks = new ArrayList<>();
        
        // Find overcrowded stations/areas
        List<Station> fullStations = findFullStations();
        List<Station> emptyStations = findEmptyStations();
        
        // Create tasks to move bikes from full to empty
        for (Station full : fullStations) {
            for (Station empty : emptyStations) {
                int bikesToMove = Math.min(
                    full.getDockedBikeCount() - full.getTotalDocks() / 2,
                    empty.getAvailableDocks()
                );
                
                if (bikesToMove > 0) {
                    tasks.add(new RebalancingTask(full.getId(), empty.getId(), bikesToMove));
                }
            }
        }
        
        return tasks;
    }
}

class RebalancingTask {
    private final String fromStationId;
    private final String toStationId;
    private final int bikeCount;
    
    // Constructor and getters
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- Docked or dockless system?
- Pricing model (time, distance, subscription)?
- E-bikes or regular bikes?
- Reservation support?
- Maintenance workflow?

**2. Identify Core Entities (5 minutes)**
- User, Bike, Rental
- Station (if docked)
- Payment, Pricing
- Maintenance records

**3. Design State Machines (10 minutes)**
- Bike states (Available, In-Use, Maintenance)
- Rental states (Reserved, Active, Completed)
- Draw transitions

**4. Implement Core Logic (20 minutes)**
- Bike discovery (geospatial)
- Rental flow (reserve, unlock, ride, lock)
- Atomic bike reservation
- Payment processing

**5. Discuss Extensions (5 minutes)**
- Dynamic pricing
- Subscriptions
- Geofencing
- Rebalancing

### Common Interview Questions

**Q1: "How do you prevent two users from renting the same bike?"**

**A:** Atomic reservation with CAS:
```java
class Bike {
    private final AtomicReference<String> currentRentalId = 
        new AtomicReference<>(null);
    
    public boolean tryReserve(String rentalId) {
        return currentRentalId.compareAndSet(null, rentalId);
    }
}
```

**Q2: "How to find nearest available bike efficiently?"**

**A:** Use spatial indexing (QuadTree or Grid):
```java
// Option 1: Simple (acceptable for interview)
List<Bike> findNearest(Location user, double radius) {
    return bikes.stream()
        .filter(Bike::isAvailable)
        .filter(b -> b.getLocation().distanceTo(user) <= radius)
        .sorted(by distance)
        .limit(10);
}

// Option 2: Optimized with QuadTree
QuadTree<Bike> bikeIndex = new QuadTree<>();
List<Bike> candidates = bikeIndex.rangeQuery(user, radius);
```

**Q3: "What if user doesn't return bike properly?"**

**A:** Multiple strategies:
1. **Grace period:** 15 minutes to properly lock
2. **Continuous charging:** Keep charging until locked
3. **Penalties:** Charge penalty for improper return
4. **Account suspension:** Block user after repeated violations

```java
public void handleImproperReturn(Rental rental) {
    // Continue charging
    while (!bike.isLocked()) {
        wait(15 * 60 * 1000); // 15 minutes
        chargeAdditionalFee(rental, 5.0);
    }
    
    // Apply penalty
    applyPenalty(rental.getUserId(), 25.0);
}
```

**Q4: "How to handle bike maintenance?"**

**A:** Track condition and automate maintenance scheduling:
```java
class BikeMaintenanceManager {
    public void checkBikeHealth(Bike bike) {
        // Automatic detection
        if (bike.getCondition() < 0.5) {
            markForMaintenance(bike, "Low condition rating");
        }
        
        if (bike.getType() == BikeType.ELECTRIC && bike.getBatteryLevel() < 10) {
            markForMaintenance(bike, "Low battery");
        }
        
        // User reports also trigger maintenance
    }
    
    public void completeMaintenance(String bikeId) {
        Bike bike = bikes.get(bikeId);
        bike.setCondition(1.0);
        bike.setStatus(BikeStatus.AVAILABLE);
        bike.setBatteryLevel(100); // For e-bikes
    }
}
```

**Q5: "How to handle station rebalancing?"**

**A:** ML-based or rule-based prediction:
```java
class RebalancingOptimizer {
    public void rebalance() {
        // Predict demand based on historical data
        Map<String, Integer> demandForecast = predictDemand();
        
        // Find imbalanced stations
        for (Station station : stations) {
            int expected = demandForecast.get(station.getId());
            int actual = station.getDockedBikeCount();
            
            if (actual < expected * 0.5) {
                // Move bikes TO this station
                scheduleRebalancing(station, expected - actual);
            } else if (actual > expected * 1.5) {
                // Move bikes FROM this station
                scheduleRebalancing(station, actual - expected);
            }
        }
    }
}
```

### Design Patterns Used

1. **State Pattern:** Bike and Rental status management
2. **Strategy Pattern:** Different pricing strategies
3. **Observer Pattern:** Real-time bike location updates
4. **Factory Pattern:** Creating rentals and payments
5. **Singleton Pattern:** BikeRentalSystem instance

### Expected Level Performance

**Junior Engineer:**
- Basic entities (User, Bike, Rental)
- Simple rental flow (unlock, ride, lock)
- Time-based pricing
- Basic geospatial search

**Mid-Level Engineer:**
- Complete state machine for bikes and rentals
- Multiple pricing strategies
- Atomic bike reservation (prevent double-booking)
- Station management (if docked)
- Payment processing
- Thread-safe operations

**Senior Engineer:**
- Production-ready with all features
- Dynamic pricing and subscriptions
- Geofencing with penalties
- Maintenance tracking and automation
- Rebalancing algorithms
- Performance optimization (spatial indexing)
- Distributed system considerations

### Key Trade-offs

**1. Docked vs Dockless**
- **Docked:** Organized but less flexible
- **Dockless:** Flexible but can create clutter
- **Hybrid:** Best of both, more complex

**2. Reservation System**
- **With reservation:** Better UX, more complex
- **Without reservation:** Simpler, but bikes may be taken
- **Time-limited:** Balance between both

**3. Pricing Model**
- **Time-based:** Simple, predictable
- **Distance-based:** Fair for short trips
- **Hybrid:** Most accurate, more complex

**4. Payment Methods**
- **Wallet only:** Simpler, requires preload
- **Credit card:** More flexible, higher fees
- **Multiple methods:** Best UX, more complexity

### Algorithm Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Find nearby bikes | O(n) or O(log n) | O(n) simple, O(log n) with QuadTree |
| Reserve bike | O(1) | Atomic CAS operation |
| Start rental | O(1) | Hash map lookup |
| End rental | O(1) | Hash map lookup + payment |
| Station dock/undock | O(1) | Set operations with lock |

---

## Summary

This Bike Rental System demonstrates:

1. **Atomic Operations:** Prevent double-booking with CAS
2. **State Machines:** Bike and Rental lifecycle management
3. **Geospatial Queries:** Find nearby bikes/stations
4. **Strategy Pattern:** Multiple pricing models
5. **Thread Safety:** Concurrent rental operations
6. **Real-World Features:** Reservations, maintenance, payments

**Key Takeaways:**
- **Atomic bike reservation** prevents race conditions
- **Geospatial indexing** for performance
- **Strategy pattern** for pricing flexibility
- **State machines** manage complex workflows
- **Thread safety** critical for concurrent rentals

**Related Systems:**
- Car rental (Zipcar, Turo)
- Scooter rental (Lime, Bird)
- Equipment rental
- Locker systems

---

*Last Updated: January 5, 2026*
*Difficulty Level: Medium*
*Key Focus: State Machine, Concurrency, Geospatial, Pricing*
*Companies: Lime, Bird, Citi Bike, Lyft Bikes*
*Interview Success Rate: High with proper state machine and concurrency handling*
