# Parking Lot System - HELLO Interview Framework

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Must Have (P0)
1. **Multiple Vehicle Types**
   - Support different vehicle types: Car, Motorcycle, Truck, Van
   - Each type has different size requirements
   - Vehicles can only park in appropriate spots

2. **Multiple Spot Types**
   - Compact spots (motorcycles, small cars)
   - Regular spots (cars)
   - Large spots (trucks, vans)
   - Spot size hierarchy: Large > Regular > Compact

3. **Parking Operations**
   - Park vehicle: Find and assign appropriate spot
   - Unpark vehicle: Release spot and calculate fee
   - Check availability: Query available spots by type

4. **Fee Calculation**
   - Hourly rate based on vehicle type
   - Calculate total fee on exit
   - Round up to nearest hour

5. **Multi-Level Support**
   - Parking lot has multiple floors/levels
   - Each level has multiple spots
   - Vehicles can park on any level

#### Nice to Have (P1)
- Reserved parking spots (handicapped, VIP)
- Different pricing for peak/off-peak hours
- Parking spot recommendations (closest to entrance)
- Payment integration
- Parking history and receipts
- Real-time occupancy display
- Advance booking/reservation

### Non-Functional Requirements

#### Performance
- **Park operation**: < 100ms to find available spot
- **Unpark operation**: < 50ms to release and calculate fee
- **Concurrent access**: Support 100+ simultaneous operations

#### Scalability
- Support 1000+ parking spots
- Handle 10,000+ vehicles per day
- Multiple entry/exit points

#### Thread Safety
- Multiple vehicles can park/unpark simultaneously
- No double booking of spots
- Consistent spot availability tracking

#### Reliability
- No data loss (parking records)
- Graceful handling of edge cases
- Proper error messages

### Constraints and Assumptions

**Constraints**:
- Fixed number of spots (cannot dynamically add/remove)
- One vehicle per spot
- Vehicle stays in same spot until departure
- Single parking lot location (not distributed)

**Assumptions**:
- Vehicle registration number is unique
- Vehicles are honest about their type
- Payment is processed separately (out of scope for core logic)
- Time tracking is accurate

**Out of Scope**:
- Payment gateway integration
- Security cameras and surveillance
- Automated gates and barriers
- Mobile app development
- Multi-location parking lot chains

---

## 2️⃣ Core Entities

### Enum: VehicleType
**Responsibility**: Define different vehicle categories

**Values**:
- `MOTORCYCLE` - Two-wheelers
- `CAR` - Standard cars
- `TRUCK` - Large trucks
- `VAN` - Vans and SUVs

### Enum: SpotType
**Responsibility**: Define different parking spot sizes

**Values**:
- `COMPACT` - Small spots for motorcycles/compact cars
- `REGULAR` - Standard spots for regular cars
- `LARGE` - Large spots for trucks/vans
- `HANDICAPPED` - Reserved for handicapped (optional)

**Size Hierarchy**: LARGE > REGULAR > COMPACT

### Class: Vehicle (Abstract)
**Responsibility**: Represent a vehicle

**Key Attributes**:
- `registrationNumber: String` - Unique identifier
- `vehicleType: VehicleType` - Type of vehicle

**Key Methods**:
- `getRequiredSpotType()`: Determine minimum spot type needed
- `equals()`, `hashCode()`: Based on registration number

**Relationships**:
- Subclassed by: Motorcycle, Car, Truck, Van
- Used by: Ticket

**Subclasses**:
```
Vehicle (abstract)
  ├─ Motorcycle (requires COMPACT)
  ├─ Car (requires REGULAR)
  ├─ Truck (requires LARGE)
  └─ Van (requires LARGE)
```

### Class: ParkingSpot
**Responsibility**: Represent a single parking spot

**Key Attributes**:
- `spotNumber: String` - Unique identifier (e.g., "A-101")
- `spotType: SpotType` - Size category
- `floor: int` - Floor/level number
- `occupied: boolean` - Current status
- `currentVehicle: Vehicle` - Parked vehicle (if occupied)

**Key Methods**:
- `canFitVehicle(Vehicle)`: Check if vehicle can park here
- `parkVehicle(Vehicle)`: Assign vehicle to spot
- `removeVehicle()`: Clear spot
- `isAvailable()`: Check if spot is free

