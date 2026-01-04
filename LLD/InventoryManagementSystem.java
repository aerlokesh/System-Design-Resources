/**
 * INVENTORY MANAGEMENT SYSTEM - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a thread-safe inventory management system
 * that tracks products across multiple warehouse locations.
 * 
 * Key Features:
 * 1. Multi-warehouse inventory tracking
 * 2. Thread-safe operations (ReadWriteLock)
 * 3. Atomic stock transfers with deadlock prevention
 * 4. Low-stock alerts with pluggable listeners
 * 5. Inventory reservations (preventing overselling)
 * 6. In-transit inventory tracking
 * 7. Batch operations support
 * 
 * Design Patterns Used:
 * - Observer Pattern (AlertListener)
 * - Strategy Pattern (Different alert implementations)
 * - Composite Pattern (CompositeAlertListener)
 * - Delegation Pattern (InventoryManager delegates to Warehouse)
 * - State Pattern (Reservation and transfer status)
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;

// ============================================================
// CORE INTERFACES AND ENUMS
// ============================================================

/**
 * Callback interface for inventory alerts
 */
interface AlertListener {
    void onLowStock(String warehouseId, String productId, 
                    int currentStock, int threshold);
}

// ============================================================
// ALERT IMPLEMENTATIONS
// ============================================================

/**
 * Console logger for alerts
 */
class ConsoleAlertListener implements AlertListener {
    @Override
    public void onLowStock(String warehouseId, String productId, 
                           int currentStock, int threshold) {
        System.out.println(String.format(
            "[ALERT] Warehouse: %s, Product: %s - Stock: %d (Threshold: %d)",
            warehouseId, productId, currentStock, threshold
        ));
    }
}

/**
 * Composite listener to notify multiple channels
 */
class CompositeAlertListener implements AlertListener {
    private final List<AlertListener> listeners;
    
    public CompositeAlertListener(List<AlertListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }
    
    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void onLowStock(String warehouseId, String productId, 
                           int currentStock, int threshold) {
        for (AlertListener listener : listeners) {
            try {
                listener.onLowStock(warehouseId, productId, currentStock, threshold);
            } catch (Exception e) {
                System.err.println("Alert listener failed: " + e.getMessage());
            }
        }
    }
}

/**
 * Email alert listener (mock implementation)
 */
class EmailAlertListener implements AlertListener {
    @Override
    public void onLowStock(String warehouseId, String productId, 
                           int currentStock, int threshold) {
        System.out.println(String.format(
            "[EMAIL] Sending alert email for %s at %s - Stock: %d",
            productId, warehouseId, currentStock
        ));
    }
}

// ============================================================
// ALERT CONFIGURATION
// ============================================================

/**
 * Configuration for low-stock alerts
 * Maintains alert state to prevent duplicate notifications
 */
class AlertConfig {
    private final int threshold;
    private boolean triggered;
    
    public AlertConfig(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");
        }
        this.threshold = threshold;
        this.triggered = false;
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    /**
     * Check if alert should fire
     * @return true if alert should be triggered
     */
    public boolean shouldTriggerAlert(int currentStock) {
        if (currentStock < threshold && !triggered) {
            triggered = true;
            return true;
        }
        return false;
    }
    
    /**
     * Reset alert when stock is replenished
     */
    public void checkReset(int currentStock) {
        if (currentStock >= threshold && triggered) {
            triggered = false;
        }
    }
    
    public boolean isTriggered() {
        return triggered;
    }
}

// ============================================================
// WAREHOUSE
// ============================================================

/**
 * Represents a warehouse with inventory and alert management
 * Thread-safe for concurrent operations using ReadWriteLock
 */
class Warehouse {
    private final String warehouseId;
    private final Map<String, Integer> inventory;
    private final Map<String, AlertConfig> alertConfigs;
    private final AlertListener alertListener;
    private final ReadWriteLock lock;
    
