import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shopping Cart System - HELLO Interview Framework
 * 
 * Companies: PayPal, Amazon, Walmart, Flipkart, Shopify +7 more
 * Pattern: Strategy (discounts) + Observer (cart events)
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Strategy Pattern — PercentageDiscount / FlatDiscount / BOGODiscount
 * 2. Observer Pattern — notify InventoryUpdater + AnalyticsTracker on cart changes
 * 3. HashMap — O(1) add/remove/update by product ID
 * 4. Immutable Order — snapshot of cart at checkout
 */

// ==================== Product ====================

class Product {
    private final String id;
    private final String name;
    private final double price;
    private int stock;
    private final String category;

    Product(String id, String name, double price, int stock, String category) {
        this.id = id; this.name = name; this.price = price;
        this.stock = stock; this.category = category;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public String getCategory() { return category; }

    synchronized boolean reserveStock(int qty) {
        if (stock >= qty) { stock -= qty; return true; }
        return false;
    }

    synchronized void restoreStock(int qty) { stock += qty; }

    @Override
    public String toString() { return name + " ($" + String.format("%.2f", price) + ")"; }
}

// ==================== CartItem ====================

class CartItem {
    private final Product product;
    private int quantity;

    CartItem(Product product, int quantity) {
        this.product = product; this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    void setQuantity(int q) { this.quantity = q; }
    double getSubtotal() { return product.getPrice() * quantity; }

    @Override
    public String toString() {
        return String.format("  %-20s x%d  $%.2f", product.getName(), quantity, getSubtotal());
    }
}

// ==================== Strategy: Discount ====================

interface DiscountStrategy {
    double apply(double subtotal, List<CartItem> items);
    String getDescription();
}

class PercentageDiscount implements DiscountStrategy {
    private final double percent;

    PercentageDiscount(double percent) { this.percent = percent; }

    @Override
    public double apply(double subtotal, List<CartItem> items) {
        return subtotal * (percent / 100.0);
    }

    @Override
    public String getDescription() { return percent + "% off"; }
}

class FlatDiscount implements DiscountStrategy {
    private final double amount;

    FlatDiscount(double amount) { this.amount = amount; }

    @Override
    public double apply(double subtotal, List<CartItem> items) {
        return Math.min(amount, subtotal); // can't discount more than subtotal
    }

    @Override
    public String getDescription() { return "$" + String.format("%.2f", amount) + " off"; }
}

class BuyOneGetOneFreeDiscount implements DiscountStrategy {
    private final String targetCategory; // e.g., "Electronics"

    BuyOneGetOneFreeDiscount(String targetCategory) { this.targetCategory = targetCategory; }

    @Override
    public double apply(double subtotal, List<CartItem> items) {
        double discount = 0;
        for (CartItem item : items) {
            if (targetCategory == null || item.getProduct().getCategory().equals(targetCategory)) {
                int freeItems = item.getQuantity() / 2; // every 2nd item free
                discount += freeItems * item.getProduct().getPrice();
            }
        }
        return discount;
    }

    @Override
    public String getDescription() {
        return "BOGO" + (targetCategory != null ? " on " + targetCategory : "");
    }
}

// ==================== Coupon ====================

class Coupon {
    private final String code;
    private final DiscountStrategy strategy;
    private final double minOrderAmount;
    private boolean used;

    Coupon(String code, DiscountStrategy strategy, double minOrderAmount) {
        this.code = code; this.strategy = strategy;
        this.minOrderAmount = minOrderAmount; this.used = false;
    }

    public String getCode() { return code; }
    public DiscountStrategy getStrategy() { return strategy; }
    public double getMinOrderAmount() { return minOrderAmount; }
    public boolean isUsed() { return used; }
    void markUsed() { this.used = true; }

    boolean isApplicable(double subtotal) {
        return !used && subtotal >= minOrderAmount;
    }

