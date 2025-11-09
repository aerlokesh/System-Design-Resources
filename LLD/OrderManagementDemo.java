import java.util.*;
import java.time.*;

// ==================== Enums ====================
// Why Enums? Type safety, self-documenting, prevents invalid states
// Better than strings which can have typos and no compile-time validation

enum OrderStatus {
    PENDING,        // Order created, awaiting payment
    CONFIRMED,      // Payment successful
    PROCESSING,     // Being prepared for shipment
    SHIPPED,        // Out for delivery
    DELIVERED,      // Successfully delivered
    CANCELLED,      // Cancelled before shipment
    RETURNED,       // Returned after delivery
    REFUNDED        // Money returned to customer
}

enum PaymentStatus {
    PENDING,
    AUTHORIZED,     // Payment approved but not captured
    COMPLETED,
    FAILED,
    REFUNDED
}

enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    UPI,
    NET_BANKING,
    CASH_ON_DELIVERY,
    WALLET
}

enum ShipmentStatus {
    PREPARING,      // Packing items
    SHIPPED,        // In transit
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED_DELIVERY,
    RETURNED
}

enum ProductCategory {
    ELECTRONICS,
    CLOTHING,
    BOOKS,
    HOME_AND_KITCHEN,
    TOYS,
    SPORTS
}

// ==================== Address ====================
// Encapsulates shipping/billing address details
// Immutable design - once created, address cannot be modified

class Address {
    private final String street;
    private final String city;
    private final String state;
    private final String zipCode;
    private final String country;

    public Address(String street, String city, String state, String zipCode, String country) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.country = country;
    }

    // Only getters - no setters (immutability)
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZipCode() { return zipCode; }
    public String getCountry() { return country; }

    @Override
    public String toString() {
        return street + ", " + city + ", " + state + " " + zipCode + ", " + country;
    }
}

// ==================== Customer ====================
// Represents a customer with account details and order history

class Customer {
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private List<Address> addresses;        // Multiple delivery addresses
    private List<Order> orderHistory;       // Past orders
    private double walletBalance;           // For wallet payments

    public Customer(String customerId, String name, String email, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.addresses = new ArrayList<>();
        this.orderHistory = new ArrayList<>();
        this.walletBalance = 0.0;
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public List<Address> getAddresses() { return addresses; }
    public List<Order> getOrderHistory() { return orderHistory; }
    public double getWalletBalance() { return walletBalance; }

    public void addAddress(Address address) {
        addresses.add(address);
    }

    public void addOrder(Order order) {
        orderHistory.add(order);
    }

    public void addToWallet(double amount) {
        walletBalance += amount;
    }

    public boolean deductFromWallet(double amount) {
        if (walletBalance >= amount) {
            walletBalance -= amount;
            return true;
        }
        return false;
    }
}

// ==================== Product ====================
// Represents a product in the catalog

class Product {
    private String productId;
    private String name;
    private String description;
    private double price;
    private ProductCategory category;
    private int availableQuantity;          // Inventory count
    private String sellerId;                // For marketplace model

    public Product(String productId, String name, String description, double price,
                   ProductCategory category, int availableQuantity, String sellerId) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.availableQuantity = availableQuantity;
        this.sellerId = sellerId;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public ProductCategory getCategory() { return category; }
    public int getAvailableQuantity() { return availableQuantity; }
    public String getSellerId() { return sellerId; }

    // Thread-safe inventory operations
    public synchronized boolean reserveQuantity(int quantity) {
        if (availableQuantity >= quantity) {
            availableQuantity -= quantity;
            return true;
        }
        return false;
    }

    public synchronized void releaseQuantity(int quantity) {
        availableQuantity += quantity;
    }

    @Override
    public String toString() {
        return name + " ($" + price + ") - " + availableQuantity + " available";
    }
}

// ==================== Shopping Cart ====================
// Temporary storage for items before order placement

class ShoppingCart {
    private String cartId;
    private String customerId;
    private Map<String, Integer> items;     // productId -> quantity
    private LocalDateTime lastUpdated;

    public ShoppingCart(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
        this.items = new HashMap<>();
        this.lastUpdated = LocalDateTime.now();
    }

