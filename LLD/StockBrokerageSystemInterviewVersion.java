import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY Stock Brokerage System
 * Time to complete: 50-60 minutes
 * Focus: Order matching, portfolio management, trading
 */

// ==================== Order Type ====================
enum OrderType {
    MARKET, LIMIT
}

enum OrderSide {
    BUY, SELL
}

enum OrderStatus {
    PENDING, FILLED, PARTIALLY_FILLED, CANCELLED
}

// ==================== Stock ====================
class Stock {
    private final String symbol;
    private final String companyName;
    private double currentPrice;

    public Stock(String symbol, String companyName, double currentPrice) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.currentPrice = currentPrice;
    }

    public void updatePrice(double newPrice) {
        this.currentPrice = newPrice;
    }

    public String getSymbol() { return symbol; }
    public double getCurrentPrice() { return currentPrice; }

    @Override
    public String toString() {
        return symbol + " (" + companyName + ") - $" + currentPrice;
    }
}

// ==================== Order ====================
class Order {
    private final String orderId;
    private final String userId;
    private final String symbol;
    private final OrderType type;
    private final OrderSide side;
    private final int quantity;
    private final double price;  // For limit orders
    private int filledQuantity;
    private OrderStatus status;
    private final LocalDateTime timestamp;

    public Order(String orderId, String userId, String symbol, OrderType type,
                OrderSide side, int quantity, double price) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.filledQuantity = 0;
        this.status = OrderStatus.PENDING;
        this.timestamp = LocalDateTime.now();
    }

    public void fill(int quantity) {
        filledQuantity += quantity;
        if (filledQuantity >= this.quantity) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    public int getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public OrderType getType() { return type; }
    public OrderSide getSide() { return side; }
    public double getPrice() { return price; }
    public OrderStatus getStatus() { return status; }

    @Override
    public String toString() {
        return orderId + ": " + side + " " + quantity + " " + symbol + 
               " @ $" + price + " [" + status + "]";
    }
}

// ==================== Portfolio ====================
class Portfolio {
    private final String userId;
    private double cashBalance;
    private final Map<String, Integer> holdings;  // symbol -> quantity
    private final List<Order> orderHistory;

    public Portfolio(String userId, double initialBalance) {
        this.userId = userId;
        this.cashBalance = initialBalance;
        this.holdings = new ConcurrentHashMap<>();
        this.orderHistory = new ArrayList<>();
    }

    public synchronized boolean hasCash(double amount) {
        return cashBalance >= amount;
    }

    public synchronized void deductCash(double amount) {
        cashBalance -= amount;
    }

    public synchronized void addCash(double amount) {
        cashBalance += amount;
    }

    public synchronized boolean hasShares(String symbol, int quantity) {
        return holdings.getOrDefault(symbol, 0) >= quantity;
    }

    public synchronized void addShares(String symbol, int quantity) {
        holdings.put(symbol, holdings.getOrDefault(symbol, 0) + quantity);
    }

    public synchronized void deductShares(String symbol, int quantity) {
        int current = holdings.getOrDefault(symbol, 0);
        holdings.put(symbol, current - quantity);
    }

    public void addToHistory(Order order) {
        orderHistory.add(order);
    }

    public String getUserId() { return userId; }
    public double getCashBalance() { return cashBalance; }
    public Map<String, Integer> getHoldings() { return new HashMap<>(holdings); }

    @Override
    public String toString() {
        return "Portfolio[" + userId + "] Cash: $" + String.format("%.2f", cashBalance) + 
               " | Holdings: " + holdings.size() + " stocks";
    }
}

// ==================== Stock Exchange ====================
class StockExchange {
    private final Map<String, Stock> stocks;
    private final Map<String, Portfolio> portfolios;
    private final List<Order> orders;
    private int orderCounter;

    public StockExchange() {
        this.stocks = new ConcurrentHashMap<>();
        this.portfolios = new ConcurrentHashMap<>();
        this.orders = new ArrayList<>();
        this.orderCounter = 1;
    }

    public void addStock(Stock stock) {
        stocks.put(stock.getSymbol(), stock);
        System.out.println("✓ Listed stock: " + stock);
    }

    public void createPortfolio(String userId, double initialBalance) {
        portfolios.put(userId, new Portfolio(userId, initialBalance));
        System.out.println("✓ Created portfolio for " + userId + " with $" + initialBalance);
    }

