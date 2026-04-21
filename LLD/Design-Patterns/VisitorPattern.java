import java.util.*;

// ==================== VISITOR PATTERN ====================
// Definition: Lets you define a new operation without changing the classes of the
// elements on which it operates. Separates algorithm from object structure.
//
// REAL System Design Interview Examples:
// 1. Bill/Invoice Calculator - Different pricing for different item types
// 2. File System Operations - Size calc, search, export on different node types
// 3. AST/Query Processor - Different operations on parsed tree nodes
// 4. Permission Checker - Different auth rules for different resources
// 5. Metric Collector - Gather different stats from different system components
//
// Interview Use Cases:
// - Design Pricing Engine: Different tax/discount rules per product type
// - Design File System: Multiple operations (search, size, export) on files
// - Design Query Optimizer: Transform/analyze SQL AST nodes
// - Design Infrastructure Monitor: Collect metrics from different resource types
// - Design Permission System: Check access rules per resource type

// ==================== EXAMPLE 1: PRICING/INVOICE SYSTEM ====================
// Used in: E-commerce, billing systems, tax calculators
// Interview Question: Design a pricing/billing system

interface BillingVisitor {
    double visit(PhysicalProduct product);
    double visit(DigitalProduct product);
    double visit(SubscriptionProduct product);
    double visit(ServiceProduct service);
}

interface BillableItem {
    double accept(BillingVisitor visitor);
    String getName();
    double getBasePrice();
}

class PhysicalProduct implements BillableItem {
    private String name;
    private double basePrice;
    private double weight; // kg

    public PhysicalProduct(String name, double basePrice, double weight) {
        this.name = name;
        this.basePrice = basePrice;
        this.weight = weight;
    }

    @Override
    public double accept(BillingVisitor visitor) { return visitor.visit(this); }
    @Override
    public String getName() { return name; }
    @Override
    public double getBasePrice() { return basePrice; }
    public double getWeight() { return weight; }
}

class DigitalProduct implements BillableItem {
    private String name;
    private double basePrice;
    private String region;

    public DigitalProduct(String name, double basePrice, String region) {
        this.name = name;
        this.basePrice = basePrice;
        this.region = region;
    }

    @Override
    public double accept(BillingVisitor visitor) { return visitor.visit(this); }
    @Override
    public String getName() { return name; }
    @Override
    public double getBasePrice() { return basePrice; }
    public String getRegion() { return region; }
}

class SubscriptionProduct implements BillableItem {
    private String name;
    private double monthlyPrice;
    private int months;

    public SubscriptionProduct(String name, double monthlyPrice, int months) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.months = months;
    }

    @Override
    public double accept(BillingVisitor visitor) { return visitor.visit(this); }
    @Override
    public String getName() { return name; }
    @Override
    public double getBasePrice() { return monthlyPrice * months; }
    public double getMonthlyPrice() { return monthlyPrice; }
    public int getMonths() { return months; }
}

class ServiceProduct implements BillableItem {
    private String name;
    private double hourlyRate;
    private int hours;

    public ServiceProduct(String name, double hourlyRate, int hours) {
        this.name = name;
        this.hourlyRate = hourlyRate;
        this.hours = hours;
    }

    @Override
    public double accept(BillingVisitor visitor) { return visitor.visit(this); }
    @Override
    public String getName() { return name; }
    @Override
    public double getBasePrice() { return hourlyRate * hours; }
    public double getHourlyRate() { return hourlyRate; }
    public int getHours() { return hours; }
}

// --- Visitors: New operations without modifying item classes ---

class TaxCalculator implements BillingVisitor {
    @Override
    public double visit(PhysicalProduct product) {
        double tax = product.getBasePrice() * 0.08; // 8% sales tax
        System.out.printf("   💰 Tax on '%s' (physical): $%.2f × 8%% = $%.2f%n",
            product.getName(), product.getBasePrice(), tax);
        return tax;
    }

