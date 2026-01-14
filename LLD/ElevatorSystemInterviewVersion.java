import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Elevator System
 * Time to complete: 45-60 minutes
 * Focus: State pattern, scheduling algorithm
 */

// ==================== Enums ====================
enum Direction {
    UP, DOWN, IDLE
}

enum ElevatorStatus {
    MOVING, STOPPED, MAINTENANCE
}

// ==================== Request ====================
class Request {
    private final int floor;
    private final Direction direction;

    public Request(int floor, Direction direction) {
        this.floor = floor;
        this.direction = direction;
    }

    public int getFloor() { return floor; }
    public Direction getDirection() { return direction; }

    @Override
    public String toString() {
        return "Request[floor=" + floor + ", dir=" + direction + "]";
    }
}

// ==================== Elevator ====================
class Elevator {
    private final int id;
    private int currentFloor;
    private Direction currentDirection;
    private ElevatorStatus status;
    private final PriorityQueue<Integer> upQueue;
    private final PriorityQueue<Integer> downQueue;

    public Elevator(int id) {
        this.id = id;
        this.currentFloor = 0;
        this.currentDirection = Direction.IDLE;
        this.status = ElevatorStatus.STOPPED;
        this.upQueue = new PriorityQueue<>();  // Min heap for up
        this.downQueue = new PriorityQueue<>(Collections.reverseOrder());  // Max heap for down
    }

    public void addRequest(Request request) {
        int floor = request.getFloor();
        
        if (floor > currentFloor) {
            upQueue.offer(floor);
            System.out.println("Elevator " + id + ": Added UP request to floor " + floor);
        } else if (floor < currentFloor) {
            downQueue.offer(floor);
            System.out.println("Elevator " + id + ": Added DOWN request to floor " + floor);
        } else {
            System.out.println("Elevator " + id + ": Already at floor " + floor);
        }
    }

    public void processRequests() {
        while (!upQueue.isEmpty() || !downQueue.isEmpty()) {
            // Process UP requests
            while (!upQueue.isEmpty()) {
                currentDirection = Direction.UP;
                int targetFloor = upQueue.poll();
                moveToFloor(targetFloor);
            }

            // Process DOWN requests
            while (!downQueue.isEmpty()) {
                currentDirection = Direction.DOWN;
                int targetFloor = downQueue.poll();
                moveToFloor(targetFloor);
            }

            currentDirection = Direction.IDLE;
        }
    }

    private void moveToFloor(int targetFloor) {
        System.out.println("Elevator " + id + ": Moving from " + currentFloor + 
                         " to " + targetFloor + " [" + currentDirection + "]");
        
        while (currentFloor != targetFloor) {
            if (currentFloor < targetFloor) {
                currentFloor++;
            } else {
                currentFloor--;
            }
            
            try {
                Thread.sleep(100);  // Simulate travel time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("Elevator " + id + ": Arrived at floor " + currentFloor + " ✓");
        openDoors();
        closeDoors();
    }

    private void openDoors() {
        System.out.println("Elevator " + id + ": Doors opening...");
        status = ElevatorStatus.STOPPED;
    }

    private void closeDoors() {
        try {
            Thread.sleep(200);  // Door open time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Elevator " + id + ": Doors closing...");
        status = ElevatorStatus.MOVING;
    }

    public int getCurrentFloor() { return currentFloor; }
    public Direction getCurrentDirection() { return currentDirection; }
    public int getId() { return id; }
}

// ==================== Elevator Controller ====================
class ElevatorController {
    private final List<Elevator> elevators;

    public ElevatorController(int numberOfElevators) {
        this.elevators = new ArrayList<>();
        for (int i = 1; i <= numberOfElevators; i++) {
            elevators.add(new Elevator(i));
        }
    }

    public void requestElevator(int floor, Direction direction) {
        System.out.println("\n>>> External request: Floor " + floor + ", Direction " + direction);
        
        // Simple strategy: Find closest idle elevator
        Elevator best = elevators.get(0);
        int minDistance = Math.abs(best.getCurrentFloor() - floor);

        for (Elevator elevator : elevators) {
            int distance = Math.abs(elevator.getCurrentFloor() - floor);
            if (distance < minDistance) {
                best = elevator;
                minDistance = distance;
            }
        }

        best.addRequest(new Request(floor, direction));
    }

    public void processAllRequests() {
        for (Elevator elevator : elevators) {
            elevator.processRequests();
        }
    }

    public void displayStatus() {
        System.out.println("\n=== Elevator Status ===");
        for (Elevator elevator : elevators) {
            System.out.println("Elevator " + elevator.getId() + 
                             ": Floor " + elevator.getCurrentFloor() + 
                             ", Direction " + elevator.getCurrentDirection());
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class ElevatorSystemInterviewVersion {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Elevator System Demo ===\n");

        // Create controller with 2 elevators
        ElevatorController controller = new ElevatorController(2);
        controller.displayStatus();

        // Simulate external requests
        controller.requestElevator(5, Direction.UP);
        controller.requestElevator(3, Direction.DOWN);
        controller.requestElevator(7, Direction.UP);
        controller.requestElevator(1, Direction.UP);

        // Process all requests
        controller.processAllRequests();

        controller.displayStatus();

        System.out.println("✅ Demo complete!");
    }
}