    public Order placeOrder(String userId, String symbol, OrderType type,
                          OrderSide side, int quantity, double limitPrice) {
        Portfolio portfolio = portfolios.get(userId);
        Stock stock = stocks.get(symbol);

        if (portfolio == null || stock == null) {
            System.out.println("✗ Invalid user or stock");
            return null;
        }

        // Validate order
        double totalCost = quantity * (type == OrderType.MARKET ? stock.getCurrentPrice() : limitPrice);
        
        if (side == OrderSide.BUY && !portfolio.hasCash(totalCost)) {
            System.out.println("✗ Insufficient funds");
            return null;
        }

        if (side == OrderSide.SELL && !portfolio.hasShares(symbol, quantity)) {
            System.out.println("✗ Insufficient shares");
            return null;
        }

        // Create order
        String orderId = "ORD" + orderCounter++;
        Order order = new Order(orderId, userId, symbol, type, side, quantity, limitPrice);
        orders.add(order);

        // Execute immediately for simplicity (in real system would match with order book)
        executeOrder(order);

        portfolio.addToHistory(order);
        return order;
    }

    private void executeOrder(Order order) {
        Stock stock = stocks.get(order.getSymbol());
        Portfolio portfolio = portfolios.get(order.getUserId());
        double executionPrice = order.getType() == OrderType.MARKET ? 
                               stock.getCurrentPrice() : order.getPrice();

        if (order.getSide() == OrderSide.BUY) {
            double totalCost = order.getRemainingQuantity() * executionPrice;
            portfolio.deductCash(totalCost);
            portfolio.addShares(order.getSymbol(), order.getRemainingQuantity());
            order.fill(order.getRemainingQuantity());
            
            System.out.println("✓ BUY executed: " + order.getRemainingQuantity() + 
                             " " + order.getSymbol() + " @ $" + executionPrice);
        } else {
            double totalRevenue = order.getRemainingQuantity() * executionPrice;
            portfolio.deductShares(order.getSymbol(), order.getRemainingQuantity());
            portfolio.addCash(totalRevenue);
            order.fill(order.getRemainingQuantity());
            
            System.out.println("✓ SELL executed: " + order.getRemainingQuantity() + 
                             " " + order.getSymbol() + " @ $" + executionPrice);
        }
    }

    public void displayPortfolio(String userId) {
        Portfolio portfolio = portfolios.get(userId);
        if (portfolio == null) {
            System.out.println("✗ Portfolio not found");
            return;
        }

        System.out.println("\n=== Portfolio for " + userId + " ===");
        System.out.println("Cash Balance: $" + String.format("%.2f", portfolio.getCashBalance()));
        System.out.println("\nHoldings:");
        
        Map<String, Integer> holdings = portfolio.getHoldings();
        if (holdings.isEmpty()) {
            System.out.println("  No stocks");
        } else {
            double totalValue = 0;
            for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
                String symbol = entry.getKey();
                int qty = entry.getValue();
                double price = stocks.get(symbol).getCurrentPrice();
                double value = qty * price;
                totalValue += value;
                
                System.out.println("  " + symbol + ": " + qty + " shares @ $" + 
                                 price + " = $" + String.format("%.2f", value));
            }
            System.out.println("\nTotal Portfolio Value: $" + 
                             String.format("%.2f", portfolio.getCashBalance() + totalValue));
        }
        System.out.println();
    }

    public void displayMarket() {
        System.out.println("\n=== Market Prices ===");
        for (Stock stock : stocks.values()) {
            System.out.println("  " + stock);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class StockBrokerageSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Stock Brokerage System Demo ===\n");

        StockExchange exchange = new StockExchange();

        // Add stocks
        System.out.println("--- Listing Stocks ---");
        exchange.addStock(new Stock("AAPL", "Apple Inc", 150.0));
        exchange.addStock(new Stock("GOOGL", "Google", 2800.0));
        exchange.addStock(new Stock("TSLA", "Tesla", 700.0));

        exchange.displayMarket();

        // Create portfolios
        System.out.println("--- Creating Portfolios ---");
        exchange.createPortfolio("alice", 10000);
        exchange.createPortfolio("bob", 5000);

        // Place orders
        System.out.println("\n--- Placing Orders ---");
        
        // Alice buys AAPL
        exchange.placeOrder("alice", "AAPL", OrderType.MARKET, OrderSide.BUY, 10, 0);
        
        // Bob buys TSLA
        exchange.placeOrder("bob", "TSLA", OrderType.LIMIT, OrderSide.BUY, 5, 700.0);

        // Alice buys GOOGL
        exchange.placeOrder("alice", "GOOGL", OrderType.MARKET, OrderSide.BUY, 2, 0);

        // Display portfolios
        exchange.displayPortfolio("alice");
        exchange.displayPortfolio("bob");

        // Sell orders
        System.out.println("\n--- Selling Stocks ---");
        exchange.placeOrder("alice", "AAPL", OrderType.MARKET, OrderSide.SELL, 5, 0);

        exchange.displayPortfolio("alice");

        // Try invalid order
        System.out.println("\n--- Invalid Orders ---");
        exchange.placeOrder("bob", "AAPL", OrderType.MARKET, OrderSide.SELL, 100, 0);  // Don't have shares

        System.out.println("✅ Demo complete!");
    }
}