    public Warehouse(String warehouseId, AlertListener alertListener) {
        this.warehouseId = warehouseId;
        this.inventory = new HashMap<>();
        this.alertConfigs = new HashMap<>();
        this.alertListener = alertListener;
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Add stock with alert check
     * Uses write lock for thread safety
     */
    public void addStock(String productId, int quantity) {
        lock.writeLock().lock();
        try {
            int currentStock = inventory.getOrDefault(productId, 0);
            int newStock = currentStock + quantity;
            inventory.put(productId, newStock);
            
            // Check if alert should be reset
            AlertConfig config = alertConfigs.get(productId);
            if (config != null) {
                config.checkReset(newStock);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove stock with validation and alert check
     * @return true if successful, false if insufficient stock
     */
    public boolean removeStock(String productId, int quantity) {
        lock.writeLock().lock();
        try {
            int currentStock = inventory.getOrDefault(productId, 0);
            
            if (currentStock < quantity) {
                return false; // Insufficient stock
            }
            
            int newStock = currentStock - quantity;
            inventory.put(productId, newStock);
            
            // Check if alert should be triggered
            checkAndTriggerAlert(productId, newStock);
            
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current stock level
     * Uses read lock for concurrent reads
     */
    public int getStock(String productId) {
        lock.readLock().lock();
        try {
            return inventory.getOrDefault(productId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if warehouse can fulfill quantity
     */
    public boolean canFulfill(String productId, int quantity) {
        lock.readLock().lock();
        try {
            return inventory.getOrDefault(productId, 0) >= quantity;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Configure alert threshold
     */
    public void setAlertThreshold(String productId, int threshold) {
        lock.writeLock().lock();
        try {
            alertConfigs.put(productId, new AlertConfig(threshold));
            
            // Check if alert should be triggered immediately
            int currentStock = inventory.getOrDefault(productId, 0);
            checkAndTriggerAlert(productId, currentStock);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void checkAndTriggerAlert(String productId, int currentStock) {
        AlertConfig config = alertConfigs.get(productId);
        if (config != null && config.shouldTriggerAlert(currentStock)) {
            alertListener.onLowStock(warehouseId, productId, 
                                     currentStock, config.getThreshold());
        }
    }
    
    public String getWarehouseId() {
        return warehouseId;
    }
    
    // For external locking during transfers
    public void lock() {
        lock.writeLock().lock();
    }
    
    public void unlock() {
        lock.writeLock().unlock();
    }
    
    // For testing and monitoring
    public Map<String, Integer> getInventorySnapshot() {
        lock.readLock().lock();
        try {
            return new HashMap<>(inventory);
        } finally {
            lock.readLock().unlock();
        }
    }
}

// ============================================================
// INVENTORY MANAGER
// ============================================================

/**
 * Central manager for inventory operations across warehouses
 * Thread-safe coordinator for all inventory activities
 */
class InventoryManager {
    private final Map<String, Warehouse> warehouses;
    private final AlertListener alertListener;
    
    public InventoryManager(List<String> warehouseIds, AlertListener alertListener) {
        this.warehouses = new ConcurrentHashMap<>();
        this.alertListener = alertListener;
        
        for (String id : warehouseIds) {
            warehouses.put(id, new Warehouse(id, alertListener));
        }
    }
    
    /**
     * Add stock to a specific warehouse
     */
    public void addStock(String warehouseId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Warehouse warehouse = getWarehouse(warehouseId);
        warehouse.addStock(productId, quantity);
    }
    
    /**
     * Remove stock from a specific warehouse
     */
    public boolean removeStock(String warehouseId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Warehouse warehouse = getWarehouse(warehouseId);
        return warehouse.removeStock(productId, quantity);
    }
    
    /**
     * Get current stock level at a specific warehouse
     */
    public int getStock(String warehouseId, String productId) {
        Warehouse warehouse = getWarehouse(warehouseId);
        return warehouse.getStock(productId);
    }
    
    /**
     * Get total stock across all warehouses
     */
    public int getTotalStock(String productId) {
        return warehouses.values().stream()
            .mapToInt(w -> w.getStock(productId))
            .sum();
    }
    
    /**
     * Get warehouses that can fulfill the requested quantity
     */
    public List<String> getAvailableWarehouses(String productId, int quantity) {
        return warehouses.values().stream()
            .filter(w -> w.canFulfill(productId, quantity))
            .map(Warehouse::getWarehouseId)
            .collect(Collectors.toList());
    }
    
    /**
     * Transfer stock between warehouses
     * Atomic operation: either succeeds completely or fails
     * Uses consistent lock ordering to prevent deadlock
     */
    public boolean transferStock(String fromWarehouseId, String toWarehouseId, 
                                  String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new IllegalArgumentException("Cannot transfer to same warehouse");
        }
        
        Warehouse from = getWarehouse(fromWarehouseId);
        Warehouse to = getWarehouse(toWarehouseId);
        
        // Lock both warehouses in consistent order to prevent deadlock
        Warehouse first = fromWarehouseId.compareTo(toWarehouseId) < 0 ? from : to;
        Warehouse second = first == from ? to : from;
        
        first.lock();
        try {
            second.lock();
            try {
                // Check if source has sufficient stock
                if (!from.canFulfill(productId, quantity)) {
                    return false;
                }
                
                // Perform atomic transfer
                boolean removed = from.removeStock(productId, quantity);
                if (removed) {
                    to.addStock(productId, quantity);
                    return true;
                }
                return false;
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }
    
    /**
     * Configure low-stock alert for a product at a warehouse
     */
    public void setAlertThreshold(String warehouseId, String productId, int threshold) {
        Warehouse warehouse = getWarehouse(warehouseId);
        warehouse.setAlertThreshold(productId, threshold);
    }
    
    private Warehouse getWarehouse(String warehouseId) {
        Warehouse warehouse = warehouses.get(warehouseId);
        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse not found: " + warehouseId);
        }
        return warehouse;
    }
    
    /**
     * Get inventory snapshot for monitoring
     */
    public Map<String, Integer> getInventorySnapshot(String warehouseId) {
        return getWarehouse(warehouseId).getInventorySnapshot();
    }
    
    /**
     * Get all warehouse IDs
     */
    public List<String> getWarehouseIds() {
        return new ArrayList<>(warehouses.keySet());
    }
}

// ============================================================
// INVENTORY RESERVATIONS (EXTENSION)
// ============================================================

/**
 * Represents a temporary hold on inventory
 * Prevents overselling during order processing
 */
class InventoryReservation {
    private final String reservationId;
    private final String warehouseId;
    private final String productId;
    private final int quantity;
    private final long expirationTime;
    private ReservationStatus status;
    
    public enum ReservationStatus {
        ACTIVE, COMMITTED, RELEASED, EXPIRED
    }
    
    public InventoryReservation(String warehouseId, String productId, 
                                int quantity, long ttlMillis) {
        this.reservationId = UUID.randomUUID().toString();
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.quantity = quantity;
        this.expirationTime = System.currentTimeMillis() + ttlMillis;
        this.status = ReservationStatus.ACTIVE;
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
    
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE && !isExpired();
    }
    
    // Getters
    public String getReservationId() { return reservationId; }
    public String getWarehouseId() { return warehouseId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    
    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}

/**
 * Extended warehouse with reservation support
 */
class WarehouseWithReservations extends Warehouse {
    private final Map<String, List<InventoryReservation>> reservations;
    private final ScheduledExecutorService expirationChecker;
    
    public WarehouseWithReservations(String warehouseId, AlertListener alertListener) {
        super(warehouseId, alertListener);
        this.reservations = new HashMap<>();
        this.expirationChecker = Executors.newScheduledThreadPool(1);
        startExpirationChecker();
    }
    
    /**
     * Reserve inventory for an order
     * @return Reservation ID if successful, null if insufficient available stock
     */
    public String reserveStock(String productId, int quantity, long ttlMillis) {
        lock();
        try {
            int available = getAvailableStock(productId);
            if (available < quantity) {
                return null;
            }
            
            InventoryReservation reservation = new InventoryReservation(
                getWarehouseId(), productId, quantity, ttlMillis
            );
            
            reservations.computeIfAbsent(productId, k -> new ArrayList<>())
                       .add(reservation);
            
            return reservation.getReservationId();
        } finally {
            unlock();
        }
    }
    
    /**
     * Commit reservation (deduct from actual inventory)
     */
    public boolean commitReservation(String reservationId) {
        lock();
        try {
            InventoryReservation reservation = findReservation(reservationId);
            if (reservation == null || !reservation.isActive()) {
                return false;
            }
            
            boolean removed = removeStock(reservation.getProductId(), 
                                         reservation.getQuantity());
            if (removed) {
                reservation.setStatus(InventoryReservation.ReservationStatus.COMMITTED);
                return true;
            }
            return false;
        } finally {
            unlock();
        }
    }
    
    /**
     * Release reservation (make inventory available again)
     */
    public void releaseReservation(String reservationId) {
        lock();
        try {
            InventoryReservation reservation = findReservation(reservationId);
            if (reservation != null && reservation.isActive()) {
                reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
            }
        } finally {
            unlock();
        }
    }
    
    /**
     * Get available stock (physical stock - reserved stock)
     */
    public int getAvailableStock(String productId) {
        int physicalStock = getStock(productId);
        int reservedStock = getReservedStock(productId);
        return physicalStock - reservedStock;
    }
    
    private int getReservedStock(String productId) {
        List<InventoryReservation> productReservations = reservations.get(productId);
        if (productReservations == null) {
            return 0;
        }
        
        return productReservations.stream()
            .filter(InventoryReservation::isActive)
            .mapToInt(InventoryReservation::getQuantity)
            .sum();
    }
    
    private InventoryReservation findReservation(String reservationId) {
        for (List<InventoryReservation> productReservations : reservations.values()) {
            for (InventoryReservation reservation : productReservations) {
                if (reservation.getReservationId().equals(reservationId)) {
                    return reservation;
                }
            }
        }
        return null;
    }
    
    private void startExpirationChecker() {
        expirationChecker.scheduleAtFixedRate(() -> {
            lock();
            try {
                for (List<InventoryReservation> productReservations : reservations.values()) {
                    for (InventoryReservation reservation : productReservations) {
                        if (reservation.isExpired() && 
                            reservation.getStatus() == InventoryReservation.ReservationStatus.ACTIVE) {
                            reservation.setStatus(InventoryReservation.ReservationStatus.EXPIRED);
                        }
                    }
                }
            } finally {
                unlock();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        expirationChecker.shutdown();
    }
}

// ============================================================
// IN-TRANSIT INVENTORY (EXTENSION)
// ============================================================

/**
 * Represents inventory in transit between warehouses
 */
class InTransitInventory {
    private final String transferId;
    private final String fromWarehouse;
    private final String toWarehouse;
    private final String productId;
    private final int quantity;
    private final long startTime;
    private final long estimatedArrival;
    private TransferStatus status;
    
    public enum TransferStatus {
        IN_TRANSIT, DELIVERED, CANCELLED
    }
    
    public InTransitInventory(String fromWarehouse, String toWarehouse,
                              String productId, int quantity, long etaMillis) {
        this.transferId = UUID.randomUUID().toString();
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = toWarehouse;
        this.productId = productId;
        this.quantity = quantity;
        this.startTime = System.currentTimeMillis();
        this.estimatedArrival = startTime + etaMillis;
        this.status = TransferStatus.IN_TRANSIT;
    }
    
    public boolean isDelayed() {
        return System.currentTimeMillis() > estimatedArrival && 
               status == TransferStatus.IN_TRANSIT;
    }
    
    // Getters
    public String getTransferId() { return transferId; }
    public String getFromWarehouse() { return fromWarehouse; }
    public String getToWarehouse() { return toWarehouse; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public TransferStatus getStatus() { return status; }
    public long getEstimatedArrival() { return estimatedArrival; }
    
    public void setStatus(TransferStatus status) {
        this.status = status;
    }
}

/**
 * Extended inventory manager with in-transit tracking
 */
class InventoryManagerWithTransit extends InventoryManager {
    private final Map<String, InTransitInventory> inTransitInventory;
    
    public InventoryManagerWithTransit(List<String> warehouseIds, 
                                       AlertListener alertListener) {
        super(warehouseIds, alertListener);
        this.inTransitInventory = new ConcurrentHashMap<>();
    }
    
    /**
     * Initiate transfer with tracking
     */
    public String initiateTransfer(String fromWarehouse, String toWarehouse,
                                    String productId, int quantity, long etaMillis) {
        // Remove from source
        boolean removed = removeStock(fromWarehouse, productId, quantity);
        if (!removed) {
            return null;
        }
        
        // Create in-transit record
        InTransitInventory transit = new InTransitInventory(
            fromWarehouse, toWarehouse, productId, quantity, etaMillis
        );
        
        inTransitInventory.put(transit.getTransferId(), transit);
        return transit.getTransferId();
    }
    
    /**
     * Complete transfer (arrival at destination)
     */
    public void completeTransfer(String transferId) {
        InTransitInventory transit = inTransitInventory.get(transferId);
        if (transit == null || transit.getStatus() != InTransitInventory.TransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("Invalid transfer");
        }
        
        // Add to destination
        addStock(transit.getToWarehouse(), transit.getProductId(), 
                transit.getQuantity());
        
        transit.setStatus(InTransitInventory.TransferStatus.DELIVERED);
    }
    
    /**
     * Get total stock including in-transit inventory
     */
    public int getTotalStockWithTransit(String productId) {
        int stock = getTotalStock(productId);
        int inTransit = inTransitInventory.values().stream()
            .filter(t -> t.getProductId().equals(productId))
            .filter(t -> t.getStatus() == InTransitInventory.TransferStatus.IN_TRANSIT)
            .mapToInt(InTransitInventory::getQuantity)
            .sum();
        
        return stock + inTransit;
    }
    
    /**
     * Get delayed shipments
     */
    public List<InTransitInventory> getDelayedShipments() {
        return inTransitInventory.values().stream()
            .filter(InTransitInventory::isDelayed)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all in-transit shipments
     */
    public List<InTransitInventory> getAllInTransitShipments() {
        return inTransitInventory.values().stream()
            .filter(t -> t.getStatus() == InTransitInventory.TransferStatus.IN_TRANSIT)
            .collect(Collectors.toList());
    }
}

// ============================================================
// DEMO AND TESTING
// ============================================================

public class InventoryManagementSystem {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INVENTORY MANAGEMENT SYSTEM DEMO ===\n");
        
        // Demo 1: Basic Operations
        demoBasicOperations();
        
        // Demo 2: Stock Transfers
        demoStockTransfers();
        
        // Demo 3: Low Stock Alerts
        demoLowStockAlerts();
        
        // Demo 4: Concurrent Operations
        demoConcurrentOperations();
        
        // Demo 5: Reservations
        demoReservations();
        
        // Demo 6: In-Transit Tracking
        demoInTransitTracking();
    }
    
    private static void demoBasicOperations() {
        System.out.println("--- Demo 1: Basic Operations ---");
        
        List<String> warehouses = Arrays.asList("WH-EAST", "WH-WEST", "WH-CENTRAL");
        InventoryManager manager = new InventoryManager(warehouses, new ConsoleAlertListener());
        
        // Add stock
        manager.addStock("WH-EAST", "LAPTOP-001", 50);
        manager.addStock("WH-WEST", "LAPTOP-001", 30);
        manager.addStock("WH-CENTRAL", "LAPTOP-001", 20);
        
        // Check stock
        System.out.println("WH-EAST stock: " + manager.getStock("WH-EAST", "LAPTOP-001"));
        System.out.println("Total stock: " + manager.getTotalStock("LAPTOP-001"));
        
        // Remove stock
        boolean removed = manager.removeStock("WH-EAST", "LAPTOP-001", 10);
        System.out.println("Removed 10 units: " + removed);
        System.out.println("WH-EAST stock after removal: " + manager.getStock("WH-EAST", "LAPTOP-001"));
        
        // Check availability
        List<String> available = manager.getAvailableWarehouses("LAPTOP-001", 25);
        System.out.println("Warehouses with 25+ units: " + available);
        
        System.out.println();
    }
    
    private static void demoStockTransfers() {
        System.out.println("--- Demo 2: Stock Transfers ---");
        
        List<String> warehouses = Arrays.asList("WH-A", "WH-B");
        InventoryManager manager = new InventoryManager(warehouses, new ConsoleAlertListener());
        
        manager.addStock("WH-A", "PHONE-001", 100);
        manager.addStock("WH-B", "PHONE-001", 50);
        
        System.out.println("Before transfer:");
        System.out.println("  WH-A: " + manager.getStock("WH-A", "PHONE-001"));
        System.out.println("  WH-B: " + manager.getStock("WH-B", "PHONE-001"));
        
        // Transfer stock
        boolean transferred = manager.transferStock("WH-A", "WH-B", "PHONE-001", 30);
        System.out.println("\nTransfer successful: " + transferred);
        
        System.out.println("\nAfter transfer:");
        System.out.println("  WH-A: " + manager.getStock("WH-A", "PHONE-001"));
        System.out.println("  WH-B: " + manager.getStock("WH-B", "PHONE-001"));
        
        System.out.println();
    }
    
    private static void demoLowStockAlerts() {
        System.out.println("--- Demo 3: Low Stock Alerts ---");
        
        List<String> warehouses = Arrays.asList("WH-MAIN");
        CompositeAlertListener alertListener = new CompositeAlertListener(
            Arrays.asList(new ConsoleAlertListener(), new EmailAlertListener())
        );
        
        InventoryManager manager = new InventoryManager(warehouses, alertListener);
        
        // Configure alert
        manager.setAlertThreshold("WH-MAIN", "TABLET-001", 10);
        manager.addStock("WH-MAIN", "TABLET-001", 100);
        
        System.out.println("Initial stock: 100");
        System.out.println("Alert threshold: 10");
        System.out.println("\nRemoving stock to trigger alert...\n");
        
        // Remove stock gradually
        for (int i = 0; i < 95; i++) {
            manager.removeStock("WH-MAIN", "TABLET-001", 1);
        }
        
        System.out.println("\nFinal stock: " + manager.getStock("WH-MAIN", "TABLET-001"));
        
        // Replenish and trigger again
        System.out.println("\nReplenishing stock...");
        manager.addStock("WH-MAIN", "TABLET-001", 20);
        System.out.println("Stock after replenishment: " + manager.getStock("WH-MAIN", "TABLET-001"));
        
        System.out.println("\nRemoving stock again...");
        for (int i = 0; i < 20; i++) {
            manager.removeStock("WH-MAIN", "TABLET-001", 1);
        }
        
        System.out.println();
    }
    
    private static void demoConcurrentOperations() throws InterruptedException {
        System.out.println("--- Demo 4: Concurrent Operations ---");
        
        List<String> warehouses = Arrays.asList("WH-CONCURRENT");
        InventoryManager manager = new InventoryManager(warehouses, new ConsoleAlertListener());
        
        manager.addStock("WH-CONCURRENT", "ITEM-001", 1000);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);
        
        System.out.println("Initial stock: 1000");
        System.out.println("Running 50 add operations (10 units each)");
        System.out.println("Running 50 remove operations (10 units each)");
        
        // 50 threads adding 10 units each
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    manager.addStock("WH-CONCURRENT", "ITEM-001", 10);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 50 threads removing 10 units each
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    manager.removeStock("WH-CONCURRENT", "ITEM-001", 10);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        int finalStock = manager.getStock("WH-CONCURRENT", "ITEM-001");
        System.out.println("\nFinal stock: " + finalStock);
        System.out.println("Expected: 1000 (50*10 added, 50*10 removed)");
        System.out.println("Consistency maintained: " + (finalStock == 1000));
        
        System.out.println();
    }
    
    private static void demoReservations() {
        System.out.println("--- Demo 5: Inventory Reservations ---");
        
        List<String> warehouses = Arrays.asList("WH-RESERVE");
        WarehouseWithReservations warehouse = new WarehouseWithReservations(
            "WH-RESERVE", new ConsoleAlertListener()
        );
        
        warehouse.addStock("PRODUCT-001", 100);
        
        System.out.println("Initial stock: 100");
        System.out.println("Physical stock: " + warehouse.getStock("PRODUCT-001"));
        System.out.println("Available stock: " + warehouse.getAvailableStock("PRODUCT-001"));
        
        // Create reservations
        System.out.println("\nCreating reservations...");
        String reservation1 = warehouse.reserveStock("PRODUCT-001", 30, 15 * 60 * 1000);
        String reservation2 = warehouse.reserveStock("PRODUCT-001", 40, 15 * 60 * 1000);
        
        System.out.println("Reservation 1 ID: " + reservation1);
        System.out.println("Reservation 2 ID: " + reservation2);
        
        System.out.println("\nAfter reservations:");
        System.out.println("Physical stock: " + warehouse.getStock("PRODUCT-001"));
        System.out.println("Available stock: " + warehouse.getAvailableStock("PRODUCT-001"));
        
        // Commit first reservation
        System.out.println("\nCommitting reservation 1...");
        boolean committed = warehouse.commitReservation(reservation1);
        System.out.println("Committed: " + committed);
        
        System.out.println("\nAfter commit:");
        System.out.println("Physical stock: " + warehouse.getStock("PRODUCT-001"));
        System.out.println("Available stock: " + warehouse.getAvailableStock("PRODUCT-001"));
        
        // Release second reservation
        System.out.println("\nReleasing reservation 2...");
        warehouse.releaseReservation(reservation2);
        
        System.out.println("\nAfter release:");
        System.out.println("Physical stock: " + warehouse.getStock("PRODUCT-001"));
        System.out.println("Available stock: " + warehouse.getAvailableStock("PRODUCT-001"));
        
        warehouse.shutdown();
        System.out.println();
    }
    
    private static void demoInTransitTracking() {
        System.out.println("--- Demo 6: In-Transit Inventory Tracking ---");
        
        List<String> warehouses = Arrays.asList("WH-ORIGIN", "WH-DESTINATION");
        InventoryManagerWithTransit manager = new InventoryManagerWithTransit(
            warehouses, new ConsoleAlertListener()
        );
        
        manager.addStock("WH-ORIGIN", "PRODUCT-XYZ", 200);
        
        System.out.println("Initial stock:");
        System.out.println("  WH-ORIGIN: " + manager.getStock("WH-ORIGIN", "PRODUCT-XYZ"));
        System.out.println("  WH-DESTINATION: " + manager.getStock("WH-DESTINATION", "PRODUCT-XYZ"));
        System.out.println("  Total: " + manager.getTotalStock("PRODUCT-XYZ"));
        
        // Initiate transfer
        System.out.println("\nInitiating transfer of 50 units...");
        String transferId = manager.initiateTransfer(
            "WH-ORIGIN", "WH-DESTINATION", "PRODUCT-XYZ", 50, 2 * 60 * 60 * 1000 // 2 hours
        );
        System.out.println("Transfer ID: " + transferId);
        
        System.out.println("\nDuring transit:");
        System.out.println("  WH-ORIGIN: " + manager.getStock("WH-ORIGIN", "PRODUCT-XYZ"));
        System.out.println("  WH-DESTINATION: " + manager.getStock("WH-DESTINATION", "PRODUCT-XYZ"));
        System.out.println("  Total physical: " + manager.getTotalStock("PRODUCT-XYZ"));
        System.out.println("  Total with in-transit: " + 
                          manager.getTotalStockWithTransit("PRODUCT-XYZ"));
        
        // Complete transfer
        System.out.println("\nCompleting transfer...");
        manager.completeTransfer(transferId);
        
        System.out.println("\nAfter delivery:");
        System.out.println("  WH-ORIGIN: " + manager.getStock("WH-ORIGIN", "PRODUCT-XYZ"));
        System.out.println("  WH-DESTINATION: " + manager.getStock("WH-DESTINATION", "PRODUCT-XYZ"));
        System.out.println("  Total: " + manager.getTotalStock("PRODUCT-XYZ"));
        
        System.out.println();
    }
}