**Relationships**:
- Contained by: Floor
- References: Vehicle (when occupied)

### Class: Floor
**Responsibility**: Represent a parking level

**Key Attributes**:
- `floorNumber: int` - Level identifier
- `spots: List<ParkingSpot>` - All spots on this floor
- `availableSpots: Map<SpotType, List<ParkingSpot>>` - Quick lookup

**Key Methods**:
- `findAvailableSpot(SpotType)`: Find first available spot of type
- `getAvailableCount(SpotType)`: Count available spots
- `releaseSpot(ParkingSpot)`: Mark spot as available

**Relationships**:
- Contains: Multiple ParkingSpot
- Contained by: ParkingLot

### Class: Ticket
**Responsibility**: Represent parking ticket/session

**Key Attributes**:
- `ticketNumber: String` - Unique ticket ID
- `vehicle: Vehicle` - Parked vehicle
- `spot: ParkingSpot` - Assigned spot
- `entryTime: LocalDateTime` - Entry timestamp
- `exitTime: LocalDateTime` - Exit timestamp (null until exit)
- `fee: double` - Calculated parking fee

**Key Methods**:
- `calculateFee()`: Compute parking fee based on duration
- `getDuration()`: Get parking duration in hours

**Relationships**:
- References: Vehicle, ParkingSpot
- Created by: ParkingLot
- Used for: Fee calculation

### Class: ParkingLot (Singleton)
**Responsibility**: Main system coordinator

**Key Attributes**:
- `name: String` - Parking lot name
- `floors: List<Floor>` - All floors
- `activeTickets: Map<String, Ticket>` - Ongoing parking sessions
- `hourlyRates: Map<VehicleType, Double>` - Pricing

**Key Methods**:
- `parkVehicle(Vehicle)`: Find spot and issue ticket
- `unparkVehicle(ticketNumber)`: Process exit and calculate fee
- `getAvailableSpots(SpotType)`: Check availability
- `isFull()`: Check if lot is full

**Relationships**:
- Contains: Multiple Floor
- Manages: Ticket lifecycle
- Singleton pattern: Only one instance

### Class: ParkingLotSystem
**Responsibility**: Facade for external interactions

**Key Methods**:
- `getInstance()`: Get ParkingLot singleton
- `displayAvailability()`: Show spot counts
- `generateReceipt(Ticket)`: Create parking receipt

**Relationships**:
- Facade for: ParkingLot
- Provides: Simple public API

---

## 3️⃣ API Design

### Abstract Class: Vehicle

```java
/**
 * Base class for all vehicle types
 */
public abstract class Vehicle {
    private final String registrationNumber;
    private final VehicleType vehicleType;
    
    public Vehicle(String registrationNumber, VehicleType vehicleType) {
        if (registrationNumber == null || registrationNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Registration number cannot be null/empty");
        }
        this.registrationNumber = registrationNumber;
        this.vehicleType = vehicleType;
    }
    
    /**
     * Get minimum spot type required for this vehicle
     * @return Required spot type
     */
    public abstract SpotType getRequiredSpotType();
    
    public String getRegistrationNumber() {
        return registrationNumber;
    }
    
    public VehicleType getVehicleType() {
        return vehicleType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return registrationNumber.equals(vehicle.registrationNumber);
    }
    
    @Override
    public int hashCode() {
        return registrationNumber.hashCode();
    }
}
```

### Concrete Vehicle Classes

```java
public class Motorcycle extends Vehicle {
    public Motorcycle(String registrationNumber) {
        super(registrationNumber, VehicleType.MOTORCYCLE);
    }
    
    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.COMPACT;
    }
}

public class Car extends Vehicle {
    public Car(String registrationNumber) {
        super(registrationNumber, VehicleType.CAR);
    }
    
    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.REGULAR;
    }
}

public class Truck extends Vehicle {
    public Truck(String registrationNumber) {
        super(registrationNumber, VehicleType.TRUCK);
    }
    
    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.LARGE;
    }
}

public class Van extends Vehicle {
    public Van(String registrationNumber) {
        super(registrationNumber, VehicleType.VAN);
    }
    
    @Override
    public SpotType getRequiredSpotType() {
        return SpotType.LARGE;
    }
}
```

