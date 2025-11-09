import java.util.*;

// ==================== Enums ====================
// Why Enums? Type safety, prevents invalid states, self-documenting code

enum Direction {
    UP,
    DOWN,
    IDLE
    // Enum prevents invalid directions like "SIDEWAYS"
}

enum ElevatorState {
    IDLE,           // Not moving, no requests
    MOVING_UP,      // Moving to upper floor
    MOVING_DOWN,    // Moving to lower floor
    STOPPED,        // Doors open at floor
    MAINTENANCE     // Out of service
}

enum DoorState {
    OPEN,
    CLOSED,
    OPENING,
    CLOSING
}

enum RequestType {
    EXTERNAL_UP,    // Up button pressed on floor
    EXTERNAL_DOWN,  // Down button pressed on floor
    INTERNAL        // Destination button inside elevator
}

// ==================== Request ====================
// Represents a request to use elevator (external or internal)

class Request {
    private int floor;
    private RequestType type;
    private Direction direction;  // For external requests
    private long timestamp;

    public Request(int floor, RequestType type, Direction direction) {
        this.floor = floor;
        this.type = type;
        this.direction = direction;
        this.timestamp = System.currentTimeMillis();
    }

    public int getFloor() { return floor; }
    public RequestType getType() { return type; }
    public Direction getDirection() { return direction; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        if (type == RequestType.INTERNAL) {
            return "Internal request to floor " + floor;
        }
        return "External " + direction + " request from floor " + floor;
    }
}

// ==================== Button ====================
// Represents buttons (inside elevator and on floors)

class Button {
    private int floor;
    private boolean pressed;

    public Button(int floor) {
        this.floor = floor;
        this.pressed = false;
    }

    public void press() {
        this.pressed = true;
    }

    public void reset() {
        this.pressed = false;
    }

    public boolean isPressed() {
        return pressed;
    }

    public int getFloor() {
        return floor;
    }
}

// ==================== Display ====================
// Shows current floor and direction

class Display {
    private int currentFloor;
    private Direction direction;

    public Display() {
        this.currentFloor = 1;
        this.direction = Direction.IDLE;
    }

    public void update(int floor, Direction direction) {
        this.currentFloor = floor;
        this.direction = direction;
    }

    public void show() {
        String dirSymbol = direction == Direction.UP ? "↑" : 
                          direction == Direction.DOWN ? "↓" : "-";
        System.out.println("Display: Floor " + currentFloor + " " + dirSymbol);
    }

    public int getCurrentFloor() {
        return currentFloor;
    }
}

// ==================== Door ====================
// Represents elevator doors

class Door {
    private DoorState state;

    public Door() {
        this.state = DoorState.CLOSED;
    }

    public void open() {
        state = DoorState.OPENING;
        System.out.println("Doors opening...");
        // Simulate door opening time
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        state = DoorState.OPEN;
        System.out.println("Doors open");
    }

    public void close() {
        state = DoorState.CLOSING;
        System.out.println("Doors closing...");
        // Simulate door closing time
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        state = DoorState.CLOSED;
        System.out.println("Doors closed");
    }

    public DoorState getState() {
        return state;
    }
}

// ==================== Elevator State Pattern ====================
// Why State Pattern? Elevator behavior changes based on current state
// Benefits: Encapsulates state-specific behavior, easy state transitions

interface ElevatorStateInterface {
    void moveUp(Elevator elevator);
    void moveDown(Elevator elevator);
    void stop(Elevator elevator);
    void openDoors(Elevator elevator);
    void closeDoors(Elevator elevator);
}

// Idle State - waiting for requests
class IdleState implements ElevatorStateInterface {
    @Override
    public void moveUp(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " starting to move UP");
        elevator.setState(new MovingUpState());
    }

    @Override
    public void moveDown(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " starting to move DOWN");
        elevator.setState(new MovingDownState());
    }

    @Override
    public void stop(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " already stopped");
    }

    @Override
    public void openDoors(Elevator elevator) {
        elevator.getDoor().open();
    }

    @Override
    public void closeDoors(Elevator elevator) {
        elevator.getDoor().close();
    }
}

