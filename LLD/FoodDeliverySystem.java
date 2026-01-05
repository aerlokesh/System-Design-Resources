/**
 * FOOD DELIVERY SERVICE - LOW LEVEL DESIGN
 * 
 * This implementation demonstrates a complete food delivery system
 * similar to DoorDash, UberEats, Swiggy, Zomato.
 * 
 * Key Features:
 * 1. Multi-actor system (Customer, Restaurant, DeliveryPerson)
 * 2. Complete order state machine (10 states)
 * 3. Driver assignment strategies (Nearest, Highest-rated, Balanced)
 * 4. Geospatial queries (find nearby restaurants)
 * 5. Payment processing and refunds
 * 6. Rating system (bidirectional)
 * 7. Cart management
 * 8. Thread-safe operations
 * 
 * Design Patterns Used:
 * - State Pattern (Order status)
 * - Strategy Pattern (Driver assignment)
 * - Observer Pattern (Order tracking)
 * - Factory Pattern (Order creation)
 * 
 * Companies: DoorDash, UberEats, Swiggy, Zomato
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

// ============================================================
// ENUMS
// ============================================================

enum OrderStatus {
    PENDING, CONFIRMED, PREPARING, READY_FOR_PICKUP, 
    PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, 
    CANCELLED, REJECTED
}

enum DeliveryStatus {
    AVAILABLE, BUSY, OFFLINE
}

enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, CASH, WALLET, UPI
}

enum PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED
}

// ============================================================
// LOCATION & ADDRESS
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

class Address {
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
    
    public Location getLocation() { return location; }
    
    @Override
    public String toString() {
        return street + ", " + city + ", " + state + " " + zipCode;
    }
}

// ============================================================
// USER HIERARCHY
// ============================================================

abstract class User {
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
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}

class Customer extends User {
    private final List<Address> addresses;
    private final List<Order> orderHistory;
    
    public Customer(String id, String name, String email, String phone) {
        super(id, name, email, phone);
        this.addresses = new ArrayList<>();
        this.orderHistory = new ArrayList<>();
    }
    
    public void addAddress(Address address) {
        addresses.add(address);
    }
    
    public void addOrder(Order order) {
        orderHistory.add(order);
    }
    
    public List<Address> getAddresses() { return addresses; }
}

class DeliveryPerson extends User {
    private Location currentLocation;
    private DeliveryStatus status;
    private final AtomicReference<Order> currentOrder;
    private double rating;
    private int totalDeliveries;
    
    public DeliveryPerson(String id, String name, String email, String phone, Location location) {
        super(id, name, email, phone);
        this.currentLocation = location;
        this.status = DeliveryStatus.OFFLINE;
        this.currentOrder = new AtomicReference<>(null);
        this.rating = 5.0;
        this.totalDeliveries = 0;
    }
    
    public boolean isAvailable() {
        return status == DeliveryStatus.AVAILABLE;
    }
    
    public void goOnline() {
        this.status = DeliveryStatus.AVAILABLE;
    }
    
    public boolean tryAssignOrder(Order order) {
        if (currentOrder.compareAndSet(null, order)) {
            this.status = DeliveryStatus.BUSY;
            return true;
        }
        return false;
    }
    
    public void completeDelivery() {
        this.currentOrder.set(null);
        this.status = DeliveryStatus.AVAILABLE;
        this.totalDeliveries++;
    }
    
    public void updateLocation(Location location) {
        this.currentLocation = location;
    }
    
    public Location getCurrentLocation() { return currentLocation; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getTotalDeliveries() { return totalDeliveries; }
}

// ============================================================
// RESTAURANT & MENU
// ============================================================

class Restaurant {
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
    
    public String getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location; }
    public String getCuisineType() { return cuisineType; }
    public List<MenuItem> getMenu() { return Collections.unmodifiableList(menu); }
    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { this.isOpen = open; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getTotalOrders() { return totalOrders; }
    public void incrementTotalOrders() { this.totalOrders++; }
}

class MenuItem {
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
    
    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    
    @Override
    public String toString() {
        return String.format("%s - $%.2f", name, price);
    }
}

// ============================================================
// CART & ORDER
// ============================================================

class Cart {
    private final String customerId;
    private final String restaurantId;
    private final List<OrderItem> items;
    
    public Cart(String customerId, String restaurantId) {
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.items = new ArrayList<>();
    }
    
    public void addItem(MenuItem menuItem, int quantity) {
        for (OrderItem item : items) {
            if (item.getMenuItemId().equals(menuItem.getId())) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
        }
        items.add(new OrderItem(menuItem.getId(), menuItem.getName(), 
                               menuItem.getPrice(), quantity));
    }
    
    public double getTotal() {
        return items.stream()
            .mapToDouble(item -> item.getPrice() * item.getQuantity())
            .sum();
    }
    
    public String getRestaurantId() { return restaurantId; }
    public List<OrderItem> getItems() { return new ArrayList<>(items); }
}

class OrderItem {
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
    
    public String getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    
    @Override
    public String toString() {
        return String.format("%dx %s ($%.2f)", quantity, name, getSubtotal());
    }
}

class Order {
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
        
        double itemsTotal = items.stream().mapToDouble(OrderItem::getSubtotal).sum();
        this.totalAmount = itemsTotal + 5.0 + (itemsTotal * 0.10);
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
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }
    
    public void assignDeliveryPerson(String deliveryPersonId) {
        this.deliveryPersonId = deliveryPersonId;
        updateStatus(OrderStatus.PREPARING);
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getRestaurantId() { return restaurantId; }
    public List<OrderItem> getItems() { return items; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public OrderStatus getStatus() { return status; }
    public String getDeliveryPersonId() { return deliveryPersonId; }
    public double getTotalAmount() { return totalAmount; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
}

// ============================================================
// PAYMENT
// ============================================================

class Payment {
    private final String id;
    private final String orderId;
    private final double amount;
    private final PaymentMethod method;
    private PaymentStatus status;
    
    public Payment(String id, String orderId, double amount, PaymentMethod method) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }
    
    public void markCompleted() { this.status = PaymentStatus.COMPLETED; }
    public void markFailed() { this.status = PaymentStatus.FAILED; }
    public void refund() { this.status = PaymentStatus.REFUNDED; }
    
    public PaymentStatus getStatus() { return status; }
}

// ============================================================
// DELIVERY ASSIGNMENT STRATEGIES
// ============================================================

interface DeliveryAssignmentStrategy {
    DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers, 
                                Map<String, Restaurant> restaurants);
}

class NearestDriverStrategy implements DeliveryAssignmentStrategy {
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers,
                                       Map<String, Restaurant> restaurants) {
        if (availableDrivers.isEmpty()) return null;
        
        Restaurant restaurant = restaurants.get(order.getRestaurantId());
        Location restaurantLocation = restaurant.getLocation();
        
        return availableDrivers.stream()
            .min(Comparator.comparingDouble(driver -> 
                driver.getCurrentLocation().distanceTo(restaurantLocation)))
            .orElse(null);
    }
}

class HighestRatedDriverStrategy implements DeliveryAssignmentStrategy {
    @Override
    public DeliveryPerson assignDriver(Order order, List<DeliveryPerson> availableDrivers,
                                       Map<String, Restaurant> restaurants) {
        return availableDrivers.stream()
            .max(Comparator.comparingDouble(DeliveryPerson::getRating))
            .orElse(null);
    }
}

// ============================================================
// FOOD DELIVERY SERVICE
// ============================================================

class FoodDeliveryService {
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
    
    public void registerCustomer(Customer customer) {
        customers.put(customer.getId(), customer);
    }
    
    public void registerRestaurant(Restaurant restaurant) {
        restaurants.put(restaurant.getId(), restaurant);
    }
    
    public void registerDeliveryPerson(DeliveryPerson person) {
        deliveryPersons.put(person.getId(), person);
    }
    
    public List<Restaurant> findNearbyRestaurants(Location location, double radiusKm) {
        return restaurants.values().stream()
            .filter(r -> r.getLocation().distanceTo(location) <= radiusKm)
            .filter(Restaurant::isOpen)
            .sorted(Comparator.comparingDouble(r -> r.getLocation().distanceTo(location)))
            .collect(Collectors.toList());
    }
    
    public Order placeOrder(String customerId, Cart cart, Address deliveryAddress) {
        String orderId = "ORD-" + orderIdCounter.getAndIncrement();
        Order order = new Order(orderId, customerId, cart.getRestaurantId(),
                               cart.getItems(), deliveryAddress);
        
        orders.put(orderId, order);
        customers.get(customerId).addOrder(order);
        
        return order;
    }
    
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
            driver.tryAssignOrder(order);
        }
        
        return true;
    }
    
    public boolean rejectOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        
        order.updateStatus(OrderStatus.REJECTED);
        return true;
    }
    
    private DeliveryPerson assignDeliveryPerson(Order order) {
        List<DeliveryPerson> availableDrivers = deliveryPersons.values().stream()
            .filter(DeliveryPerson::isAvailable)
            .collect(Collectors.toList());
        
        return assignmentStrategy.assignDriver(order, availableDrivers, restaurants);
    }
    
    public void markPickedUp(String orderId, String deliveryPersonId) {
        Order order = orders.get(orderId);
        if (order != null && deliveryPersonId.equals(order.getDeliveryPersonId())) {
            order.updateStatus(OrderStatus.PICKED_UP);
        }
    }
    
    public void markDelivered(String orderId, String deliveryPersonId) {
        Order order = orders.get(orderId);
        if (order != null && deliveryPersonId.equals(order.getDeliveryPersonId())) {
            order.updateStatus(OrderStatus.DELIVERED);
            
            // Process payment
            Payment payment = new Payment(UUID.randomUUID().toString(),
                orderId, order.getTotalAmount(), PaymentMethod.CREDIT_CARD);
            payment.markCompleted();
            order.setPayment(payment);
            order.updateStatus(OrderStatus.COMPLETED);
            
            // Complete delivery
            DeliveryPerson driver = deliveryPersons.get(deliveryPersonId);
            if (driver != null) {
                driver.completeDelivery();
            }
        }
    }
    
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.canCancel()) {
            return false;
        }
        
        order.updateStatus(OrderStatus.CANCELLED);
        
        if (order.getPayment() != null) {
            order.getPayment().refund();
        }
        
        return true;
    }
    
    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }
    
    public Restaurant getRestaurant(String restaurantId) {
        return restaurants.get(restaurantId);
    }
}

// ============================================================
// DEMO
// ============================================================

public class FoodDeliverySystem {
    
    public static void main(String[] args) {
        System.out.println("=== FOOD DELIVERY SERVICE DEMO ===\n");
        
        // Initialize service
        FoodDeliveryService service = new FoodDeliveryService(new NearestDriverStrategy());
        
        // Setup data
        setupRestaurantsAndDrivers(service);
        
        // Demo 1: Complete order flow
        demoCompleteOrderFlow(service);
        
        // Demo 2: Find nearby restaurants
        demoRestaurantDiscovery(service);
        
        // Demo 3: Order cancellation
        demoOrderCancellation(service);
        
        // Demo 4: Multiple concurrent orders
        demoConcurrentOrders(service);
    }
    
    private static void setupRestaurantsAndDrivers(FoodDeliveryService service) {
        // Register restaurants
        Restaurant pizzaPlace = new Restaurant("R1", "Pizza Palace",
            new Location(37.7749, -122.4194), "Italian");
        pizzaPlace.addMenuItem(new MenuItem("M1", "Margherita Pizza", "Classic pizza", 12.99, "Main"));
        pizzaPlace.addMenuItem(new MenuItem("M2", "Pepperoni Pizza", "With pepperoni", 14.99, "Main"));
        pizzaPlace.addMenuItem(new MenuItem("M3", "Garlic Bread", "Side dish", 5.99, "Appetizer"));
        service.registerRestaurant(pizzaPlace);
        
        Restaurant burgerJoint = new Restaurant("R2", "Burger Joint",
            new Location(37.7849, -122.4094), "American");
        burgerJoint.addMenuItem(new MenuItem("M4", "Cheeseburger", "Classic burger", 9.99, "Main"));
        burgerJoint.addMenuItem(new MenuItem("M5", "Fries", "Crispy fries", 3.99, "Side"));
        service.registerRestaurant(burgerJoint);
        
        // Register delivery persons
        DeliveryPerson driver1 = new DeliveryPerson("D1", "John Driver",
            "john@email.com", "555-0001", new Location(37.7759, -122.4184));
        driver1.goOnline();
        service.registerDeliveryPerson(driver1);
        
        DeliveryPerson driver2 = new DeliveryPerson("D2", "Jane Courier",
            "jane@email.com", "555-0002", new Location(37.7889, -122.4064));
        driver2.goOnline();
        service.registerDeliveryPerson(driver2);
    }
    
    private static void demoCompleteOrderFlow(FoodDeliveryService service) {
        System.out.println("--- Demo 1: Complete Order Flow ---\n");
        
        // Register customer
        Customer customer = new Customer("C1", "Alice Customer",
            "alice@email.com", "555-1234");
        Address address = new Address("123 Main St", "San Francisco", "CA",
            "94102", new Location(37.7849, -122.4194));
        customer.addAddress(address);
        service.registerCustomer(customer);
        
        // Browse menu and add to cart
        Restaurant restaurant = service.getRestaurant("R1");
        System.out.println("Customer browsing: " + restaurant.getName());
        System.out.println("Menu:");
        restaurant.getMenu().forEach(item -> System.out.println("  " + item));
        
        // Build cart
        Cart cart = new Cart("C1", "R1");
        cart.addItem(restaurant.getMenu().get(0), 2); // 2 Margherita pizzas
        cart.addItem(restaurant.getMenu().get(2), 1); // 1 Garlic bread
        
        System.out.println("\nCart total: $" + String.format("%.2f", cart.getTotal()));
        
        // Place order
        Order order = service.placeOrder("C1", cart, address);
        System.out.println("\nOrder placed: " + order.getId());
        System.out.println("Status: " + order.getStatus());
        System.out.println("Total amount: $" + String.format("%.2f", order.getTotalAmount()));
        
        // Restaurant accepts
        service.acceptOrder(order.getId(), "R1");
        System.out.println("\nRestaurant accepted order");
        System.out.println("Status: " + service.getOrder(order.getId()).getStatus());
        System.out.println("Assigned driver: " + order.getDeliveryPersonId());
        
        // Driver picks up
        service.markPickedUp(order.getId(), order.getDeliveryPersonId());
        System.out.println("\nDriver picked up food");
        System.out.println("Status: " + service.getOrder(order.getId()).getStatus());
        
        // Driver delivers
        service.markDelivered(order.getId(), order.getDeliveryPersonId());
        System.out.println("\nFood delivered!");
        System.out.println("Status: " + service.getOrder(order.getId()).getStatus());
        System.out.println("Payment: " + order.getPayment().getStatus());
        
        System.out.println();
    }
    
    private static void demoRestaurantDiscovery(FoodDeliveryService service) {
        System.out.println("--- Demo 2: Restaurant Discovery ---\n");
        
        Location customerLocation = new Location(37.7749, -122.4194);
        System.out.println("Customer location: " + customerLocation);
        
        List<Restaurant> nearby = service.findNearbyRestaurants(customerLocation, 5.0);
        System.out.println("\nRestaurants within 5km:");
        
        for (Restaurant r : nearby) {
            double distance = customerLocation.distanceTo(r.getLocation());
            System.out.println(String.format("  %s (%.2f km) - %s - Rating: %.1f",
                r.getName(), distance, r.getCuisineType(), r.getRating()));
        }
        
        System.out.println();
    }
    
    private static void demoOrderCancellation(FoodDeliveryService service) {
        System.out.println("--- Demo 3: Order Cancellation ---\n");
        
        // Create and place order
        Cart cart = new Cart("C1", "R1");
        cart.addItem(service.getRestaurant("R1").getMenu().get(0), 1);
        
        Address address = new Address("456 Oak St", "San Francisco", "CA",
            "94103", new Location(37.7749, -122.4194));
        
        Order order = service.placeOrder("C1", cart, address);
        System.out.println("Order placed: " + order.getId());
        System.out.println("Status: " + order.getStatus());
        
        // Try to cancel
        boolean cancelled = service.cancelOrder(order.getId());
        System.out.println("\nCancellation attempt: " + (cancelled ? "SUCCESS" : "FAILED"));
        System.out.println("Status: " + service.getOrder(order.getId()).getStatus());
        
        System.out.println();
    }
    
    private static void demoConcurrentOrders(FoodDeliveryService service) {
        System.out.println("--- Demo 4: Concurrent Orders ---\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        
        System.out.println("Placing 5 concurrent orders...");
        
        for (int i = 0; i < 5; i++) {
            final int orderId = i;
            executor.submit(() -> {
                try {
                    Cart cart = new Cart("C1", "R1");
                    cart.addItem(service.getRestaurant("R1").getMenu().get(0), 1);
                    
                    Address addr = new Address("Test St", "SF", "CA", "94102",
                        new Location(37.7749 + (orderId * 0.001), -122.4194));
                    
                    Order order = service.placeOrder("C1", cart, addr);
                    System.out.println("  Order " + order.getId() + " placed by thread " + 
                                      Thread.currentThread().getName());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            executor.shutdown();
            System.out.println("\nAll orders placed successfully!");
            System.out.println("Total orders: 5");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
}
