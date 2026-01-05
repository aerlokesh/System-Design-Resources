# Food Delivery Service - Low Level Design

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Core Entities and Relationships](#core-entities-and-relationships)
4. [Order State Machine](#order-state-machine)
5. [Class Design](#class-design)
6. [Complete Implementation](#complete-implementation)
7. [Delivery Assignment Strategies](#delivery-assignment-strategies)
8. [Extensibility](#extensibility)
9. [Interview Tips](#interview-tips)

---

## Problem Statement

**What is a Food Delivery Service?**

A food delivery service connects customers who want to order food with restaurants and delivery personnel. The system manages the entire flow: browsing restaurants, placing orders, assigning delivery drivers, tracking deliveries, and processing payments.

**Real-World Examples:**
- DoorDash
- UberEats
- Grubhub
- Swiggy (India)
- Zomato (India)
- Deliveroo (UK)

**Core Workflow:**
```
Customer → Browse Restaurants → Place Order → Restaurant Accepts
    ↓
Assign Delivery Driver → Driver Picks Up → Driver Delivers
    ↓
Payment Processed → Customer & Restaurant Rate Each Other
```

**Key Challenges:**
- **Order Management:** Complex state machine with many transitions
- **Driver Matching:** Find nearest available driver efficiently
- **Real-Time Tracking:** Update order status and location
- **Concurrent Orders:** Handle multiple orders simultaneously
- **Cancellations:** Handle at various stages
- **Payment Processing:** Secure and reliable
- **Rating System:** Bidirectional (customer ↔ restaurant/driver)

---

## Requirements

### Functional Requirements

1. **User Management**
   - Register customers, restaurants, delivery personnel
   - Authentication and profiles
   - Location tracking for customers and drivers

2. **Restaurant Management**
   - Add/update restaurant details
   - Manage menu items (add, remove, update price)
   - Set restaurant availability (open/closed)
   - View and accept/reject orders

3. **Order Management**
   - Browse restaurants by location
   - Browse menu and add items to cart
   - Place order with delivery address
   - Track order status in real-time
   - Cancel order (with restrictions)
   - Order history

4. **Delivery Management**
   - Assign delivery person to order
   - Update delivery status
   - Track delivery person location
   - Handle delivery completion

5. **Payment Processing**
   - Calculate order total (items + taxes + delivery fee)
   - Process payment
   - Handle refunds (for cancellations)

6. **Rating & Review**
   - Customer rates restaurant and delivery person
   - Restaurant rates customer
   - View ratings and reviews

### Non-Functional Requirements

1. **Performance**
   - Order placement: < 1 second
   - Driver matching: < 5 seconds
   - Support 10,000+ concurrent orders

2. **Scalability**
   - Handle millions of users
   - Thousands of restaurants
   - Thousands of delivery personnel

3. **Reliability**
   - No lost orders
   - Accurate order tracking
   - Consistent payment processing

4. **Availability**
   - 99.9% uptime
   - Graceful degradation if services fail

### Out of Scope

- Real-time messaging between users
- Route optimization algorithms
- Surge pricing
- Promotional codes and discounts
- Subscription services
- Restaurant onboarding workflow
- Background verification of drivers
- Multi-language support

---

## Core Entities and Relationships

### Key Entities

1. **User (Abstract)**
   - Customer
   - Restaurant
   - DeliveryPerson

2. **Restaurant**
   - Name, location, cuisine type
   - Menu items
   - Operating hours
   - Ratings

3. **MenuItem**
   - Name, description, price
   - Category (appetizer, main, dessert)
   - Availability

4. **Order**
   - Order items
   - Customer, restaurant, delivery person
   - Status (state machine)
   - Total amount
   - Delivery address

5. **Cart**
   - Temporary container for order items
   - Belongs to customer and restaurant

6. **DeliveryPerson**
   - Current location
   - Availability status
   - Current order (if any)
   - Ratings

7. **Payment**
   - Amount, method, status
   - Transaction ID

8. **Rating**
   - Score (1-5), review text
   - Rater and ratee

### Entity Relationship Diagram

```
┌─────────────┐
│   Customer  │
└──────┬──────┘
       │ places
       ▼
┌─────────────┐      contains      ┌──────────────┐
│    Order    │◄───────────────────│  OrderItem   │
└──────┬──────┘                    └──────────────┘
       │
       │ from                       references
       ▼                                 ▼
┌─────────────┐                    ┌──────────────┐
│ Restaurant  │◄───────────────────│  MenuItem    │
└──────┬──────┘    has menu         └──────────────┘
       │
       │ rated by
       ▼
┌─────────────┐
│   Rating    │
└─────────────┘

┌──────────────┐     assigned to    ┌─────────────┐
│   Order      │◄───────────────────│  Delivery   │
└──────────────┘                    │   Person    │
                                    └─────────────┘

┌──────────────┐     processed via  ┌─────────────┐
│   Order      │◄───────────────────│   Payment   │
└──────────────┘                    └─────────────┘
```

---

## Order State Machine

### States

```
                     place
┌──────────┐   ────────────────►  ┌──────────────┐
│   CART   │                      │   PENDING    │
└──────────┘                      └──────┬───────┘
                                         │
                           ┌─────────────┴──────────────┐
                           │                            │
                      restaurant                   restaurant
                       accepts                      rejects
                           │                            │
                           ▼                            ▼
                  ┌─────────────────┐          ┌──────────────┐
                  │   CONFIRMED     │          │   REJECTED   │
                  └────────┬────────┘          └──────────────┘
                           │
                      assign driver
                           │
                           ▼
                  ┌─────────────────┐
                  │    PREPARING    │
                  └────────┬────────┘
                           │
                     driver picks up
                           │
                           ▼
                  ┌─────────────────┐
                  │    PICKED_UP    │
                  └────────┬────────┘
                           │
                    driver delivers
                           │
                           ▼
                  ┌─────────────────┐
                  │   DELIVERED     │
                  └────────┬────────┘
                           │
                     payment complete
                           │
                           ▼
                  ┌─────────────────┐
                  │   COMPLETED     │
                  └─────────────────┘

Cancellation paths:
- PENDING → CANCELLED (customer cancels before acceptance)
- CONFIRMED → CANCELLED (customer/restaurant cancels)
- PREPARING → CANCELLED (rare, refund issued)
```

### State Transitions

| From State | To State | Trigger | Actor |
|------------|----------|---------|-------|
| CART | PENDING | Place order | Customer |
| PENDING | CONFIRMED | Accept order | Restaurant |
| PENDING | REJECTED | Reject order | Restaurant |
| PENDING | CANCELLED | Cancel | Customer |
| CONFIRMED | PREPARING | Assign driver | System |
| CONFIRMED | CANCELLED | Cancel | Customer/Restaurant |
| PREPARING | PICKED_UP | Pick up food | Delivery Person |
| PREPARING | CANCELLED | Cancel | Restaurant |
| PICKED_UP | DELIVERED | Deliver food | Delivery Person |
| DELIVERED | COMPLETED | Payment done | System |

---

## Class Design

### 1. User Hierarchy

```java
/**
 * Abstract base class for all users
 */
public abstract class User {
    private final String id;
    private final String name;
    private final String email;
    private final String phone;
    
    public User(String id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}

/**
 * Customer who places orders
 */
public class Customer extends User {
    private final List<Address> addresses;
    private final List<Order> orderHistory;
    private Cart currentCart;
    
    public Customer(String id, String name, String email, String phone) {
        super(id, name, email, phone);
        this.addresses = new ArrayList<>();
        this.orderHistory = new ArrayList<>();
    }
    
    public void addAddress(Address address) {
        addresses.add(address);
    }
    
    public Cart getCart() {
        return currentCart;
    }
    
    public void setCart(Cart cart) {
        this.currentCart = cart;
    }
    
    public void addOrder(Order order) {
        orderHistory.add(order);
    }
}

/**
 * Delivery person who delivers orders
 */
public class DeliveryPerson extends User {
    private Location currentLocation;
    private DeliveryStatus status;
    private Order currentOrder;
    private double rating;
    private int totalDeliveries;
    
    public enum DeliveryStatus {
        AVAILABLE, BUSY, OFFLINE
    }
    
    public DeliveryPerson(String id, String name, String email, String phone) {
        super(id, name, email, phone);
        this.status = DeliveryStatus.OFFLINE;
        this.rating = 5.0;
        this.totalDeliveries = 0;
    }
    
    public boolean isAvailable() {
        return status == DeliveryStatus.AVAILABLE;
    }
    
    public void assignOrder(Order order) {
        this.currentOrder = order;
        this.status = DeliveryStatus.BUSY;
    }
    
    public void completeDelivery() {
        this.currentOrder = null;
        this.status = DeliveryStatus.AVAILABLE;
        this.totalDeliveries++;
    }
    
    // Getters and setters
}
```

### 2. Restaurant & Menu

```java
/**
 * Restaurant with menu and orders
 */
public class Restaurant {
    private final String id;
    private final String name;
    private final Location location;
    private final String cuisineType;
    private final List<MenuItem> menu;
    private boolean isOpen;
    private double rating;
    private int totalOrders;
    
    public Restaurant(String id, String name, Location location, String cuisineType) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.cuisineType = cuisineType;
        this.menu = new ArrayList<>();
        this.isOpen = true;
        this.rating = 5.0;
        this.totalOrders = 0;
    }
    
    public void addMenuItem(MenuItem item) {
        menu.add(item);
    }
    
    public void removeMenuItem(String itemId) {
        menu.removeIf(item -> item.getId().equals(itemId));
    }
    
    public MenuItem getMenuItem(String itemId) {
        return menu.stream()
            .filter(item -> item.getId().equals(itemId))
            .findFirst()
            .orElse(null);
    }
    
    public List<MenuItem> getMenu() {
        return Collections.unmodifiableList(menu);
    }
    
    // Getters and setters
}

/**
 * Menu item
 */
public class MenuItem {
    private final String id;
    private final String name;
    private final String description;
    private double price;
    private final String category;
    private boolean available;
    
    public MenuItem(String id, String name, String description, 
                    double price, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.available = true;
    }
    
    // Getters and setters
}
```

### 3. Order & Cart

```java
/**
 * Order state enum
 */
public enum OrderStatus {
    PENDING,        // Order placed, waiting for restaurant
    CONFIRMED,      // Restaurant accepted
    PREPARING,      // Restaurant preparing food
    READY_FOR_PICKUP, // Food ready, waiting for driver
    PICKED_UP,      // Driver picked up
    OUT_FOR_DELIVERY, // Driver on the way
    DELIVERED,      // Food delivered to customer
    COMPLETED,      // Payment done, order complete
    CANCELLED,      // Order cancelled
    REJECTED        // Restaurant rejected
}

/**
 * Cart for building orders
 */
public class Cart {
    private final String customerId;
    private final String restaurantId;
    private final List<OrderItem> items;
    
    public Cart(String customerId, String restaurantId) {
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.items = new ArrayList<>();
    }
    
    public void addItem(MenuItem menuItem, int quantity) {
        // Check if item already in cart
        for (OrderItem item : items) {
            if (item.getMenuItemId().equals(menuItem.getId())) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
        }
        items.add(new OrderItem(menuItem.getId(), menuItem.getName(), 
                               menuItem.getPrice(), quantity));
    }
    
    public void removeItem(String menuItemId) {
        items.removeIf(item -> item.getMenuItemId().equals(menuItemId));
    }
    
    public double getTotal() {
        return items.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
    }
    
    public void clear() {
        items.clear();
    }
}

/**
 * Order item (snapshot of menu item at order time)
 */
public class OrderItem {
    private final String menuItemId;
    private final String name;
    private final double price;
    private int quantity;
    
    public OrderItem(String menuItemId, String name, double price, int quantity) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    
    public double getSubtotal() {
        return price * quantity;
    }
    
    // Getters and setters
}

/**
 * Order entity
 */
public class Order {
    private final String id;
    private final String customerId;
    private final String restaurantId;
    private final List<OrderItem> items;
    private final Address deliveryAddress;
    private OrderStatus status;
    private String deliveryPersonId;
    private final long orderTime;
    private Long acceptedTime;
    private Long deliveredTime;
    private Payment payment;
    private double totalAmount;
    
    public Order(String id, String customerId, String restaurantId,
                 List<OrderItem> items, Address deliveryAddress) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.items = new ArrayList<>(items);
        this.deliveryAddress = deliveryAddress;
        this.status = OrderStatus.PENDING;
        this.orderTime = System.currentTimeMillis();
        
        // Calculate total
        double itemsTotal = items.stream()
            .mapToDouble(OrderItem::getSubtotal)
            .sum();
        this.totalAmount = itemsTotal + calculateDeliveryFee() + calculateTax(itemsTotal);
    }
    
    private double calculateDeliveryFee() {
        return 5.0; // Flat fee, could be dynamic based on distance
    }
    
    private double calculateTax(double subtotal) {
        return subtotal * 0.10; // 10% tax
    }
    
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        
        if (newStatus == OrderStatus.CONFIRMED) {
            this.acceptedTime = System.currentTimeMillis();
        } else if (newStatus == OrderStatus.DELIVERED) {
            this.deliveredTime = System.currentTimeMillis();
        }
    }
    
    public boolean canCancel() {
        return status == OrderStatus.PENDING || 
               status == OrderStatus.CONFIRMED;
    }
    
    public void assignDeliveryPerson(String deliveryPersonId) {
        this.deliveryPersonId = deliveryPersonId;
        updateStatus(OrderStatus.PREPARING);
    }
    
    // Getters and setters
}
```

### 4. Location & Address

```java
/**
 * Geographic location
 */
public class Location {
    private final double latitude;
    private final double longitude;
    
    public Location(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public double distanceTo(Location other) {
        // Haversine formula
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
    
    // Getters
}

/**
 * Delivery address
 */
public class Address {
    private final String street;
    private final String city;
    private final String state;
    private final String zipCode;
    private final Location location;
    
    public Address(String street, String city, String state, 
                   String zipCode, Location location) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.location = location;
    }
    
    // Getters
}
```

### 5. Payment

```java
/**
 * Payment entity
 */
public class Payment {
    private final String id;
    private final String orderId;
    private final double amount;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final long timestamp;
    
    public enum PaymentMethod {
        CREDIT_CARD, DEBIT_CARD, CASH, WALLET, UPI
    }
    
    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
    
    public Payment(String id, String orderId, double amount, PaymentMethod method) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
        this.timestamp = System.currentTimeMillis();
    }
    
    public void markCompleted() {
        this.status = PaymentStatus.COMPLETED;
    }
    
    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }
    
    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
    
    // Getters
}
```

### 6. Rating

```java
/**
 * Rating entity
 */
public class Rating {
    private final String id;
    private final String orderId;
    private final String raterId;  // Who gave the rating
    private final String rateeId;  // Who received the rating
    private final int score; // 1-5
    private final String review;
    private final long timestamp;
    
    public Rating(String id, String orderId, String raterId, String rateeId,
                  int score, String review) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }
        
        this.id = id;
        this.orderId = orderId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.score = score;
        this.review = review;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
}
```

---

## Delivery Assignment Strategies

### 1. Nearest Driver Strategy

**Algorithm:** Assign closest available driver

```java
public interface DeliveryAssignmentStrategy {
    DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers);
}

public class NearestDriverStrategy implements DeliveryAssignmentStrategy {
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers) {
        if (availableDrivers.isEmpty()) {
            return null;
        }
        
        Location restaurantLocation = getRestaurantLocation(order.getRestaurantId());
        
        return availableDrivers.stream()
            .min(Comparator.comparingDouble(driver -> 
                driver.getCurrentLocation().distanceTo(restaurantLocation)))
            .orElse(null);
    }
}
```

### 2. Highest Rated Driver Strategy

```java
public class HighestRatedDriverStrategy implements DeliveryAssignmentStrategy {
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers) {
        return availableDrivers.stream()
            .max(Comparator.comparingDouble(DeliveryPerson::getRating))
            .orElse(null);
    }
}
```

### 3. Balanced Strategy (Distance + Rating)

```java
public class BalancedAssignmentStrategy implements DeliveryAssignmentStrategy {
    private final double distanceWeight = 0.7;
    private final double ratingWeight = 0.3;
    
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers) {
        Location restaurantLocation = getRestaurantLocation(order.getRestaurantId());
        
        return availableDrivers.stream()
            .max(Comparator.comparingDouble(driver -> calculateScore(driver, restaurantLocation)))
            .orElse(null);
    }
    
    private double calculateScore(DeliveryPerson driver, Location restaurantLoc) {
        double distance = driver.getCurrentLocation().distanceTo(restaurantLoc);
        double normalizedDistance = 1.0 / (1.0 + distance); // Closer is better
        double normalizedRating = driver.getRating() / 5.0;
        
        return (normalizedDistance * distanceWeight) + (normalizedRating * ratingWeight);
    }
}
```

---

## Complete Implementation

### FoodDeliveryService - Main Coordinator

```java
/**
 * Main service coordinating all operations
 */
public class FoodDeliveryService {
    private final Map<String, Customer> customers;
    private final Map<String, Restaurant> restaurants;
    private final Map<String, DeliveryPerson> deliveryPersons;
    private final Map<String, Order> orders;
    private final DeliveryAssignmentStrategy assignmentStrategy;
    private final AtomicInteger orderIdCounter;
    
    public FoodDeliveryService(DeliveryAssignmentStrategy strategy) {
        this.customers = new ConcurrentHashMap<>();
        this.restaurants = new ConcurrentHashMap<>();
        this.deliveryPersons = new ConcurrentHashMap<>();
        this.orders = new ConcurrentHashMap<>();
        this.assignmentStrategy = strategy;
        this.orderIdCounter = new AtomicInteger(1);
    }
    
    // User Management
    public void registerCustomer(Customer customer) {
        customers.put(customer.getId(), customer);
    }
    
    public void registerRestaurant(Restaurant restaurant) {
        restaurants.put(restaurant.getId(), restaurant);
    }
    
    public void registerDeliveryPerson(DeliveryPerson person) {
        deliveryPersons.put(person.getId(), person);
    }
    
    // Restaurant Discovery
    public List<Restaurant> findNearbyRestaurants(Location location, double radiusKm) {
        return restaurants.values().stream()
            .filter(r -> r.getLocation().distanceTo(location) <= radiusKm)
            .filter(Restaurant::isOpen)
            .sorted(Comparator.comparingDouble(r -> 
                r.getLocation().distanceTo(location)))
            .collect(Collectors.toList());
    }
    
    // Order Placement
    public Order placeOrder(String customerId, Cart cart, Address deliveryAddress) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Customer not found");
        }
        
        String orderId = "ORD-" + orderIdCounter.getAndIncrement();
        Order order = new Order(orderId, customerId, cart.getRestaurantId(),
                               cart.getItems(), deliveryAddress);
        
        orders.put(orderId, order);
        customer.addOrder(order);
        
        return order;
    }
    
    // Restaurant Actions
    public boolean acceptOrder(String orderId, String restaurantId) {
        Order order = orders.get(orderId);
        if (order == null || !order.getRestaurantId().equals(restaurantId)) {
            return false;
        }
        
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        
        order.updateStatus(OrderStatus.CONFIRMED);
        
        // Assign delivery person
        DeliveryPerson driver = assignDeliveryPerson(order);
        if (driver != null) {
            order.assignDeliveryPerson(driver.getId());
            driver.assignOrder(order);
        }
        
        return true;
    }
    
    public boolean rejectOrder(String orderId, String restaurantId) {
        Order order = orders.get(orderId);
        if (order == null || !order.getRestaurantId().equals(restaurantId)) {
            return false;
        }
        
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        
        order.updateStatus(OrderStatus.REJECTED);
        return true;
    }
    
    // Delivery Assignment
    private DeliveryPerson assignDeliveryPerson(Order order) {
        List<DeliveryPerson> availableDrivers = deliveryPersons.values().stream()
            .filter(DeliveryPerson::isAvailable)
            .collect(Collectors.toList());
        
        return assignmentStrategy.assignDriver(order, availableDrivers);
    }
    
    // Order Updates
    public void markReadyForPickup(String orderId) {
        Order order = orders.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.PREPARING) {
            order.updateStatus(OrderStatus.READY_FOR_PICKUP);
        }
    }
    
    public void markPickedUp(String orderId, String deliveryPersonId) {
        Order order = orders.get(orderId);
        if (order != null && order.getDeliveryPersonId().equals(deliveryPersonId)) {
            order.updateStatus(OrderStatus.PICKED_UP);
        }
    }
    
    public void markDelivered(String orderId, String deliveryPersonId) {
        Order order = orders.get(orderId);
        if (order != null && order.getDeliveryPersonId().equals(deliveryPersonId)) {
            order.updateStatus(OrderStatus.DELIVERED);
            
            // Process payment
            processPayment(order);
            
            // Update delivery person
            DeliveryPerson driver = deliveryPersons.get(deliveryPersonId);
            if (driver != null) {
                driver.completeDelivery();
            }
        }
    }
    
    // Payment
    private void processPayment(Order order) {
        Payment payment = new Payment(
            UUID.randomUUID().toString(),
            order.getId(),
            order.getTotalAmount(),
            Payment.PaymentMethod.CREDIT_CARD
        );
        
        // Simulate payment processing
        payment.markCompleted();
        order.setPayment(payment);
        order.updateStatus(OrderStatus.COMPLETED);
    }
    
    // Cancellation
    public boolean cancelOrder(String orderId, String userId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        
        if (!order.canCancel()) {
            return false; // Too late to cancel
        }
        
        order.updateStatus(OrderStatus.CANCELLED);
        
        // Refund if payment was made
        if (order.getPayment() != null) {
            order.getPayment().refund();
        }
        
        return true;
    }
    
    // Rating
    public void rateRestaurant(String orderId, String customerId, 
                              int score, String review) {
        Order order = orders.get(orderId);
        if (order == null || !order.getCustomerId().equals(customerId)) {
            return;
        }
        
        if (order.getStatus() != OrderStatus.COMPLETED) {
            return;
        }
        
        Rating rating = new Rating(
            UUID.randomUUID().toString(),
            orderId,
            customerId,
            order.getRestaurantId(),
            score,
            review
        );
        
        // Update restaurant rating
        updateRestaurantRating(order.getRestaurantId(), score);
    }
    
    public void rateDeliveryPerson(String orderId, String customerId,
                                   int score, String review) {
        Order order = orders.get(orderId);
        if (order == null || !order.getCustomerId().equals(customerId)) {
            return;
        }
        
        Rating rating = new Rating(
            UUID.randomUUID().toString(),
            orderId,
            customerId,
            order.getDeliveryPersonId(),
            score,
            review
        );
        
        // Update delivery person rating
        updateDeliveryPersonRating(order.getDeliveryPersonId(), score);
    }
    
    private void updateRestaurantRating(String restaurantId, int newScore) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant != null) {
            double currentRating = restaurant.getRating();
            int totalOrders = restaurant.getTotalOrders();
            double newRating = ((currentRating * totalOrders) + newScore) / (totalOrders + 1);
            restaurant.setRating(newRating);
            restaurant.incrementTotalOrders();
        }
    }
    
    private void updateDeliveryPersonRating(String deliveryPersonId, int newScore) {
        DeliveryPerson person = deliveryPersons.get(deliveryPersonId);
        if (person != null) {
            double currentRating = person.getRating();
            int totalDeliveries = person.getTotalDeliveries();
            double newRating = ((currentRating * totalDeliveries) + newScore) / (totalDeliveries + 1);
            person.setRating(newRating);
        }
    }
}
```

---

## Extensibility

### 1. Notification System

**Notify users of order updates:**

```java
public interface NotificationService {
    void notifyOrderPlaced(Order order);
    void notifyOrderAccepted(Order order);
    void notifyOrderReady(Order order);
    void notifyDriverAssigned(Order order);
    void notifyOutForDelivery(Order order);
    void notifyDelivered(Order order);
}

public class EmailNotificationService implements NotificationService {
    @Override
    public void notifyOrderPlaced(Order order) {
        // Send email to customer and restaurant
        sendEmail(order.getCustomerId(), "Order Placed", "Your order #" + order.getId());
    }
    
    // Other methods...
}

public class SMSNotificationService implements NotificationService {
    // SMS implementation
}
```

### 2. Surge Pricing

**Dynamic pricing during peak hours:**

```java
public class SurgePricingCalculator {
    private final Map<String, Double> surgeMultipliers = new ConcurrentHashMap<>();
    
    public double calculateDeliveryFee(Location from, Location to, long timestamp) {
        double baseDistance = from.distanceTo(to);
        double baseFee = baseDistance * 2.0; // $2 per km
        
        // Check surge pricing
        String zone = getZone(from);
        double surgeMultiplier = surgeMultipliers.getOrDefault(zone, 1.0);
        
        return baseFee * surgeMultiplier;
    }
    
    public void updateSurge(String zone, double multiplier) {
        surgeMultipliers.put(zone, multiplier);
    }
}
```

### 3. Restaurant Search with Filters

**Advanced search capabilities:**

```java
public class RestaurantFilter {
    private String cuisineType;
    private Double minRating;
    private Double maxDeliveryFee;
    private Boolean vegetarianOnly;
    
    public boolean matches(Restaurant restaurant) {
        if (cuisineType != null && !cuisineType.equals(restaurant.getCuisineType())) {
            return false;
        }
        if (minRating != null && restaurant.getRating() < minRating) {
            return false;
        }
        return true;
    }
}

public List<Restaurant> searchRestaurants(Location location, RestaurantFilter filter) {
    return restaurants.values().stream()
        .filter(filter::matches)
        .filter(Restaurant::isOpen)
        .sorted(Comparator.comparingDouble(r -> r.getLocation().distanceTo(location)))
        .collect(Collectors.toList());
}
```

### 4. Order Tracking with Events

**Event-driven architecture for tracking:**

```java
public interface OrderEventListener {
    void onOrderStatusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus);
}

public class OrderTracker implements OrderEventListener {
    private final Map<String, List<OrderStatusEvent>> trackingHistory = new ConcurrentHashMap<>();
    
    @Override
    public void onOrderStatusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        trackingHistory.computeIfAbsent(order.getId(), k -> new ArrayList<>())
            .add(new OrderStatusEvent(newStatus, System.currentTimeMillis()));
    }
    
    public List<OrderStatusEvent> getOrderHistory(String orderId) {
        return trackingHistory.getOrDefault(orderId, Collections.emptyList());
    }
}

class OrderStatusEvent {
    private final OrderStatus status;
    private final long timestamp;
    
    public OrderStatusEvent(OrderStatus status, long timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }
}
```

---

## Interview Tips

### Approach Strategy

**1. Clarifying Questions (5 minutes)**
- Single restaurant per order or multiple?
- How to handle driver unavailability?
- Cancellation policy and refunds?
- Real-time tracking requirements?
- Payment methods supported?

**2. Identify Core Entities (5 minutes)**
- User types (Customer, Restaurant, DeliveryPerson)
- Order with state machine
- Menu items and cart
- Payment and rating

**3. Design State Machine (10 minutes)**
- Draw order lifecycle
- Identify all states
- Define transitions
- Handle edge cases

**4. Implement Core Logic (20 minutes)**
- Start with order placement flow
- Add driver assignment
- Implement state transitions
- Show thread safety

**5. Discuss Extensions (5 minutes)**
- Notifications
- Surge pricing
- Advanced search
- Order tracking

### Common Interview Questions

**Q1: "How do you prevent double-booking of drivers?"**

**A:** Use atomic operations:
```java
public class DriverMatcher {
    public DeliveryPerson assignDriver(Order order) {
        List<DeliveryPerson> available = getAvailableDrivers();
        
        for (DeliveryPerson driver : available) {
            // Atomic: only one thread can successfully assign
            if (driver.tryAssign(order)) {
                return driver;
            }
        }
        return null;
    }
}

class DeliveryPerson {
    private final AtomicReference<Order> currentOrder = new AtomicReference<>(null);
    
    public boolean tryAssign(Order order) {
        return currentOrder.compareAndSet(null, order);
    }
}
```

**Q2: "How to handle order cancellations?"**

**A:** State-based cancellation policy:
```java
public boolean canCancel(Order order) {
    switch (order.getStatus()) {
        case PENDING:
        case CONFIRMED:
            return true; // Free cancellation
        case PREPARING:
            return checkWithRestaurant(); // Restaurant approval needed
        case PICKED_UP:
        case DELIVERED:
            return false; // Too late
        default:
            return false;
    }
}
```

**Q3: "How to calculate ETA (Estimated Time of Arrival)?"**

**A:** Consider multiple factors:
```java
public class ETACalculator {
    public int calculateETA(Order order, DeliveryPerson driver) {
        Location restaurant = getRestaurantLocation(order);
        Location customer = order.getDeliveryAddress().getLocation();
        
        // Restaurant preparation time
        int prepTime = estimatePreparationTime(order);
        
        // Driver travel time to restaurant
        double distanceToRestaurant = driver.getLocation().distanceTo(restaurant);
        int travelToRestaurant = (int) (distanceToRestaurant / AVERAGE_SPEED * 60);
        
        // Delivery travel time
        double distanceToCustomer = restaurant.distanceTo(customer);
        int deliveryTime = (int) (distanceToCustomer / AVERAGE_SPEED * 60);
        
        return prepTime + travelToRestaurant + deliveryTime; // minutes
    }
}
```

**Q4: "How to handle high demand (many orders, few drivers)?"**

**A:** Priority queue based on order value, wait time, customer tier:
```java
public class PriorityBasedAssignment implements DeliveryAssignmentStrategy {
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> drivers) {
        // Prioritize high-value orders
        double priority = calculatePriority(order);
        
        // Assign to nearest available driver
        return drivers.stream()
            .min(Comparator.comparingDouble(d -> 
                d.getLocation().distanceTo(getRestaurantLocation(order))))
            .orElse(null);
    }
    
    private double calculatePriority(Order order) {
        double orderValue = order.getTotalAmount();
        long waitTime = System.currentTimeMillis() - order.getOrderTime();
        
        return orderValue * 0.5 + waitTime * 0.5;
    }
}
```

**Q5: "How to ensure data consistency across services?"**

**A:** Use database transactions and event sourcing:
```java
@Transactional
public void processOrderCompletion(String orderId) {
    // 1. Update order status
    orderRepository.updateStatus(orderId, OrderStatus.COMPLETED);
    
    // 2. Process payment
    paymentService.processPayment(orderId);
    
    // 3. Update delivery person status
    deliveryService.markAvailable(getDeliveryPersonId(orderId));
    
    // 4. Publish event
    eventBus.publish(new OrderCompletedEvent(orderId));
}
```

### Design Patterns Used

1. **State Pattern:** Order status management
2. **Strategy Pattern:** Delivery assignment algorithms
3. **Observer Pattern:** Order tracking and notifications
4. **Factory Pattern:** Creating orders, payments
5. **Builder Pattern:** Complex object creation (Order, Restaurant)
6. **Singleton Pattern:** FoodDeliveryService instance

### Expected Level Performance

**Junior Engineer:**
- Basic entities (User, Restaurant, Order)
- Simple order flow (place, accept, deliver)
- Basic state transitions
- Simple driver matching

**Mid-Level Engineer:**
- Complete state machine
- Multiple delivery strategies
- Payment processing
- Rating system
- Thread-safe operations
- Handle cancellations

**Senior Engineer:**
- Production-ready with all features
- Advanced driver matching
- Notification system
- Surge pricing
- Event-driven architecture
- Performance optimization
- Distributed system considerations

### Key Trade-offs

**1. Driver Assignment**
- **Nearest:** Fast ETA, but may overwork nearby drivers
- **Highest rated:** Better service, but longer wait
- **Balanced:** Best of both, more complex

**2. Order State Granularity**
- **Few states:** Simpler, less precise tracking
- **Many states:** Better tracking, more complex
- **Balance:** 8-10 states cover most scenarios

**3. Cancellation Policy**
- **Lenient:** Better UX, more refunds
- **Strict:** Less abuse, revenue protection
- **Staged:** Free early, fee later

**4. Real-Time Updates**
- **Polling:** Simple, higher latency
- **WebSocket:** Real-time, more complex
- **Push notifications:** Balanced

---

## Summary

This Food Delivery Service demonstrates:

1. **Complex State Machine:** Order lifecycle with 10+ states
2. **Multi-Actor System:** Customers, restaurants, delivery personnel
3. **Geospatial Queries:** Find nearby restaurants and drivers
4. **Strategy Pattern:** Pluggable delivery assignment
5. **Real-World Complexity:** Payments, ratings, cancellations
6. **Scalable Design:** Thread-safe, concurrent operations

**Key Takeaways:**
- **State machine is core** - Draw it early in interview
- **Multiple actors** require clear responsibilities
- **Geospatial logic** uses Haversine formula
- **Thread safety** critical for concurrent orders
- **Strategy pattern** for assignment flexibility

**Related Systems:**
- Ride-sharing (Uber, Lyft)
- E-commerce order management
- Hotel booking systems
- Ticket booking platforms

---

*Last Updated: January 5, 2026*
*Difficulty Level: Medium to Hard*
*Key Focus: State Machine, Multi-Actor Coordination, Geospatial*
*Companies: DoorDash, UberEats, Swiggy, Zomato*
*Interview Success Rate: High with proper state machine design*