    @Override
    public String toString() {
        return code + " (" + strategy.getDescription() + ", min $"
            + String.format("%.2f", minOrderAmount) + ")";
    }
}

// ==================== Observer ====================

interface CartObserver {
    void onItemAdded(CartItem item);
    void onItemRemoved(CartItem item);
    void onCheckout(Order order);
}

class InventoryObserver implements CartObserver {
    @Override
    public void onItemAdded(CartItem item) {
        System.out.println("    📦 Inventory: Reserved " + item.getQuantity()
            + "x " + item.getProduct().getName());
    }

    @Override
    public void onItemRemoved(CartItem item) {
        System.out.println("    📦 Inventory: Restored " + item.getQuantity()
            + "x " + item.getProduct().getName());
    }

    @Override
    public void onCheckout(Order order) {
        System.out.println("    📦 Inventory: Order " + order.getOrderId() + " confirmed, stock committed.");
    }
}

class AnalyticsObserver implements CartObserver {
    @Override
    public void onItemAdded(CartItem item) {
        System.out.println("    📊 Analytics: " + item.getProduct().getName() + " added to cart.");
    }

    @Override
    public void onItemRemoved(CartItem item) {
        System.out.println("    📊 Analytics: " + item.getProduct().getName() + " removed from cart.");
    }

    @Override
    public void onCheckout(Order order) {
        System.out.printf("    📊 Analytics: Order $%.2f placed with %d items.%n",
            order.getTotal(), order.getItemCount());
    }
}

// ==================== Order (Immutable checkout snapshot) ====================

class Order {
    private final String orderId;
    private final List<CartItem> items;
    private final double subtotal;
    private final double discount;
    private final double tax;
    private final double total;
    private final String couponCode;

    Order(String orderId, List<CartItem> items, double subtotal,
          double discount, double tax, double total, String couponCode) {
        this.orderId = orderId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.subtotal = subtotal; this.discount = discount;
        this.tax = tax; this.total = total; this.couponCode = couponCode;
    }

    public String getOrderId() { return orderId; }
    public double getTotal() { return total; }
    public int getItemCount() { return items.stream().mapToInt(CartItem::getQuantity).sum(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔═══ Order: ").append(orderId).append(" ═══\n");
        for (CartItem item : items) sb.append("║ ").append(item).append("\n");
        sb.append("║ ─────────────────────────────────\n");
        sb.append(String.format("║  Subtotal:  $%.2f%n", subtotal));
        if (discount > 0)
            sb.append(String.format("║  Discount:  -$%.2f (%s)%n", discount, couponCode != null ? couponCode : "none"));
        sb.append(String.format("║  Tax:       $%.2f%n", tax));
        sb.append(String.format("║  TOTAL:     $%.2f%n", total));
        sb.append("╚═══════════════════════════════════\n");
        return sb.toString();
    }
}

// ==================== Shopping Cart ====================

class ShoppingCart {
    private final Map<String, CartItem> items = new LinkedHashMap<>(); // preserve insertion order
    private Coupon appliedCoupon;
    private final double taxRate;
    private final List<CartObserver> observers = new CopyOnWriteArrayList<>();
    private static int orderCounter = 0;

    ShoppingCart(double taxRate) { this.taxRate = taxRate; }

    void addObserver(CartObserver o) { observers.add(o); }

    // ─── Cart Operations ───

    boolean addItem(Product product, int quantity) {
        if (quantity <= 0) {
            System.out.println("  ✗ Quantity must be positive");
            return false;
        }
        if (!product.reserveStock(quantity)) {
            System.out.println("  ✗ Insufficient stock for " + product.getName()
                + " (available: " + product.getStock() + ")");
            return false;
        }

        CartItem existing = items.get(product.getId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            existing = new CartItem(product, quantity);
            items.put(product.getId(), existing);
        }

        System.out.println("  ✅ Added " + quantity + "x " + product.getName());
        for (CartObserver o : observers) o.onItemAdded(existing);
        return true;
    }

    boolean removeItem(String productId) {
        CartItem removed = items.remove(productId);
        if (removed == null) {
            System.out.println("  ✗ Item not in cart");
            return false;
        }
        removed.getProduct().restoreStock(removed.getQuantity());
        System.out.println("  ✅ Removed " + removed.getProduct().getName());
        for (CartObserver o : observers) o.onItemRemoved(removed);
        return true;
    }