// Moving Up State
class MovingUpState implements ElevatorStateInterface {
    @Override
    public void moveUp(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " already moving UP");
    }

    @Override
    public void moveDown(Elevator elevator) {
        System.out.println("Cannot move DOWN while moving UP");
    }

    @Override
    public void stop(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " stopping");
        elevator.setState(new StoppedState());
    }

    @Override
    public void openDoors(Elevator elevator) {
        System.out.println("Cannot open doors while moving");
    }

    @Override
    public void closeDoors(Elevator elevator) {
        System.out.println("Doors already closed while moving");
    }
}

// Moving Down State
class MovingDownState implements ElevatorStateInterface {
    @Override
    public void moveUp(Elevator elevator) {
        System.out.println("Cannot move UP while moving DOWN");
    }

    @Override
    public void moveDown(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " already moving DOWN");
    }

    @Override
    public void stop(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " stopping");
        elevator.setState(new StoppedState());
    }

    @Override
    public void openDoors(Elevator elevator) {
        System.out.println("Cannot open doors while moving");
    }

    @Override
    public void closeDoors(Elevator elevator) {
        System.out.println("Doors already closed while moving");
    }
}

// Stopped State - at a floor with doors can open
class StoppedState implements ElevatorStateInterface {
    @Override
    public void moveUp(Elevator elevator) {
        if (elevator.getDoor().getState() == DoorState.CLOSED) {
            elevator.setState(new MovingUpState());
        } else {
            System.out.println("Close doors before moving");
        }
    }

    @Override
    public void moveDown(Elevator elevator) {
        if (elevator.getDoor().getState() == DoorState.CLOSED) {
            elevator.setState(new MovingDownState());
        } else {
            System.out.println("Close doors before moving");
        }
    }

    @Override
    public void stop(Elevator elevator) {
        System.out.println("Elevator " + elevator.getId() + " already stopped");
    }

    @Override
    public void openDoors(Elevator elevator) {
        elevator.getDoor().open();
    }

    @Override
    public void closeDoors(Elevator elevator) {
        elevator.getDoor().close();
        elevator.setState(new IdleState());
    }
}

// Maintenance State (for out-of-service elevators)
class MaintenanceState implements ElevatorStateInterface {
    @Override
    public void moveUp(Elevator elevator) {
        System.out.println("Elevator in maintenance, cannot move");
    }

    @Override
    public void moveDown(Elevator elevator) {
        System.out.println("Elevator in maintenance, cannot move");
    }

    @Override
    public void stop(Elevator elevator) {
        System.out.println("Elevator in maintenance");
    }

    @Override
    public void openDoors(Elevator elevator) {
        System.out.println("Elevator in maintenance");
    }

    @Override
    public void closeDoors(Elevator elevator) {
        System.out.println("Elevator in maintenance");
    }
}

// ==================== Elevator ====================
// Represents a single elevator car

class Elevator {
    private int id;
    private int currentFloor;
    private Direction currentDirection;
    private ElevatorStateInterface state;
    private Door door;
    private Display display;
    private List<Button> buttons;  // Internal buttons
    private int maxFloor;
    private int minFloor;
    
    // Pending requests for this elevator
    // Package-private for access by scheduling strategies
    TreeSet<Integer> upRequests;    // Sorted ascending
    TreeSet<Integer> downRequests;  // Sorted descending

    public Elevator(int id, int minFloor, int maxFloor) {
        this.id = id;
        this.currentFloor = minFloor;
        this.currentDirection = Direction.IDLE;
        this.state = new IdleState();
        this.door = new Door();
        this.display = new Display();
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.buttons = new ArrayList<>();
        
        // TreeSet automatically sorts (ascending for up, need custom comparator for down)
        this.upRequests = new TreeSet<>();
        this.downRequests = new TreeSet<>(Collections.reverseOrder());
        
        // Create internal buttons
        for (int i = minFloor; i <= maxFloor; i++) {
            buttons.add(new Button(i));
        }
    }

    // Getters
    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getCurrentDirection() { return currentDirection; }
    public Door getDoor() { return door; }
    public Display getDisplay() { return display; }
    public ElevatorStateInterface getState() { return state; }

    // State management
    public void setState(ElevatorStateInterface newState) {
        this.state = newState;
    }