### Class: ParkingSpot

```java
/**
 * Represents a single parking spot
 * Thread-safe for concurrent access
 */
public class ParkingSpot {
    private final String spotNumber;
    private final SpotType spotType;
    private final int floor;
    private volatile boolean occupied;
    private Vehicle currentVehicle;
    
    public ParkingSpot(String spotNumber, SpotType spotType, int floor) {
        this.spotNumber = spotNumber;
        this.spotType = spotType;
        this.floor = floor;
        this.occupied = false;
    }
    
    /**
     * Check if vehicle can fit in this spot
     * @param vehicle Vehicle to check
     * @return true if vehicle can park here
     */
    public synchronized boolean canFitVehicle(Vehicle vehicle) {
        if (occupied) {
            return false;
        }
        
        SpotType requiredType = vehicle.getRequiredSpotType();
        
        // Check if spot is large enough
        // LARGE can fit anything, REGULAR can fit CAR and MOTORCYCLE, etc.
        return spotType.ordinal() >= requiredType.ordinal();
    }
    
    /**
     * Park vehicle in this spot
     * @param vehicle Vehicle to park
     * @return true if successful
     */
    public synchronized boolean parkVehicle(Vehicle vehicle) {
        if (!canFitVehicle(vehicle)) {
            return false;
        }
        
        this.occupied = true;
        this.currentVehicle = vehicle;
        return true;
    }
    
    /**
     * Remove vehicle from spot
     * @return The vehicle that was removed
     */
    public synchronized Vehicle removeVehicle() {
        if (!occupied) {
            return null;
        }
        
        Vehicle vehicle = this.currentVehicle;
        this.occupied = false;
        this.currentVehicle = null;
        return vehicle;
    }
    
    public synchronized boolean isAvailable() {
        return !occupied;
    }
    
    public String getSpotNumber() {
        return spotNumber;
    }
    
    public SpotType getSpotType() {
        return spotType;
    }
    
    public int getFloor() {
        return floor;
    }
    
    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }
}
```

### Class: Floor

```java
/**
 * Represents one floor in the parking lot
 */
public class Floor {
    private final int floorNumber;
    private final List<ParkingSpot> spots;
    private final Map<SpotType, List<ParkingSpot>> availableSpotsByType;
    
    public Floor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.availableSpotsByType = new ConcurrentHashMap<>();
        
        for (SpotType type : SpotType.values()) {
            availableSpotsByType.put(type, new CopyOnWriteArrayList<>());
        }
    }
    
    /**
     * Add parking spot to this floor
     * @param spot Spot to add
     */
    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
        availableSpotsByType.get(spot.getSpotType()).add(spot);
    }
    
    /**
     * Find first available spot of given type
     * Tries exact type first, then larger types
     * 
     * @param requiredType Minimum spot type needed
     * @return Available spot or null
     */
    public synchronized ParkingSpot findAvailableSpot(SpotType requiredType) {
        // Try exact type first
        ParkingSpot spot = findSpotOfType(requiredType);
        if (spot != null) {
            return spot;
        }
        
        // Try larger types (upgrade)
        for (SpotType type : SpotType.values()) {
            if (type.ordinal() > requiredType.ordinal()) {
                spot = findSpotOfType(type);
                if (spot != null) {
                    return spot;
                }
            }
        }
        
        return null;
    }
    
    private ParkingSpot findSpotOfType(SpotType type) {
        List<ParkingSpot> spotsOfType = availableSpotsByType.get(type);
        
        for (ParkingSpot spot : spotsOfType) {
            if (spot.isAvailable()) {
                return spot;
            }
        }
        
        return null;
    }
    
    /**
     * Release spot back to available pool
     * @param spot Spot to release
     */
    public synchronized void releaseSpot(ParkingSpot spot) {
        // Spot is already marked available by removeVehicle()
        // This is just for bookkeeping if needed
    }
    
    /**
     * Get count of available spots by type
     * @param type Spot type
     * @return Number of available spots
     */
    public int getAvailableCount(SpotType type) {
        List<ParkingSpot> spotsOfType = availableSpotsByType.get(type);
        return (int) spotsOfType.stream()
            .filter(ParkingSpot::isAvailable)
            .count();
    }
    
    public int getFloorNumber() {
        return floorNumber;
    }
}
```

