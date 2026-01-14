import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Inventory Management System
 * Time to complete: 40-50 minutes
 * Focus: Stock tracking, concurrency handling
 */

// ==================== Product ====================
class Product {
    private final String productId;
    private final String name;
    private final double price;
    private int quantity;

    public Product(String productId, String name, double price, int initialQuantity) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = initialQuantity;
    }

    public synchronized boolean removeStock(int amount) {
        if (amount > quantity) {
            return false;
        }
        quantity -= amount;
        return true;
    }

    public synchronized void addStock(int amount) {
        quantity += amount;
    }

    public synchronized int getQuantity() {
        return quantity;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public double getPrice() { return price; }

    @Override
    public String toString() {
        return name + " (ID:" + productId + ", Price:$" + price + ", Stock:" + quantity + ")";
    }
}

// ==================== Order ====================
class Order {
    private final String orderId;
    private final Map<String, Integer> items;  // productId -> quantity
    private double totalAmount;
    private final String userId;

    public Order(String orderId, String userId) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = new HashMap<>();
        this.totalAmount = 0;
    }

    public void addItem(String productId, int quantity, double price) {
        items.put(productId, items.getOrDefault(productId, 0) + quantity);
        totalAmount += quantity * price;
    }

    public String getOrderId() { return orderId; }
    public Map<String, Integer> getItems() { return items; }
    public double getTotalAmount() { return totalAmount; }

    @Override
    public String toString() {
        return "Order[" + orderId + ", Items:" + items.size() + ", Total:$" + 
               String.format("%.2f", totalAmount) + "]";
    }
}

// ==================== Inventory Manager ====================
class InventoryManager {
    private final Map<String, Product> products;
    private final List<Order> orders;
    private int orderCounter;

    public InventoryManager() {
        this.products = new ConcurrentHashMap<>();
        this.orders = new CopyOnWriteArrayList<>();
        this.orderCounter = 1;
    }

    public void addProduct(Product product) {
        products.put(product.getProductId(), product);
        System.out.println("✓ Added product: " + product);
    }

    public void updateStock(String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) {
            System.out.println("✗ Product not found: " + productId);
            return;
        }

        product.addStock(quantity);
        System.out.println("✓ Updated stock for " + product.getName() + 
                         ": " + product.getQuantity());
    }

    public Order createOrder(String userId, Map<String, Integer> items) {
        String orderId = "O" + orderCounter++;
        Order order = new Order(orderId, userId);

        // Verify stock availability
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = products.get(productId);
            if (product == null) {
                System.out.println("✗ Product not found: " + productId);
                return null;
            }

            if (product.getQuantity() < quantity) {
                System.out.println("✗ Insufficient stock for " + product.getName() + 
                                 " (Available: " + product.getQuantity() + 
                                 ", Requested: " + quantity + ")");
                return null;
            }
        }

        // Reserve stock
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = products.get(productId);

            if (!product.removeStock(quantity)) {
                // Rollback
                rollbackOrder(order);
                System.out.println("✗ Failed to reserve stock");
                return null;
            }

            order.addItem(productId, quantity, product.getPrice());
        }

        orders.add(order);
        System.out.println("✓ Order created: " + order);
        return order;
    }

    private void rollbackOrder(Order order) {
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = products.get(productId);
            if (product != null) {
                product.addStock(quantity);
            }
        }
    }

    public void displayInventory() {
        System.out.println("\n=== Inventory Status ===");
        System.out.println("Total products: " + products.size());
        System.out.println("Total orders: " + orders.size());
        
        System.out.println("\nProducts:");
        for (Product product : products.values()) {
            System.out.println("  " + product);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class InventoryManagementSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Inventory Management Demo ===\n");

        InventoryManager manager = new InventoryManager();

        // Add products
        manager.addProduct(new Product("P1", "Laptop", 999.99, 10));
        manager.addProduct(new Product("P2", "Mouse", 29.99, 50));
        manager.addProduct(new Product("P3", "Keyboard", 79.99, 30));
        manager.addProduct(new Product("P4", "Monitor", 299.99, 15));

        manager.displayInventory();

        // Create orders
        System.out.println("--- Creating Orders ---");
        
        Map<String, Integer> order1Items = new HashMap<>();
        order1Items.put("P1", 2);  // 2 laptops
        order1Items.put("P2", 1);  // 1 mouse
        
        Order o1 = manager.createOrder("user1", order1Items);

        Map<String, Integer> order2Items = new HashMap<>();
        order2Items.put("P3", 5);  // 5 keyboards
        
        Order o2 = manager.createOrder("user2", order2Items);

        manager.displayInventory();

        // Try order with insufficient stock
        System.out.println("\n--- Order with Insufficient Stock ---");
        Map<String, Integer> order3Items = new HashMap<>();
        order3Items.put("P1", 20);  // More than available
        
        manager.createOrder("user3", order3Items);

        // Restock
        System.out.println("\n--- Restocking ---");
        manager.updateStock("P1", 10);

        manager.displayInventory();

        System.out.println("✅ Demo complete!");
    }
}