    public String getCartId() { return cartId; }
    public String getCustomerId() { return customerId; }
    public Map<String, Integer> getItems() { return items; }

    public void addItem(String productId, int quantity) {
        items.put(productId, items.getOrDefault(productId, 0) + quantity);
        lastUpdated = LocalDateTime.now();
    }

    public void removeItem(String productId) {
        items.remove(productId);
        lastUpdated = LocalDateTime.now();
    }

    public void updateQuantity(String productId, int quantity) {
        if (quantity <= 0) {
            items.remove(productId);
        } else {
            items.put(productId, quantity);
        }
        lastUpdated = LocalDateTime.now();
    }

    public void clear() {
        items.clear();
        lastUpdated = LocalDateTime.now();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}

// ==================== Order Item ====================
// Represents a single item within an order

class OrderItem {
    private String orderItemId;
    private Product product;
    private int quantity;
    private double priceAtOrder;            // Price when ordered (can change later)
    private double discount;

    public OrderItem(String orderItemId, Product product, int quantity, double discount) {
        this.orderItemId = orderItemId;
        this.product = product;
        this.quantity = quantity;
        this.priceAtOrder = product.getPrice();  // Capture current price
        this.discount = discount;
    }

    public String getOrderItemId() { return orderItemId; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public double getPriceAtOrder() { return priceAtOrder; }
    public double getDiscount() { return discount; }

    public double getTotalPrice() {
        return (priceAtOrder * quantity) - discount;
    }

    @Override
    public String toString() {
        return product.getName() + " x" + quantity + " @ $" + priceAtOrder + " = $" + getTotalPrice();
    }
}

// ==================== Payment ====================
// Represents payment for an order

class Payment {
    private String paymentId;
    private String orderId;
    private double amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private LocalDateTime paymentTime;
    private String transactionId;           // External payment gateway ID

    public Payment(String paymentId, String orderId, double amount, PaymentMethod method) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
        this.paymentTime = null;
        this.transactionId = null;
    }

    public String getPaymentId() { return paymentId; }
    public String getOrderId() { return orderId; }
    public double getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getPaymentTime() { return paymentTime; }
    public String getTransactionId() { return transactionId; }

    // Simulate payment processing
    public boolean processPayment() {
        // In real system: integrate with payment gateway
        this.status = PaymentStatus.COMPLETED;
        this.paymentTime = LocalDateTime.now();
        this.transactionId = "TXN-" + System.currentTimeMillis();
        return true;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
}

// ==================== Shipment ====================
// Represents shipping details for an order

class Shipment {
    private String shipmentId;
    private String orderId;
    private Address shippingAddress;
    private ShipmentStatus status;
    private String trackingNumber;
    private LocalDateTime shippedDate;
    private LocalDateTime estimatedDelivery;
    private LocalDateTime actualDelivery;
    private String carrier;                 // FedEx, UPS, etc.

    public Shipment(String shipmentId, String orderId, Address shippingAddress, String carrier) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.shippingAddress = shippingAddress;
        this.status = ShipmentStatus.PREPARING;
        this.trackingNumber = "TRACK-" + System.currentTimeMillis();
        this.carrier = carrier;
    }

