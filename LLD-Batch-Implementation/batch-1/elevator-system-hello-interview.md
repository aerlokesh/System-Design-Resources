# Elevator System (Single Lift) - HELLO Interview Framework

> **Companies**: Microsoft, Adobe, Oracle +10 more  
> **Difficulty**: Medium  
> **Primary Pattern**: State  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview Elevator](https://www.hellointerview.com/learn/low-level-design/problem-breakdowns/elevator)

## Table of Contents
1. [Requirements](#1️⃣-requirements)
2. [Core Entities](#2️⃣-core-entities)
3. [API Design](#3️⃣-api-design)
4. [Data Flow](#4️⃣-data-flow)
5. [Design](#5️⃣-design)
6. [Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "How many floors does the building have?" → Configurable (1 to N)
- "Single lift or multiple lifts?" → **Single lift** (this problem)
- "Do we need to handle weight limits?" → Not for now, P1
- "Should we handle door open/close explicitly?" → Yes
- "Is this a real-time simulation or request processing?" → Request processing with step-based simulation

### Functional Requirements

#### Must Have (P0)
1. **Request Handling**
   - Accept external requests (floor button: UP/DOWN pressed on a floor)
   - Accept internal requests (destination button pressed inside elevator)
   - Queue and process requests efficiently

2. **Movement**
   - Move elevator between floors (1 to N)
   - Follow SCAN algorithm (elevator algorithm) — serve requests in current direction before reversing
   - Stop at requested floors, open/close doors

3. **State Management**
   - Track current floor, direction, and state (IDLE, MOVING_UP, MOVING_DOWN, STOPPED)
   - Handle door states (OPEN, CLOSED)

4. **Request Feasibility**
   - Validate if a request can be served (floor within range)
   - Determine optimal handling of requests based on current state

#### Nice to Have (P1)
- Weight/capacity limits
- Emergency stop and alarm
- Door obstruction handling
- VIP/priority floors
- Maintenance mode
- Multiple lifts (extends to Elevator Management System)

### Non-Functional Requirements
- **Thread Safety**: Multiple requests can arrive concurrently
- **Responsiveness**: Request acceptance < 10ms
- **Fairness**: No request starved indefinitely (SCAN guarantees this)
- **Memory**: O(N) where N = number of floors

### Constraints & Assumptions
- Single elevator, single building
- Floors numbered 1 to N (no basement for simplicity)
- Elevator starts at floor 1, IDLE
- Door open/close is instantaneous in simulation (configurable delay in real system)
- One step = one floor movement

### Out of Scope
- Physical motor control, sensors
- UI/display system
- Multi-elevator coordination
- Payment/access control

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────┐
│                ElevatorController                │
│  (Singleton - manages the system)               │
│  - elevator: Elevator                           │
│  - addRequest(Request): boolean                 │
│  - step(): void                                 │
│  - getStatus(): String                          │
└──────────────────────┬──────────────────────────┘
                       │ has-a
                       ▼
┌─────────────────────────────────────────────────┐
│                   Elevator                       │
│  - currentFloor: int                            │
│  - state: ElevatorState (State pattern)         │
│  - direction: Direction                         │
│  - door: Door                                   │
│  - upRequests: TreeSet<Integer>                 │
│  - downRequests: TreeSet<Integer>               │
│  - move(): void                                 │
│  - shouldStop(): boolean                        │
│  - processFloor(): void                         │
└──────────┬──────────────────────┬───────────────┘
           │ has-a               │ uses
           ▼                     ▼
┌──────────────────┐   ┌─────────────────────────┐
│       Door       │   │    ElevatorState (enum)  │
│  - state: enum   │   │    IDLE                  │
│  - open(): void  │   │    MOVING_UP             │
│  - close(): void │   │    MOVING_DOWN           │
│  - isOpen(): bool│   │    DOOR_OPEN             │
└──────────────────┘   └─────────────────────────┘
                       ┌─────────────────────────┐
                       │    Request               │
                       │  - floor: int            │
                       │  - type: RequestType     │
                       │  - direction: Direction  │
                       │  - timestamp: long       │
                       └─────────────────────────┘
```

### Enum: Direction
```
UP, DOWN, IDLE
```

### Enum: ElevatorState
```
IDLE        → No pending requests, stationary
MOVING_UP   → Traveling upward
MOVING_DOWN → Traveling downward
DOOR_OPEN   → Stopped at a floor, doors open
```
> **State Pattern**: The elevator's behavior changes based on its state. In IDLE, it picks a direction; in MOVING_UP, it only stops at floors above; in DOOR_OPEN, it transitions back to moving or idle.

### Enum: RequestType
```
EXTERNAL_UP   → Up button pressed on a floor
EXTERNAL_DOWN → Down button pressed on a floor
INTERNAL      → Destination button pressed inside
```

### Class: Request
**Responsibility**: Encapsulate a single request (external or internal)

| Attribute   | Type        | Description                     |
|-------------|-------------|---------------------------------|
| floor       | int         | Target floor                    |
| type        | RequestType | External up/down or internal    |
| direction   | Direction   | Desired direction (external)    |
| timestamp   | long        | When request was made           |

### Class: Door
**Responsibility**: Manage door open/close state

| Attribute | Type      | Description        |
|-----------|-----------|--------------------|
| state     | DoorState | OPEN or CLOSED     |

| Method  | Description              |
|---------|--------------------------|
| open()  | Opens door, sets OPEN    |
| close() | Closes door, sets CLOSED |
| isOpen()| Returns true if OPEN     |

### Class: Elevator
**Responsibility**: Core entity — tracks floor, state, direction, processes requests

| Attribute     | Type             | Description                              |
|---------------|------------------|------------------------------------------|
| currentFloor  | int              | Current floor position                   |
| minFloor      | int              | Lowest floor (1)                         |
| maxFloor      | int              | Highest floor (N)                        |
| state         | ElevatorState    | Current state                            |
| direction     | Direction        | Current travel direction                 |
| door          | Door             | Door controller                          |
| upRequests    | TreeSet<Integer> | Floors to visit going UP (sorted asc)    |
| downRequests  | TreeSet<Integer> | Floors to visit going DOWN (sorted desc) |

**Why TreeSet?**
- O(log n) insert/remove
- Sorted order → easily find next floor in direction
- `higher()` / `lower()` for directional scanning
- No duplicates (pressing same floor twice is a no-op)

### Class: ElevatorController (Singleton)
**Responsibility**: Public-facing controller, validates and routes requests

| Attribute | Type     | Description         |
|-----------|----------|---------------------|
| elevator  | Elevator | The managed elevator|

---

## 3️⃣ API Design

### ElevatorController (Public API)

```java
public class ElevatorController {
    private static ElevatorController instance;
    private Elevator elevator;

    /**
     * Get singleton instance
     */
    public static synchronized ElevatorController getInstance(int minFloor, int maxFloor);

    /**
     * Accept an external request (button pressed on a floor)
     * @param floor      The floor where button was pressed
     * @param direction  UP or DOWN
     * @return true if request accepted, false if invalid
     * @throws IllegalArgumentException if floor out of range
     */
    public boolean requestElevator(int floor, Direction direction);

    /**
     * Accept an internal request (passenger presses destination inside)
     * @param floor  Destination floor
     * @return true if request accepted
     * @throws IllegalArgumentException if floor out of range
     */
    public boolean selectFloor(int floor);

    /**
     * Advance the elevator by one step (simulation tick)
     * In real system, this would be event-driven
     */
    public void step();

    /**
     * Get current system status
     * @return Human-readable status string
     */
    public String getStatus();
}
```

### Elevator (Internal API)

```java
public class Elevator {
    /**
     * Add a floor to visit in the UP direction
     */
    void addUpRequest(int floor);

    /**
     * Add a floor to visit in the DOWN direction
     */
    void addDownRequest(int floor);

    /**
     * Move one step — process current floor or advance
     */
    void move();

    /**
     * Check if elevator should stop at current floor
     */
    boolean shouldStopAtCurrentFloor();

    /**
     * Process arriving at a floor — open door, remove from requests
     */
    void processCurrentFloor();

    /**
     * Determine next direction when current direction exhausted
     */
    void updateDirection();

    int getCurrentFloor();
    ElevatorState getState();
    Direction getDirection();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: External Request (User on floor 5 presses UP)

```
User → ElevatorController.requestElevator(5, UP)
  │
  ├─ Validate: 1 ≤ 5 ≤ maxFloor ✓
  ├─ Validate: direction = UP at floor 5 (not top floor) ✓
  │
  ├─ elevator.addUpRequest(5)
  │     └─ upRequests.add(5)  // TreeSet: O(log n)
  │
  └─ If elevator is IDLE:
        └─ Determine direction toward floor 5
           Set state = MOVING_UP or MOVING_DOWN
```

### Scenario 2: Step-by-Step Movement (SCAN Algorithm)

```
State: Floor=1, Direction=UP, upRequests={3, 7}, downRequests={2}

Step 1: move() → floor=2, shouldStop? No (2 not in upRequests) → continue
Step 2: move() → floor=3, shouldStop? YES (3 in upRequests)
         → openDoor, remove 3 from upRequests
         → state = DOOR_OPEN
Step 3: processCurrentFloor() → closeDoor, resume MOVING_UP
Step 4: move() → floor=4... continue up
...
Step 7: move() → floor=7, shouldStop? YES → process
         → upRequests now empty → check downRequests
         → downRequests={2} → switch direction to DOWN
Step 8: move() → floor=6 (going down now)
...
Step 12: move() → floor=2, shouldStop? YES → process
          → downRequests now empty → IDLE
```

### Scenario 3: Internal Request During Movement

```
Elevator at floor 4, MOVING_UP, upRequests={8}
Passenger inside presses floor 6:

ElevatorController.selectFloor(6)
  ├─ Current direction = UP, 6 > currentFloor(4)
  │   → elevator.addUpRequest(6)
  │   → upRequests = {6, 8} (TreeSet auto-sorted)
  │
  └─ Next stop: floor 6 (before 8, since TreeSet is sorted ascending)
```

### State Transition Diagram

```
                    ┌──────────────┐
                    │     IDLE     │◄──── no more requests
                    └──────┬───────┘
                           │ request arrives
                    ┌──────▼───────┐
               ┌────┤  DETERMINE   ├────┐
               │    │  DIRECTION   │    │
               │    └──────────────┘    │
               ▼                        ▼
      ┌────────────────┐      ┌────────────────┐
      │   MOVING_UP    │      │  MOVING_DOWN   │
      └───────┬────────┘      └────────┬───────┘
              │ arrived at              │ arrived at
              │ requested floor         │ requested floor
              ▼                         ▼
      ┌────────────────────────────────────────┐
      │              DOOR_OPEN                  │
      │  (passengers enter/exit)                │
      └────────────────┬───────────────────────┘
                       │ door closes
                       ▼
              ┌────────────────┐
              │ MORE REQUESTS? │
              │ same direction?│
              └───────┬────────┘
                 YES  │  NO
                 ↓    ↓
          continue  reverse or IDLE
```

---

## 5️⃣ Design

### Design Pattern: State Pattern (Primary)

**Why State Pattern?**
The elevator's behavior fundamentally changes based on its current state:
- **IDLE**: Accepts any request, picks direction toward it
- **MOVING_UP**: Only stops at floors above current, ignores below
- **MOVING_DOWN**: Only stops at floors below current, ignores above
- **DOOR_OPEN**: Waits, then transitions to moving or idle

Instead of massive if-else blocks checking state everywhere, each state encapsulates its own behavior.

**How it manifests in code:**
```
In move():
  switch(state) {
    IDLE       → check if requests exist, pick direction
    MOVING_UP  → increment floor, check if should stop
    MOVING_DOWN→ decrement floor, check if should stop
    DOOR_OPEN  → close door, decide next action
  }
```

### Design Pattern: SCAN Algorithm (Elevator Algorithm)

**Why SCAN (not FCFS)?**
- FCFS (First Come First Served) causes excessive back-and-forth
- SCAN serves all requests in one direction before reversing
- Like a disk arm — moves in one direction serving requests, then reverses
- **Guarantees fairness** — every request served in at most 2 full sweeps
- **Minimizes total travel** — reduces direction changes

**Implementation with TreeSet:**
```
upRequests:   TreeSet<Integer> (natural order: ascending)
downRequests: TreeSet<Integer> (natural order, iterated descending)

Going UP:   serve floor = upRequests.ceiling(currentFloor)  → next floor ≥ current
Going DOWN: serve floor = downRequests.floor(currentFloor)  → next floor ≤ current
```

### Data Structure Choices

| Structure | Purpose | Why |
|-----------|---------|-----|
| `TreeSet<Integer>` for upRequests | Floors to visit going up | Sorted, O(log n) ops, `ceiling()` for next floor |
| `TreeSet<Integer>` for downRequests | Floors to visit going down | Sorted, O(log n) ops, `floor()` for prev floor |
| `enum` for State/Direction | State management | Type-safe, prevents invalid states |

### Thread Safety Strategy
- `synchronized` on `addRequest` and `move` methods
- `ConcurrentSkipListSet` alternative for lock-free TreeSet
- In real system: use `ReentrantLock` with condition variables for blocking

---

### Complete Implementation

```java
import java.util.*;
import java.util.concurrent.*;

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
            System.out.println("  🚪 Door OPENED");
        }
    }

    public synchronized void close() {
        if (state != DoorState.CLOSED) {
            state = DoorState.CLOSED;
            System.out.println("  🚪 Door CLOSED");
        }
    }

    public boolean isOpen() { return state == DoorState.OPEN; }
    public DoorState getState() { return state; }
}

// ==================== Elevator ====================

class Elevator {
    private int currentFloor;
    private final int minFloor;
    private final int maxFloor;
    private ElevatorState state;
    private Direction direction;
    private final Door door;

    // SCAN algorithm: two sorted sets for bidirectional requests
    private final TreeSet<Integer> upRequests;    // floors to visit going UP
    private final TreeSet<Integer> downRequests;  // floors to visit going DOWN

    public Elevator(int minFloor, int maxFloor) {
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = minFloor;
        this.state = ElevatorState.IDLE;
        this.direction = Direction.IDLE;
        this.door = new Door();
        this.upRequests = new TreeSet<>();
        this.downRequests = new TreeSet<>();
    }

    // --- Request Routing ---

    public synchronized void addUpRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        upRequests.add(floor);
        if (state == ElevatorState.IDLE) {
            determineDirection();
        }
    }

    public synchronized void addDownRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        downRequests.add(floor);
        if (state == ElevatorState.IDLE) {
            determineDirection();
        }
    }

    /**
     * Add internal request — routes to appropriate set based on current direction
     */
    public synchronized void addInternalRequest(int floor) {
        if (floor < minFloor || floor > maxFloor) return;
        if (floor == currentFloor) return; // already here

        if (floor > currentFloor) {
            upRequests.add(floor);
        } else {
            downRequests.add(floor);
        }

        if (state == ElevatorState.IDLE) {
            determineDirection();
        }
    }

    // --- Core Movement Logic (SCAN Algorithm) ---

    public synchronized void move() {
        switch (state) {
            case IDLE:
                if (!upRequests.isEmpty() || !downRequests.isEmpty()) {
                    determineDirection();
                }
                break;

            case MOVING_UP:
                currentFloor++;
                System.out.println("  ⬆ Moving UP → Floor " + currentFloor);
                if (shouldStopAtCurrentFloor()) {
                    processCurrentFloor();
                }
                break;

            case MOVING_DOWN:
                currentFloor--;
                System.out.println("  ⬇ Moving DOWN → Floor " + currentFloor);
                if (shouldStopAtCurrentFloor()) {
                    processCurrentFloor();
                }
                break;

            case DOOR_OPEN:
                // Close door and decide next action
                door.close();
                afterDoorClose();
                break;
        }
    }

    private boolean shouldStopAtCurrentFloor() {
        if (direction == Direction.UP) {
            return upRequests.contains(currentFloor);
        } else if (direction == Direction.DOWN) {
            return downRequests.contains(currentFloor);
        }
        return false;
    }

    private void processCurrentFloor() {
        System.out.println("  🛑 STOPPED at Floor " + currentFloor);
        door.open();
        state = ElevatorState.DOOR_OPEN;

        // Remove from appropriate request set
        upRequests.remove(currentFloor);
        downRequests.remove(currentFloor);
    }

    private void afterDoorClose() {
        // Continue in current direction if more requests
        if (direction == Direction.UP && !upRequests.isEmpty()) {
            Integer next = upRequests.ceiling(currentFloor);
            if (next != null) {
                state = ElevatorState.MOVING_UP;
                return;
            }
        }
        if (direction == Direction.DOWN && !downRequests.isEmpty()) {
            Integer next = downRequests.floor(currentFloor);
            if (next != null) {
                state = ElevatorState.MOVING_DOWN;
                return;
            }
        }

        // Reverse direction if requests exist in other direction
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
            System.out.println("  💤 Elevator is now IDLE");
        }
    }

    private void determineDirection() {
        if (state != ElevatorState.IDLE) return;

        // If requests above, go up first
        if (!upRequests.isEmpty() && (downRequests.isEmpty()
                || upRequests.first() - currentFloor <= currentFloor - downRequests.last())) {
            direction = Direction.UP;
            state = ElevatorState.MOVING_UP;

            // If we're already at a requested floor
            if (upRequests.contains(currentFloor)) {
                processCurrentFloor();
            }
        } else if (!downRequests.isEmpty()) {
            direction = Direction.DOWN;
            state = ElevatorState.MOVING_DOWN;

            if (downRequests.contains(currentFloor)) {
                processCurrentFloor();
            }
        }
    }

    // --- Getters ---

    public int getCurrentFloor() { return currentFloor; }
    public ElevatorState getState() { return state; }
    public Direction getDirection() { return direction; }
    public Door getDoor() { return door; }
    public int getMinFloor() { return minFloor; }
    public int getMaxFloor() { return maxFloor; }
    public boolean hasRequests() { return !upRequests.isEmpty() || !downRequests.isEmpty(); }

    public String getStatus() {
        return String.format("Floor=%d | State=%s | Dir=%s | Up=%s | Down=%s | Door=%s",
            currentFloor, state, direction, upRequests, downRequests, door.getState());
    }
}

// ==================== ElevatorController (Singleton) ====================

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

    /**
     * External request: user on a floor presses UP or DOWN button
     */
    public boolean requestElevator(int floor, Direction direction) {
        if (!isValidFloor(floor)) {
            throw new IllegalArgumentException("Floor " + floor + " out of range");
        }
        if (direction == Direction.IDLE) {
            throw new IllegalArgumentException("External request must specify UP or DOWN");
        }
        if (direction == Direction.UP && floor == elevator.getMaxFloor()) {
            throw new IllegalArgumentException("Cannot go UP from top floor");
        }
        if (direction == Direction.DOWN && floor == elevator.getMinFloor()) {
            throw new IllegalArgumentException("Cannot go DOWN from bottom floor");
        }

        System.out.println("📥 External Request: Floor " + floor + " " + direction);

        if (direction == Direction.UP) {
            elevator.addUpRequest(floor);
        } else {
            elevator.addDownRequest(floor);
        }
        return true;
    }

    /**
     * Internal request: passenger inside presses a destination floor
     */
    public boolean selectFloor(int floor) {
        if (!isValidFloor(floor)) {
            throw new IllegalArgumentException("Floor " + floor + " out of range");
        }

        System.out.println("📥 Internal Request: Floor " + floor);
        elevator.addInternalRequest(floor);
        return true;
    }

    /**
     * Advance simulation by one step
     */
    public void step() {
        elevator.move();
    }

    /**
     * Run until all requests are processed
     */
    public void runToCompletion() {
        int maxSteps = 1000; // safety limit
        int steps = 0;
        while (elevator.hasRequests() || elevator.getState() == ElevatorState.DOOR_OPEN) {
            step();
            if (++steps > maxSteps) {
                System.out.println("⚠️ Max steps reached, stopping simulation");
                break;
            }
        }
        System.out.println("✅ All requests processed in " + steps + " steps");
    }

    public String getStatus() {
        return elevator.getStatus();
    }

    private boolean isValidFloor(int floor) {
        return floor >= elevator.getMinFloor() && floor <= elevator.getMaxFloor();
    }
}

// ==================== Main Demo ====================

public class ElevatorSystem {
    public static void main(String[] args) {
        System.out.println("=== Elevator System (Single Lift) - SCAN Algorithm ===\n");

        ElevatorController controller = ElevatorController.getInstance(1, 10);

        // Scenario 1: Simple up request
        System.out.println("--- Scenario 1: Person on floor 5 wants to go UP ---");
        System.out.println("Status: " + controller.getStatus());
        controller.requestElevator(5, Direction.UP);
        controller.runToCompletion();
        System.out.println("Status: " + controller.getStatus());

        // Scenario 2: Passenger selects floor 8
        System.out.println("\n--- Scenario 2: Passenger inside selects floor 8 ---");
        controller.selectFloor(8);
        controller.runToCompletion();
        System.out.println("Status: " + controller.getStatus());

        // Scenario 3: Multiple requests — SCAN in action
        System.out.println("\n--- Scenario 3: Multiple requests (SCAN demo) ---");
        // Elevator at floor 8, requests at 3, 6, 10, 1
        controller.requestElevator(10, Direction.UP);  // up to 10
        controller.selectFloor(6);                      // internal to 6 (below, goes to downRequests)
        controller.requestElevator(3, Direction.DOWN);   // external down at 3
        controller.requestElevator(1, Direction.DOWN);   // external down at 1
        System.out.println("Status: " + controller.getStatus());
        controller.runToCompletion();
        System.out.println("Status: " + controller.getStatus());

        // Scenario 4: Request at current floor
        System.out.println("\n--- Scenario 4: Request at current floor ---");
        System.out.println("Status: " + controller.getStatus());
        controller.requestElevator(controller.getStatus().contains("Floor=1") ? 1 : 1, Direction.UP);
        controller.step(); // Should immediately open door
        System.out.println("Status: " + controller.getStatus());

        System.out.println("\n=== Demo Complete ===");
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Why SCAN over FCFS?

| Algorithm | Behavior | Pros | Cons |
|-----------|----------|------|------|
| **FCFS** | Serve requests in arrival order | Simple | Thrashing — constant direction changes |
| **SSTF** | Serve nearest request first | Minimizes local wait | **Starvation** — far requests ignored |
| **SCAN** | Serve all in one direction, then reverse | Fair, efficient | Slightly longer wait for some |
| **LOOK** | Like SCAN but only goes as far as furthest request | Saves unnecessary travel | Slightly more complex |

Our implementation is actually **LOOK** (we only go as far as the furthest request, not wall-to-wall). This is the industry standard.

### Deep Dive 2: Thread Safety Analysis

**Problem**: Multiple users can press buttons simultaneously.

**Solution**: `synchronized` on Elevator methods.

```java
// Thread-safe request addition
public synchronized void addUpRequest(int floor) {
    upRequests.add(floor);  // TreeSet is not thread-safe by default
    if (state == ElevatorState.IDLE) determineDirection();
}
```

**Alternative for high-concurrency**:
```java
// Lock-free with ConcurrentSkipListSet
private final ConcurrentSkipListSet<Integer> upRequests = new ConcurrentSkipListSet<>();
// + AtomicReference<ElevatorState> for state
```

**Trade-off**: `synchronized` is simpler and sufficient for single-elevator; `ConcurrentSkipListSet` scales better for multi-elevator systems.

### Deep Dive 3: Extensibility — From Single to Multiple Lifts

To extend to **Elevator Management System** (Multiple Lifts):

```
Current:
  ElevatorController → 1 Elevator

Extended:
  ElevatorManager → N Elevators
    └─ DispatchStrategy (Strategy pattern)
         ├─ NearestElevatorStrategy
         ├─ LeastLoadedStrategy
         └─ ZoneBasedStrategy
```

**Key changes**:
1. `ElevatorManager` holds `List<Elevator>`
2. `DispatchStrategy` interface: `Elevator selectElevator(Request request, List<Elevator> elevators)`
3. Each Elevator runs independently
4. Manager routes external requests to best elevator

### Deep Dive 4: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Request at current floor | Immediately open door (no movement needed) |
| Request at top/bottom floor invalid direction | Throw IllegalArgumentException |
| Duplicate request (same floor) | TreeSet ignores duplicates — no-op |
| No requests | Elevator stays IDLE |
| Request during DOOR_OPEN | Added to set, processed after door closes |
| Floor out of range | Validated in controller, throws exception |

### Deep Dive 5: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Add request | O(log n) | O(1) per request |
| Find next stop (ceiling/floor) | O(log n) | — |
| Remove processed floor | O(log n) | — |
| Check should stop | O(log n) | — |
| Total space | — | O(n) where n = floors |

**Why TreeSet over PriorityQueue?**
- TreeSet: `contains()` is O(log n), `ceiling()`/`floor()` available
- PriorityQueue: `contains()` is O(n), no efficient `ceiling()`
- TreeSet: No duplicates (natural for floor requests)

### Deep Dive 6: Testing Strategy

```java
// Unit tests to write:
@Test void testIdleElevatorPicksCorrectDirection()
@Test void testSCANServesAllUpBeforeDown()
@Test void testRequestAtCurrentFloorImmediateStop()
@Test void testDuplicateRequestIgnored()
@Test void testInvalidFloorThrowsException()
@Test void testDirectionReversalWhenNoMoreInDirection()
@Test void testConcurrentRequestsThreadSafe()
@Test void testElevatorReturnsToIdleWhenEmpty()
```

---

## 📋 Interview Checklist

### What Interviewer Looks For:
- [ ] **Requirements**: Asked clarifying questions, scoped the problem
- [ ] **State Pattern**: Recognized elevator = state machine
- [ ] **SCAN Algorithm**: Explained why over FCFS, showed understanding
- [ ] **Data Structures**: Justified TreeSet (O(log n), ceiling/floor, no dups)
- [ ] **Thread Safety**: Mentioned synchronized/concurrent structures
- [ ] **Clean Code**: Separation of concerns (Controller → Elevator → Door)
- [ ] **Edge Cases**: Current floor request, boundary floors, duplicates
- [ ] **Extensibility**: Discussed multi-lift extension with Strategy pattern

### Time Spent:
| Phase | Target | Actual |
|-------|--------|--------|
| Requirements | 3-5 min | — |
| Core Entities | 5 min | — |
| API Design | 5 min | — |
| Design + Code | 15-20 min | — |
| Deep Dives | 5-10 min | — |
| **Total** | **~35 min** | — |