    boolean updateQuantity(String productId, int newQty) {
        CartItem item = items.get(productId);
        if (item == null) { System.out.println("  ✗ Item not in cart"); return false; }

        int diff = newQty - item.getQuantity();
        if (diff > 0) {
            if (!item.getProduct().reserveStock(diff)) {
                System.out.println("  ✗ Insufficient stock"); return false;
            }
        } else if (diff < 0) {
            item.getProduct().restoreStock(-diff);
        }

        if (newQty == 0) return removeItem(productId);
        item.setQuantity(newQty);
        System.out.println("  ✅ Updated " + item.getProduct().getName() + " to " + newQty);
        return true;
    }

    // ─── Coupon ───

    boolean applyCoupon(Coupon coupon) {
        double subtotal = getSubtotal();
        if (!coupon.isApplicable(subtotal)) {
            System.out.println("  ✗ Coupon " + coupon.getCode() + " not applicable (min: $"
                + String.format("%.2f", coupon.getMinOrderAmount()) + ", cart: $"
                + String.format("%.2f", subtotal) + ")");
            return false;
        }
        this.appliedCoupon = coupon;
        System.out.println("  🏷️ Applied coupon: " + coupon);
        return true;
    }

    void removeCoupon() {
        if (appliedCoupon != null) {
            System.out.println("  🏷️ Removed coupon: " + appliedCoupon.getCode());
            appliedCoupon = null;
        }
    }

    // ─── Calculations ───

    double getSubtotal() {
        return items.values().stream().mapToDouble(CartItem::getSubtotal).sum();
    }

    double getDiscount() {
        if (appliedCoupon == null) return 0;
        return appliedCoupon.getStrategy().apply(getSubtotal(), new ArrayList<>(items.values()));
    }

    double getTax() { return (getSubtotal() - getDiscount()) * taxRate; }

    double getTotal() { return getSubtotal() - getDiscount() + getTax(); }

    // ─── Checkout ───

    Order checkout() {
        if (items.isEmpty()) {
            System.out.println("  ✗ Cart is empty!");
            return null;
        }

        String orderId = "ORD-" + (++orderCounter);
        String couponCode = appliedCoupon != null ? appliedCoupon.getCode() : null;
        Order order = new Order(orderId, new ArrayList<>(items.values()),
            getSubtotal(), getDiscount(), getTax(), getTotal(), couponCode);

        if (appliedCoupon != null) appliedCoupon.markUsed();

        // Notify observers
        for (CartObserver o : observers) o.onCheckout(order);

        // Clear cart
        items.clear();
        appliedCoupon = null;

        return order;
    }

    // ─── Display ───

    String display() {
        if (items.isEmpty()) return "  🛒 Cart is empty.\n";
        StringBuilder sb = new StringBuilder();
        sb.append("  🛒 Shopping Cart:\n");
        for (CartItem item : items.values()) sb.append(item).append("\n");
        sb.append("  ─────────────────────────────────\n");
        sb.append(String.format("  Subtotal:  $%.2f%n", getSubtotal()));
        if (appliedCoupon != null)
            sb.append(String.format("  Coupon:    -%s ($%.2f off)%n",
                appliedCoupon.getCode(), getDiscount()));
        sb.append(String.format("  Tax (%.0f%%): $%.2f%n", taxRate * 100, getTax()));
        sb.append(String.format("  TOTAL:     $%.2f%n", getTotal()));
        return sb.toString();
    }

    int getItemCount() { return items.size(); }
    boolean isEmpty() { return items.isEmpty(); }
}

// ==================== Main Demo ====================

public class ShoppingCartSystem {
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Shopping Cart System - Strategy (Discounts) + Observer ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        // Setup products
        Product laptop = new Product("P1", "Laptop", 999.99, 10, "Electronics");
        Product phone = new Product("P2", "Phone", 699.99, 20, "Electronics");
        Product headphones = new Product("P3", "Headphones", 149.99, 50, "Electronics");
        Product book = new Product("P4", "Design Patterns Book", 49.99, 100, "Books");
        Product shirt = new Product("P5", "T-Shirt", 29.99, 200, "Clothing");