### Class: Ticket

```java
/**
 * Parking ticket issued when vehicle enters
 */
public class Ticket {
    private final String ticketNumber;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;
    
    public Ticket(String ticketNumber, Vehicle vehicle, ParkingSpot spot) {
        this.ticketNumber = ticketNumber;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = LocalDateTime.now();
        this.exitTime = null;
        this.fee = 0.0;
    }
    
    /**
     * Calculate parking fee based on hourly rate
     * @param hourlyRate Rate per hour for vehicle type
     * @return Calculated fee
     */
    public double calculateFee(double hourlyRate) {
        if (exitTime == null) {
            exitTime = LocalDateTime.now();
        }
        
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        // Round up to nearest hour
        long hours = (minutes + 59) / 60;
        
        this.fee = hours * hourlyRate;
        return this.fee;
    }
    
    /**
     * Get parking duration in hours (rounded up)
     * @return Duration in hours
     */
    public long getDurationInHours() {
        LocalDateTime end = exitTime != null ? exitTime : LocalDateTime.now();
        long minutes = Duration.between(entryTime, end).toMinutes();
        return (minutes + 59) / 60;
    }
    
    // Getters
    public String getTicketNumber() { return ticketNumber; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public double getFee() { return fee; }
}
```

### Class: ParkingLot (Singleton)

```java
/**
 * Main parking lot system (Singleton)
 * Manages all parking operations
 */
public class ParkingLot {
    private static ParkingLot instance;
    private static final Object lock = new Object();
    
    private final String name;
    private final List<Floor> floors;
    private final Map<String, Ticket> activeTickets;
    private final Map<VehicleType, Double> hourlyRates;
    private final AtomicLong ticketCounter;
    
    private ParkingLot(String name, int numberOfFloors) {
        this.name = name;
        this.floors = new ArrayList<>();
        this.activeTickets = new ConcurrentHashMap<>();
        this.hourlyRates = new HashMap<>();
        this.ticketCounter = new AtomicLong(1);
        
        // Initialize floors
        for (int i = 1; i <= numberOfFloors; i++) {
            floors.add(new Floor(i));
        }
        
        // Set default hourly rates
        hourlyRates.put(VehicleType.MOTORCYCLE, 5.0);
        hourlyRates.put(VehicleType.CAR, 10.0);
        hourlyRates.put(VehicleType.TRUCK, 20.0);
        hourlyRates.put(VehicleType.VAN, 15.0);
    }
    
    /**
     * Get singleton instance
     * @param name Parking lot name (only used on first call)
     * @param numberOfFloors Number of floors (only used on first call)
     * @return ParkingLot instance
     */
    public static ParkingLot getInstance(String name, int numberOfFloors) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ParkingLot(name, numberOfFloors);
                }
            }
        }
        return instance;
    }
    
    public static ParkingLot getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ParkingLot not initialized");
        }
        return instance;
    }
    
    /**
     * Park a vehicle
     * @param vehicle Vehicle to park
     * @return Ticket if successful, null if no space
     */
    public Ticket parkVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null");
        }
        
        SpotType requiredType = vehicle.getRequiredSpotType();
        
        // Try to find spot on any floor
        for (Floor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(requiredType);
            
            if (spot != null && spot.parkVehicle(vehicle)) {
                // Create ticket
                String ticketNumber = "T" + ticketCounter.getAndIncrement();
                Ticket ticket = new Ticket(ticketNumber, vehicle, spot);
                activeTickets.put(ticketNumber, ticket);
                
                System.out.println("Vehicle " + vehicle.getRegistrationNumber() + 
                                   " parked at " + spot.getSpotNumber() + 
                                   " on floor " + spot.getFloor());
                
                return ticket;
            }
        }
        
        System.out.println("No available spot for vehicle " + 
                           vehicle.getRegistrationNumber());
        return null;
    }
    
    /**
     * Unpark vehicle and calculate fee
     * @param ticketNumber Ticket number
     * @return Ticket with calculated fee, or null if not found
     */
    public Ticket unparkVehicle(String ticketNumber) {
        Ticket ticket = activeTickets.remove(ticketNumber);
        
        if (ticket == null) {
            System.out.println("Invalid ticket number: " + ticketNumber);
            return null;
        }
        
        // Remove vehicle from spot
        ParkingSpot spot = ticket.getSpot();
        Vehicle vehicle = spot.removeVehicle();
        
        // Calculate fee
        double hourlyRate = hourlyRates.get(vehicle.getVehicleType());
        double fee = ticket.calculateFee(hourlyRate);
        
        System.out.println("Vehicle " + vehicle.getRegistrationNumber() + 
                           " exited. Duration: " + ticket.getDurationInHours() + 
                           " hours. Fee: $" + fee);
        
        return ticket;
    }
    
    /**
     * Get available spot count by type
     * @param type Spot type
     * @return Number of available spots
     */
    public int getAvailableSpots(SpotType type) {
        return floors.stream()
            .mapToInt(floor -> floor.getAvailableCount(type))
            .sum();
    }
    
    /**
     * Check if parking lot is full
     * @return true if no spots available
     */
    public boolean isFull() {
        for (SpotType type : SpotType.values()) {
            if (getAvailableSpots(type) > 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Display current availability
     */
    public void displayAvailability() {
        System.out.println("\n=== " + name + " Availability ===");
        for (SpotType type : SpotType.values()) {
            System.out.println(type + ": " + getAvailableSpots(type) + " spots available");
        }
        System.out.println("Active vehicles: " + activeTickets.size());
        System.out.println();
    }
    
    // For testing: Add spots to floors
    public void addParkingSpot(int floorNumber, ParkingSpot spot) {
        if (floorNumber < 1 || floorNumber > floors.size()) {
            throw new IllegalArgumentException("Invalid floor number");
        }
        floors.get(floorNumber - 1).addSpot(spot);
    }
}
```