    @Override
    public double visit(DigitalProduct product) {
        double rate = "EU".equals(product.getRegion()) ? 0.20 : 0.05; // VAT varies by region
        double tax = product.getBasePrice() * rate;
        System.out.printf("   💰 Tax on '%s' (digital, %s): $%.2f × %.0f%% = $%.2f%n",
            product.getName(), product.getRegion(), product.getBasePrice(), rate * 100, tax);
        return tax;
    }

    @Override
    public double visit(SubscriptionProduct product) {
        double tax = product.getBasePrice() * 0.10; // 10% service tax
        System.out.printf("   💰 Tax on '%s' (subscription, %d mo): $%.2f × 10%% = $%.2f%n",
            product.getName(), product.getMonths(), product.getBasePrice(), tax);
        return tax;
    }

    @Override
    public double visit(ServiceProduct service) {
        double tax = service.getBasePrice() * 0.15; // 15% service tax
        System.out.printf("   💰 Tax on '%s' (service, %dh): $%.2f × 15%% = $%.2f%n",
            service.getName(), service.getHours(), service.getBasePrice(), tax);
        return tax;
    }
}

class ShippingCostCalculator implements BillingVisitor {
    @Override
    public double visit(PhysicalProduct product) {
        double cost = product.getWeight() * 2.50; // $2.50 per kg
        System.out.printf("   🚚 Shipping '%s': %.1fkg × $2.50 = $%.2f%n",
            product.getName(), product.getWeight(), cost);
        return cost;
    }

    @Override
    public double visit(DigitalProduct product) {
        System.out.printf("   🚚 Shipping '%s': $0.00 (digital delivery)%n", product.getName());
        return 0.0;
    }

    @Override
    public double visit(SubscriptionProduct product) {
        System.out.printf("   🚚 Shipping '%s': $0.00 (subscription)%n", product.getName());
        return 0.0;
    }

    @Override
    public double visit(ServiceProduct service) {
        System.out.printf("   🚚 Shipping '%s': $0.00 (service)%n", service.getName());
        return 0.0;
    }
}

class DiscountCalculator implements BillingVisitor {
    @Override
    public double visit(PhysicalProduct product) {
        double discount = product.getBasePrice() > 100 ? product.getBasePrice() * 0.10 : 0;
        System.out.printf("   🏷️  Discount '%s': %s%n", product.getName(),
            discount > 0 ? String.format("-$%.2f (10%% bulk)", discount) : "none");
        return discount;
    }

    @Override
    public double visit(DigitalProduct product) {
        System.out.printf("   🏷️  Discount '%s': none (digital)%n", product.getName());
        return 0.0;
    }

    @Override
    public double visit(SubscriptionProduct product) {
        double discount = product.getMonths() >= 12 ? product.getBasePrice() * 0.20 : 0;
        System.out.printf("   🏷️  Discount '%s': %s%n", product.getName(),
            discount > 0 ? String.format("-$%.2f (annual 20%%)", discount) : "none");
        return discount;
    }

    @Override
    public double visit(ServiceProduct service) {
        double discount = service.getHours() >= 20 ? service.getBasePrice() * 0.15 : 0;
        System.out.printf("   🏷️  Discount '%s': %s%n", service.getName(),
            discount > 0 ? String.format("-$%.2f (bulk hours 15%%)", discount) : "none");
        return discount;
    }
}

// ==================== EXAMPLE 2: INFRASTRUCTURE MONITOR ====================
// Used in: Prometheus, Datadog, CloudWatch
// Interview Question: Design an infrastructure monitoring system

interface InfraVisitor {
    void visit(ServerNode server);
    void visit(DatabaseNode database);
    void visit(CacheNode cache);
    void visit(LoadBalancerNode lb);
}

interface InfraComponent {
    void accept(InfraVisitor visitor);
    String getId();
}

class ServerNode implements InfraComponent {
    private String id;
    private double cpuUsage;
    private double memoryUsage;
    private int requestsPerSec;

    public ServerNode(String id, double cpu, double memory, int rps) {
        this.id = id; this.cpuUsage = cpu; this.memoryUsage = memory; this.requestsPerSec = rps;
    }