    public String getShipmentId() { return shipmentId; }
    public String getOrderId() { return orderId; }
    public Address getShippingAddress() { return shippingAddress; }
    public ShipmentStatus getStatus() { return status; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDateTime getShippedDate() { return shippedDate; }
    public LocalDateTime getEstimatedDelivery() { return estimatedDelivery; }
    public LocalDateTime getActualDelivery() { return actualDelivery; }
    public String getCarrier() { return carrier; }

    public void ship() {
        this.status = ShipmentStatus.SHIPPED;
        this.shippedDate = LocalDateTime.now();
        this.estimatedDelivery = LocalDateTime.now().plusDays(3);  // 3 days estimate
    }

    public void markOutForDelivery() {
        this.status = ShipmentStatus.OUT_FOR_DELIVERY;
    }

    public void markDelivered() {
        this.status = ShipmentStatus.DELIVERED;
        this.actualDelivery = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = ShipmentStatus.FAILED_DELIVERY;
    }

    @Override
    public String toString() {
        return "Shipment " + trackingNumber + " - Status: " + status;
    }
}

// ==================== Order State Pattern ====================
// Why State Pattern? Order behavior changes based on current state
// Benefits: 1) Encapsulates state-specific behavior 2) Easy state transitions
// 3) Eliminates complex conditionals 4) Each state is a separate class

interface OrderState {
    void confirmOrder(Order order);
    void processOrder(Order order);
    void shipOrder(Order order);
    void deliverOrder(Order order);
    void cancelOrder(Order order);
    void returnOrder(Order order);
}

// Pending State - awaiting payment confirmation
class PendingOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Order confirmed. Processing payment...");
        order.setState(new ConfirmedOrderState());
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Cannot process. Order must be confirmed first.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Cannot ship. Order must be confirmed first.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Cannot deliver. Order must be shipped first.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Order cancelled.");
        order.setState(new CancelledOrderState());
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Cannot return. Order not delivered yet.");
    }
}

// Confirmed State - payment successful, ready for processing
class ConfirmedOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Order already confirmed.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Order is being processed...");
        order.setState(new ProcessingOrderState());
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Cannot ship. Order must be processed first.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Cannot deliver. Order must be shipped first.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Order cancelled. Refund initiated.");
        order.setState(new CancelledOrderState());
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Cannot return. Order not delivered yet.");
    }
}

// Processing State - being prepared for shipment
class ProcessingOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Order already confirmed.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Order already in processing.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Order shipped!");
        order.setState(new ShippedOrderState());
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Cannot deliver. Order must be shipped first.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Order cancelled. Refund initiated.");
        order.setState(new CancelledOrderState());
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Cannot return. Order not delivered yet.");
    }
}

// Shipped State - in transit
class ShippedOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Order already confirmed.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Order already processed.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Order already shipped.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Order delivered successfully!");
        order.setState(new DeliveredOrderState());
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Cannot cancel. Order already shipped.");
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Cannot return. Order not delivered yet.");
    }
}

// Delivered State - successfully delivered to customer
class DeliveredOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Order already confirmed.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Order already processed.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Order already shipped.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Order already delivered.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Cannot cancel. Order already delivered.");
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Return initiated. Refund will be processed.");
        order.setState(new ReturnedOrderState());
    }
}

// Cancelled State - order cancelled
class CancelledOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Cannot confirm cancelled order.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Cannot process cancelled order.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Cannot ship cancelled order.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Cannot deliver cancelled order.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Order already cancelled.");
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Cannot return cancelled order.");
    }
}

// Returned State - order returned by customer
class ReturnedOrderState implements OrderState {
    @Override
    public void confirmOrder(Order order) {
        System.out.println("Cannot confirm returned order.");
    }

    @Override
    public void processOrder(Order order) {
        System.out.println("Cannot process returned order.");
    }

    @Override
    public void shipOrder(Order order) {
        System.out.println("Cannot ship returned order.");
    }

    @Override
    public void deliverOrder(Order order) {
        System.out.println("Cannot deliver returned order.");
    }

    @Override
    public void cancelOrder(Order order) {
        System.out.println("Cannot cancel returned order.");
    }

    @Override
    public void returnOrder(Order order) {
        System.out.println("Order already returned.");
    }
}

// ==================== Order ====================
// Core entity representing a customer order

class Order {
    private String orderId;
    private Customer customer;
    private List<OrderItem> items;
    private Address shippingAddress;
    private OrderStatus status;
    private OrderState state;               // State pattern
    private Payment payment;
    private Shipment shipment;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private double totalAmount;
    private double shippingCharges;
    private double tax;

    public Order(String orderId, Customer customer, Address shippingAddress) {
        this.orderId = orderId;
        this.customer = customer;
        this.shippingAddress = shippingAddress;
        this.items = new ArrayList<>();
        this.status = OrderStatus.PENDING;
        this.state = new PendingOrderState();  // Initial state
        this.orderDate = LocalDateTime.now();
        this.totalAmount = 0.0;
        this.shippingCharges = 0.0;
        this.tax = 0.0;
    }

