import java.util.*;
import java.util.concurrent.*;

/**
 * INTERVIEW-READY AB Testing Platform
 * Time to complete: 45-60 minutes
 * Focus: Experiment management, user bucketing, metrics tracking
 */

// ==================== Variant ====================
class Variant {
    private final String variantId;
    private final String name;
    private final int allocationPercentage;

    public Variant(String variantId, String name, int allocationPercentage) {
        this.variantId = variantId;
        this.name = name;
        this.allocationPercentage = allocationPercentage;
    }

    public String getVariantId() { return variantId; }
    public String getName() { return name; }
    public int getAllocationPercentage() { return allocationPercentage; }

    @Override
    public String toString() {
        return name + " (" + allocationPercentage + "%)";
    }
}

// ==================== Experiment ====================
class Experiment {
    private final String experimentId;
    private final String name;
    private final List<Variant> variants;
    private final Map<String, Variant> userAssignments;
    private final Map<String, Integer> conversionCounts;
    private boolean isActive;

    public Experiment(String experimentId, String name) {
        this.experimentId = experimentId;
        this.name = name;
        this.variants = new ArrayList<>();
        this.userAssignments = new ConcurrentHashMap<>();
        this.conversionCounts = new ConcurrentHashMap<>();
        this.isActive = false;
    }

    public void addVariant(Variant variant) {
        variants.add(variant);
        conversionCounts.put(variant.getVariantId(), 0);
    }

    public void start() {
        isActive = true;
        System.out.println("✓ Started experiment: " + name);
    }

    public void stop() {
        isActive = false;
        System.out.println("✓ Stopped experiment: " + name);
    }

    public Variant assignUser(String userId) {
        if (!isActive) {
            return null;
        }

        // Check if user already assigned
        if (userAssignments.containsKey(userId)) {
            return userAssignments.get(userId);
        }

        // Assign based on percentage allocation
        Variant assigned = selectVariant(userId);
        userAssignments.put(userId, assigned);
        
        System.out.println("→ User " + userId + " assigned to: " + assigned.getName());
        return assigned;
    }

    private Variant selectVariant(String userId) {
        // Simple hash-based selection
        int hash = Math.abs(userId.hashCode());
        int percentage = hash % 100;
        
        int cumulative = 0;
        for (Variant variant : variants) {
            cumulative += variant.getAllocationPercentage();
            if (percentage < cumulative) {
                return variant;
            }
        }
        
        return variants.get(0); // Fallback
    }

    public void recordConversion(String userId) {
        Variant variant = userAssignments.get(userId);
        if (variant != null) {
            conversionCounts.merge(variant.getVariantId(), 1, Integer::sum);
            System.out.println("✓ Conversion recorded for " + variant.getName());
        }
    }

    public String getExperimentId() { return experimentId; }
    public boolean isActive() { return isActive; }

    public void displayResults() {
        System.out.println("\n=== Experiment Results: " + name + " ===");
        System.out.println("Status: " + (isActive ? "ACTIVE" : "STOPPED"));
        System.out.println("Total users: " + userAssignments.size());
        
        System.out.println("\nVariant Performance:");
        for (Variant variant : variants) {
            long users = userAssignments.values().stream()
                .filter(v -> v.getVariantId().equals(variant.getVariantId()))
                .count();
            
            int conversions = conversionCounts.get(variant.getVariantId());
            double conversionRate = users > 0 ? (conversions * 100.0 / users) : 0;
            
            System.out.println("  " + variant.getName() + ":");
            System.out.println("    Users: " + users);
            System.out.println("    Conversions: " + conversions);
            System.out.println("    Conversion Rate: " + String.format("%.2f%%", conversionRate));
        }
        System.out.println();
    }
}

// ==================== AB Testing Service ====================
class ABTestingService {
    private final Map<String, Experiment> experiments;
    private int experimentCounter;

    public ABTestingService() {
        this.experiments = new ConcurrentHashMap<>();
        this.experimentCounter = 1;
    }

    public Experiment createExperiment(String name, List<Variant> variants) {
        String experimentId = "EXP" + experimentCounter++;
        Experiment experiment = new Experiment(experimentId, name);
        
        for (Variant variant : variants) {
            experiment.addVariant(variant);
        }
        
        experiments.put(experimentId, experiment);
        System.out.println("✓ Created experiment: " + name);
        
        return experiment;
    }

    public Variant getVariant(String experimentId, String userId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null || !experiment.isActive()) {
            return null;
        }

        return experiment.assignUser(userId);
    }

    public void recordConversion(String experimentId, String userId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment != null) {
            experiment.recordConversion(userId);
        }
    }

    public void displayExperiment(String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment != null) {
            experiment.displayResults();
        }
    }
}

// ==================== Demo ====================
public class ABTestingPlatformInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== AB Testing Platform Demo ===\n");

        ABTestingService service = new ABTestingService();

        // Create experiment
        System.out.println("--- Creating Experiment ---");
        List<Variant> variants = Arrays.asList(
            new Variant("V1", "Control", 50),
            new Variant("V2", "Variant A", 50)
        );

        Experiment exp = service.createExperiment("Button Color Test", variants);
        exp.start();

        // Assign users to variants
        System.out.println("\n--- Assigning Users ---");
        for (int i = 1; i <= 100; i++) {
            String userId = "user" + i;
            service.getVariant(exp.getExperimentId(), userId);
        }

        // Simulate conversions
        System.out.println("\n--- Recording Conversions ---");
        Random random = new Random();
        for (int i = 1; i <= 100; i++) {
            String userId = "user" + i;
            // Control: 10% conversion, Variant A: 15% conversion (simulated)
            Variant variant = service.getVariant(exp.getExperimentId(), userId);
            
            if (variant.getName().equals("Control")) {
                if (random.nextDouble() < 0.10) {
                    service.recordConversion(exp.getExperimentId(), userId);
                }
            } else {
                if (random.nextDouble() < 0.15) {
                    service.recordConversion(exp.getExperimentId(), userId);
                }
            }
        }

        // Display results
        service.displayExperiment(exp.getExperimentId());

        System.out.println("✅ Demo complete!");
    }
}