    // Add request to elevator's queue
    public void addRequest(int floor, Direction direction) {
        if (direction == Direction.UP || direction == Direction.IDLE) {
            upRequests.add(floor);
        }
        if (direction == Direction.DOWN || direction == Direction.IDLE) {
            downRequests.add(floor);
        }
    }

    // Check if elevator should stop at floor
    private boolean shouldStopAtFloor(int floor) {
        if (currentDirection == Direction.UP) {
            return upRequests.contains(floor);
        } else if (currentDirection == Direction.DOWN) {
            return downRequests.contains(floor);
        }
        return upRequests.contains(floor) || downRequests.contains(floor);
    }

    // Move one floor in current direction
    public void move() {
        if (currentDirection == Direction.UP) {
            currentFloor++;
            System.out.println("Elevator " + id + " moving UP to floor " + currentFloor);
        } else if (currentDirection == Direction.DOWN) {
            currentFloor--;
            System.out.println("Elevator " + id + " moving DOWN to floor " + currentFloor);
        }
        
        display.update(currentFloor, currentDirection);
        
        // Check if should stop at this floor
        if (shouldStopAtFloor(currentFloor)) {
            stop();
        }
    }

    // Stop at current floor
    private void stop() {
        state.stop(this);
        
        // Remove this floor from requests
        upRequests.remove(currentFloor);
        downRequests.remove(currentFloor);
        
        // Open doors
        state.openDoors(this);
        
        // Wait for passengers
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        // Close doors
        state.closeDoors(this);
        
        // Determine next direction
        updateDirection();
    }

    // Update direction based on pending requests
    private void updateDirection() {
        if (currentDirection == Direction.UP) {
            // Check if more requests above
            if (!upRequests.isEmpty() && upRequests.first() > currentFloor) {
                // Continue UP
                state.moveUp(this);
            } else if (!downRequests.isEmpty()) {
                // Switch to DOWN
                currentDirection = Direction.DOWN;
                state.moveDown(this);
            } else {
                // No more requests
                currentDirection = Direction.IDLE;
                System.out.println("Elevator " + id + " now IDLE at floor " + currentFloor);
            }
        } else if (currentDirection == Direction.DOWN) {
            // Check if more requests below
            if (!downRequests.isEmpty() && downRequests.first() < currentFloor) {
                // Continue DOWN
                state.moveDown(this);
            } else if (!upRequests.isEmpty()) {
                // Switch to UP
                currentDirection = Direction.UP;
                state.moveUp(this);
            } else {
                // No more requests
                currentDirection = Direction.IDLE;
                System.out.println("Elevator " + id + " now IDLE at floor " + currentFloor);
            }
        } else {
            // Currently IDLE, pick direction based on requests
            if (!upRequests.isEmpty()) {
                currentDirection = Direction.UP;
                state.moveUp(this);
            } else if (!downRequests.isEmpty()) {
                currentDirection = Direction.DOWN;
                state.moveDown(this);
            }
        }
    }

    // Process next move (called by controller)
    public void processNextMove() {
        if (currentDirection != Direction.IDLE) {
            move();
        }
    }

    // Check if elevator can handle request
    public boolean canHandleRequest(int floor, Direction direction) {
        // Elevator going up and request is above and also going up
        if (currentDirection == Direction.UP && 
            direction == Direction.UP && 
            floor >= currentFloor) {
            return true;
        }
        
        // Elevator going down and request is below and also going down
        if (currentDirection == Direction.DOWN && 
            direction == Direction.DOWN && 
            floor <= currentFloor) {
            return true;
        }
        
        // Elevator is idle
        if (currentDirection == Direction.IDLE) {
            return true;
        }
        
        return false;
    }

    // Calculate distance to request
    public int distanceToRequest(int floor) {
        return Math.abs(currentFloor - floor);
    }

    public boolean hasRequests() {
        return !upRequests.isEmpty() || !downRequests.isEmpty();
    }

    @Override
    public String toString() {
        return "Elevator " + id + " at floor " + currentFloor + 
               " going " + currentDirection + 
               " (" + (upRequests.size() + downRequests.size()) + " requests)";
    }
}