        // Setup coupons (Strategy pattern in action)
        Coupon coupon10pct = new Coupon("SAVE10", new PercentageDiscount(10), 50.0);
        Coupon coupon20flat = new Coupon("FLAT20", new FlatDiscount(20), 100.0);
        Coupon couponBogo = new Coupon("BOGO", new BuyOneGetOneFreeDiscount("Electronics"), 200.0);

        // ── Scenario 1: Basic cart operations ──
        System.out.println("━━━ Scenario 1: Add/Remove/Update items ━━━");
        ShoppingCart cart = new ShoppingCart(0.08); // 8% tax
        cart.addObserver(new InventoryObserver());
        cart.addObserver(new AnalyticsObserver());

        cart.addItem(laptop, 1);
        cart.addItem(phone, 1);
        cart.addItem(book, 2);
        System.out.println(cart.display());

        cart.updateQuantity("P4", 3); // book: 2 → 3
        cart.removeItem("P2");        // remove phone
        System.out.println(cart.display());

        // ── Scenario 2: Apply percentage coupon ──
        System.out.println("━━━ Scenario 2: Percentage discount (10% off) ━━━");
        cart.addItem(phone, 1); // add phone back
        cart.applyCoupon(coupon10pct);
        System.out.println(cart.display());

        // ── Scenario 3: Checkout ──
        System.out.println("━━━ Scenario 3: Checkout ━━━");
        Order order1 = cart.checkout();
        System.out.println(order1);
        System.out.println("  Cart after checkout: " + (cart.isEmpty() ? "Empty ✓" : "NOT empty ✗"));
        System.out.println();

        // ── Scenario 4: Flat discount ──
        System.out.println("━━━ Scenario 4: Flat $20 discount ━━━");
        ShoppingCart cart2 = new ShoppingCart(0.08);
        cart2.addItem(headphones, 1);
        cart2.addItem(shirt, 3);
        cart2.applyCoupon(coupon20flat);
        System.out.println(cart2.display());
        Order order2 = cart2.checkout();
        System.out.println(order2);

        // ── Scenario 5: BOGO discount ──
        System.out.println("━━━ Scenario 5: Buy-One-Get-One-Free (Electronics) ━━━");
        ShoppingCart cart3 = new ShoppingCart(0.08);
        cart3.addItem(headphones, 4); // buy 4, get 2 free
        cart3.addItem(shirt, 2);      // not electronics, no BOGO
        System.out.println("  Before BOGO:");
        System.out.println(cart3.display());
        cart3.applyCoupon(couponBogo);
        System.out.println("  After BOGO (2 headphones free):");
        System.out.println(cart3.display());

        // ── Scenario 6: Edge cases ──
        System.out.println("━━━ Scenario 6: Edge cases ━━━");
        ShoppingCart cart4 = new ShoppingCart(0.08);

        // Empty cart checkout
        Order emptyOrder = cart4.checkout();
        System.out.println("  Empty checkout result: " + (emptyOrder == null ? "null ✓" : "NOT null ✗"));

        // Insufficient stock
        Product limitedItem = new Product("P6", "Rare Item", 500.0, 2, "Collectibles");
        cart4.addItem(limitedItem, 3); // only 2 in stock

        // Coupon below minimum
        cart4.addItem(book, 1); // $49.99
        cart4.applyCoupon(coupon20flat); // min $100

        // Used coupon
        System.out.println("  Coupon SAVE10 used? " + coupon10pct.isUsed());
        cart4.addItem(laptop, 1);
        cart4.applyCoupon(coupon10pct); // already used in order1

        // Negative quantity
        cart4.addItem(book, -1);

        System.out.println("\n✅ All shopping cart scenarios complete.");
    }
}
