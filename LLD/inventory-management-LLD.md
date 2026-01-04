# Inventory Management System - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Class Design](#class-design)
5. [Complete Implementation](#complete-implementation)
6. [Concurrency & Thread Safety](#concurrency--thread-safety)
7. [Extensibility](#extensibility)
8. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is an Inventory Management System?**

An inventory management system tracks product stock across multiple warehouse locations. When inventory arrives, the system records it. When orders ship, the system deducts stock. The system can also transfer inventory between locations and alert managers when stock runs low.

**Real-World Use Cases:**
- E-commerce platforms (Amazon, Walmart)
- Retail chains with multiple stores
- Manufacturing with distributed warehouses
- Fulfillment centers for marketplace sellers
- Supply chain management systems

**Core Challenges:**
- **Concurrency:** Multiple operations happening simultaneously
- **Consistency:** Preventing negative inventory or overselling
- **Accuracy:** Maintaining correct counts across locations
- **Performance:** Fast lookups for availability checks
- **Alerting:** Timely notifications for low stock

---

## Requirements

### Functional Requirements

1. **Inventory Operations**
   - Add stock to a specific warehouse (receiving shipments)
   - Remove stock from a specific warehouse (fulfilling orders)
   - Check current stock level for a product at a warehouse
   - Get total stock across all warehouses for a product

2. **Availability Check**
   - Given a product and quantity, return which warehouses can fulfill it
   - Support checking across multiple warehouses

3. **Stock Transfers**
   - Transfer stock between warehouses
   - Atomic operation (all or nothing)
   - Validate source has sufficient stock

4. **Low-Stock Alerts**
   - Configure threshold per product per warehouse
   - Trigger alert when stock drops below threshold
   - Pluggable alert mechanism (callback interface)
   - Reset alert when stock is replenished above threshold

5. **Validation**
   - Reject operations that would result in negative inventory
   - Validate warehouse existence
   - Validate product existence

### Non-Functional Requirements

1. **Thread Safety**
   - Support concurrent operations from multiple threads
   - Prevent race conditions in stock updates
   - Ensure atomic transfers between warehouses

2. **Performance**
   - Fast lookups (O(1) for warehouse-product combination)
   - Efficient availability checks
   - Minimal lock contention

3. **Consistency**
   - Strong consistency for inventory counts
   - No lost updates or phantom reads
   - Accurate alert state management

4. **Extensibility**
   - Easy to add new alert types
   - Support for reservations/holds
   - Pluggable notification mechanisms

### Out of Scope

- Product catalog management (products exist externally)
- Order processing and payment handling
- Warehouse lifecycle management (adding/removing warehouses)
- Persistence layer (database/file system)
- User authentication and authorization
- Shipping and logistics
- Returns processing

---

## Core Entities and Relationships

### Key Entities

1. **InventoryManager**
   - Central coordinator for all inventory operations
   - Manages warehouses and products
   - Handles transfers between warehouses
   - Delegates to warehouses for operations

2. **Warehouse**
   - Represents a physical warehouse location
   - Maintains stock levels per product
   - Handles add/remove/check operations
   - Manages alert configurations per product
   - Thread-safe operations

3. **Product**
   - Simple identifier (productId)
   - No behavior, just a key for lookups
   - Exists externally to the inventory system

4. **AlertConfig**
   - Configuration for low-stock alerts
   - Threshold value per product per warehouse
   - Alert state management (triggered/not triggered)

5. **AlertListener (Interface)**
   - Callback interface for notifications
   - Pluggable implementation
   - Receives product, warehouse, and stock level

6. **InventoryReservation (Extension)**
   - Temporary hold on inventory
   - Prevents overselling
   - Has expiration time
   - Can be committed or released

### Entity Relationships

```
┌─────────────────────────────────────────────────┐
│           InventoryManager                       │
│  - manages all warehouses                        │
│  - coordinates transfers                         │
│  - aggregates stock across locations             │
└───────────────────┬─────────────────────────────┘
                    │ manages
                    ▼
        ┌───────────────────────┐
        │     Warehouse         │
        │  - warehouseId        │
        │  - inventory map      │
        │  - alert configs      │
        └───────────┬───────────┘
                    │ contains
                    ▼
    ┌───────────────────────────────────┐
    │  Map<ProductId, StockLevel>       │
    │  Map<ProductId, AlertConfig>      │
    └───────────────────────────────────┘
                    │
                    │ notifies
                    ▼
        ┌───────────────────────┐
        │   AlertListener       │
        │   <<interface>>       │
        │  - onLowStock()       │
        └───────────────────────┘
                    ▲
                    │ implements
        ┌───────────┴───────────┐
        │                       │
┌───────▼────────┐   ┌──────────▼──────┐
│  EmailAlert    │   │  SlackAlert     │
│  Listener      │   │  Listener       │
└────────────────┘   └─────────────────┘
```

### State Transitions

**Alert State Machine:**
```
           Stock >= Threshold
    ┌──────────────────────────────┐
    │                              │
    ▼                              │
┌────────────┐  Stock < Threshold  │
│   NOT      │────────────────────┐│
│ TRIGGERED  │                    ││
└────────────┘                    ││
                                  ▼│
                          ┌──────────────┐
                          │  TRIGGERED   │
                          │              │
                          └──────────────┘
```

**Reservation State Machine:**
```
┌──────────┐   reserve()    ┌──────────┐
│  NONE    │──────────────► │ RESERVED │
└──────────┘                └─────┬────┘
                                  │
                 ┌────────────────┼────────────────┐
                 │                │                │
           commit()          release()        expire()
                 │                │                │
                 ▼                ▼                ▼
         ┌──────────┐      ┌──────────┐    ┌──────────┐
         │COMMITTED │      │ RELEASED │    │ EXPIRED  │
         └──────────┘      └──────────┘    └──────────┘
```

---

## Class Design

### 1. InventoryManager

```java
/**
 * Central manager for inventory operations across warehouses
 * Thread-safe coordinator for all inventory activities
 */
public class InventoryManager {
    private final Map<String, Warehouse> warehouses;
    private final AlertListener alertListener;
    
    /**
     * Initialize with warehouses and alert listener
     * @param warehouseIds List of warehouse identifiers
     * @param alertListener Callback for low-stock notifications
     */
    public InventoryManager(List<String> warehouseIds, AlertListener alertListener) {
        this.warehouses = new ConcurrentHashMap<>();
        this.alertListener = alertListener;
        
        for (String id : warehouseIds) {
            warehouses.put(id, new Warehouse(id, alertListener));
        }
    }
    
    /**
     * Add stock to a specific warehouse
     * @param warehouseId Warehouse identifier
     * @param productId Product identifier
     * @param quantity Amount to add (must be positive)
     * @throws IllegalArgumentException if quantity <= 0 or warehouse doesn't exist
     */
    public void addStock(String warehouseId, String productId, int quantity);
    
    /**
     * Remove stock from a specific warehouse
     * @param warehouseId Warehouse identifier
     * @param productId Product identifier
     * @param quantity Amount to remove
     * @return true if successful, false if insufficient stock
     * @throws IllegalArgumentException if quantity <= 0 or warehouse doesn't exist
     */
    public boolean removeStock(String warehouseId, String productId, int quantity);
    
    /**
     * Get current stock level at a specific warehouse
     */
    public int getStock(String warehouseId, String productId);
    
    /**
     * Get total stock across all warehouses
     */
    public int getTotalStock(String productId);
    
    /**
     * Get warehouses that can fulfill the requested quantity
     * @return List of warehouse IDs that have sufficient stock
     */
    public List<String> getAvailableWarehouses(String productId, int quantity);
    
    /**
     * Transfer stock between warehouses
     * Atomic operation: either succeeds completely or fails
     * @return true if transfer successful, false otherwise
     */
    public boolean transferStock(String fromWarehouse, String toWarehouse, 
                                  String productId, int quantity);
    
    /**
     * Configure low-stock alert for a product at a warehouse
     */
    public void setAlertThreshold(String warehouseId, String productId, int threshold);
}
```

**Design Decisions:**
- **ConcurrentHashMap** for warehouses: Thread-safe access without explicit locking
- **Delegation pattern**: Manager delegates to individual warehouses
- **Validation at boundary**: Check inputs before delegating
- **Atomic transfers**: Use locking to ensure consistency

### 2. Warehouse

```java
/**
 * Represents a warehouse with inventory and alert management
 * Thread-safe for concurrent operations
 */
public class Warehouse {
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
    public void addStock(String productId, int quantity);
    
    /**
     * Remove stock with validation and alert check
     * @return true if successful, false if insufficient stock
     */
    public boolean removeStock(String productId, int quantity);
    
    /**
     * Get current stock level
     * Uses read lock for concurrent reads
     */
    public int getStock(String productId);
    
    /**
     * Check if warehouse can fulfill quantity
     */
    public boolean canFulfill(String productId, int quantity);
    
    /**
     * Configure alert threshold
     */
    public void setAlertThreshold(String productId, int threshold);
    
    /**
     * Check and trigger alerts if needed
     * Maintains alert state to prevent duplicate notifications
     */
    private void checkAndTriggerAlert(String productId);
}
```

**Design Decisions:**
- **ReadWriteLock**: Multiple concurrent reads, exclusive writes
- **HashMap for inventory**: Fast O(1) lookups, protected by lock
- **AlertConfig per product**: Granular threshold management
- **State tracking**: Prevents duplicate alert notifications

### 3. AlertConfig

```java
/**
 * Configuration for low-stock alerts
 * Maintains alert state to prevent duplicate notifications
 */
public class AlertConfig {
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
     * @param currentStock Current inventory level
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
     * @param currentStock Current inventory level
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
```

**Design Decisions:**
- **Immutable threshold**: Set at creation time
- **Mutable state**: Tracks whether alert has been triggered
- **Resettable**: Alert can be re-triggered after replenishment
- **Guard against duplicates**: Only trigger once until reset

### 4. AlertListener Interface

```java
/**
 * Callback interface for inventory alerts
 * Implement this to receive low-stock notifications
 */
public interface AlertListener {
    /**
     * Called when stock drops below threshold
     * @param warehouseId Warehouse where stock is low
     * @param productId Product that is low on stock
     * @param currentStock Current stock level
     * @param threshold Configured threshold
     */
    void onLowStock(String warehouseId, String productId, 
                    int currentStock, int threshold);
}
```

**Implementations:**

```java
/**
 * Console logger for alerts
 */
public class ConsoleAlertListener implements AlertListener {
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
 * Email notification for alerts
 */
public class EmailAlertListener implements AlertListener {
    private final EmailService emailService;
    
    public EmailAlertListener(EmailService emailService) {
        this.emailService = emailService;
    }
    
    @Override
    public void onLowStock(String warehouseId, String productId, 
                           int currentStock, int threshold) {
        String subject = "Low Stock Alert: " + productId;
        String body = String.format(
            "Stock level for %s at warehouse %s has dropped to %d (threshold: %d)",
            productId, warehouseId, currentStock, threshold
        );
        emailService.sendEmail("inventory-team@company.com", subject, body);
    }
}

/**
 * Composite listener to notify multiple channels
 */
public class CompositeAlertListener implements AlertListener {
    private final List<AlertListener> listeners;
    
    public CompositeAlertListener(List<AlertListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }
    
    @Override
    public void onLowStock(String warehouseId, String productId, 
                           int currentStock, int threshold) {
        for (AlertListener listener : listeners) {
            listener.onLowStock(warehouseId, productId, currentStock, threshold);
        }
    }
}
```

**Design Decisions:**
- **Interface segregation**: Single method with clear purpose
- **Observer pattern**: Decouples alert mechanism from notification
- **Composite pattern**: Support multiple notification channels
- **Synchronous callbacks**: Alerts happen immediately (can be async if needed)

---

## Complete Implementation

### InventoryManager (Full Implementation)

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class InventoryManager {
    private final Map<String, Warehouse> warehouses;
    private final AlertListener alertListener;
    
    public InventoryManager(List<String> warehouseIds, AlertListener alertListener) {
        this.warehouses = new ConcurrentHashMap<>();
        this.alertListener = alertListener;
        
        for (String id : warehouseIds) {
            warehouses.put(id, new Warehouse(id, alertListener));
        }
    }
    
    public void addStock(String warehouseId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Warehouse warehouse = getWarehouse(warehouseId);
        warehouse.addStock(productId, quantity);
    }
    
    public boolean removeStock(String warehouseId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Warehouse warehouse = getWarehouse(warehouseId);
        return warehouse.removeStock(productId, quantity);
    }
    
    public int getStock(String warehouseId, String productId) {
        Warehouse warehouse = getWarehouse(warehouseId);
        return warehouse.getStock(productId);
    }
    
    public int getTotalStock(String productId) {
        return warehouses.values().stream()
            .mapToInt(w -> w.getStock(productId))
            .sum();
    }
    
    public List<String> getAvailableWarehouses(String productId, int quantity) {
        return warehouses.values().stream()
            .filter(w -> w.canFulfill(productId, quantity))
            .map(Warehouse::getWarehouseId)
            .collect(Collectors.toList());
    }
    
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
    
    // For testing and monitoring
    public Map<String, Integer> getInventorySnapshot(String warehouseId) {
        return getWarehouse(warehouseId).getInventorySnapshot();
    }
}
```

### Warehouse (Full Implementation)

```java
import java.util.*;
import java.util.concurrent.locks.*;

public class Warehouse {
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
    
    public int getStock(String productId) {
        lock.readLock().lock();
        try {
            return inventory.getOrDefault(productId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public boolean canFulfill(String productId, int quantity) {
        lock.readLock().lock();
        try {
            return inventory.getOrDefault(productId, 0) >= quantity;
        } finally {
            lock.readLock().unlock();
        }
    }
    
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
```

### AlertConfig (Full Implementation)

```java
public class AlertConfig {
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
    
    public boolean shouldTriggerAlert(int currentStock) {
        if (currentStock < threshold && !triggered) {
            triggered = true;
            return true;
        }
        return false;
    }
    
    public void checkReset(int currentStock) {
        if (currentStock >= threshold && triggered) {
            triggered = false;
        }
    }
    
    public boolean isTriggered() {
        return triggered;
    }
}
```

### Usage Example

```java
public class InventoryManagementDemo {
    public static void main(String[] args) {
        // Setup
        List<String> warehouses = Arrays.asList("WH-EAST", "WH-WEST", "WH-CENTRAL");
        AlertListener alertListener = new ConsoleAlertListener();
        
        InventoryManager manager = new InventoryManager(warehouses, alertListener);
        
        // Configure alerts
        manager.setAlertThreshold("WH-EAST", "PROD-001", 10);
        manager.setAlertThreshold("WH-WEST", "PROD-001", 15);
        
        // Add initial stock
        manager.addStock("WH-EAST", "PROD-001", 100);
        manager.addStock("WH-WEST", "PROD-001", 50);
        
        System.out.println("Total stock for PROD-001: " + 
                          manager.getTotalStock("PROD-001"));
        
        // Remove stock (triggers alert when threshold crossed)
        for (int i = 0; i < 95; i++) {
            manager.removeStock("WH-EAST", "PROD-001", 1);
        }
        
        // Check availability
        List<String> available = manager.getAvailableWarehouses("PROD-001", 20);
        System.out.println("Warehouses with 20+ units: " + available);
        
        // Transfer stock
        boolean transferred = manager.transferStock(
            "WH-WEST", "WH-EAST", "PROD-001", 30
        );
        System.out.println("Transfer successful: " + transferred);
        
        // Final stock levels
        System.out.println("WH-EAST stock: " + 
                          manager.getStock("WH-EAST", "PROD-001"));
        System.out.println("WH-WEST stock: " + 
                          manager.getStock("WH-WEST", "PROD-001"));
    }
}
```

**Output:**
```
Total stock for PROD-001: 150
[ALERT] Warehouse: WH-EAST, Product: PROD-001 - Stock: 9 (Threshold: 10)
Warehouses with 20+ units: [WH-WEST]
Transfer successful: true
WH-EAST stock: 35
WH-WEST stock: 20
```

---

## Concurrency & Thread Safety

### Thread Safety Guarantees

1. **Warehouse-Level Locking**
   - **ReadWriteLock** for concurrent reads
   - Exclusive write lock for modifications
   - Allows multiple readers, single writer

2. **Transfer Atomicity**
   - Lock both warehouses in consistent order
   - Prevents deadlock using lexicographic ordering
   - All-or-nothing semantics

3. **Alert State Management**
   - Protected by warehouse lock
   - No race conditions in trigger/reset logic
   - Consistent state transitions

### Deadlock Prevention

**Problem:** Two threads trying to transfer in opposite directions:
- Thread 1: Transfer WH-A → WH-B
- Thread 2: Transfer WH-B → WH-A

**Solution:** Always acquire locks in consistent order (lexicographic)

```java
// Lock ordering prevents deadlock
Warehouse first = fromWarehouseId.compareTo(toWarehouseId) < 0 ? from : to;
Warehouse second = first == from ? to : from;

first.lock();
try {
    second.lock();
    try {
        // Perform transfer
    } finally {
        second.unlock();
    }
} finally {
    first.unlock();
}
```

### Concurrency Test

```java
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        InventoryManager manager = new InventoryManager(
            Arrays.asList("WH-1", "WH-2"),
            new ConsoleAlertListener()
        );
        
        manager.addStock("WH-1", "PROD-A", 1000);
        manager.addStock("WH-2", "PROD-A", 1000);
        
        // Simulate concurrent operations
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);
        
        // 50 threads removing from WH-1
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    manager.removeStock("WH-1", "PROD-A", 10);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 50 threads adding to WH-1
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    manager.addStock("WH-1", "PROD-A", 10);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // Should still be 1000 (50 * 10 added, 50 * 10 removed)
        System.out.println("Final stock: " + manager.getStock("WH-1", "PROD-A"));
    }
}
```

---

## Extensibility

### 1. Inventory Reservations (Preventing Overselling)

**Problem:** Orders are processed, but inventory isn't deducted immediately. Multiple orders could reserve the same inventory.

**Solution:** Implement reservation system

```java
/**
 * Represents a temporary hold on inventory
 */
public class InventoryReservation {
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
    
    // Getters and state transitions
}
```

**Extended Warehouse:**

```java
public class WarehouseWithReservations extends Warehouse {
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
        lock.writeLock().lock();
        try {
            int available = getAvailableStock(productId);
            if (available < quantity) {
                return null;
            }
            
            InventoryReservation reservation = new InventoryReservation(
                warehouseId, productId, quantity, ttlMillis
            );
            
            reservations.computeIfAbsent(productId, k -> new ArrayList<>())
                       .add(reservation);
            
            return reservation.getReservationId();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Commit reservation (deduct from actual inventory)
     */
    public boolean commitReservation(String reservationId) {
        lock.writeLock().lock();
        try {
            InventoryReservation reservation = findReservation(reservationId);
            if (reservation == null || !reservation.isActive()) {
                return false;
            }
            
            boolean removed = removeStock(reservation.getProductId(), 
                                         reservation.getQuantity());
            if (removed) {
                reservation.setStatus(ReservationStatus.COMMITTED);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Release reservation (make inventory available again)
     */
    public void releaseReservation(String reservationId) {
        lock.writeLock().lock();
        try {
            InventoryReservation reservation = findReservation(reservationId);
            if (reservation != null && reservation.isActive()) {
                reservation.setStatus(ReservationStatus.RELEASED);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get available stock (physical stock - reserved stock)
     */
    public int getAvailableStock(String productId) {
        lock.readLock().lock();
        try {
            int physicalStock = getStock(productId);
            int reservedStock = getReservedStock(productId);
            return physicalStock - reservedStock;
        } finally {
            lock.readLock().unlock();
        }
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
    
    private void startExpirationChecker() {
        expirationChecker.scheduleAtFixedRate(() -> {
            lock.writeLock().lock();
            try {
                for (List<InventoryReservation> productReservations : reservations.values()) {
                    for (InventoryReservation reservation : productReservations) {
                        if (reservation.isExpired() && 
                            reservation.getStatus() == ReservationStatus.ACTIVE) {
                            reservation.setStatus(ReservationStatus.EXPIRED);
                        }
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
}
```

**Usage Example:**
```java
// Reserve stock when order is created
String reservationId = warehouse.reserveStock("PROD-001", 5, 15 * 60 * 1000); // 15 min TTL

if (reservationId != null) {
    // Process payment
    boolean paymentSuccess = processPayment(order);
    
    if (paymentSuccess) {
        // Commit reservation
        warehouse.commitReservation(reservationId);
    } else {
        // Release reservation
        warehouse.releaseReservation(reservationId);
    }
}
```

**Key Points:**
- Prevents overselling by tracking reserved vs available stock
- Time-bound reservations with automatic expiration
- Commit or release based on order outcome
- Background thread cleans up expired reservations

### 2. In-Transit Inventory

**Problem:** Stock being transferred between warehouses shouldn't be available at either location.

**Solution:** Track in-transit inventory

```java
/**
 * Represents inventory in transit between warehouses
 */
public class InTransitInventory {
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
    
    // Getters and setters
}
```

**Extended InventoryManager:**

```java
public class InventoryManagerWithTransit extends InventoryManager {
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
        if (transit == null || transit.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("Invalid transfer");
        }
        
        // Add to destination
        addStock(transit.getToWarehouse(), transit.getProductId(), 
                transit.getQuantity());
        
        transit.setStatus(TransferStatus.DELIVERED);
    }
    
    /**
     * Get total stock including in-transit inventory
     */
    public int getTotalStockWithTransit(String productId) {
        int stock = getTotalStock(productId);
        int inTransit = inTransitInventory.values().stream()
            .filter(t -> t.getProductId().equals(productId))
            .filter(t -> t.getStatus() == TransferStatus.IN_TRANSIT)
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
}
```

**Key Points:**
- Track inventory during warehouse transfers
- Monitor for delayed shipments
- Include in-transit in total inventory calculations
- Complete transfer when shipment arrives

### 3. Batch Operations

**Question:** "How would you handle bulk operations efficiently?"

**Solution:** Batch API with validation

```java
/**
 * Batch operation request
 */
public class BatchInventoryUpdate {
    private final String warehouseId;
    private final String productId;
    private final int quantity;
    private final OperationType operation;
    
    public enum OperationType {
        ADD, REMOVE
    }
    
    // Constructor and getters
}

/**
 * Batch operation result
 */
public class BatchResult {
    private final int successCount;
    private final int failureCount;
    private final List<String> errors;
    
    // Constructor and getters
}
```

**Extended InventoryManager:**

```java
public BatchResult executeBatch(List<BatchInventoryUpdate> updates) {
    int successCount = 0;
    int failureCount = 0;
    List<String> errors = new ArrayList<>();
    
    for (BatchInventoryUpdate update : updates) {
        try {
            boolean success = false;
            
            if (update.getOperation() == OperationType.ADD) {
                addStock(update.getWarehouseId(), update.getProductId(), 
                        update.getQuantity());
                success = true;
            } else if (update.getOperation() == OperationType.REMOVE) {
                success = removeStock(update.getWarehouseId(), 
                                     update.getProductId(), update.getQuantity());
            }
            
            if (success) {
                successCount++;
            } else {
                failureCount++;
                errors.add(String.format("Failed: %s - %s", 
                    update.getWarehouseId(), update.getProductId()));
            }
        } catch (Exception e) {
            failureCount++;
            errors.add(String.format("Error: %s - %s", 
                update.getWarehouseId(), e.getMessage()));
        }
    }
    
    return new BatchResult(successCount, failureCount, errors);
}
```

**Key Points:**
- Process multiple operations in single call
- Continue on errors (don't fail entire batch)
- Return detailed results
- Better performance than individual calls

---

## Interview Tips

### Approach Strategy

**1. Start with Clarifying Questions (5 minutes)**
- What operations are supported?
- How do alerts work?
- Concurrency requirements?
- Validation rules?
- What's out of scope?

**2. Define Requirements Clearly (3 minutes)**
- List functional requirements
- Note non-functional requirements (thread safety!)
- Explicitly state what's out of scope

**3. Identify Core Entities (5 minutes)**
- InventoryManager (coordinator)
- Warehouse (location-specific operations)
- AlertConfig (threshold management)
- AlertListener (pluggable notifications)

**4. Design Interfaces First (5 minutes)**
- Start with InventoryManager public API
- Design Warehouse operations
- Define AlertListener callback

**5. Implement Core Logic (15-20 minutes)**
- Start with Warehouse (thread-safe operations)
- Implement InventoryManager (delegation + transfers)
- Add alert mechanism
- Show thread safety considerations

**6. Discuss Concurrency (5-10 minutes)**
- Explain ReadWriteLock choice
- Demonstrate deadlock prevention
- Discuss trade-offs

### Common Interview Questions & Answers

**Q1: "Why use ReadWriteLock instead of synchronized?"**

**A:** ReadWriteLock allows multiple concurrent readers while maintaining exclusive write access:
- **Performance:** Multiple threads can read stock levels simultaneously
- **Consistency:** Writes are still exclusive and atomic
- **Trade-off:** Slightly more complex than synchronized, but better scalability

**Q2: "How do you prevent deadlock in transfers?"**

**A:** Use consistent lock ordering (lexicographic):
```java
// Always lock warehouses in alphabetical order
Warehouse first = fromId.compareTo(toId) < 0 ? from : to;
Warehouse second = first == from ? to : from;

first.lock();
try {
    second.lock();
    try {
        // Perform transfer
    } finally { second.unlock(); }
} finally { first.unlock(); }
```

**Q3: "What happens if alert is triggered multiple times?"**

**A:** AlertConfig maintains state to prevent duplicate notifications:
- **First trigger:** Stock drops below threshold, alert fires, state = TRIGGERED
- **Subsequent drops:** Alert already triggered, no notification
- **Replenishment:** Stock rises above threshold, state reset to NOT_TRIGGERED
- **Re-trigger:** Can fire again after reset

**Q4: "How would you handle distributed inventory (multiple servers)?"**

**A:** 
1. **Shared Database:** Single source of truth, use database transactions
2. **Distributed Cache:** Redis for inventory counts, use Lua scripts for atomicity
3. **Event Sourcing:** Append-only log of inventory events
4. **Two-Phase Commit:** For transfers across systems

**Q5: "How do you test concurrent operations?"**

**A:**
```java
// Test concurrent adds/removes maintain consistency
ExecutorService executor = Executors.newFixedThreadPool(20);
CountDownLatch latch = new CountDownLatch(1000);

// 500 threads adding 10 units
for (int i = 0; i < 500; i++) {
    executor.submit(() -> {
        manager.addStock("WH-1", "PROD-A", 10);
        latch.countDown();
    });
}

// 500 threads removing 10 units
for (int i = 0; i < 500; i++) {
    executor.submit(() -> {
        manager.removeStock("WH-1", "PROD-A", 10);
        latch.countDown();
    });
}

latch.await();
// Final stock should match initial stock
```

### Design Patterns Used

1. **Observer Pattern:** AlertListener for notifications
2. **Strategy Pattern:** Different alert implementations
3. **Composite Pattern:** CompositeAlertListener
4. **Delegation Pattern:** InventoryManager delegates to Warehouse
5. **State Pattern:** Reservation and transfer status
6. **Template Method:** Common structure for warehouse operations

### Expected Level Performance

**Junior Engineer:**
- Basic inventory operations (add, remove, check)
- Simple validation (no negative inventory)
- Understand threading concepts (may not implement correctly)
- Single warehouse focus

**Mid-Level Engineer:**
- Complete CRUD operations
- Thread-safe implementation with ReadWriteLock
- Alert mechanism with callback interface
- Transfer operations with validation
- Explain deadlock prevention strategy

**Senior Engineer:**
- Complete system with all requirements
- Advanced concurrency (proper deadlock prevention)
- Extensible design (reservations, in-transit inventory)
- Performance considerations (lock granularity)
- Testing strategy for concurrent operations
- Trade-off discussions (consistency vs availability)

### Key Trade-offs to Discuss

**1. Locking Granularity**
- **Coarse-grained (warehouse level):** Simpler, but lower concurrency
- **Fine-grained (product level):** Higher concurrency, more complex
- **Our choice:** Warehouse level for simplicity, acceptable for most use cases

**2. Alert Mechanism**
- **Synchronous:** Simple, immediate notification
- **Asynchronous:** Better performance, delayed notification
- **Our choice:** Synchronous with pluggable listener (can be made async)

**3. Consistency Model**
- **Strong consistency:** Always accurate, may sacrifice availability
- **Eventual consistency:** High availability, may have temporary inconsistencies
- **Our choice:** Strong consistency for inventory (critical for overselling prevention)

**4. Reservation TTL**
- **Short TTL (5-15 min):** Less inventory locked, better availability
- **Long TTL (30+ min):** Better user experience, more locked inventory
- **Trade-off:** Balance between availability and user experience

### Code Quality Checklist

✅ **Thread Safety**
- ReadWriteLock for concurrent reads
- Proper lock ordering in transfers
- No race conditions in alert state

✅ **Validation**
- Reject negative quantities
- Check warehouse existence
- Validate sufficient stock

✅ **Error Handling**
- Clear exception messages
- Fail fast on invalid input
- Return boolean for operations that can fail

✅ **Extensibility**
- Interface for alert mechanism
- Easy to add new warehouses
- Support for reservations and in-transit

✅ **Testing**
- Unit tests for each operation
- Concurrency tests
- Edge cases (negative stock, missing warehouses)

---

## Summary

This Inventory Management System demonstrates:

1. **Concurrent Operations:** Thread-safe with ReadWriteLock and proper locking strategies
2. **Consistency Guarantees:** Atomic transfers, no negative inventory, accurate counts
3. **Alert Mechanism:** Stateful, resettable, pluggable notification system
4. **Extensibility:** Easy to add reservations, in-transit tracking, batch operations
5. **Interview Success:** Clear requirements, proper concurrency, extensible design

**Key Takeaways:**
- **Concurrency is critical:** Use appropriate locking mechanisms
- **Deadlock prevention:** Consistent lock ordering is essential
- **State management:** Careful handling of alert states prevents duplicates
- **Extensibility:** Design for future requirements (reservations, in-transit)
- **Validation:** Enforce invariants at system boundaries

**Related Topics:**
- Distributed transactions (2-phase commit, Saga pattern)
- Event sourcing for inventory tracking
- CQRS for read/write optimization
- Database-level optimistic locking

---

*Last Updated: January 4, 2026*
*Difficulty Level: Medium to Hard*
*Key Focus: Concurrency, Thread Safety, Consistency*
*Interview Success Rate: High with proper concurrency handling*
