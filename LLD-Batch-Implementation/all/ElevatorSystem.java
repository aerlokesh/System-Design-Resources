import java.util.*;

/**
 * Elevator System (Single Lift) - HELLO Interview Framework
 * 
 * Companies: Microsoft, Adobe, Oracle +10 more
 * Pattern: State Pattern + SCAN (LOOK) Algorithm
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. State Pattern — elevator behavior changes by state (IDLE, MOVING_UP, MOVING_DOWN, DOOR_OPEN)
 * 2. SCAN Algorithm — serve all requests in one direction before reversing (like disk arm)
 * 3. TreeSet — O(log n) insert/remove, sorted, ceiling()/floor() for directional scanning
 * 4. Synchronized — thread-safe for concurrent button presses
 */

// ==================== Enums ====================

enum Direction { UP, DOWN, IDLE }

enum ElevatorState { IDLE, MOVING_UP, MOVING_DOWN, DOOR_OPEN }

enum RequestType { EXTERNAL_UP, EXTERNAL_DOWN, INTERNAL }

enum DoorState { OPEN, CLOSED }

// ==================== Request ====================

class Request {
    private final int floor;
    private final RequestType type;
    private final Direction direction;
    private final long timestamp;

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
        return type == RequestType.INTERNAL
            ? "Internal→Floor " + floor
            : "External " + direction + "→Floor " + floor;
    }
}

// ==================== Door ====================

class Door {
    private DoorState state;

    public Door() { this.state = DoorState.CLOSED; }

    public synchronized void open() {
        if (state != DoorState.OPEN) {
            state = DoorState.OPEN;
            System.out.println("    🚪 Door OPENED");
        }
    }

    public synchronized void close() {
        if (state != DoorState.CLOSED) {
            state = DoorState.CLOSED;
            System.out.println("    🚪 Door CLOSED");
        }
    }

    public boolean isOpen() { return state == DoorState.OPEN; }
    public DoorState getState() { return state; }
}

// ==================== Elevator ====================
// Core entity: manages floor, state, direction, processes requests using SCAN

class Elevator {
    private int currentFloor;
    private final int minFloor;
    private final int maxFloor;
    private ElevatorState state;
    private Direction direction;
    private final Door door;

    // SCAN algorithm: two sorted sets for bidirectional requests
    // TreeSet chosen over PriorityQueue because:
    //   - O(log n) contains(), insert, remove
    //   - ceiling() / floor() for directional next-stop lookup
    //   - No duplicates (pressing same floor twice = no-op)
    private final TreeSet<Integer> upRequests;
    private final TreeSet<Integer> downRequests;

    public Elevator(int minFloor, int maxFloor) {
        if (minFloor > maxFloor) throw new IllegalArgumentException("minFloor must be <= maxFloor");
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = minFloor;
        this.state = ElevatorState.IDLE;
        this.direction = Direction.IDLE;
        this.door = new Door();
        this.upRequests = new TreeSet<>();
        this.downRequests = new TreeSet<>();
    }

    // ─── Request Routing ───