// ==================== Scheduling Strategy (Strategy Pattern) ====================
// Why Strategy Pattern? Different algorithms for elevator dispatch
// Benefits: Interchangeable, testable, easy to add new strategies

interface SchedulingStrategy {
    Elevator selectElevator(List<Elevator> elevators, Request request);
}

// Strategy 1: Nearest elevator
class NearestElevatorStrategy implements SchedulingStrategy {
    @Override
    public Elevator selectElevator(List<Elevator> elevators, Request request) {
        Elevator nearest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (Elevator elevator : elevators) {
            // Skip if in maintenance
            if (elevator.getState() instanceof MaintenanceState) {
                continue;
            }
            
            int distance = elevator.distanceToRequest(request.getFloor());
            
            // Prefer elevator that can handle request en route
            if (elevator.canHandleRequest(request.getFloor(), request.getDirection())) {
                distance = distance / 2;  // Bonus for same direction
            }
            
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }
        
        return nearest;
    }
}

// Strategy 2: Load balancing (distribute requests evenly)
class LoadBalancingStrategy implements SchedulingStrategy {
    @Override
    public Elevator selectElevator(List<Elevator> elevators, Request request) {
        Elevator leastLoaded = null;
        int minRequests = Integer.MAX_VALUE;
        
        for (Elevator elevator : elevators) {
            if (elevator.getState() instanceof MaintenanceState) {
                continue;
            }
            
            // Count pending requests
            int requestCount = elevator.upRequests.size() + elevator.downRequests.size();
            
            if (requestCount < minRequests) {
                minRequests = requestCount;
                leastLoaded = elevator;
            }
        }
        
        return leastLoaded;
    }
}

// Strategy 3: Zone-based (each elevator handles specific floors)
class ZoneBasedStrategy implements SchedulingStrategy {
    private Map<Integer, List<Elevator>> zoneMap;

    public ZoneBasedStrategy(List<Elevator> elevators, int floorsPerZone) {
        this.zoneMap = new HashMap<>();
        // Assign elevators to zones (simplified)
        int zone = 0;
        for (Elevator elevator : elevators) {
            zoneMap.computeIfAbsent(zone, k -> new ArrayList<>()).add(elevator);
            zone = (zone + 1) % 3;  // Assuming 3 zones
        }
    }

    @Override
    public Elevator selectElevator(List<Elevator> elevators, Request request) {
        // Determine zone for requested floor
        int zone = request.getFloor() / 10;  // Simplified: every 10 floors is a zone
        
        List<Elevator> zoneElevators = zoneMap.getOrDefault(zone, elevators);
        
        // Among zone elevators, pick nearest
        Elevator nearest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (Elevator elevator : zoneElevators) {
            if (elevator.getState() instanceof MaintenanceState) {
                continue;
            }
            
            int distance = elevator.distanceToRequest(request.getFloor());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }
        
        return nearest != null ? nearest : elevators.get(0);
    }
}

// ==================== Elevator Controller (Singleton) ====================
// Why Singleton? Central controller for all elevators in building
// Manages dispatch logic, coordinates elevators

class ElevatorController {
    private static ElevatorController instance;
    private List<Elevator> elevators;
    private Queue<Request> pendingRequests;
    private SchedulingStrategy strategy;

    private ElevatorController() {
        this.elevators = new ArrayList<>();
        this.pendingRequests = new LinkedList<>();
        this.strategy = new NearestElevatorStrategy();  // Default
    }

    public static synchronized ElevatorController getInstance() {
        if (instance == null) {
            instance = new ElevatorController();
        }
        return instance;
    }

    public void addElevator(Elevator elevator) {
        elevators.add(elevator);
    }

    public void setStrategy(SchedulingStrategy strategy) {
        this.strategy = strategy;
    }

    // External request (from floor button)
    public void requestElevator(int floor, Direction direction) {
        System.out.println("\n--- Request: " + direction + " from floor " + floor + " ---");
        
        RequestType type = direction == Direction.UP ? 
                          RequestType.EXTERNAL_UP : RequestType.EXTERNAL_DOWN;
        Request request = new Request(floor, type, direction);
        
        // Select best elevator using strategy
        Elevator selectedElevator = strategy.selectElevator(elevators, request);
        
        if (selectedElevator != null) {
            System.out.println("Assigned to Elevator " + selectedElevator.getId());
            selectedElevator.addRequest(floor, direction);
        } else {
            System.out.println("No available elevator, queuing request");
            pendingRequests.add(request);
        }
    }