    // Getters
    public String getOrderId() { return orderId; }
    public Customer getCustomer() { return customer; }
    public List<OrderItem> getItems() { return items; }
    public Address getShippingAddress() { return shippingAddress; }
    public OrderStatus getStatus() { return status; }
    public Payment getPayment() { return payment; }
    public Shipment getShipment() { return shipment; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public double getTotalAmount() { return totalAmount; }

    // State management
    public void setState(OrderState state) {
        this.state = state;
        // Update status enum based on state class
        updateStatusFromState();
    }

    private void updateStatusFromState() {
        if (state instanceof PendingOrderState) status = OrderStatus.PENDING;
        else if (state instanceof ConfirmedOrderState) status = OrderStatus.CONFIRMED;
        else if (state instanceof ProcessingOrderState) status = OrderStatus.PROCESSING;
        else if (state instanceof ShippedOrderState) status = OrderStatus.SHIPPED;
        else if (state instanceof DeliveredOrderState) status = OrderStatus.DELIVERED;
        else if (state instanceof CancelledOrderState) status = OrderStatus.CANCELLED;
        else if (state instanceof ReturnedOrderState) status = OrderStatus.RETURNED;
    }

    // Add item to order
    public void addItem(OrderItem item) {
        items.add(item);
        calculateTotal();
    }

    // Calculate order total
    private void calculateTotal() {
        double itemsTotal = items.stream()
            .mapToDouble(OrderItem::getTotalPrice)
            .sum();
        
        // Simple tax calculation (10%)
        this.tax = itemsTotal * 0.10;
        
        // Shipping charges based on order value
        this.shippingCharges = itemsTotal > 500 ? 0.0 : 50.0;  // Free shipping > $500
        
        this.totalAmount = itemsTotal + tax + shippingCharges;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    // Delegate to current state
    public void confirm() { state.confirmOrder(this); }
    public void process() { state.processOrder(this); }
    public void ship() { state.shipOrder(this); }
    public void deliver() { state.deliverOrder(this); }
    public void cancel() { state.cancelOrder(this); }
    public void returnOrder() { state.returnOrder(this); }

    @Override
    public String toString() {
        return String.format("Order[%s, Customer=%s, Items=%d, Total=$%.2f, Status=%s]",
            orderId, customer.getName(), items.size(), totalAmount, status);
    }
}

// ==================== Notification Service ====================
// Why Observer Pattern? Notify multiple parties about order events
// Benefits: Loose coupling, easy to add new notification channels

interface OrderObserver {
    void update(Order order, String event);
}

class EmailNotification implements OrderObserver {
    @Override
    public void update(Order order, String event) {
        System.out.println("📧 Email sent to " + order.getCustomer().getEmail() +
            ": Your order " + order.getOrderId() + " - " + event);
    }
}

class SMSNotification implements OrderObserver {
    @Override
    public void update(Order order, String event) {
        System.out.println("📱 SMS sent to " + order.getCustomer().getPhone() +
            ": Order " + order.getOrderId() + " - " + event);
    }
}

class PushNotification implements OrderObserver {
    @Override
    public void update(Order order, String event) {
        System.out.println("🔔 Push notification: Order " + order.getOrderId() + " - " + event);
    }
}

// ==================== Order Management System (Singleton) ====================
// Why Singleton? Central coordination point for all order operations
// Manages catalog, inventory, orders, payments, shipments

class OrderManagementSystem {
    private static OrderManagementSystem instance;
    private Map<String, Product> productCatalog;
    private Map<String, Customer> customers;
    private Map<String, Order> orders;
    private Map<String, ShoppingCart> carts;
    private List<OrderObserver> observers;          // Observer pattern
    private int orderCounter;

    // Private constructor - Singleton
    private OrderManagementSystem() {
        this.productCatalog = new HashMap<>();
        this.customers = new HashMap<>();
        this.orders = new HashMap<>();
        this.carts = new HashMap<>();
        this.observers = new ArrayList<>();
        this.orderCounter = 0;
    }

    // Thread-safe singleton
    public static synchronized OrderManagementSystem getInstance() {
        if (instance == null) {
            instance = new OrderManagementSystem();
        }
        return instance;
    }

    // Add observer for notifications
    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }

    // Notify all observers
    private void notifyObservers(Order order, String event) {
        for (OrderObserver observer : observers) {
            observer.update(order, event);
        }
    }

    // Register product
    public void addProduct(Product product) {
        productCatalog.put(product.getProductId(), product);
    }

    // Register customer
    public void addCustomer(Customer customer) {
        customers.put(customer.getCustomerId(), customer);
    }

    // Get or create shopping cart
    public ShoppingCart getCart(String customerId) {
        return carts.computeIfAbsent(customerId,
            k -> new ShoppingCart("CART-" + customerId, customerId));
    }

    // Add item to cart
    public void addToCart(String customerId, String productId, int quantity) {
        Product product = productCatalog.get(productId);
        if (product == null) {
            System.out.println("Product not found!");
            return;
        }

        if (product.getAvailableQuantity() < quantity) {
            System.out.println("Insufficient stock!");
            return;
        }

        ShoppingCart cart = getCart(customerId);
        cart.addItem(productId, quantity);
        System.out.println("Added " + quantity + " x " + product.getName() + " to cart");
    }

    // Place order from cart
    public synchronized Order placeOrder(String customerId, Address shippingAddress,
                                        PaymentMethod paymentMethod) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            System.out.println("Customer not found!");
            return null;
        }