---

## 4️⃣ Data Flow

### Flow 1: Park Vehicle (Success)

**Sequence**:
1. Vehicle arrives at entrance
2. System calls `parkVehicle(vehicle)`
3. Determine required spot type based on vehicle
4. Iterate through floors to find available spot
5. Floor 1 searched: `findAvailableSpot(REGULAR)`
6. Available spot found: "A-101"
7. Call `spot.parkVehicle(vehicle)` - synchronized
8. Spot marks itself as occupied
9. Generate unique ticket number: "T1001"
10. Create Ticket object with entry time
11. Store ticket in activeTickets map
12. Return ticket to customer
13. Print confirmation message

**Sequence Diagram**:
```
Customer → ParkingLot: parkVehicle(Car("ABC123"))
ParkingLot → Car: getRequiredSpotType()
Car → ParkingLot: REGULAR
ParkingLot → Floor1: findAvailableSpot(REGULAR)
Floor1 → Floor1: iterate spots
Floor1 → ParkingLot: Spot("A-101")
ParkingLot → Spot: parkVehicle(Car)
Spot → Spot: set occupied=true
Spot → ParkingLot: true
ParkingLot → ParkingLot: generateTicket()
ParkingLot → Customer: Ticket("T1001")
```

**Time**: ~50-100ms

### Flow 2: Park Vehicle (No Space)

**Sequence**:
1. Vehicle arrives
2. System calls `parkVehicle(vehicle)`
3. Required spot type: LARGE (truck)
4. Iterate through all floors
5. Floor 1: No LARGE spots available
6. Floor 2: No LARGE spots available
7. Floor 3: No LARGE spots available
8. All floors exhausted
9. Return null
10. Print "No available spot" message

**Visual**:
```
Floor 1: COMPACT [X][X]  REGULAR [X][X]  LARGE [X]
Floor 2: COMPACT [X][ ]  REGULAR [X][X]  LARGE [X]
Floor 3: COMPACT [ ][ ]  REGULAR [X][ ]  LARGE [X]

Truck needs LARGE → All LARGE spots occupied → Park fails
```

### Flow 3: Unpark Vehicle and Calculate Fee

**Sequence**:
1. Customer presents ticket: "T1001"
2. System calls `unparkVehicle("T1001")`
3. Lookup ticket in activeTickets map
4. Ticket found with:
   - Vehicle: Car("ABC123")
   - Spot: "A-101"
   - Entry: 10:00 AM
