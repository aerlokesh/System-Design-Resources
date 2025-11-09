import java.util.*;
import java.time.*;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

// ==================== Enums ====================

enum ExperimentStatus {
    DRAFT,          // Not started, can be modified
    SCHEDULED,      // Scheduled to start
    RUNNING,        // Active, collecting data
    PAUSED,         // Temporarily stopped
    COMPLETED,      // Finished, analyzing results
    ARCHIVED        // Historical, read-only
}

enum ExperimentType {
    AB_TEST,        // 2 variants (A vs B)
    MULTIVARIATE,   // Multiple variants (A vs B vs C)
    FEATURE_FLAG,   // Gradual rollout
    MULTI_ARMED_BANDIT  // Dynamic allocation
}

enum EventType {
    EXPOSURE,       // User saw variant
    CLICK,          // User clicked element
    CONVERSION,     // User completed goal
    PAGE_VIEW,      // User viewed page
    ADD_TO_CART,    // Shopping cart action
    PURCHASE,       // Completed purchase
    CUSTOM          // Custom event
}

enum AssignmentMethod {
    HASH,           // Deterministic hash-based (default)
    MANUAL,         // Manually assigned
    OVERRIDE,       // QA/testing override
    RANDOM,         // Pure random (no consistency)
    WEIGHTED        // Weighted random (multi-armed bandit)
}

enum MetricType {
    CONVERSION_RATE,    // Binary success/failure
    REVENUE,            // Monetary value
    TIME_SPENT,         // Duration
    COUNT,              // Number of actions
    RATE                // Events per unit time
}

enum TargetingOperator {
    EQUALS,
    NOT_EQUALS,
    IN,
    NOT_IN,
    GREATER_THAN,
    LESS_THAN,
    CONTAINS,
    REGEX_MATCH
}

// ==================== Value Objects ====================

/**
 * Immutable user context for targeting
 * Contains user attributes for experiment eligibility
 */
class UserContext {
    private final String userId;
    private final Map<String, String> attributes;
    private final LocalDateTime requestTime;
    
    public UserContext(String userId, Map<String, String> attributes) {
        this.userId = userId;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
        this.requestTime = LocalDateTime.now();
    }
    
    public String getUserId() { return userId; }
    public Map<String, String> getAttributes() { return attributes; }
    public String getAttribute(String key) { return attributes.get(key); }
    public LocalDateTime getRequestTime() { return requestTime; }
    
    @Override
    public String toString() {
        return String.format("UserContext[userId=%s, attrs=%s]", userId, attributes);
    }
}

/**
 * Immutable assignment result
 * Returned to clients with variant information
 */
class AssignmentResult {
    private final String experimentId;
    private final String variantId;
    private final String variantKey;
    private final Map<String, Object> config;
    private final boolean isNewAssignment;
    private final LocalDateTime timestamp;
    
    public AssignmentResult(String experimentId, String variantId, String variantKey,
                           Map<String, Object> config, boolean isNewAssignment) {
        this.experimentId = experimentId;
        this.variantId = variantId;
        this.variantKey = variantKey;
        this.config = Collections.unmodifiableMap(new HashMap<>(config));
        this.isNewAssignment = isNewAssignment;
        this.timestamp = LocalDateTime.now();
    }
    
    public String getExperimentId() { return experimentId; }
    public String getVariantId() { return variantId; }
    public String getVariantKey() { return variantKey; }
    public Map<String, Object> getConfig() { return config; }
    public boolean isNewAssignment() { return isNewAssignment; }
    public LocalDateTime getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("Assignment[exp=%s, variant=%s, new=%s]",
            experimentId, variantKey, isNewAssignment);
    }
}

// ==================== Variant ====================

class Variant {
    private final String variantId;
    private final String experimentId;
    private final String key;
    private final String name;
    private final String description;
    private int trafficPercentage;
    private final boolean isControl;
    private final Map<String, Object> config;
    private final LocalDateTime createdAt;
    
    // For weighted allocation (multi-armed bandit)
    private double weight;  // Dynamic weight based on performance

    public Variant(String variantId, String experimentId, String key, String name,
                   int trafficPercentage, boolean isControl) {
        this.variantId = variantId;
        this.experimentId = experimentId;
        this.key = key;
        this.name = name;
        this.description = "";
        this.trafficPercentage = trafficPercentage;
        this.isControl = isControl;
        this.config = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.weight = trafficPercentage / 100.0;  // Initial weight = allocation
    }

    // Getters
    public String getVariantId() { return variantId; }
    public String getExperimentId() { return experimentId; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public int getTrafficPercentage() { return trafficPercentage; }
    public boolean isControl() { return isControl; }
    public Map<String, Object> getConfig() { return new HashMap<>(config); }
    public double getWeight() { return weight; }

    // Configuration management
    public void setConfig(String key, Object value) {
        config.put(key, value);
    }

    public Object getConfigValue(String key) {
        return config.get(key);
    }
    
    // Weight management (for multi-armed bandit)
    public void setWeight(double weight) {
        this.weight = Math.max(0.0, Math.min(1.0, weight));
    }

    // Check if hash value falls into this variant's allocation range
    public boolean matchesAllocation(int hashValue, int rangeStart) {
        int rangeEnd = rangeStart + trafficPercentage;
        return hashValue >= rangeStart && hashValue < rangeEnd;
    }

    @Override
    public String toString() {
        return String.format("Variant[%s: %s, %d%%, control=%s]", 
            key, name, trafficPercentage, isControl);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Variant)) return false;
        Variant variant = (Variant) o;
        return variantId.equals(variant.variantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(variantId);
    }
}

// ==================== Targeting Rule ====================

class TargetingRule {
    private final String ruleId;
    private final String dimension;
    private final TargetingOperator operator;
    private final Set<String> values;
    private final int priority;  // Higher priority rules evaluated first

    public TargetingRule(String ruleId, String dimension, TargetingOperator operator, 
                        Set<String> values, int priority) {
        this.ruleId = ruleId;
        this.dimension = dimension;
        this.operator = operator;
        this.values = new HashSet<>(values);
        this.priority = priority;
    }
    
    public TargetingRule(String ruleId, String dimension, TargetingOperator operator, 
                        Set<String> values) {
        this(ruleId, dimension, operator, values, 0);
    }

    public int getPriority() { return priority; }