    public synchronized void addUpRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        upRequests.add(floor);
        if (state == ElevatorState.IDLE) determineDirection();
    }

    public synchronized void addDownRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        downRequests.add(floor);
        if (state == ElevatorState.IDLE) determineDirection();
    }

    /** Internal request — routes to up/down set based on relative position */
    public synchronized void addInternalRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        if (floor == currentFloor) return; // already here

        if (floor > currentFloor) {
            upRequests.add(floor);
        } else {
            downRequests.add(floor);
        }

        if (state == ElevatorState.IDLE) determineDirection();
    }

    // ─── Core Movement Logic (SCAN / LOOK Algorithm) ───

    /**
     * Advance elevator by one step. Called repeatedly by controller.
     * State Pattern: behavior differs based on current state.
     */
    public synchronized void move() {
        switch (state) {
            case IDLE:
                if (!upRequests.isEmpty() || !downRequests.isEmpty()) {
                    determineDirection();
                }
                break;

            case MOVING_UP:
                currentFloor++;
                System.out.println("    ⬆ Floor " + currentFloor);
                if (shouldStopAtCurrentFloor()) {
                    processCurrentFloor();
                }
                break;

            case MOVING_DOWN:
                currentFloor--;
                System.out.println("    ⬇ Floor " + currentFloor);
                if (shouldStopAtCurrentFloor()) {
                    processCurrentFloor();
                }
                break;

            case DOOR_OPEN:
                door.close();
                afterDoorClose();
                break;
        }
    }

    /** Check if we have a request at the current floor in our travel direction */
    private boolean shouldStopAtCurrentFloor() {
        if (direction == Direction.UP) return upRequests.contains(currentFloor);
        if (direction == Direction.DOWN) return downRequests.contains(currentFloor);
        return false;
    }

    /** Arrived at requested floor: open door, remove from request set */
    private void processCurrentFloor() {
        System.out.println("    🛑 STOPPED at Floor " + currentFloor);
        door.open();
        state = ElevatorState.DOOR_OPEN;
        upRequests.remove(currentFloor);
        downRequests.remove(currentFloor);
    }

    /** After door closes: continue same direction, reverse, or go idle */
    private void afterDoorClose() {
        // Try to continue in same direction
        if (direction == Direction.UP && !upRequests.isEmpty()) {
            Integer next = upRequests.ceiling(currentFloor);
            if (next != null) { state = ElevatorState.MOVING_UP; return; }
        }
        if (direction == Direction.DOWN && !downRequests.isEmpty()) {
            Integer next = downRequests.floor(currentFloor);
            if (next != null) { state = ElevatorState.MOVING_DOWN; return; }
        }

        // Reverse direction if requests in other direction
        if (direction == Direction.UP && !downRequests.isEmpty()) {
            direction = Direction.DOWN;
            state = ElevatorState.MOVING_DOWN;
        } else if (direction == Direction.DOWN && !upRequests.isEmpty()) {
            direction = Direction.UP;
            state = ElevatorState.MOVING_UP;
        } else {
            // No more requests
            state = ElevatorState.IDLE;
            direction = Direction.IDLE;
            System.out.println("    💤 IDLE");
        }
    }

    /** Pick initial direction when transitioning from IDLE */
    private void determineDirection() {
        if (state != ElevatorState.IDLE) return;

        boolean hasUp = !upRequests.isEmpty();
        boolean hasDown = !downRequests.isEmpty();

        // Prefer closer direction; tie-break: UP first
        if (hasUp && (!hasDown || upRequests.first() - currentFloor <= currentFloor - downRequests.last())) {
            direction = Direction.UP;
            state = ElevatorState.MOVING_UP;
            if (upRequests.contains(currentFloor)) processCurrentFloor();
        } else if (hasDown) {
            direction = Direction.DOWN;
            state = ElevatorState.MOVING_DOWN;
            if (downRequests.contains(currentFloor)) processCurrentFloor();
        }
    }

    // ─── Getters ───

    public int getCurrentFloor() { return currentFloor; }
    public ElevatorState getState() { return state; }
    public Direction getDirection() { return direction; }
    public int getMinFloor() { return minFloor; }
    public int getMaxFloor() { return maxFloor; }
    public boolean hasRequests() { return !upRequests.isEmpty() || !downRequests.isEmpty(); }

    public String getStatus() {
        return String.format("Floor=%d | State=%s | Dir=%s | Up=%s | Down=%s | Door=%s",
            currentFloor, state, direction, upRequests, downRequests, door.getState());
    }
}

// ==================== ElevatorController (Singleton) ====================
// Public API: validates requests, delegates to Elevator

class ElevatorController {
    private static volatile ElevatorController instance;
    private final Elevator elevator;

    private ElevatorController(int minFloor, int maxFloor) {
        this.elevator = new Elevator(minFloor, maxFloor);
    }

    public static ElevatorController getInstance(int minFloor, int maxFloor) {
        if (instance == null) {
            synchronized (ElevatorController.class) {
                if (instance == null) {
                    instance = new ElevatorController(minFloor, maxFloor);
                }
            }
        }
        return instance;
    }

    // Reset for testing (since singleton persists)
    public static void resetInstance() {
        synchronized (ElevatorController.class) { instance = null; }
    }

    /** External request: user presses UP/DOWN button on a floor */
    public boolean requestElevator(int floor, Direction direction) {
        validateExternalRequest(floor, direction);
        System.out.println("  📥 External: Floor " + floor + " " + direction);
        if (direction == Direction.UP) elevator.addUpRequest(floor);
        else elevator.addDownRequest(floor);
        return true;
    }

    /** Internal request: passenger presses destination floor inside */
    public boolean selectFloor(int floor) {
        validateFloor(floor);
        System.out.println("  📥 Internal: Floor " + floor);
        elevator.addInternalRequest(floor);
        return true;
    }

    /** Advance simulation by one step */
    public void step() { elevator.move(); }

    /** Run until all requests processed (with safety limit) */
    public void runToCompletion() {
        int steps = 0;
        while (elevator.hasRequests() || elevator.getState() == ElevatorState.DOOR_OPEN) {
            step();
            if (++steps > 500) {
                System.out.println("  ⚠️ Safety limit reached");
                break;
            }
        }
        System.out.println("  ✅ Done in " + steps + " steps\n");
    }

    public String getStatus() { return elevator.getStatus(); }
    public int getCurrentFloor() { return elevator.getCurrentFloor(); }

    // ─── Validation ───

    private void validateFloor(int floor) {
        if (floor < elevator.getMinFloor() || floor > elevator.getMaxFloor())
            throw new IllegalArgumentException("Floor " + floor + " out of range ["
                + elevator.getMinFloor() + "," + elevator.getMaxFloor() + "]");
    }

    private void validateExternalRequest(int floor, Direction direction) {
        validateFloor(floor);
        if (direction == Direction.IDLE)
            throw new IllegalArgumentException("External request must specify UP or DOWN");
        if (direction == Direction.UP && floor == elevator.getMaxFloor())
            throw new IllegalArgumentException("Cannot go UP from top floor");
        if (direction == Direction.DOWN && floor == elevator.getMinFloor())
            throw new IllegalArgumentException("Cannot go DOWN from bottom floor");
    }
}

// ==================== Main Demo ====================

public class ElevatorSystem {
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  Elevator System (Single Lift) - SCAN Algorithm ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        ElevatorController.resetInstance();
        ElevatorController ctrl = ElevatorController.getInstance(1, 10);

        // ── Scenario 1: Simple pickup ──
        System.out.println("━━━ Scenario 1: Pick up person on floor 5 (going UP) ━━━");
        System.out.println("  Status: " + ctrl.getStatus());
        ctrl.requestElevator(5, Direction.UP);
        ctrl.runToCompletion();
        System.out.println("  Status: " + ctrl.getStatus());

        // ── Scenario 2: Internal destination ──
        System.out.println("━━━ Scenario 2: Passenger selects floor 8 ━━━");
        ctrl.selectFloor(8);
        ctrl.runToCompletion();
        System.out.println("  Status: " + ctrl.getStatus());

        // ── Scenario 3: Multiple requests — SCAN in action ──
        System.out.println("━━━ Scenario 3: Multiple requests (SCAN demo) ━━━");
        System.out.println("  Elevator at floor 8. Requests: UP→10, Internal→3, DOWN@6, DOWN@2");
        ctrl.requestElevator(9, Direction.UP);     // go up first
        ctrl.selectFloor(3);                        // below → downRequests
        ctrl.requestElevator(6, Direction.DOWN);    // pickup at 6 going down
        ctrl.requestElevator(2, Direction.DOWN);    // pickup at 2 going down
        System.out.println("  Status: " + ctrl.getStatus());
        ctrl.runToCompletion();
        System.out.println("  Status: " + ctrl.getStatus());

        // ── Scenario 4: Edge case — request at current floor ──
        System.out.println("━━━ Scenario 4: Request at current floor ━━━");
        int curFloor = ctrl.getCurrentFloor();
        System.out.println("  Current floor: " + curFloor);
        if (curFloor < 10) {
            ctrl.requestElevator(curFloor, Direction.UP);
            ctrl.step(); // Should immediately open door
            System.out.println("  Status: " + ctrl.getStatus());
            ctrl.runToCompletion();
        }

        // ── Scenario 5: Error handling ──
        System.out.println("━━━ Scenario 5: Error handling ━━━");
        try {
            ctrl.requestElevator(15, Direction.UP); // floor out of range
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Caught: " + e.getMessage());
        }
        try {
            ctrl.requestElevator(10, Direction.UP); // can't go UP from top
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Caught: " + e.getMessage());
        }
        try {
            ctrl.requestElevator(1, Direction.DOWN); // can't go DOWN from bottom
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Caught: " + e.getMessage());
        }

        System.out.println("\n✅ All scenarios complete.");
    }
}