        ShoppingCart cart = carts.get(customerId);
        if (cart == null || cart.isEmpty()) {
            System.out.println("Cart is empty!");
            return null;
        }

        // Create order
        String orderId = "ORD-" + (++orderCounter);
        Order order = new Order(orderId, customer, shippingAddress);

        // Add items and reserve inventory
        for (Map.Entry<String, Integer> entry : cart.getItems().entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = productCatalog.get(productId);

            if (!product.reserveQuantity(quantity)) {
                System.out.println("Failed to reserve " + product.getName());
                // Rollback previous reservations
                rollbackReservations(order);
                return null;
            }

            OrderItem orderItem = new OrderItem(
                "ITEM-" + orderId + "-" + productId,
                product,
                quantity,
                0.0  // No discount for demo
            );
            order.addItem(orderItem);
        }

        // Process payment
        Payment payment = new Payment("PAY-" + orderId, orderId,
            order.getTotalAmount(), paymentMethod);
        
        if (paymentMethod == PaymentMethod.WALLET) {
            if (!customer.deductFromWallet(order.getTotalAmount())) {
                System.out.println("Insufficient wallet balance!");
                rollbackReservations(order);
                return null;
            }
        }

        if (!payment.processPayment()) {
            System.out.println("Payment failed!");
            rollbackReservations(order);
            return null;
        }

        order.setPayment(payment);
        
        // Confirm order
        order.confirm();
        
        // Create shipment
        Shipment shipment = new Shipment("SHIP-" + orderId, orderId,
            shippingAddress, "FedEx");
        order.setShipment(shipment);

        // Save order
        orders.put(orderId, order);
        customer.addOrder(order);

        // Clear cart
        cart.clear();

        // Notify observers
        notifyObservers(order, "Order placed successfully");

        System.out.println("\n✅ " + order);
        return order;
    }

    // Rollback inventory reservations
    private void rollbackReservations(Order order) {
        for (OrderItem item : order.getItems()) {
            item.getProduct().releaseQuantity(item.getQuantity());
        }
    }

    // Process order (move to next state)
    public void processOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.process();
            notifyObservers(order, "Order is being processed");
        }
    }

    // Ship order
    public void shipOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.ship();
            order.getShipment().ship();
            notifyObservers(order, "Order has been shipped");
        }
    }

    // Deliver order
    public void deliverOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.deliver();
            order.getShipment().markDelivered();
            notifyObservers(order, "Order delivered successfully");
        }
    }

    // Cancel order
    public void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.cancel();
            // Release inventory
            for (OrderItem item : order.getItems()) {
                item.getProduct().releaseQuantity(item.getQuantity());
            }
            // Refund payment
            if (order.getPayment() != null) {
                order.getPayment().refund();
                order.getCustomer().addToWallet(order.getTotalAmount());
            }
            notifyObservers(order, "Order cancelled and refunded");
        }
    }

    // Return order
    public void returnOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.returnOrder();
            // Release inventory
            for (OrderItem item : order.getItems()) {
                item.getProduct().releaseQuantity(item.getQuantity());
            }
            // Refund payment
            if (order.getPayment() != null) {
                order.getPayment().refund();
                order.getCustomer().addToWallet(order.getTotalAmount());
            }
            notifyObservers(order, "Order returned and refunded");
        }
    }

    // Track shipment
    public void trackShipment(String orderId) {
        Order order = orders.get(orderId);
        if (order != null && order.getShipment() != null) {
            System.out.println("\n📦 " + order.getShipment());
            System.out.println("Shipping to: " + order.getShippingAddress());
            if (order.getShipment().getEstimatedDelivery() != null) {
                System.out.println("Est. Delivery: " + order.getShipment().getEstimatedDelivery());
            }
        }
    }

    // View order details
    public void viewOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            System.out.println("\n" + order);
            System.out.println("Items:");
            for (OrderItem item : order.getItems()) {
                System.out.println("  - " + item);
            }
            System.out.println("Shipping: $" + order.getShippingAddress());
            System.out.println("Status: " + order.getStatus());
        }
    }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete order management workflow