    /**
     * Evaluate if user context matches this rule
     * 
     * Time Complexity: O(1) for most operators, O(n) for IN/NOT_IN where n = values size
     * Space Complexity: O(1)
     */
    public boolean evaluate(UserContext userContext) {
        String userValue = userContext.getAttribute(dimension);
        if (userValue == null) {
            return operator == TargetingOperator.NOT_EQUALS || operator == TargetingOperator.NOT_IN;
        }

        switch (operator) {
            case EQUALS:
                return values.contains(userValue);
            case NOT_EQUALS:
                return !values.contains(userValue);
            case IN:
                return values.contains(userValue);
            case NOT_IN:
                return !values.contains(userValue);
            case CONTAINS:
                return values.stream().anyMatch(userValue::contains);
            case GREATER_THAN:
                try {
                    double userNum = Double.parseDouble(userValue);
                    double threshold = Double.parseDouble(values.iterator().next());
                    return userNum > threshold;
                } catch (Exception e) {
                    return false;
                }
            case LESS_THAN:
                try {
                    double userNum = Double.parseDouble(userValue);
                    double threshold = Double.parseDouble(values.iterator().next());
                    return userNum < threshold;
                } catch (Exception e) {
                    return false;
                }
            case REGEX_MATCH:
                String pattern = values.iterator().next();
                return userValue.matches(pattern);
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return String.format("Rule[%s %s %s, priority=%d]", 
            dimension, operator, values, priority);
    }
}

// ==================== Metric Definition ====================

class MetricDefinition {
    private final String metricId;
    private final String name;
    private final String description;
    private final MetricType type;
    private final String eventName;
    private final Map<String, String> filters;

    public MetricDefinition(String metricId, String name, MetricType type, String eventName) {
        this.metricId = metricId;
        this.name = name;
        this.description = "";
        this.type = type;
        this.eventName = eventName;
        this.filters = new HashMap<>();
    }

    public String getMetricId() { return metricId; }
    public String getName() { return name; }
    public MetricType getType() { return type; }
    public String getEventName() { return eventName; }
    public Map<String, String> getFilters() { return new HashMap<>(filters); }
    
    public void addFilter(String key, String value) {
        filters.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("Metric[%s: %s, type=%s]", name, eventName, type);
    }
}

// ==================== Experiment ====================

class Experiment {
    private final String experimentId;
    private final String key;
    private final String name;
    private String description;
    private ExperimentStatus status;
    private final ExperimentType type;
    
    // Dates
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Variants
    private final List<Variant> variants;
    private final String salt;
    
    // Targeting
    private final List<TargetingRule> targetingRules;
    private final Set<String> excludedUserIds;
    private final Set<String> includedUserIds;  // Whitelist for testing
    
    // Metrics
    private MetricDefinition primaryMetric;
    private final List<MetricDefinition> secondaryMetrics;
    
    // Metadata
    private String createdBy;
    private String organizationId;
    private String hypothesis;
    
    // Multi-armed bandit specific
    private int explorationPeriod;  // Number of samples before exploitation
    private double explorationRate;  // % of traffic for exploration

    public Experiment(String experimentId, String key, String name, ExperimentType type) {
        this.experimentId = experimentId;
        this.key = key;
        this.name = name;
        this.type = type;
        this.status = ExperimentStatus.DRAFT;
        this.description = "";
        this.variants = new ArrayList<>();
        this.targetingRules = new ArrayList<>();
        this.excludedUserIds = ConcurrentHashMap.newKeySet();
        this.includedUserIds = ConcurrentHashMap.newKeySet();
        this.secondaryMetrics = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.salt = UUID.randomUUID().toString();
        this.explorationPeriod = 1000;
        this.explorationRate = 0.1;  // 10% for exploration
    }