5. Get current time: 12:30 PM
6. Calculate duration: 2.5 hours → round up to 3 hours
7. Get hourly rate for CAR: $10/hour
8. Calculate fee: 3 hours × $10 = $30
9. Call `spot.removeVehicle()`
10. Spot clears occupied flag
11. Remove ticket from activeTickets
12. Return ticket with fee to customer
13. Print exit confirmation

**Mathematical Calculation**:
```
Entry: 10:00 AM
Exit: 12:30 PM
Duration: 150 minutes

Round up: ⌈150/60⌉ = ⌈2.5⌉ = 3 hours

Fee = 3 hours × $10/hour = $30
```

### Flow 4: Spot Upgrade (Motorcycle in Regular Spot)

**Scenario**: Motorcycle needs COMPACT but only REGULAR available

**Sequence**:
1. Motorcycle arrives (requires COMPACT)
2. Check Floor 1 COMPACT spots: All occupied
3. Check Floor 1 REGULAR spots: Available!
4. Regular spot CAN fit motorcycle (size hierarchy)
5. Park motorcycle in REGULAR spot
6. Issue ticket
7. Motorcycle uses larger spot than needed

**Visual**:
```
Spot Size Hierarchy:
LARGE (4 units) ──► Can fit: TRUCK, VAN, CAR, MOTORCYCLE
REGULAR (2 units) ──► Can fit: CAR, MOTORCYCLE  
COMPACT (1 unit) ──► Can fit: MOTORCYCLE

Motorcycle (needs 1 unit):
  ✓ Can use COMPACT
  ✓ Can use REGULAR (upgrade)
  ✓ Can use LARGE (upgrade)
```

### Flow 5: Display Availability

**Sequence**:
1. System calls `displayAvailability()`
2. For each SpotType (COMPACT, REGULAR, LARGE):
3. Iterate all floors
4. Count available spots of that type
5. Sum across floors
6. Print totals
7. Show active vehicle count

**Output**:
```
=== City Center Parking Availability ===
COMPACT: 15 spots available
REGULAR: 8 spots available
LARGE: 2 spots available
Active vehicles: 37
```

---

## 5️⃣ Design

### Class Diagram

```
┌─────────────────────────────────┐
│  <<enum>> VehicleType           │
│  + MOTORCYCLE                   │
│  + CAR                          │
│  + TRUCK                        │
│  + VAN                          │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  <<enum>> SpotType              │
│  + COMPACT                      │
│  + REGULAR                      │
│  + LARGE                        │
│  + HANDICAPPED                  │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  <<abstract>> Vehicle           │
│  - registrationNumber: String   │
│  - vehicleType: VehicleType     │
│  + getRequiredSpotType()        │
└────────────▲────────────────────┘
             │
             │ extends
    ┌────────┴─────┬──────────┬────────┐
    │              │          │        │
┌───▼────┐  ┌──────▼──┐  ┌───▼───┐  ┌▼──┐
│Motorcycle  │   Car   │  │ Truck │  │Van│
└────────┘  └─────────┘  └───────┘  └───┘

┌─────────────────────────────────┐
│  ParkingSpot                    │
│  - spotNumber: String           │
│  - spotType: SpotType           │
│  - floor: int                   │
│  - occupied: boolean            │
│  - currentVehicle: Vehicle      │
│  + canFitVehicle()              │
│  + parkVehicle()                │
│  + removeVehicle()              │
└─────────────────────────────────┘
           ▲
           │ contains
           │
┌──────────┴──────────────────────┐
│  Floor                          │
│  - floorNumber: int             │
│  - spots: List<ParkingSpot>     │
│  + findAvailableSpot()          │
│  + getAvailableCount()          │
└─────────────────────────────────┘
           ▲
           │ contains
           │
┌──────────┴──────────────────────┐
│  ParkingLot <<Singleton>>       │
│  - floors: List<Floor>          │
│  - activeTickets: Map           │
│  - hourlyRates: Map             │
│  + parkVehicle()                │
│  + unparkVehicle()              │
│  + getAvailableSpots()          │
│  + isFull()                     │
└─────────────┬───────────────────┘
              │
              │ creates/manages
              ▼
┌─────────────────────────────────┐
│  Ticket                         │
│  - ticketNumber: String         │
│  - vehicle: Vehicle             │
│  - spot: ParkingSpot            │
│  - entryTime: LocalDateTime     │
│  - exitTime:
