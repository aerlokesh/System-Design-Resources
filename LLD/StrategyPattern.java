import java.util.*;

// ==================== STRATEGY PATTERN ====================
// Definition: Defines a family of algorithms, encapsulates each one, and makes them
// interchangeable. Strategy lets the algorithm vary independently from clients that use it.
//
// Real-World Examples:
// 1. Sorting Algorithms - QuickSort, MergeSort, BubbleSort
// 2. Payment Methods - Credit Card, PayPal, Cash
// 3. Compression Algorithms - ZIP, RAR, 7Z
// 4. Route Planning - Fastest, Shortest, Scenic
// 5. Pricing Strategies - Regular, Holiday, Loyalty discount
//
// When to Use:
// - Multiple related classes differ only in behavior
// - Need different variants of an algorithm
// - Algorithm uses data that clients shouldn't know about
// - Want to avoid conditional statements for algorithm selection
//
// Benefits:
// 1. Open/Closed Principle - add new strategies without changing context
// 2. Runtime algorithm switching
// 3. Isolates algorithm implementation details
// 4. Replaces inheritance with composition

// ==================== EXAMPLE 1: SORTING ALGORITHMS ====================

interface SortStrategy {
    void sort(int[] array);
    String getName();
}

class QuickSort implements SortStrategy {
    @Override
    public void sort(int[] array) {
        System.out.println("⚡ Using QuickSort algorithm");
        quickSort(array, 0, array.length - 1);
        System.out.println("Sorted: " + Arrays.toString(array));
    }

    private void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);
            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    private int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int i = (low - 1);
        for (int j = low; j < high; j++) {
            if (arr[j] < pivot) {
                i++;
                int temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }
        int temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;
        return i + 1;
    }

    @Override
    public String getName() {
        return "QuickSort";
    }
}

class BubbleSort implements SortStrategy {
    @Override
    public void sort(int[] array) {
        System.out.println("🔄 Using BubbleSort algorithm");
        int n = array.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (array[j] > array[j + 1]) {
                    int temp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = temp;
                }
            }
        }
        System.out.println("Sorted: " + Arrays.toString(array));
    }

    @Override
    public String getName() {
        return "BubbleSort";
    }
}

class MergeSort implements SortStrategy {
    @Override
    public void sort(int[] array) {
        System.out.println("🔀 Using MergeSort algorithm");
        mergeSort(array, 0, array.length - 1);
        System.out.println("Sorted: " + Arrays.toString(array));
    }

    private void mergeSort(int[] arr, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(arr, left, mid);
            mergeSort(arr, mid + 1, right);
            merge(arr, left, mid, right);
        }
    }

    private void merge(int[] arr, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;
        int[] L = new int[n1];
        int[] R = new int[n2];
        
        for (int i = 0; i < n1; i++) L[i] = arr[left + i];
        for (int j = 0; j < n2; j++) R[j] = arr[mid + 1 + j];
        
        int i = 0, j = 0, k = left;
        while (i < n1 && j < n2) {
            if (L[i] <= R[j]) {
                arr[k] = L[i];
                i++;
            } else {
                arr[k] = R[j];
                j++;
            }
            k++;
        }
        while (i < n1) {
            arr[k] = L[i];
            i++;
            k++;
        }
        while (j < n2) {
            arr[k] = R[j];
            j++;
            k++;
        }
    }

    @Override
    public String getName() {
        return "MergeSort";
    }
}

class SortContext {
    private SortStrategy strategy;

    public void setStrategy(SortStrategy strategy) {
        this.strategy = strategy;
    }

    public void executeSort(int[] array) {
        if (strategy == null) {
            throw new IllegalStateException("Strategy not set");
        }
        strategy.sort(array);
    }
}

// ==================== EXAMPLE 2: PAYMENT METHODS ====================

interface PaymentStrategy {
    void pay(double amount);
    String getMethodName();
}

class CreditCardPayment implements PaymentStrategy {
    private String cardNumber;
    private String cvv;

    public CreditCardPayment(String cardNumber, String cvv) {
        this.cardNumber = cardNumber;
        this.cvv = cvv;
    }

    @Override
    public void pay(double amount) {
        System.out.printf("💳 Paid $%.2f using Credit Card ending in %s%n", 
            amount, cardNumber.substring(cardNumber.length() - 4));
    }

    @Override
    public String getMethodName() {
        return "Credit Card";
    }
}

class PayPalPayment implements PaymentStrategy {
    private String email;

    public PayPalPayment(String email) {
        this.email = email;
    }

    @Override
    public void pay(double amount) {
        System.out.printf("🅿️ Paid $%.2f using PayPal account %s%n", amount, email);
    }

    @Override
    public String getMethodName() {
        return "PayPal";
    }
}

class CashPayment implements PaymentStrategy {
    @Override
    public void pay(double amount) {
        System.out.printf("💵 Paid $%.2f in Cash%n", amount);
    }

    @Override
    public String getMethodName() {
        return "Cash";
    }
}

class ShoppingCart {
    private List<String> items = new ArrayList<>();
    private double total = 0;
    private PaymentStrategy paymentStrategy;

    public void addItem(String item, double price) {
        items.add(item);
        total += price;
    }

    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }

    public void checkout() {
        System.out.println("\n🛒 Cart Items: " + items);
        System.out.printf("Total: $%.2f%n", total);
        
        if (paymentStrategy == null) {
            throw new IllegalStateException("Payment method not set");
        }
        
        paymentStrategy.pay(total);
        System.out.println("✅ Payment successful!");
    }
}

// ==================== EXAMPLE 3: COMPRESSION ALGORITHMS ====================

interface CompressionStrategy {
    void compress(String filename);
    String getFormat();
}

class ZipCompression implements CompressionStrategy {
    @Override
    public void compress(String filename) {
        System.out.println("📦 Compressing " + filename + " using ZIP format");
        System.out.println("   Compression ratio: ~60%");
    }

    @Override
    public String getFormat() {
        return "ZIP";
    }
}

class RarCompression implements CompressionStrategy {
    @Override
    public void compress(String filename) {
        System.out.println("📦 Compressing " + filename + " using RAR format");
        System.out.println("   Compression ratio: ~65%");
    }

    @Override
    public String getFormat() {
        return "RAR";
    }
}

class SevenZipCompression implements CompressionStrategy {
    @Override
    public void compress(String filename) {
        System.out.println("📦 Compressing " + filename + " using 7Z format");
        System.out.println("   Compression ratio: ~70%");
    }

    @Override
    public String getFormat() {
        return "7Z";
    }
}

class FileCompressor {
    private CompressionStrategy strategy;

    public void setStrategy(CompressionStrategy strategy) {
        this.strategy = strategy;
    }

    public void compressFile(String filename) {
        if (strategy == null) {
            throw new IllegalStateException("Compression strategy not set");
        }
        strategy.compress(filename);
    }
}

// ==================== EXAMPLE 4: ROUTE PLANNING ====================

interface RouteStrategy {
    void calculateRoute(String start, String end);
    String getRouteName();
}

class FastestRoute implements RouteStrategy {
    @Override
    public void calculateRoute(String start, String end) {
        System.out.println("🚗 Fastest Route: " + start + " → " + end);
        System.out.println("   Distance: 45 km");
        System.out.println("   Time: 35 minutes");
        System.out.println("   Via Highway");
    }

    @Override
    public String getRouteName() {
        return "Fastest";
    }
}

class ShortestRoute implements RouteStrategy {
    @Override
    public void calculateRoute(String start, String end) {
        System.out.println("📏 Shortest Route: " + start + " → " + end);
        System.out.println("   Distance: 38 km");
        System.out.println("   Time: 50 minutes");
        System.out.println("   Via Local Roads");
    }

    @Override
    public String getRouteName() {
        return "Shortest";
    }
}

class ScenicRoute implements RouteStrategy {
    @Override
    public void calculateRoute(String start, String end) {
        System.out.println("🌄 Scenic Route: " + start + " → " + end);
        System.out.println("   Distance: 60 km");
        System.out.println("   Time: 90 minutes");
        System.out.println("   Via Coastal Road");
    }

    @Override
    public String getRouteName() {
        return "Scenic";
    }
}

class Navigator {
    private RouteStrategy strategy;

    public void setRouteStrategy(RouteStrategy strategy) {
        this.strategy = strategy;
    }

    public void navigate(String start, String end) {
        if (strategy == null) {
            throw new IllegalStateException("Route strategy not set");
        }
        strategy.calculateRoute(start, end);
    }
}

// ==================== EXAMPLE 5: PRICING STRATEGIES ====================

interface PricingStrategy {
    double calculatePrice(double originalPrice);
    String getStrategyName();
}

class RegularPricing implements PricingStrategy {
    @Override
    public double calculatePrice(double originalPrice) {
        return originalPrice;
    }

    @Override
    public String getStrategyName() {
        return "Regular Price";
    }
}

class HolidayPricing implements PricingStrategy {
    private double discountPercent = 20.0;

    @Override
    public double calculatePrice(double originalPrice) {
        double discount = originalPrice * (discountPercent / 100);
        return originalPrice - discount;
    }

    @Override
    public String getStrategyName() {
        return "Holiday Sale (20% off)";
    }
}

class LoyaltyPricing implements PricingStrategy {
    private int loyaltyYears;

    public LoyaltyPricing(int loyaltyYears) {
        this.loyaltyYears = loyaltyYears;
    }

    @Override
    public double calculatePrice(double originalPrice) {
        double discountPercent = Math.min(loyaltyYears * 5, 30); // Max 30% discount
        double discount = originalPrice * (discountPercent / 100);
        return originalPrice - discount;
    }

    @Override
    public String getStrategyName() {
        return "Loyalty Discount (" + Math.min(loyaltyYears * 5, 30) + "% off)";
    }
}

class PriceCalculator {
    private PricingStrategy strategy;

    public void setPricingStrategy(PricingStrategy strategy) {
        this.strategy = strategy;
    }

    public void displayPrice(String product, double originalPrice) {
        if (strategy == null) {
            throw new IllegalStateException("Pricing strategy not set");
        }
        
        double finalPrice = strategy.calculatePrice(originalPrice);
        System.out.printf("💰 %s: $%.2f → $%.2f (%s)%n", 
            product, originalPrice, finalPrice, strategy.getStrategyName());
    }
}

// ==================== DEMO ====================

public class StrategyPattern {
    public static void main(String[] args) {
        System.out.println("========== STRATEGY PATTERN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: SORTING ALGORITHMS =====
        System.out.println("===== EXAMPLE 1: SORTING ALGORITHMS =====\n");
        
        int[] data1 = {64, 34, 25, 12, 22, 11, 90};
        int[] data2 = {64, 34, 25, 12, 22, 11, 90};
        int[] data3 = {64, 34, 25, 12, 22, 11, 90};
        
        SortContext sortContext = new SortContext();
        
        System.out.println("Original: " + Arrays.toString(data1));
        sortContext.setStrategy(new QuickSort());
        sortContext.executeSort(data1);
        
        System.out.println("\nOriginal: " + Arrays.toString(data2));
        sortContext.setStrategy(new BubbleSort());
        sortContext.executeSort(data2);
        
        System.out.println("\nOriginal: " + Arrays.toString(data3));
        sortContext.setStrategy(new MergeSort());
        sortContext.executeSort(data3);

        // ===== EXAMPLE 2: PAYMENT METHODS =====
        System.out.println("\n\n===== EXAMPLE 2: PAYMENT METHODS =====\n");
        
        ShoppingCart cart = new ShoppingCart();
        cart.addItem("Laptop", 999.99);
        cart.addItem("Mouse", 29.99);
        cart.addItem("Keyboard", 79.99);
        
        System.out.println("Payment Method 1: Credit Card");
        cart.setPaymentStrategy(new CreditCardPayment("1234567890123456", "123"));
        cart.checkout();
        
        ShoppingCart cart2 = new ShoppingCart();
        cart2.addItem("Book", 19.99);
        cart2.addItem("Pen", 2.99);
        
        System.out.println("\nPayment Method 2: PayPal");
        cart2.setPaymentStrategy(new PayPalPayment("user@example.com"));
        cart2.checkout();

        // ===== EXAMPLE 3: COMPRESSION ALGORITHMS =====
        System.out.println("\n\n===== EXAMPLE 3: COMPRESSION ALGORITHMS =====\n");
        
        FileCompressor compressor = new FileCompressor();
        String filename = "document.txt";
        
        compressor.setStrategy(new ZipCompression());
        compressor.compressFile(filename);
        
        System.out.println();
        compressor.setStrategy(new RarCompression());
        compressor.compressFile(filename);
        
        System.out.println();
        compressor.setStrategy(new SevenZipCompression());
        compressor.compressFile(filename);

        // ===== EXAMPLE 4: ROUTE PLANNING =====
        System.out.println("\n\n===== EXAMPLE 4: ROUTE PLANNING =====\n");
        
        Navigator navigator = new Navigator();
        String start = "San Francisco";
        String end = "Los Angeles";
        
        navigator.setRouteStrategy(new FastestRoute());
        navigator.navigate(start, end);
        
        System.out.println();
        navigator.setRouteStrategy(new ShortestRoute());
        navigator.navigate(start, end);
        
        System.out.println();
        navigator.setRouteStrategy(new ScenicRoute());
        navigator.navigate(start, end);

        // ===== EXAMPLE 5: PRICING STRATEGIES =====
        System.out.println("\n\n===== EXAMPLE 5: PRICING STRATEGIES =====\n");
        
        PriceCalculator calculator = new PriceCalculator();
        double originalPrice = 100.00;
        
        calculator.setPricingStrategy(new RegularPricing());
        calculator.displayPrice("Product A", originalPrice);
        
        calculator.setPricingStrategy(new HolidayPricing());
        calculator.displayPrice("Product A", originalPrice);
        
        calculator.setPricingStrategy(new LoyaltyPricing(3));
        calculator.displayPrice("Product A", originalPrice);
        
        calculator.setPricingStrategy(new LoyaltyPricing(7));
        calculator.displayPrice("Product A", originalPrice);

        System.out.println("\n========== DEMO COMPLETE ==========");
    }
}