    // Getters
    public String getExperimentId() { return experimentId; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ExperimentStatus getStatus() { return status; }
    public ExperimentType getType() { return type; }
    public List<Variant> getVariants() { return new ArrayList<>(variants); }
    public String getSalt() { return salt; }
    public List<TargetingRule> getTargetingRules() { return new ArrayList<>(targetingRules); }
    public MetricDefinition getPrimaryMetric() { return primaryMetric; }
    public List<MetricDefinition> getSecondaryMetrics() { return new ArrayList<>(secondaryMetrics); }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public String getHypothesis() { return hypothesis; }

    // Setters
    public void setDescription(String description) { 
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setHypothesis(String hypothesis) {
        this.hypothesis = hypothesis;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setStartDate(LocalDateTime startDate) { 
        this.startDate = startDate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setEndDate(LocalDateTime endDate) { 
        this.endDate = endDate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setPrimaryMetric(MetricDefinition metric) {
        this.primaryMetric = metric;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void setCreatedBy(String userId) {
        this.createdBy = userId;
    }
    
    public void setOrganizationId(String orgId) {
        this.organizationId = orgId;
    }

    // Variant management
    public void addVariant(Variant variant) {
        variants.add(variant);
        this.updatedAt = LocalDateTime.now();
    }
    
    public Variant getVariantById(String variantId) {
        return variants.stream()
            .filter(v -> v.getVariantId().equals(variantId))
            .findFirst()
            .orElse(null);
    }
    
    public Variant getVariantByKey(String key) {
        return variants.stream()
            .filter(v -> v.getKey().equals(key))
            .findFirst()
            .orElse(null);
    }

    // Targeting
    public void addTargetingRule(TargetingRule rule) {
        targetingRules.add(rule);
        // Sort by priority (higher first)
        targetingRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        this.updatedAt = LocalDateTime.now();
    }
    
    public void addSecondaryMetric(MetricDefinition metric) {
        secondaryMetrics.add(metric);
        this.updatedAt = LocalDateTime.now();
    }

    public void excludeUser(String userId) {
        excludedUserIds.add(userId);
        includedUserIds.remove(userId);  // Can't be both
        this.updatedAt = LocalDateTime.now();
    }
    
    public void includeUser(String userId) {
        includedUserIds.add(userId);
        excludedUserIds.remove(userId);  // Can't be both
        this.updatedAt = LocalDateTime.now();
    }

    // Status management with validation
    public void start() throws IllegalStateException {
        if (status == ExperimentStatus.DRAFT || status == ExperimentStatus.SCHEDULED) {
            validateConfiguration();
            this.status = ExperimentStatus.RUNNING;
            if (this.startDate == null) {
                this.startDate = LocalDateTime.now();
            }
            this.updatedAt = LocalDateTime.now();
            System.out.println("✅ Experiment started: " + this);
        } else {
            throw new IllegalStateException("Cannot start experiment in state: " + status);
        }
    }

    public void pause() {
        if (status == ExperimentStatus.RUNNING) {
            this.status = ExperimentStatus.PAUSED;
            this.updatedAt = LocalDateTime.now();
            System.out.println("⏸️  Experiment paused: " + name);
        }
    }

    public void resume() {
        if (status == ExperimentStatus.PAUSED) {
            this.status = ExperimentStatus.RUNNING;
            this.updatedAt = LocalDateTime.now();
            System.out.println("▶️  Experiment resumed: " + name);
        }
    }

    public void complete() {
        if (status == ExperimentStatus.RUNNING || status == ExperimentStatus.PAUSED) {
            this.status = ExperimentStatus.COMPLETED;
            this.endDate = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            System.out.println("✅ Experiment completed: " + name);
        }
    }

    /**
     * Comprehensive validation of experiment configuration
     * Ensures experiment can be started safely
     */
    public void validateConfiguration() throws IllegalStateException {
        // Check variants exist
        if (variants.isEmpty()) {
            throw new IllegalStateException("Experiment must have at least one variant");
        }
        
        if (variants.size() < 2 && type == ExperimentType.AB_TEST) {
            throw new IllegalStateException("AB_TEST must have at least 2 variants");
        }
        
        // Check traffic allocation
        int totalAllocation = variants.stream()
            .mapToInt(Variant::getTrafficPercentage)
            .sum();
        
        if (totalAllocation != 100) {
            throw new IllegalStateException(
                String.format("Traffic allocation must sum to 100%%, got: %d%%", totalAllocation)
            );
        }
        
        // Check control variant exists
        boolean hasControl = variants.stream().anyMatch(Variant::isControl);
        if (!hasControl && type == ExperimentType.AB_TEST) {
            throw new IllegalStateException("AB_TEST must have a control variant");
        }
        
        // Check primary metric defined
        if (primaryMetric == null) {
            throw new IllegalStateException("Primary metric must be defined");
        }
        
        System.out.println("✅ Experiment configuration validated");
    }

    // Check if experiment is active
    public boolean isActive() {
        if (status != ExperimentStatus.RUNNING) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Check start date
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }
        
        // Check end date
        if (endDate != null && now.isAfter(endDate)) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if user is eligible for experiment
     * Applies targeting rules, whitelists, blacklists
     * 
     * Time Complexity: O(r) where r = number of targeting rules
     */
    public boolean isEligible(UserContext userContext) {
        String userId = userContext.getUserId();
        
        // Not active
        if (!isActive()) {
            return false;
        }
        
        // Whitelist check (if whitelist exists, user must be in it)
        if (!includedUserIds.isEmpty() && !includedUserIds.contains(userId)) {
            return false;
        }
        
        // Blacklist check (explicitly excluded)
        if (excludedUserIds.contains(userId)) {
            return false;
        }
        
        // Check targeting rules (ALL must match = AND logic)
        for (TargetingRule rule : targetingRules) {
            if (!rule.evaluate(userContext)) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public String toString() {
        return String.format("Experiment[id=%s, key=%s, status=%s, variants=%d]",
            experimentId, key, status, variants.size());
    }
}

// ==================== User Assignment ====================

class UserAssignment {
    private final String assignmentId;
    private final String userId;
    private final String experimentId;
    private final String variantId;
    private final LocalDateTime assignedAt;
    private final AssignmentMethod method;
    private final int bucketNumber;
    private final boolean isSticky;
    private int exposureCount;
    private LocalDateTime lastExposure;

    public UserAssignment(String assignmentId, String userId, String experimentId,
                         String variantId, AssignmentMethod method, int bucketNumber) {
        this.assignmentId = assignmentId;
        this.userId = userId;
        this.experimentId = experimentId;
        this.variantId = variantId;
        this.assignedAt = LocalDateTime.now();
        this.method = method;
        this.bucketNumber = bucketNumber;
        this.isSticky = true;
        this.exposureCount = 0;
        this.lastExposure = null;
    }

    // Getters
    public String getAssignmentId() { return assignmentId; }
    public String getUserId() { return userId; }
    public String getExperimentId() { return experimentId; }
    public String getVariantId() { return variantId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public AssignmentMethod getMethod() { return method; }
    public int getBucketNumber() { return bucketNumber; }
    public boolean isSticky() { return isSticky; }
    public int getExposureCount() { return exposureCount; }
    public LocalDateTime getLastExposure() { return lastExposure; }

    public synchronized void incrementExposure() {
        this.exposureCount++;
        this.lastExposure = LocalDateTime.now();
    }
    
    // Create composite key for storage
    public String getCompositeKey() {
        return userId + ":" + experimentId;
    }

    @Override
    public String toString() {
        return String.format("Assignment[user=%s, exp=%s, variant=%s, bucket=%d, exposures=%d]",
            userId, experimentId, variantId, bucketNumber, exposureCount);
    }
}

// ==================== Event ====================

class Event {
    private final String eventId;
    private final String userId;
    private final String experimentId;
    private final String variantId;
    private final EventType eventType;
    private final String eventName;
    private final Map<String, Object> properties;
    private final LocalDateTime timestamp;
    private final String sessionId;

    public Event(String eventId, String userId, String experimentId, String variantId,
                EventType eventType, String eventName, String sessionId) {
        this.eventId = eventId;
        this.userId = userId;
        this.experimentId = experimentId;
        this.variantId = variantId;
        this.eventType = eventType;
        this.eventName = eventName;
        this.sessionId = sessionId;
        this.properties = new ConcurrentHashMap<>();
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public String getEventId() { return eventId; }
    public String getUserId() { return userId; }
    public String getExperimentId() { return experimentId; }
    public String getVariantId() { return variantId; }
    public EventType getEventType() { return eventType; }
    public String getEventName() { return eventName; }
    public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }

    // Properties
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    @Override
    public String toString() {
        return String.format("Event[%s: %s by %s, variant=%s, time=%s]",
            eventType, eventName, userId, variantId, timestamp);
    }
}

// ==================== Experiment Results ====================

class ExperimentResults {
    private final String experimentId;
    private final Map<String, VariantResults> variantResults;
    private double pValue;
    private String winningVariantId;
    private String recommendation;
    private final LocalDateTime computedAt;
    private int totalSampleSize;
    private boolean hasMinimumSampleSize;

    public ExperimentResults(String experimentId) {
        this.experimentId = experimentId;
        this.variantResults = new HashMap<>();
        this.computedAt = LocalDateTime.now();
        this.totalSampleSize = 0;
        this.hasMinimumSampleSize = false;
    }

    public void addVariantResults(String variantId, VariantResults results) {
        variantResults.put(variantId, results);
        totalSampleSize += results.getExposures();
    }

    public void setPValue(double pValue) { this.pValue = pValue; }
    public void setWinningVariantId(String id) { this.winningVariantId = id; }
    public void setRecommendation(String rec) { this.recommendation = rec; }
    public void setHasMinimumSampleSize(boolean has) { this.hasMinimumSampleSize = has; }

    public Map<String, VariantResults> getVariantResults() { return new HashMap<>(variantResults); }
    public double getPValue() { return pValue; }
    public String getWinningVariantId() { return winningVariantId; }
    public String getRecommendation() { return recommendation; }
    public int getTotalSampleSize() { return totalSampleSize; }
    public boolean hasMinimumSampleSize() { return hasMinimumSampleSize; }
    public boolean isStatisticallySignificant() { return pValue < 0.05; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n┌").append("─".repeat(58)).append("┐\n");
        sb.append(String.format("│ %-56s │\n", "EXPERIMENT RESULTS"));
        sb.append("├").append("─".repeat(58)).append("┤\n");
        
        for (Map.Entry<String, VariantResults> entry : variantResults.entrySet()) {
            VariantResults vr = entry.getValue();
            sb.append(String.format("│ Variant %-10s │ %8d exp │ %6d conv │ %5.2f%% │\n",
                entry.getKey().substring(0, Math.min(10, entry.getKey().length())),
                vr.getExposures(),
                vr.getConversions(),
                vr.getConversionRate() * 100));
            
            if (vr.getLift() != null) {
                sb.append(String.format("│   Lift: %+.1f%%  │ CI: [%.2f%%, %.2f%%]                │\n",
                    vr.getLift(),
                    vr.getConfidenceInterval()[0] * 100,
                    vr.getConfidenceInterval()[1] * 100));
            }
        }
        
        sb.append("├").append("─".repeat(58)).append("┤\n");
        sb.append(String.format("│ P-value: %.4f  │ Significant: %-5s │ Sample: %6d   │\n",
            pValue, isStatisticallySignificant(), totalSampleSize));
        sb.append("├").append("─".repeat(58)).append("┤\n");
        sb.append(String.format("│ %-56s │\n", recommendation));
        sb.append("└").append("─".repeat(58)).append("┘");
        
        return sb.toString();
    }
}

class VariantResults {
    private final int exposures;
    private final int conversions;
    private final double conversionRate;
    private double[] confidenceInterval;
    private Double lift;
    private Double revenue;  // Optional revenue metric

    public VariantResults(int exposures, int conversions) {
        this.exposures = exposures;
        this.conversions = conversions;
        this.conversionRate = exposures > 0 ? (double) conversions / exposures : 0;
        this.confidenceInterval = new double[]{0.0, 0.0};
    }

    public int getExposures() { return exposures; }
    public int getConversions() { return conversions; }
    public double getConversionRate() { return conversionRate; }
    public double[] getConfidenceInterval() { return confidenceInterval; }
    public Double getLift() { return lift; }
    public Double getRevenue() { return revenue; }

    public void setConfidenceInterval(double lower, double upper) {
        this.confidenceInterval = new double[]{lower, upper};
    }

    public void setLift(double lift) { this.lift = lift; }
    public void setRevenue(double revenue) { this.revenue = revenue; }

    @Override
    public String toString() {
        return String.format("Results[exp=%d, conv=%d, rate=%.2f%%]",
            exposures, conversions, conversionRate * 100);
    }
}

// ==================== Assignment Strategies ====================

interface AssignmentStrategy {
    Variant assignVariant(String userId, Experiment experiment, int bucket);
}

/**
 * Hash-Based Assignment Strategy (Deterministic)
 * This is the production-standard approach used by Amazon Weblab, Optimizely, etc.
 * 
 * Algorithm:
 * 1. Calculate bucket (0-99) from hash(userId + experimentId + salt)
 * 2. Map bucket to variant based on traffic allocation
 * 3. Same user always gets same bucket = consistency
 * 
 * Time Complexity: O(v) where v = number of variants (typically 2-5)
 * Space Complexity: O(1)
 * 
 * Why MD5 hash?
 * - Uniform distribution (no bias)
 * - Deterministic (same input = same output)
 * - Fast (< 1ms)
 * - Industry standard
 */
class HashBasedAssignmentStrategy implements AssignmentStrategy {
    
    @Override
    public Variant assignVariant(String userId, Experiment experiment, int bucket) {
        // Find variant that owns this bucket based on traffic allocation
        int rangeStart = 0;
        for (Variant variant : experiment.getVariants()) {
            if (variant.matchesAllocation(bucket, rangeStart)) {
                return variant;
            }
            rangeStart += variant.getTrafficPercentage();
        }
        
        // Fallback to first variant (should never happen if validated)
        return experiment.getVariants().get(0);
    }
}

/**
 * Weighted Assignment Strategy (Multi-Armed Bandit)
 * Dynamically adjusts traffic based on performance
 * 
 * Algorithm:
 * 1. Exploration phase: Random assignment to gather data
 * 2. Exploitation phase: Favor better-performing variants
 * 3. Balance exploration vs exploitation (epsilon-greedy)
 */
class WeightedAssignmentStrategy implements AssignmentStrategy {
    private final Random random = new Random();
    
    @Override
    public Variant assignVariant(String userId, Experiment experiment, int bucket) {
        List<Variant> variants = experiment.getVariants();
        
        // Calculate cumulative weights
        double[] cumulativeWeights = new double[variants.size()];
        double sum = 0;
        for (int i = 0; i < variants.size(); i++) {
            sum += variants.get(i).getWeight();
            cumulativeWeights[i] = sum;
        }
        
        // Random selection based on weights
        double rand = random.nextDouble() * sum;
        for (int i = 0; i < variants.size(); i++) {
            if (rand < cumulativeWeights[i]) {
                return variants.get(i);
            }
        }
        
        return variants.get(variants.size() - 1);
    }
}

// ==================== Analytics Service ====================

/**
 * Calculates experiment results and statistical significance
 * Implements standard statistical tests used in industry
 */
class AnalyticsService {
    
    private static final int MINIMUM_SAMPLE_SIZE = 100;  // Per variant
    private static final double SIGNIFICANCE_LEVEL = 0.05;  // 95% confidence
    
    /**
     * Calculate comprehensive results for an experiment
     * 
     * Time Complexity: O(e) where e = number of events
     * Space Complexity: O(v) where v = number of variants
     */
    public ExperimentResults calculateResults(Experiment experiment, 
                                             Map<String, List<Event>> eventsByVariant) {
        ExperimentResults results = new ExperimentResults(experiment.getExperimentId());
        
        // Calculate metrics for each variant
        for (Variant variant : experiment.getVariants()) {
            List<Event> variantEvents = eventsByVariant.getOrDefault(
                variant.getVariantId(), new ArrayList<>());
            
            // Count exposures and conversions
            long exposures = variantEvents.stream()
                .filter(e -> e.getEventType() == EventType.EXPOSURE)
                .count();
            
            long conversions = variantEvents.stream()
                .filter(e -> e.getEventType() == EventType.CONVERSION || 
                           e.getEventType() == EventType.PURCHASE)
                .count();
            
            VariantResults vr = new VariantResults((int) exposures, (int) conversions);
            
            // Calculate confidence interval
            double[] ci = calculateConfidenceInterval(exposures, conversions);
            vr.setConfidenceInterval(ci[0], ci[1]);
            
            // Calculate revenue if applicable
            double revenue = variantEvents.stream()
                .filter(e -> e.hasProperty("revenue"))
                .mapToDouble(e -> ((Number) e.getProperty("revenue")).doubleValue())
                .sum();
            if (revenue > 0) {
                vr.setRevenue(revenue);
            }
            
            results.addVariantResults(variant.getVariantId(), vr);
        }
        
        // Check if minimum sample size reached
        boolean hasMinSample = results.getVariantResults().values().stream()
            .allMatch(vr -> vr.getExposures() >= MINIMUM_SAMPLE_SIZE);
        results.setHasMinimumSampleSize(hasMinSample);
        
        // Calculate statistical significance for AB tests
        if (experiment.getType() == ExperimentType.AB_TEST && 
            experiment.getVariants().size() == 2 && hasMinSample) {
            
            Variant control = experiment.getVariants().stream()
                .filter(Variant::isControl)
                .findFirst()
                .orElse(experiment.getVariants().get(0));
            
            Variant treatment = experiment.getVariants().stream()
                .filter(v -> !v.isControl())
                .findFirst()
                .orElse(experiment.getVariants().get(1));
            
            VariantResults controlResults = results.getVariantResults().get(control.getVariantId());
            VariantResults treatmentResults = results.getVariantResults().get(treatment.getVariantId());
            
            // Calculate statistical significance
            double pValue = calculatePValue(controlResults, treatmentResults);
            results.setPValue(pValue);
            
            // Calculate lift
            if (controlResults.getConversionRate() > 0) {
                double lift = ((treatmentResults.getConversionRate() - controlResults.getConversionRate()) 
                              / controlResults.getConversionRate()) * 100;
                treatmentResults.setLift(lift);
                
                // Generate recommendation
                String recommendation = generateRecommendation(
                    pValue, lift, controlResults, treatmentResults);
                results.setRecommendation(recommendation);
                
                if (pValue < SIGNIFICANCE_LEVEL && lift > 0) {
                    results.setWinningVariantId(treatment.getVariantId());
                } else if (pValue < SIGNIFICANCE_LEVEL && lift < 0) {
                    results.setWinningVariantId(control.getVariantId());
                }
            }
        } else if (!hasMinSample) {
            results.setRecommendation("Insufficient sample size - continue experiment");
        }
        
        return results;
    }
    
    /**
     * Calculate confidence interval using Wilson score interval
     * More accurate than normal approximation for small samples
     * 
     * Formula: https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval
     */
    private double[] calculateConfidenceInterval(long n, long x) {
        if (n == 0) return new double[]{0.0, 0.0};
        
        double p = (double) x / n;
        double z = 1.96;  // 95% confidence
        
        // Wilson score interval
        double denominator = 1 + (z * z / n);
        double centerAdjusted = p + (z * z / (2 * n));
        double adjustedWidth = z * Math.sqrt((p * (1 - p) / n) + (z * z / (4 * n * n)));
        
        double lower = (centerAdjusted - adjustedWidth) / denominator;
        double upper = (centerAdjusted + adjustedWidth) / denominator;
        
        return new double[]{Math.max(0, lower), Math.min(1, upper)};
    }
    
    /**
     * Calculate p-value using z-test for proportions
     * Tests null hypothesis: p1 = p2 (no difference)
     * 
     * Returns: p-value (< 0.05 means statistically significant)
     */
    private double calculatePValue(VariantResults control, VariantResults treatment) {
        int n1 = control.getExposures();
        int x1 = control.getConversions();
        int n2 = treatment.getExposures();
        int x2 = treatment.getConversions();
        
        // If sample size too small, return high p-value
        if (n1 < MINIMUM_SAMPLE_SIZE || n2 < MINIMUM_SAMPLE_SIZE) {
            return 1.0;
        }
        
        double p1 = (double) x1 / n1;
        double p2 = (double) x2 / n2;
        double pooledP = (double) (x1 + x2) / (n1 + n2);
        
        // Z-test for proportions
        double se = Math.sqrt(pooledP * (1 - pooledP) * ((1.0 / n1) + (1.0 / n2)));
        
        if (se == 0) return 1.0;  // Avoid division by zero
        
        double z = Math.abs(p1 - p2) / se;
        
        // Convert z-score to p-value (simplified)
        if (z > 2.576) return 0.01;      // 99% confidence
        if (z > 1.96) return 0.05;       // 95% confidence  
        if (z > 1.645) return 0.10;      // 90% confidence
        return 0.15;                      // Not significant
    }
    
    /**
     * Generate human-readable recommendation
     */
    private String generateRecommendation(double pValue, double lift,
                                         VariantResults control, VariantResults treatment) {
        if (pValue >= SIGNIFICANCE_LEVEL) {
            return String.format(
                "No significant difference (p=%.3f). Continue test or conclude as tie.", pValue);
        }
        
        if (lift > 0) {
            return String.format(
                "✅ Treatment wins with +%.1f%% lift (95%% confidence, p=%.3f)", lift, pValue);
        } else {
            return String.format(
                "Control performs better by %.1f%% (95%% confidence, p=%.3f)", 
                Math.abs(lift), pValue);
        }
    }
    
    /**
     * Calculate required sample size using power analysis
     * 
     * @param baselineRate Baseline conversion rate (e.g., 0.10 for 10%)
     * @param mde Minimum detectable effect (relative, e.g., 0.10 for 10% lift)
     * @param alpha Significance level (typically 0.05)
     * @param power Statistical power (typically 0.80)
     * @return Required sample size per variant
     */
    public int calculateRequiredSampleSize(double baselineRate, double mde,
                                          double alpha, double power) {
        // Simplified formula: n = 16 * p * (1-p) / (MDE)²
        double p = baselineRate;
        double absoluteMDE = p * mde;  // Convert relative to absolute
        
        int nPerVariant = (int) Math.ceil(16 * p * (1 - p) / (absoluteMDE * absoluteMDE));
        return nPerVariant;
    }
}

// ==================== Hash Calculator (Utility) ====================

/**
 * Centralized hash calculation for deterministic assignment
 * Uses MD5 for uniform distribution
 */
class HashCalculator {
    
    /**
     * Calculate bucket number (0-99) for user in experiment
     * 
     * Deterministic: Same inputs always produce same bucket
     * Uniform: Each bucket has ~1% probability
     * 
     * Time Complexity: O(1)
     */
    public static int calculateBucket(String userId, String experimentId, String salt) {
        try {
            String input = userId + ":" + experimentId + ":" + salt;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            BigInteger bigInt = new BigInteger(1, digest);
            return Math.abs(bigInt.intValue() % 100);  // 0-99
        } catch (Exception e) {
            // Fallback to Java's hashCode if MD5 unavailable
            return Math.abs((userId + experimentId + salt).hashCode() % 100);
        }
    }
    
    /**
     * Test hash distribution uniformity
     * Useful for validating assignment is truly random
     */
    public static Map<Integer, Integer> testDistribution(int sampleSize, String experimentId, String salt) {
        Map<Integer, Integer> bucketCounts = new HashMap<>();
        
        for (int i = 0; i < sampleSize; i++) {
            String userId = "user_" + i;
            int bucket = calculateBucket(userId, experimentId, salt);
            bucketCounts.put(bucket, bucketCounts.getOrDefault(bucket, 0) + 1);
        }
        
        return bucketCounts;
    }
}

// ==================== Experiment Platform (Main Service) ====================

/**
 * Central A/B Testing Platform
 * Production-ready implementation with all features
 * 
 * Thread-safe Singleton pattern
 * Handles:
 * - Experiment management
 * - User assignment (with caching simulation)
 * - Event tracking
 * - Analytics and reporting
 * - Feature flags
 */
class ExperimentPlatform {
    private static ExperimentPlatform instance;
    private static final Object lock = new Object();
    
    // Storage (in production: PostgreSQL, Redis, Kafka, ClickHouse)
    private final Map<String, Experiment> experiments;
    private final Map<String, UserAssignment> assignments;  // Simulates Redis cache
    private final List<Event> events;  // Simulates Kafka
    
    // Services
    private AssignmentStrategy assignmentStrategy;
    private final AnalyticsService analyticsService;
    
    // Thread-safe counters
    private final AtomicInteger experimentCounter;
    private final AtomicInteger variantCounter;
    private final AtomicInteger assignmentCounter;
    private final AtomicInteger eventCounter;

    private ExperimentPlatform() {
        this.experiments = new ConcurrentHashMap<>();
        this.assignments = new ConcurrentHashMap<>();
        this.events = new CopyOnWriteArrayList<>();
        this.assignmentStrategy = new HashBasedAssignmentStrategy();
        this.analyticsService = new AnalyticsService();
        this.experimentCounter = new AtomicInteger(0);
        this.variantCounter = new AtomicInteger(0);
        this.assignmentCounter = new AtomicInteger(0);
        this.eventCounter = new AtomicInteger(0);
    }

    public static ExperimentPlatform getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ExperimentPlatform();
                }
            }
        }
        return instance;
    }

    public void setAssignmentStrategy(AssignmentStrategy strategy) {
        this.assignmentStrategy = strategy;
    }

    // ========== Experiment Management ==========

    public Experiment createExperiment(String key, String name, ExperimentType type) {
        String expId = "EXP-" + experimentCounter.incrementAndGet();
        Experiment experiment = new Experiment(expId, key, name, type);
        experiments.put(expId, experiment);
        System.out.println("✅ Experiment created: " + experiment);
        return experiment;
    }

    public Experiment getExperiment(String experimentId) {
        return experiments.get(experimentId);
    }

    public Variant addVariant(Experiment experiment, String key, String name,
                             int trafficPercentage, boolean isControl) {
        String variantId = "VAR-" + variantCounter.incrementAndGet();
        Variant variant = new Variant(variantId, experiment.getExperimentId(), 
                                     key, name, trafficPercentage, isControl);
        experiment.addVariant(variant);
        System.out.println("  ✓ Variant added: " + variant);
        return variant;
    }

    // ========== User Assignment (Core Algorithm) ==========
    
    /**
     * Assign user to variant - THE MOST CRITICAL METHOD
     * 
     * Production Implementation Notes:
     * 1. Check Redis cache first (99.9% hit ratio)
     * 2. If miss: Calculate hash assignment
     * 3. Store in Redis with TTL
     * 4. Async write to PostgreSQL for audit
     * 5. Track exposure event to Kafka
     * 
     * @return AssignmentResult with variant details
     * 
     * Time Complexity: O(1) average (cache hit), O(r+v) worst case
     *   where r = targeting rules, v = variants
     */
    public AssignmentResult assignUser(String userId, Experiment experiment, 
                                       UserContext userContext) {
        // Validate experiment is active
        if (!experiment.isActive()) {
            return null;
        }
        
        String compositeKey = userId + ":" + experiment.getExperimentId();
        
        // Check cache (simulates Redis lookup - <1ms in production)
        if (assignments.containsKey(compositeKey)) {
            UserAssignment existing = assignments.get(compositeKey);
            existing.incrementExposure();
            Variant variant = experiment.getVariantById(existing.getVariantId());
            
            return new AssignmentResult(
                experiment.getExperimentId(),
                variant.getVariantId(),
                variant.getKey(),
                variant.getConfig(),
                false  // Not new
            );
        }
        
        // Check eligibility (targeting rules)
        if (!experiment.isEligible(userContext)) {
            return null;
        }
        
        // Calculate bucket using deterministic hash
        int bucket = HashCalculator.calculateBucket(
            userId, experiment.getExperimentId(), experiment.getSalt());
        
        // Assign using strategy
        Variant assignedVariant = assignmentStrategy.assignVariant(userId, experiment, bucket);
        
        // Store assignment (simulates Redis write)
        String assignmentId = "ASSIGN-" + assignmentCounter.incrementAndGet();
        UserAssignment assignment = new UserAssignment(
            assignmentId, userId, experiment.getExperimentId(),
            assignedVariant.getVariantId(), AssignmentMethod.HASH, bucket
        );
        assignments.put(compositeKey, assignment);
        
        // Track exposure event (simulates Kafka publish)
        trackEvent(userId, experiment.getExperimentId(), assignedVariant.getVariantId(),
                  EventType.EXPOSURE, "variant_exposed", "session_" + userId);
        
        return new AssignmentResult(
            experiment.getExperimentId(),
            assignedVariant.getVariantId(),
            assignedVariant.getKey(),
            assignedVariant.getConfig(),
            true  // New assignment
        );
    }

    // ========== Event Tracking ==========
    
    /**
     * Track event for analytics
     * In production: Async write to Kafka
     * 
     * @param sessionId Session identifier for funnel analysis
     */
    public Event trackEvent(String userId, String experimentId, String variantId,
                           EventType eventType, String eventName, String sessionId) {
        String eventId = "EVT-" + eventCounter.incrementAndGet();
        Event event = new Event(eventId, userId, experimentId, variantId, 
                               eventType, eventName, sessionId);
        events.add(event);
        return event;
    }
    
    public Event trackEvent(String userId, String experimentId, String variantId,
                           EventType eventType, String eventName, String sessionId,
                           Map<String, Object> properties) {
        Event event = trackEvent(userId, experimentId, variantId, eventType, eventName, sessionId);
        properties.forEach(event::setProperty);
        return event;
    }

    // ========== Analytics ==========
    
    /**
     * Get experiment results with full statistical analysis
     */
    public ExperimentResults getExperimentResults(String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) {
            return null;
        }
        
        // Group events by variant
        Map<String, List<Event>> eventsByVariant = events.stream()
            .filter(e -> e.getExperimentId().equals(experimentId))
            .collect(Collectors.groupingBy(Event::getVariantId));
        
        return analyticsService.calculateResults(experiment, eventsByVariant);
    }

    // ========== Override & Testing ==========
    
    /**
     * Manual override for QA testing
     * Allows forcing specific variant for testing
     */
    public void overrideAssignment(String userId, String experimentId, String variantId) {
        String compositeKey = userId + ":" + experimentId;
        String assignmentId = "ASSIGN-" + assignmentCounter.incrementAndGet();
        
        UserAssignment override = new UserAssignment(
            assignmentId, userId, experimentId, variantId, AssignmentMethod.OVERRIDE, -1
        );
        
        assignments.put(compositeKey, override);
        System.out.println("🔧 Override: " + override);
    }

    // ========== Reporting ==========
    
    public void printExperimentSummary(String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) {
            System.out.println("❌ Experiment not found: " + experimentId);
            return;
        }
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  EXPERIMENT: " + experiment.getName());
        System.out.println("=".repeat(70));
        System.out.println("  ID: " + experiment.getExperimentId());
        System.out.println("  Key: " + experiment.getKey());
        System.out.println("  Status: " + experiment.getStatus());
        System.out.println("  Type: " + experiment.getType());
        
        if (experiment.getHypothesis() != null) {
            System.out.println("  Hypothesis: " + experiment.getHypothesis());
        }
        
        System.out.println("\n  Variants:");
        for (Variant v : experiment.getVariants()) {
            System.out.println("    - " + v);
        }
        
        if (!experiment.getTargetingRules().isEmpty()) {
            System.out.println("\n  Targeting Rules:");
            for (TargetingRule rule : experiment.getTargetingRules()) {
                System.out.println("    - " + rule);
            }
        }
        
        System.out.println("\n  Primary Metric: " + experiment.getPrimaryMetric());
        
        // Show results
        ExperimentResults results = getExperimentResults(experimentId);
        System.out.println(results);
        System.out.println("=".repeat(70) + "\n");
    }
    
    // ========== Statistics ==========
    
    public int getExperimentCount() { return experiments.size(); }
    public int getAssignmentCount() { return assignments.size(); }
    public int getEventCount() { return events.size(); }
}

// ==================== Demo Application ====================

public class ABTestingPlatform {
    
    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("╔" + "═".repeat(68) + "╗");
        System.out.println("║" + center("A/B TESTING PLATFORM - Production Implementation", 68) + "║");
        System.out.println("║" + center("(Based on Amazon Weblab, Optimizely Architecture)", 68) + "║");
        System.out.println("╚" + "═".repeat(68) + "╝");
        System.out.println();
        
        ExperimentPlatform platform = ExperimentPlatform.getInstance();
        
        // ========== DEMO 1: Basic A/B Test ==========
        System.out.println("📊 DEMO 1: Basic A/B Test (Checkout Redesign)\n");
        
        Experiment checkoutExp = platform.createExperiment(
            "checkout_redesign_2025",
            "Checkout Page Redesign Experiment",
            ExperimentType.AB_TEST
        );
        
        checkoutExp.setDescription("Test single-page vs multi-page checkout flow");
        checkoutExp.setHypothesis("Single-page checkout will increase conversion by 15%");
        checkoutExp.setCreatedBy("product_manager_001");
        
        // Add variants
        System.out.println("\n Adding Variants:");
        Variant control = platform.addVariant(checkoutExp, "control", 
            "Multi-Page Checkout (Current)", 50, true);
        control.setConfig("layout", "multi_page");
        control.setConfig("steps", 3);
        control.setConfig("progress_bar", true);
        
        Variant treatment = platform.addVariant(checkoutExp, "treatment", 
            "Single-Page Checkout (New)", 50, false);
        treatment.setConfig("layout", "single_page");
        treatment.setConfig("steps", 1);
        treatment.setConfig("progress_bar", false);
        
        // Set metrics
        MetricDefinition primaryMetric = new MetricDefinition(
            "METRIC-1", "Purchase Completion Rate", 
            MetricType.CONVERSION_RATE, "purchase_completed"
        );
        checkoutExp.setPrimaryMetric(primaryMetric);
        
        // Add targeting rules
        System.out.println("\n Adding Targeting Rules:");
        TargetingRule countryRule = new TargetingRule(
            "RULE-1", "country", TargetingOperator.IN,
            new HashSet<>(Arrays.asList("US", "UK", "CA")), 10
        );
        checkoutExp.addTargetingRule(countryRule);
        
        TargetingRule platformRule = new TargetingRule(
            "RULE-2", "platform", TargetingOperator.EQUALS,
            new HashSet<>(Arrays.asList("web")), 5
        );
        checkoutExp.addTargetingRule(platformRule);
        
        // Start experiment
        System.out.println();
        checkoutExp.start();
        
        // ========== Test Assignment Consistency ==========
        System.out.println("\n📌 Testing Assignment Consistency:");
        
        Map<String, String> userContext = new HashMap<>();
        userContext.put("country", "US");
        userContext.put("platform", "web");
        UserContext ctx1 = new UserContext("user_test_001", userContext);
        
        AssignmentResult result1 = platform.assignUser("user_test_001", checkoutExp, ctx1);
        System.out.println("  First assignment: " + result1.getVariantKey());
        
        AssignmentResult result2 = platform.assignUser("user_test_001", checkoutExp, ctx1);
        System.out.println("  Second assignment: " + result2.getVariantKey());
        System.out.println("  ✓ Consistent: " + result1.getVariantKey().equals(result2.getVariantKey()));
        System.out.println("  ✓ Cached (not new): " + !result2.isNewAssignment());
        
        // ========== Simulate 5000 Users ==========
        System.out.println("\n🎲 Simulating 5000 Users:");
        
        Random random = new Random(42);  // Fixed seed for reproducibility
        int controlAssignments = 0;
        int treatmentAssignments = 0;
        
        for (int i = 1; i <= 5000; i++) {
            String userId = "user_" + String.format("%04d", i);
            UserContext uctx = new UserContext(userId, userContext);
            
            AssignmentResult result = platform.assignUser(userId, checkoutExp, uctx);
            if (result != null) {
                if (result.getVariantKey().equals("control")) {
                    controlAssignments++;
                } else {
                    treatmentAssignments++;
                }
                
                // Simulate conversion (treatment has higher rate)
                double conversionProb = result.getVariantKey().equals("control") ? 0.10 : 0.115;
                
                if (random.nextDouble() < conversionProb) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("revenue", 50 + random.nextDouble() * 100);  // $50-150
                    props.put("items", 1 + random.nextInt(5));
                    
                    platform.trackEvent(
                        userId, checkoutExp.getExperimentId(), result.getVariantId(),
                        EventType.PURCHASE, "purchase_completed", "session_" + userId, props
                    );
                }
            }
        }
        
        System.out.println("  Control assignments: " + controlAssignments);
        System.out.println("  Treatment assignments: " + treatmentAssignments);
        System.out.println("  Distribution: " + 
            String.format("%.1f%% / %.1f%%",
                (controlAssignments * 100.0 / 5000),
                (treatmentAssignments * 100.0 / 5000)));
        
        // ========== Show Results ==========
        System.out.println("\n📈 Experiment Results:");
        platform.printExperimentSummary(checkoutExp.getExperimentId());
        
        // ========== DEMO 2: Feature Flag with Targeting ==========
        System.out.println("\n📊 DEMO 2: Feature Flag (New UI Rollout)\n");
        
        Experiment featureFlag = platform.createExperiment(
            "new_ui_v3",
            "New UI Gradual Rollout",
            ExperimentType.FEATURE_FLAG
        );
        
        featureFlag.setHypothesis("New UI will not increase error rate");
        
        System.out.println("\n Adding Variants:");
        platform.addVariant(featureFlag, "old_ui", "Current UI", 90, true);
        platform.addVariant(featureFlag, "new_ui", "New UI v3", 10, false);
        
        MetricDefinition errorMetric = new MetricDefinition(
            "METRIC-2", "Error Rate", MetricType.RATE, "error_occurred"
        );
        featureFlag.setPrimaryMetric(errorMetric);
        
        // Add targeting for premium users only  
        TargetingRule premiumRule = new TargetingRule(
            "RULE-3", "user_segment", TargetingOperator.EQUALS,
            new HashSet<>(Arrays.asList("premium"))
        );
        featureFlag.addTargetingRule(premiumRule);
        featureFlag.start();
        
        // Simulate rollout distribution
        System.out.println("\n🎲 Testing 10% Rollout (200 users):");
        int oldUICount = 0;
        int newUICount = 0;
        
        for (int i = 1; i <= 200; i++) {
            String userId = "premium_user_" + i;
            Map<String, String> premiumContext = new HashMap<>();
            premiumContext.put("country", "US");
            premiumContext.put("platform", "web");
            premiumContext.put("user_segment", "premium");
            
            UserContext pctx = new UserContext(userId, premiumContext);
            AssignmentResult ffResult = platform.assignUser(userId, featureFlag, pctx);
            
            if (ffResult != null) {
                if (ffResult.getVariantKey().equals("new_ui")) {
                    newUICount++;
                } else {
                    oldUICount++;
                }
            }
        }
        
        System.out.println(String.format("  Old UI: %d users (%.1f%%)", 
            oldUICount, (oldUICount * 100.0 / 200)));
        System.out.println(String.format("  New UI: %d users (%.1f%%)", 
            newUICount, (newUICount * 100.0 / 200)));
        System.out.println("  Target: 90% / 10%");
        
        // ========== Final Statistics ==========
        System.out.println("\n📈 Platform Statistics:");
        System.out.println("  Total Experiments: " + platform.getExperimentCount());
        System.out.println("  Total Assignments: " + platform.getAssignmentCount());
        System.out.println("  Total Events: " + platform.getEventCount());
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  ✅ Demo Complete - Production-Ready AB Testing Platform");
        System.out.println("=".repeat(70));
        
        // ========== Architecture Notes ==========
        System.out.println("\n📚 Production Architecture Notes:");
        System.out.println("  1. Assignment Service: Go/Java + Redis (< 10ms latency)");
        System.out.println("  2. Event Tracking: Node.js + Kafka (async, non-blocking)");
        System.out.println("  3. Analytics: Flink + ClickHouse (real-time aggregation)");
        System.out.println("  4. Config Storage: PostgreSQL (ACID transactions)");
        System.out.println("  5. Caching: Redis Cluster (99.9% hit ratio)");
        
        System.out.println("\n🔑 Key Algorithms:");
        System.out.println("  • Deterministic Hashing: MD5(userId+expId+salt) % 100");
        System.out.println("  • Statistical Test: Z-test for proportions");
        System.out.println("  • Confidence Interval: Wilson score interval");
        System.out.println("  • Sample Size: Power analysis formula");
        
        System.out.println("\n⚡ Performance Characteristics:");
        System.out.println("  • Assignment: O(1) with cache, O(r+v) without");
        System.out.println("  • Event tracking: O(1) insert to Kafka");
        System.out.println("  • Analytics: O(e) where e = events (pre-aggregated)");
        System.out.println("  • Targeting: O(r) where r = targeting rules");
        
        System.out.println("\n💡 Design Patterns Used:");
        System.out.println("  • Singleton: ExperimentPlatform (single instance)");
        System.out.println("  • Strategy: AssignmentStrategy (hash vs weighted)");
        System.out.println("  • Immutability: UserContext, AssignmentResult");
        System.out.println("  • Thread-Safety: ConcurrentHashMap, AtomicInteger");
        
        System.out.println();
    }
    
    // Helper method for centered text
    private static String center(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text + 
               " ".repeat(Math.max(0, width - text.length() - padding));
    }
}