    @Override
    public void accept(InfraVisitor visitor) { visitor.visit(this); }
    @Override
    public String getId() { return id; }
    public double getCpuUsage() { return cpuUsage; }
    public double getMemoryUsage() { return memoryUsage; }
    public int getRequestsPerSec() { return requestsPerSec; }
}

class DatabaseNode implements InfraComponent {
    private String id;
    private int connections;
    private double queryLatencyMs;
    private long diskUsageGB;

    public DatabaseNode(String id, int connections, double latency, long diskGB) {
        this.id = id; this.connections = connections; this.queryLatencyMs = latency; this.diskUsageGB = diskGB;
    }

    @Override
    public void accept(InfraVisitor visitor) { visitor.visit(this); }
    @Override
    public String getId() { return id; }
    public int getConnections() { return connections; }
    public double getQueryLatencyMs() { return queryLatencyMs; }
    public long getDiskUsageGB() { return diskUsageGB; }
}

class CacheNode implements InfraComponent {
    private String id;
    private double hitRate;
    private long memoryUsedMB;
    private int evictions;

    public CacheNode(String id, double hitRate, long memMB, int evictions) {
        this.id = id; this.hitRate = hitRate; this.memoryUsedMB = memMB; this.evictions = evictions;
    }

    @Override
    public void accept(InfraVisitor visitor) { visitor.visit(this); }
    @Override
    public String getId() { return id; }
    public double getHitRate() { return hitRate; }
    public long getMemoryUsedMB() { return memoryUsedMB; }
    public int getEvictions() { return evictions; }
}

class LoadBalancerNode implements InfraComponent {
    private String id;
    private int activeConnections;
    private int healthyTargets;
    private int totalTargets;

    public LoadBalancerNode(String id, int active, int healthy, int total) {
        this.id = id; this.activeConnections = active; this.healthyTargets = healthy; this.totalTargets = total;
    }

    @Override
    public void accept(InfraVisitor visitor) { visitor.visit(this); }
    @Override
    public String getId() { return id; }
    public int getActiveConnections() { return activeConnections; }
    public int getHealthyTargets() { return healthyTargets; }
    public int getTotalTargets() { return totalTargets; }
}

class HealthCheckVisitor implements InfraVisitor {
    private List<String> issues = new ArrayList<>();

    @Override
    public void visit(ServerNode server) {
        String status = server.getCpuUsage() > 80 ? "⚠️  HIGH CPU" : "✅ OK";
        System.out.printf("   🖥️  Server %s: CPU=%.0f%%, Mem=%.0f%%, RPS=%d → %s%n",
            server.getId(), server.getCpuUsage(), server.getMemoryUsage(), server.getRequestsPerSec(), status);
        if (server.getCpuUsage() > 80) issues.add(server.getId() + ": High CPU");
    }

    @Override
    public void visit(DatabaseNode db) {
        String status = db.getQueryLatencyMs() > 100 ? "⚠️  HIGH LATENCY" : "✅ OK";
        System.out.printf("   🗃️  Database %s: Conns=%d, Latency=%.0fms, Disk=%dGB → %s%n",
            db.getId(), db.getConnections(), db.getQueryLatencyMs(), db.getDiskUsageGB(), status);
        if (db.getQueryLatencyMs() > 100) issues.add(db.getId() + ": High latency");
    }

    @Override
    public void visit(CacheNode cache) {
        String status = cache.getHitRate() < 0.8 ? "⚠️  LOW HIT RATE" : "✅ OK";
        System.out.printf("   ⚡ Cache %s: HitRate=%.0f%%, Mem=%dMB, Evictions=%d → %s%n",
            cache.getId(), cache.getHitRate() * 100, cache.getMemoryUsedMB(), cache.getEvictions(), status);
        if (cache.getHitRate() < 0.8) issues.add(cache.getId() + ": Low hit rate");
    }