    // Internal request (destination button inside elevator)
    public void requestFloor(int elevatorId, int floor) {
        Elevator elevator = elevators.get(elevatorId);
        System.out.println("\n--- Internal: Elevator " + elevatorId + 
                         " requested floor " + floor + " ---");
        
        // Determine direction
        Direction direction = floor > elevator.getCurrentFloor() ? 
                            Direction.UP : Direction.DOWN;
        elevator.addRequest(floor, direction);
    }

    // Run elevator system (simulation)
    public void run() {
        System.out.println("\n=== Elevator System Running ===");
        
        // Simulate time steps
        for (int step = 0; step < 20; step++) {
            System.out.println("\n--- Time Step " + step + " ---");
            
            // Process each elevator
            for (Elevator elevator : elevators) {
                if (elevator.hasRequests()) {
                    elevator.processNextMove();
                }
            }
            
            // Show status
            showStatus();
            
            // Simulate time passing
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }
    }

    public void showStatus() {
        System.out.println("\nCurrent Status:");
        for (Elevator elevator : elevators) {
            System.out.println("  " + elevator);
        }
    }
}

// ==================== Building ====================
// Represents the building with floors and elevators

class Building {
    private int floors;
    private List<Elevator> elevators;
    private ElevatorController controller;

    public Building(int floors, int numElevators) {
        this.floors = floors;
        this.elevators = new ArrayList<>();
        this.controller = ElevatorController.getInstance();
        
        // Create elevators
        for (int i = 0; i < numElevators; i++) {
            Elevator elevator = new Elevator(i, 1, floors);
            elevators.add(elevator);
            controller.addElevator(elevator);
        }
    }

    public ElevatorController getController() {
        return controller;
    }

    public List<Elevator> getElevators() {
        return elevators;
    }

    public int getFloors() {
        return floors;
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates elevator system with various scenarios

public class ElevatorSystem {
    public static void main(String[] args) {
        System.out.println("========== Elevator System Demo ==========\n");
        
        // Create building with 10 floors and 2 elevators
        Building building = new Building(10, 2);
        ElevatorController controller = building.getController();
        
        System.out.println("Building: 10 floors, 2 elevators");
        System.out.println("Starting configuration:");
        controller.showStatus();
        
        // ===== Scenario 1: External Requests =====
        System.out.println("\n\n===== Scenario 1: External Requests =====");
        
        // Person on floor 3 wants to go UP
        controller.requestElevator(3, Direction.UP);
        
        // Person on floor 7 wants to go DOWN
        controller.requestElevator(7, Direction.DOWN);
        
        // ===== Scenario 2: Internal Requests =====
        System.out.println("\n\n===== Scenario 2: Internal Destinations =====");
        
        // Person in Elevator 0 wants to go to floor 5
        controller.requestFloor(0, 5);
        
        // Person in Elevator 0 wants to go to floor 8
        controller.requestFloor(0, 8);
        
        // Person in Elevator 1 wants to go to floor 2
        controller.requestFloor(1, 2);
        
        // ===== Run Simulation =====
        controller.run();
        
        System.out.println("\n\n===== Final Status =====");
        controller.showStatus();
        
        // ===== Demonstrate Different Strategies =====
        System.out.println("\n\n===== Testing Different Strategies =====");
        
        // Reset elevators
        Building building2 = new Building(10, 3);
        ElevatorController controller2 = building2.getController();
        
        System.out.println("\n--- Strategy: Nearest Elevator ---");
        controller2.setStrategy(new NearestElevatorStrategy());
        controller2.requestElevator(5, Direction.UP);
        controller2.showStatus();
        
        System.out.println("\n--- Strategy: Load Balancing ---");
        controller2.setStrategy(new LoadBalancingStrategy());
        controller2.requestElevator(6, Direction.UP);
        controller2.requestElevator(7, Direction.DOWN);
        controller2.showStatus();
        
        System.out.println("\n========== Demo Complete ==========");
    }
}