public class OrderManagementDemo {
    public static void main(String[] args) {
        // Initialize Order Management System (Singleton)
        OrderManagementSystem oms = OrderManagementSystem.getInstance();

        // Add notification observers
        oms.addObserver(new EmailNotification());
        oms.addObserver(new SMSNotification());
        oms.addObserver(new PushNotification());

        System.out.println("========== Order Management System Demo ==========\n");

        // ===== Setup Products =====
        Product laptop = new Product(
            "PROD-001", "MacBook Pro", "14-inch laptop", 1999.99,
            ProductCategory.ELECTRONICS, 10, "SELLER-001"
        );
        Product book = new Product(
            "PROD-002", "Design Patterns", "Gang of Four book", 49.99,
            ProductCategory.BOOKS, 50, "SELLER-001"
        );
        Product phone = new Product(
            "PROD-003", "iPhone 15", "Latest iPhone", 999.99,
            ProductCategory.ELECTRONICS, 25, "SELLER-002"
        );

        oms.addProduct(laptop);
        oms.addProduct(book);
        oms.addProduct(phone);

        // ===== Setup Customer =====
        Customer customer = new Customer(
            "CUST-001",
            "John Doe",
            "john@example.com",
            "+1-555-1234"
        );

        Address address = new Address(
            "123 Main St",
            "San Francisco",
            "CA",
            "94105",
            "USA"
        );
        customer.addAddress(address);
        customer.addToWallet(5000.0);  // Add wallet balance

        oms.addCustomer(customer);

        // ===== Shopping Flow =====
        System.out.println("--- Shopping Cart ---");
        oms.addToCart("CUST-001", "PROD-001", 1);
        oms.addToCart("CUST-001", "PROD-002", 2);

        // ===== Place Order =====
        System.out.println("\n--- Placing Order ---");
        Order order = oms.placeOrder("CUST-001", address, PaymentMethod.CREDIT_CARD);

        if (order != null) {
            String orderId = order.getOrderId();

            // ===== Order Lifecycle =====
            System.out.println("\n--- Order Processing ---");
            oms.processOrder(orderId);

            System.out.println("\n--- Shipping Order ---");
            oms.shipOrder(orderId);

            System.out.println("\n--- Track Shipment ---");
            oms.trackShipment(orderId);

            System.out.println("\n--- Delivering Order ---");
            oms.deliverOrder(orderId);

            System.out.println("\n--- View Order Details ---");
            oms.viewOrder(orderId);
        }

        // ===== Demo Order Cancellation =====
        System.out.println("\n\n--- Demo: Order Cancellation ---");
        oms.addToCart("CUST-001", "PROD-003", 1);
        Order order2 = oms.placeOrder("CUST-001", address, PaymentMethod.WALLET);

        if (order2 != null) {
            System.out.println("\nCancelling order...");
            oms.cancelOrder(order2.getOrderId());
            System.out.println("Wallet balance after refund: $" + customer.getWalletBalance());
        }

        System.out.println("\n========== Demo Complete ==========");
    }
}