    @Override
    public void visit(LoadBalancerNode lb) {
        String status = lb.getHealthyTargets() < lb.getTotalTargets() ? "⚠️  UNHEALTHY TARGETS" : "✅ OK";
        System.out.printf("   ⚖️  LB %s: Active=%d, Healthy=%d/%d → %s%n",
            lb.getId(), lb.getActiveConnections(), lb.getHealthyTargets(), lb.getTotalTargets(), status);
        if (lb.getHealthyTargets() < lb.getTotalTargets()) issues.add(lb.getId() + ": Unhealthy targets");
    }

    public List<String> getIssues() { return issues; }
}

// ==================== DEMO ====================

public class VisitorPattern {
    public static void main(String[] args) {
        System.out.println("========== VISITOR PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: PRICING/BILLING =====
        System.out.println("===== EXAMPLE 1: PRICING/BILLING SYSTEM =====\n");

        List<BillableItem> cart = Arrays.asList(
            new PhysicalProduct("Laptop", 999.99, 2.5),
            new DigitalProduct("eBook", 19.99, "EU"),
            new SubscriptionProduct("Pro Plan", 29.99, 12),
            new ServiceProduct("Consulting", 150.0, 20)
        );

        System.out.println("--- Tax Calculation ---");
        TaxCalculator taxCalc = new TaxCalculator();
        double totalTax = 0;
        for (BillableItem item : cart) {
            totalTax += item.accept(taxCalc);
        }
        System.out.printf("   📊 Total Tax: $%.2f%n", totalTax);

        System.out.println("\n--- Shipping Calculation ---");
        ShippingCostCalculator shippingCalc = new ShippingCostCalculator();
        double totalShipping = 0;
        for (BillableItem item : cart) {
            totalShipping += item.accept(shippingCalc);
        }
        System.out.printf("   📊 Total Shipping: $%.2f%n", totalShipping);

        System.out.println("\n--- Discount Calculation ---");
        DiscountCalculator discountCalc = new DiscountCalculator();
        double totalDiscount = 0;
        for (BillableItem item : cart) {
            totalDiscount += item.accept(discountCalc);
        }
        System.out.printf("   📊 Total Discount: -$%.2f%n", totalDiscount);

        double subtotal = cart.stream().mapToDouble(BillableItem::getBasePrice).sum();
        double grandTotal = subtotal + totalTax + totalShipping - totalDiscount;
        System.out.printf("\n   🧾 INVOICE: Subtotal=$%.2f + Tax=$%.2f + Ship=$%.2f - Disc=$%.2f = $%.2f%n",
            subtotal, totalTax, totalShipping, totalDiscount, grandTotal);

        // ===== EXAMPLE 2: INFRASTRUCTURE MONITOR =====
        System.out.println("\n\n===== EXAMPLE 2: INFRASTRUCTURE HEALTH CHECK =====\n");

        List<InfraComponent> infrastructure = Arrays.asList(
            new ServerNode("web-01", 85.0, 60.0, 5000),
            new ServerNode("web-02", 45.0, 50.0, 3000),
            new DatabaseNode("db-primary", 100, 45.0, 500),
            new DatabaseNode("db-replica", 25, 120.0, 480),
            new CacheNode("redis-01", 0.95, 2048, 50),
            new CacheNode("redis-02", 0.72, 4096, 500),
            new LoadBalancerNode("alb-01", 8000, 2, 2),
            new LoadBalancerNode("nlb-01", 15000, 3, 4)
        );

        HealthCheckVisitor healthCheck = new HealthCheckVisitor();
        for (InfraComponent component : infrastructure) {
            component.accept(healthCheck);
        }

        List<String> issues = healthCheck.getIssues();
        System.out.printf("\n   🚨 Issues found: %d%n", issues.size());
        for (String issue : issues) {
            System.out.printf("      ⚠️  %s%n", issue);
        }

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Pricing/billing engines (tax, shipping, discount)");
        System.out.println("• Infrastructure monitoring (health checks, metrics)");
        System.out.println("• Query optimizers (AST transformations)");
        System.out.println("• Permission systems (access control per resource type)");
    }
}
