# Shopping Cart System - HELLO Interview Framework

> **Companies**: PayPal, Amazon, Walmart, Flipkart, Shopify, Target, eBay +7 more  
> **Difficulty**: Medium  
> **Primary Pattern**: Strategy (discounts) + Observer (cart events)  
> **Time**: 35 minutes  
> **Reference**: [HelloInterview LLD Delivery](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

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
- "Should we handle different discount types?" → Yes: percentage, flat, BOGO
- "Do we need stock validation?" → Yes, can't add more than available
- "Multiple coupons at once?" → One coupon per cart (simplicity)
- "Tax calculation?" → Yes, configurable tax rate
- "Should we notify other systems on cart changes?" → Yes (Observer)

### Functional Requirements

#### Must Have (P0)
1. **Cart Operations**
   - Add item (product + quantity) to cart
   - Remove item from cart
   - Update item quantity
   - Clear cart

2. **Discount/Coupon System** (Strategy Pattern)
   - Percentage discount (e.g., 10% off)
   - Flat amount discount (e.g., $20 off)
   - Buy-One-Get-One-Free (BOGO)
   - Apply coupon with minimum order requirement
   - One coupon per cart

3. **Price Calculation**
   - Subtotal = Σ(price × quantity)
   - Discount = strategy.apply(subtotal, items)
   - Tax = (subtotal - discount) × taxRate
   - Total = subtotal - discount + tax

4. **Checkout**
   - Create immutable Order from cart snapshot
   - Clear cart after checkout
   - Mark coupon as used

5. **Stock Validation**
   - Cannot add more items than in stock
   - Reserve stock on add, restore on remove
   - Thread-safe stock operations

6. **Notifications** (Observer Pattern)
   - Notify on item added/removed
   - Notify on checkout (inventory update, analytics)

#### Nice to Have (P1)
- Multiple coupons / stacking
- Saved carts (persistence)
- Cart expiry (abandon after 24h)
- Wishlist → cart transfer
- Price comparison / price drops

### Non-Functional Requirements
- **Thread Safety**: Concurrent cart modifications (multi-tab)
- **Correctness**: Prices calculated accurately (no floating point drift)
- **Latency**: Cart operations < 10ms
- **Memory**: O(N) where N = unique items in cart

### Constraints & Assumptions
- One cart per user session
- Products identified by unique ID
- Prices in USD (double for simplicity, BigDecimal in production)
- Stock is validated at add time and at checkout

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌────────────────────────┐
│       Product          │
│  - id: String          │
│  - name: String        │
│  - price: double       │
│  - stock: int (sync)   │
│  - category: String    │
│  - reserveStock(qty)   │
│  - restoreStock(qty)   │
└──────────┬─────────────┘
           │ referenced by
           ▼
┌────────────────────────┐     ┌──────────────────────────────┐
│       CartItem         │     │         Coupon               │
│  - product: Product    │     │  - code: String              │
│  - quantity: int       │     │  - strategy: DiscountStrategy│
│  - getSubtotal()       │     │  - minOrderAmount: double    │
└──────────┬─────────────┘     │  - isUsed: boolean           │
           │ contained by      │  - isApplicable(subtotal)    │
           ▼                   └──────────┬───────────────────┘
┌─────────────────────────────────┐       │ applied to
│         ShoppingCart            │◄──────┘
│  - items: Map<String, CartItem>│
│  - appliedCoupon: Coupon       │
│  - taxRate: double             │
│  - observers: List<Observer>   │
│  - addItem(product, qty)       │
│  - removeItem(productId)       │
│  - updateQuantity(id, qty)     │
│  - applyCoupon(coupon)         │
│  - getSubtotal/Discount/Tax/   │
│    Total()                     │
│  - checkout(): Order           │
└──────────┬──────────────────────┘
           │ creates
           ▼
┌────────────────────────────────┐
│         Order (immutable)      │
│  - orderId: String             │
│  - items: List<CartItem>       │
│  - subtotal, discount, tax     │
│  - total: double               │
│  - couponCode: String          │
└────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│         DiscountStrategy (interface)             │
│  + apply(subtotal, items): double               │
│  + getDescription(): String                     │
├─────────────────────────────────────────────────┤
│  PercentageDiscount  │  10% off entire cart     │
│  FlatDiscount        │  $20 off (capped)        │
│  BOGODiscount        │  Every 2nd item free     │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│         CartObserver (interface)                 │
│  + onItemAdded(CartItem)                        │
│  + onItemRemoved(CartItem)                      │
│  + onCheckout(Order)                            │
├─────────────────────────────────────────────────┤
│  InventoryObserver   │  Updates stock system    │
│  AnalyticsObserver   │  Tracks cart metrics     │
└─────────────────────────────────────────────────┘
```

### Class: Product
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique product ID |
| name | String | Display name |
| price | double | Unit price |
| stock | int | Available quantity (synchronized) |
| category | String | For category-specific discounts |

**Why `synchronized` stock?** Multiple carts can try to reserve same product.

### Class: CartItem
| Attribute | Type | Description |
|-----------|------|-------------|
| product | Product | Reference to product |
| quantity | int | How many in cart |
| getSubtotal() | double | price × quantity |

### Class: ShoppingCart
| Attribute | Type | Description |
|-----------|------|-------------|
| items | LinkedHashMap<String, CartItem> | Preserves insertion order |
| appliedCoupon | Coupon | Currently applied coupon (nullable) |
| taxRate | double | e.g., 0.08 for 8% |
| observers | CopyOnWriteArrayList | Thread-safe observer list |

**Why LinkedHashMap?** Preserves order items were added (for consistent display).

### Class: Coupon
| Attribute | Type | Description |
|-----------|------|-------------|
| code | String | "SAVE10", "FLAT20" |
| strategy | DiscountStrategy | How to calculate discount |
| minOrderAmount | double | Minimum cart value to apply |
| used | boolean | One-time use flag |

### Class: Order (Immutable)
| Attribute | Type | Description |
|-----------|------|-------------|
| orderId | String | "ORD-1" |
| items | List<CartItem> | Frozen snapshot |
| subtotal, discount, tax, total | double | Calculated values |
| couponCode | String | Which coupon was used |

**Why immutable?** Cart can be modified after checkout; Order must be a permanent record.

---

## 3️⃣ API Design

### ShoppingCart (Public API)

```java
class ShoppingCart {
    /** Add product to cart with quantity. Reserves stock. */
    boolean addItem(Product product, int quantity);
    
    /** Remove product from cart. Restores stock. */
    boolean removeItem(String productId);
    
    /** Update quantity. Adjusts stock reservation. */
    boolean updateQuantity(String productId, int newQuantity);
    
    /** Apply a coupon. Validates minimum order. */
    boolean applyCoupon(Coupon coupon);
    
    /** Remove applied coupon */
    void removeCoupon();
    
    /** Price calculations */
    double getSubtotal();
    double getDiscount();
    double getTax();
    double getTotal();
    
    /** Create Order from cart, clear cart, mark coupon used */
    Order checkout();
    
    /** Register observer */
    void addObserver(CartObserver observer);
}
```

### DiscountStrategy (Strategy Interface)

```java
interface DiscountStrategy {
    /**
     * Calculate discount amount
     * @param subtotal Cart subtotal before discount
     * @param items    All items in cart (for item-level discounts like BOGO)
     * @return Discount amount in dollars
     */
    double apply(double subtotal, List<CartItem> items);
    
    String getDescription(); // "10% off", "$20 off", "BOGO on Electronics"
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Add Item (with Stock Reservation)

```
User → cart.addItem(laptop, 2)
  │
  ├─ Validate: quantity > 0? ✓
  ├─ product.reserveStock(2)
  │   ├─ synchronized: stock >= 2? 
  │   ├─ YES → stock -= 2 → return true
  │   └─ (If NO → "Insufficient stock" → return false)
  │
  ├─ CartItem already in cart?
  │   ├─ YES → existing.quantity += 2
  │   └─ NO → new CartItem(laptop, 2) → items.put("P1", cartItem)
  │
  ├─ Notify observers:
  │   ├─ InventoryObserver: "Reserved 2x Laptop"
  │   └─ AnalyticsObserver: "Laptop added to cart"
  │
  └─ Return: true
```

### Scenario 2: Apply 10% Coupon

```
User → cart.applyCoupon(coupon10pct)
  │
  ├─ Validate: coupon.isUsed? → false ✓
  ├─ Validate: subtotal >= minOrderAmount?
  │   ├─ subtotal = $1849.95
  │   ├─ minOrderAmount = $50.00
  │   └─ $1849.95 >= $50.00 ✓
  │
  ├─ cart.appliedCoupon = coupon10pct
  │
  └─ Now: getDiscount() = strategy.apply($1849.95, items)
         = $1849.95 * 10% = $184.995 ≈ $185.00
```

### Scenario 3: Checkout Flow

```
User → cart.checkout()
  │
  ├─ Validate: cart not empty ✓
  │
  ├─ Snapshot calculations:
  │   ├─ subtotal = $1849.95
  │   ├─ discount = $185.00 (10% coupon)
  │   ├─ tax = ($1849.95 - $185.00) * 0.08 = $133.20
  │   └─ total = $1849.95 - $185.00 + $133.20 = $1798.15
  │
  ├─ Create Order("ORD-1", items_copy, calculations, "SAVE10")
  │
  ├─ coupon10pct.markUsed() → cannot be reused
  │
  ├─ Notify observers:
  │   ├─ InventoryObserver: "Order ORD-1 confirmed, stock committed"
  │   └─ AnalyticsObserver: "Order $1798.15, 5 items"
  │
  ├─ cart.items.clear()
  ├─ cart.appliedCoupon = null
  │
  └─ Return: Order object (immutable)
```

### Scenario 4: BOGO Discount

```
Cart: 4x Headphones ($149.99), 2x T-Shirt ($29.99)
Apply BOGO coupon for "Electronics":

strategy.apply(subtotal, items):
  ├─ Headphones: category = "Electronics" → BOGO eligible
  │   └─ 4 qty / 2 = 2 free items → discount = 2 × $149.99 = $299.98
  │
  ├─ T-Shirt: category = "Clothing" → NOT eligible
  │   └─ No discount
  │
  └─ Total discount = $299.98 (2 headphones free!)
```

---

## 5️⃣ Design

### Design Pattern 1: Strategy Pattern (Discounts)

**Why Strategy?**
- Multiple discount algorithms exist (percentage, flat, BOGO, buy-N-get-M, etc.)
- New promotions added frequently (holiday, clearance, flash sale)
- Discount logic shouldn't be embedded in ShoppingCart
- Open/Closed: add new discount without changing cart code

```
DiscountStrategy
  ├── PercentageDiscount(10)
  │     apply(1000, items) → 1000 * 0.10 = $100
  │
  ├── FlatDiscount(20)
  │     apply(1000, items) → min(20, 1000) = $20
  │
  └── BOGODiscount("Electronics")
        apply(1000, items) → sum of every-2nd-item for matching category
```

**Why `items` parameter in `apply()`?**
BOGO needs to iterate individual items. Percentage only needs subtotal. Strategy signature accommodates both.

### Design Pattern 2: Observer Pattern (Cart Events)

**Why Observer?**
- Inventory system needs to know about stock changes
- Analytics needs cart abandonment, conversion data
- Email system may send "items in your cart" reminders
- None of these are the cart's responsibility

```
ShoppingCart (Subject)
  │
  ├─ onItemAdded(cartItem)
  │    ├─ InventoryObserver → reserve stock in warehouse
  │    └─ AnalyticsObserver → track add-to-cart event
  │
  ├─ onItemRemoved(cartItem)
  │    ├─ InventoryObserver → release stock reservation
  │    └─ AnalyticsObserver → track removal (cart abandonment?)
  │
  └─ onCheckout(order)
       ├─ InventoryObserver → commit stock (permanent deduction)
       └─ AnalyticsObserver → conversion event + revenue tracking
```

### Design Pattern 3: Immutable Order

Cart is mutable (add/remove/update). But once checked out, the order must be a **frozen snapshot**:

```java
class Order {
    private final List<CartItem> items; // unmodifiable
    private final double total;         // calculated once
    
    Order(...) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        // Cannot modify after creation
    }
}
```

### Data Structure Choices

| Structure | Purpose | Why |
|-----------|---------|-----|
| `LinkedHashMap<String, CartItem>` | Cart items | O(1) lookup + preserves insertion order |
| `CopyOnWriteArrayList` | Observers | Thread-safe iteration during notification |
| `synchronized` on Product.stock | Stock reservation | Prevent overselling |
| `Coupon.used` (boolean) | One-time use | Simple flag, checked before applying |

### Price Calculation Formula

```
Subtotal = Σ(item.price × item.quantity)         // raw total
Discount = appliedCoupon.strategy.apply(subtotal, items)  // strategy
Tax      = (Subtotal - Discount) × taxRate        // tax on discounted amount
Total    = Subtotal - Discount + Tax              // final
```

**Note**: Tax is calculated AFTER discount (industry standard). Some jurisdictions tax before discount — configurable.

---

### Complete Implementation

See `ShoppingCartSystem.java` for full runnable code including:
- Product with synchronized stock management
- 3 DiscountStrategy implementations (Percentage, Flat, BOGO)
- Coupon with minimum order validation + used tracking
- ShoppingCart with Observer notifications
- Immutable Order snapshot
- 6 demo scenarios including edge cases

---

## 6️⃣ Deep Dives

### Deep Dive 1: Strategy Pattern — Adding New Discounts

Adding a holiday discount requires **zero changes** to ShoppingCart:

```java
// New discount: First purchase 20% off
class FirstPurchaseDiscount implements DiscountStrategy {
    @Override
    public double apply(double subtotal, List<CartItem> items) {
        return subtotal * 0.20; // 20% off
    }
    
    @Override
    public String getDescription() { return "First Purchase 20% off"; }
}

// Usage:
Coupon firstBuy = new Coupon("WELCOME20", new FirstPurchaseDiscount(), 0);
cart.applyCoupon(firstBuy);
// ShoppingCart code: zero changes needed ✓
```

### Deep Dive 2: Stock Management — Reserve vs Commit

**Two-phase stock management:**

| Phase | When | Action |
|-------|------|--------|
| **Reserve** | `addItem()` | `product.stock -= qty` (temporary hold) |
| **Restore** | `removeItem()` or `updateQuantity(less)` | `product.stock += qty` |
| **Commit** | `checkout()` | Stock already deducted, nothing to do |
| **Expire** | Cart timeout (P1) | `product.stock += qty` for abandoned items |

**Thread safety**: `product.reserveStock()` is `synchronized` — two carts can't over-reserve.

### Deep Dive 3: BOGO Algorithm Deep Dive

```java
class BuyOneGetOneFreeDiscount implements DiscountStrategy {
    private final String targetCategory;
    
    @Override
    public double apply(double subtotal, List<CartItem> items) {
        double discount = 0;
        for (CartItem item : items) {
            if (item.getProduct().getCategory().equals(targetCategory)) {
                int freeItems = item.getQuantity() / 2; // integer division!
                discount += freeItems * item.getProduct().getPrice();
            }
        }
        return discount;
    }
}

// Example:
// 4 headphones at $150 → 4/2 = 2 free → discount = $300
// 3 headphones at $150 → 3/2 = 1 free → discount = $150
// 1 headphone  at $150 → 1/2 = 0 free → discount = $0
```

### Deep Dive 4: Coupon Validation Rules

| Validation | Check | Error Message |
|-----------|-------|---------------|
| Already used | `coupon.isUsed()` | "Coupon already used" |
| Minimum not met | `subtotal < minOrderAmount` | "Min order $X required, cart is $Y" |
| Expired (P1) | `now > coupon.expiryDate` | "Coupon expired" |
| Wrong category (P1) | `!items.anyMatch(category)` | "No eligible items" |

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Add item with quantity 0 | Return false, "Quantity must be positive" |
| Add item with quantity > stock | Return false, "Insufficient stock" |
| Remove item not in cart | Return false, "Item not in cart" |
| Update quantity to 0 | Calls removeItem() |
| Apply coupon to empty cart | Subtotal = 0, likely below minimum |
| Checkout empty cart | Return null, "Cart is empty" |
| Coupon discount > subtotal | `Math.min(discount, subtotal)` — can't go negative |
| Add same item twice | Increments existing CartItem quantity |
| Reuse coupon after checkout | `coupon.isUsed() == true` → rejected |

### Deep Dive 6: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| addItem | O(1) | O(1) per item |
| removeItem | O(1) | O(1) |
| updateQuantity | O(1) | O(1) |
| getSubtotal | O(N) | O(1) |
| getDiscount (%) | O(N) | O(1) |
| getDiscount (BOGO) | O(N) | O(1) |
| checkout | O(N) copy | O(N) for Order |
| Notify observers | O(O) | O(1) |

Where N = unique items in cart, O = number of observers

### Deep Dive 7: Production Considerations

| Concern | Interview Answer | Production |
|---------|-----------------|------------|
| Price precision | `double` | `BigDecimal` |
| Stock management | `synchronized` | DB optimistic locking |
| Cart persistence | In-memory | Redis / DB |
| Coupon validation | Simple flags | Coupon service (microservice) |
| Tax calculation | Flat rate | Tax API (jurisdiction-based) |
| Observers | In-process | Event bus / Kafka |

---

## 📋 Interview Checklist

### What Interviewer Looks For:
- [ ] **Requirements**: Asked about discount types, stock, tax, concurrency
- [ ] **Strategy Pattern**: Swappable discount algorithms with common interface
- [ ] **Observer Pattern**: Decoupled notifications (inventory, analytics)
- [ ] **Stock Management**: Reserve/restore pattern, synchronized
- [ ] **Immutable Order**: Cart is mutable, Order is frozen snapshot
- [ ] **Price Calculation**: Subtotal → Discount → Tax → Total (correct order)
- [ ] **Coupon Validation**: Min order, used flag, one per cart
- [ ] **Edge Cases**: Empty cart, insufficient stock, quantity updates, reused coupon

### Time Spent:
| Phase | Target |
|-------|--------|
| Requirements | 3-5 min |
| Core Entities | 5 min |
| API Design | 5 min |
| Design + Code | 15-20 min |
| Deep Dives | 5-10 min |
| **Total** | **~35 min** |
