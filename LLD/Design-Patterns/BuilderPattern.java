import java.util.*;
import java.time.*;

// ==================== BUILDER PATTERN ====================
// Definition: Separates the construction of a complex object from its representation.
//
// REAL System Design Interview Examples:
// 1. HTTP Request Builder - Building complex API requests
// 2. SQL Query Builder - Dynamic query construction
// 3. Configuration Builder - System/service configuration
// 4. Elasticsearch Query Builder - Complex search queries
// 5. gRPC Request Builder - Protocol buffer message construction
//
// Interview Use Cases:
// - Design API Gateway: Complex request routing rules
// - Design Database Query Engine: Dynamic query building
// - Design Service Configuration: Multi-environment configs
// - Design Search System: Complex boolean queries
// - Design Monitoring System: Alert rule builders

// ==================== EXAMPLE 1: HTTP REQUEST BUILDER ====================
// Used in: API clients, REST frameworks, HTTP libraries
// Interview Question: Design an API client library

class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final String body;
    private final int timeout;
    private final int retryCount;
    private final boolean followRedirects;

    private HttpRequest(HttpRequestBuilder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers;
        this.queryParams = builder.queryParams;
        this.body = builder.body;
        this.timeout = builder.timeout;
        this.retryCount = builder.retryCount;
        this.followRedirects = builder.followRedirects;
    }

    public static class HttpRequestBuilder {
        // Required
        private final String method;
        private final String url;

        // Optional with defaults
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> queryParams = new HashMap<>();
        private String body = "";
        private int timeout = 30000; // 30 seconds
        private int retryCount = 3;
        private boolean followRedirects = true;

        public HttpRequestBuilder(String method, String url) {
            this.method = method;
            this.url = url;
        }

        public HttpRequestBuilder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public HttpRequestBuilder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public HttpRequestBuilder queryParam(String key, String value) {
            this.queryParams.put(key, value);
            return this;
        }

        public HttpRequestBuilder body(String body) {
            this.body = body;
            return this;
        }

        public HttpRequestBuilder jsonBody(Map<String, Object> json) {
            this.body = json.toString(); // Simplified
            this.headers.put("Content-Type", "application/json");
            return this;
        }

        public HttpRequestBuilder timeout(int milliseconds) {
            this.timeout = milliseconds;
            return this;
        }

        public HttpRequestBuilder retries(int count) {
            this.retryCount = count;
            return this;
        }

        public HttpRequestBuilder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }

    public void execute() {
        StringBuilder requestStr = new StringBuilder();
        requestStr.append(method).append(" ").append(url);
        
        if (!queryParams.isEmpty()) {
            requestStr.append("?");
            queryParams.forEach((k, v) -> requestStr.append(k).append("=").append(v).append("&"));
        }
        
        System.out.println("🌐 HTTP Request:");
        System.out.println("   " + requestStr);
        System.out.println("   Headers: " + headers);
        System.out.println("   Timeout: " + timeout + "ms, Retries: " + retryCount);
        if (!body.isEmpty()) {
            System.out.println("   Body: " + body);
        }
    }
}

// ==================== EXAMPLE 2: SQL QUERY BUILDER ====================
// Used in: ORMs (Hibernate, JPA), Query engines, Database clients
// Interview Question: Design a database query abstraction layer

class SQLQuery {
    private final String table;
    private final List<String> selectColumns;
    private final List<String> joins;
    private final String whereClause;
    private final String groupBy;
    private final String having;
    private final String orderBy;
    private final Integer limit;
    private final Integer offset;
    private final boolean forUpdate;

    private SQLQuery(SQLQueryBuilder builder) {
        this.table = builder.table;
        this.selectColumns = builder.selectColumns;
        this.joins = builder.joins;
        this.whereClause = builder.whereClause;
        this.groupBy = builder.groupBy;
        this.having = builder.having;
        this.orderBy = builder.orderBy;
        this.limit = builder.limit;
        this.offset = builder.offset;
        this.forUpdate = builder.forUpdate;
    }

    public static class SQLQueryBuilder {
        private String table;
        private List<String> selectColumns = new ArrayList<>();
        private List<String> joins = new ArrayList<>();
        private String whereClause = "";
        private String groupBy = "";
        private String having = "";
        private String orderBy = "";
        private Integer limit = null;
        private Integer offset = null;
        private boolean forUpdate = false;

        public SQLQueryBuilder from(String table) {
            this.table = table;
            return this;
        }

        public SQLQueryBuilder select(String... columns) {
            this.selectColumns.addAll(Arrays.asList(columns));
            return this;
        }

        public SQLQueryBuilder join(String table, String condition) {
            this.joins.add("JOIN " + table + " ON " + condition);
            return this;
        }

        public SQLQueryBuilder leftJoin(String table, String condition) {
            this.joins.add("LEFT JOIN " + table + " ON " + condition);
            return this;
        }

        public SQLQueryBuilder where(String condition) {
            this.whereClause = condition;
            return this;
        }

        public SQLQueryBuilder groupBy(String column) {
            this.groupBy = column;
            return this;
        }

        public SQLQueryBuilder having(String condition) {
            this.having = condition;
            return this;
        }

        public SQLQueryBuilder orderBy(String column, String direction) {
            this.orderBy = column + " " + direction;
            return this;
        }

        public SQLQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SQLQueryBuilder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public SQLQueryBuilder forUpdate() {
            this.forUpdate = true;
            return this;
        }

        public SQLQuery build() {
            if (table == null || table.isEmpty()) {
                throw new IllegalStateException("Table name is required");
            }
            return new SQLQuery(this);
        }
    }

    public String toSQL() {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        if (selectColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectColumns));
        }
        
        sql.append(" FROM ").append(table);
        
        for (String join : joins) {
            sql.append(" ").append(join);
        }
        
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        if (!groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(groupBy);
        }
        
        if (!having.isEmpty()) {
            sql.append(" HAVING ").append(having);
        }
        
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        
        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }
        
        if (offset != null) {
            sql.append(" OFFSET ").append(offset);
        }
        
        if (forUpdate) {
            sql.append(" FOR UPDATE");
        }
        
        return sql.toString();
    }

    @Override
    public String toString() {
        return "🗃️ " + toSQL();
    }
}

// ==================== EXAMPLE 3: SERVICE CONFIGURATION BUILDER ====================
// Used in: Microservices, Cloud deployments, Infrastructure as Code
// Interview Question: Design a configuration management system

class ServiceConfiguration {
    private final String serviceName;
    private final String environment;
    private final Map<String, String> databaseConfig;
    private final Map<String, String> cacheConfig;
    private final Map<String, Integer> resourceLimits;
    private final List<String> dependencies;
    private final Map<String, String> featureFlags;
    private final int instanceCount;
    private final String logLevel;

    private ServiceConfiguration(ConfigBuilder builder) {
        this.serviceName = builder.serviceName;
        this.environment = builder.environment;
        this.databaseConfig = builder.databaseConfig;
        this.cacheConfig = builder.cacheConfig;
        this.resourceLimits = builder.resourceLimits;
        this.dependencies = builder.dependencies;
        this.featureFlags = builder.featureFlags;
        this.instanceCount = builder.instanceCount;
        this.logLevel = builder.logLevel;
    }

    public static class ConfigBuilder {
        // Required
        private final String serviceName;
        private final String environment;

        // Optional with defaults
        private Map<String, String> databaseConfig = new HashMap<>();
        private Map<String, String> cacheConfig = new HashMap<>();
        private Map<String, Integer> resourceLimits = new HashMap<>();
        private List<String> dependencies = new ArrayList<>();
        private Map<String, String> featureFlags = new HashMap<>();
        private int instanceCount = 1;
        private String logLevel = "INFO";

        public ConfigBuilder(String serviceName, String environment) {
            this.serviceName = serviceName;
            this.environment = environment;
        }

        public ConfigBuilder database(String host, int port, String name) {
            this.databaseConfig.put("host", host);
            this.databaseConfig.put("port", String.valueOf(port));
            this.databaseConfig.put("database", name);
            return this;
        }

        public ConfigBuilder cache(String host, int port) {
            this.cacheConfig.put("host", host);
            this.cacheConfig.put("port", String.valueOf(port));
            return this;
        }

        public ConfigBuilder cpuLimit(int millicores) {
            this.resourceLimits.put("cpu", millicores);
            return this;
        }

        public ConfigBuilder memoryLimit(int megabytes) {
            this.resourceLimits.put("memory", megabytes);
            return this;
        }

        public ConfigBuilder addDependency(String serviceName) {
            this.dependencies.add(serviceName);
            return this;
        }

        public ConfigBuilder featureFlag(String flag, boolean enabled) {
            this.featureFlags.put(flag, String.valueOf(enabled));
            return this;
        }

        public ConfigBuilder instances(int count) {
            this.instanceCount = count;
            return this;
        }

        public ConfigBuilder logLevel(String level) {
            this.logLevel = level;
            return this;
        }

        public ServiceConfiguration build() {
            return new ServiceConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "⚙️ Service Config: %s [%s]\n" +
            "   Database: %s\n" +
            "   Cache: %s\n" +
            "   Resources: %s\n" +
            "   Dependencies: %s\n" +
            "   Feature Flags: %s\n" +
            "   Instances: %d, LogLevel: %s",
            serviceName, environment, databaseConfig, cacheConfig,
            resourceLimits, dependencies, featureFlags, instanceCount, logLevel
        );
    }
}

// ==================== EXAMPLE 4: ELASTICSEARCH QUERY BUILDER ====================
// Used in: Search engines, Log aggregation, Analytics
// Interview Question: Design a search system

class ElasticsearchQuery {
    private final String index;
    private final Map<String, Object> mustClauses;
    private final Map<String, Object> shouldClauses;
    private final Map<String, Object> filterClauses;
    private final List<String> sortFields;
    private final Map<String, Object> aggregations;
    private final Integer size;
    private final Integer from;

    private ElasticsearchQuery(ESQueryBuilder builder) {
        this.index = builder.index;
        this.mustClauses = builder.mustClauses;
        this.shouldClauses = builder.shouldClauses;
        this.filterClauses = builder.filterClauses;
        this.sortFields = builder.sortFields;
        this.aggregations = builder.aggregations;
        this.size = builder.size;
        this.from = builder.from;
    }

    public static class ESQueryBuilder {
        private String index;
        private Map<String, Object> mustClauses = new HashMap<>();
        private Map<String, Object> shouldClauses = new HashMap<>();
        private Map<String, Object> filterClauses = new HashMap<>();
        private List<String> sortFields = new ArrayList<>();
        private Map<String, Object> aggregations = new HashMap<>();
        private Integer size = 10;
        private Integer from = 0;

        public ESQueryBuilder index(String index) {
            this.index = index;
            return this;
        }

        public ESQueryBuilder must(String field, Object value) {
            this.mustClauses.put(field, value);
            return this;
        }

        public ESQueryBuilder should(String field, Object value) {
            this.shouldClauses.put(field, value);
            return this;
        }

        public ESQueryBuilder filter(String field, Object value) {
            this.filterClauses.put(field, value);
            return this;
        }

        public ESQueryBuilder sort(String field, String order) {
            this.sortFields.add(field + ":" + order);
            return this;
        }

        public ESQueryBuilder aggregation(String name, String type, String field) {
            Map<String, Object> agg = new HashMap<>();
            agg.put("type", type);
            agg.put("field", field);
            this.aggregations.put(name, agg);
            return this;
        }

        public ESQueryBuilder size(int size) {
            this.size = size;
            return this;
        }

        public ESQueryBuilder from(int from) {
            this.from = from;
            return this;
        }

        public ElasticsearchQuery build() {
            return new ElasticsearchQuery(this);
        }
    }

    public String toJSON() {
        return String.format(
            "{\n  \"index\": \"%s\",\n  \"query\": {\n    \"bool\": {\n" +
            "      \"must\": %s,\n      \"should\": %s,\n      \"filter\": %s\n    }\n  },\n" +
            "  \"sort\": %s,\n  \"aggs\": %s,\n  \"size\": %d,\n  \"from\": %d\n}",
            index, mustClauses, shouldClauses, filterClauses,
            sortFields, aggregations, size, from
        );
    }

    @Override
    public String toString() {
        return "🔍 Elasticsearch Query:\n" + toJSON();
    }
}

// ==================== EXAMPLE 5: ALERT RULE BUILDER ====================
// Used in: Monitoring systems, Incident management
// Interview Question: Design an alerting and monitoring system

class AlertRule {
    private final String ruleName;
    private final String metricName;
    private final String condition;
    private final double threshold;
    private final int duration;
    private final String severity;
    private final List<String> notificationChannels;
    private final Map<String, String> labels;
    private final String runbook;
    private final boolean enabled;

    private AlertRule(AlertRuleBuilder builder) {
        this.ruleName = builder.ruleName;
        this.metricName = builder.metricName;
        this.condition = builder.condition;
        this.threshold = builder.threshold;
        this.duration = builder.duration;
        this.severity = builder.severity;
        this.notificationChannels = builder.notificationChannels;
        this.labels = builder.labels;
        this.runbook = builder.runbook;
        this.enabled = builder.enabled;
    }

    public static class AlertRuleBuilder {
        // Required
        private final String ruleName;
        private final String metricName;

        // Optional with defaults
        private String condition = "GREATER_THAN";
        private double threshold = 0.0;
        private int duration = 300; // 5 minutes
        private String severity = "WARNING";
        private List<String> notificationChannels = new ArrayList<>();
        private Map<String, String> labels = new HashMap<>();
        private String runbook = "";
        private boolean enabled = true;

        public AlertRuleBuilder(String ruleName, String metricName) {
            this.ruleName = ruleName;
            this.metricName = metricName;
        }

        public AlertRuleBuilder greaterThan(double threshold) {
            this.condition = "GREATER_THAN";
            this.threshold = threshold;
            return this;
        }

        public AlertRuleBuilder lessThan(double threshold) {
            this.condition = "LESS_THAN";
            this.threshold = threshold;
            return this;
        }

        public AlertRuleBuilder forDuration(int seconds) {
            this.duration = seconds;
            return this;
        }

        public AlertRuleBuilder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public AlertRuleBuilder notifyVia(String... channels) {
            this.notificationChannels.addAll(Arrays.asList(channels));
            return this;
        }

        public AlertRuleBuilder addLabel(String key, String value) {
            this.labels.put(key, value);
            return this;
        }

        public AlertRuleBuilder runbook(String url) {
            this.runbook = url;
            return this;
        }

        public AlertRuleBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AlertRule build() {
            return new AlertRule(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "🚨 Alert Rule: %s\n" +
            "   Metric: %s %s %.2f for %ds\n" +
            "   Severity: %s | Enabled: %s\n" +
            "   Notify: %s\n" +
            "   Labels: %s\n" +
            "   Runbook: %s",
            ruleName, metricName, condition, threshold, duration,
            severity, enabled, notificationChannels, labels,
            runbook.isEmpty() ? "None" : runbook
        );
    }
}

// ==================== EXAMPLE 6: GRPC REQUEST BUILDER ====================
// Used in: Microservices communication, gRPC, Protocol Buffers
// Interview Question: Design inter-service communication

class GrpcRequest {
    private final String service;
    private final String method;
    private final Map<String, Object> payload;
    private final Map<String, String> metadata;
    private final int timeout;
    private final boolean streaming;
    private final String compressionType;

    private GrpcRequest(GrpcRequestBuilder builder) {
        this.service = builder.service;
        this.method = builder.method;
        this.payload = builder.payload;
        this.metadata = builder.metadata;
        this.timeout = builder.timeout;
        this.streaming = builder.streaming;
        this.compressionType = builder.compressionType;
    }

    public static class GrpcRequestBuilder {
        private final String service;
        private final String method;
        private Map<String, Object> payload = new HashMap<>();
        private Map<String, String> metadata = new HashMap<>();
        private int timeout = 5000;
        private boolean streaming = false;
        private String compressionType = "gzip";

        public GrpcRequestBuilder(String service, String method) {
            this.service = service;
            this.method = method;
        }

        public GrpcRequestBuilder withField(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }

        public GrpcRequestBuilder withMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public GrpcRequestBuilder timeout(int milliseconds) {
            this.timeout = milliseconds;
            return this;
        }

        public GrpcRequestBuilder streaming(boolean isStreaming) {
            this.streaming = isStreaming;
            return this;
        }

        public GrpcRequestBuilder compression(String type) {
            this.compressionType = type;
            return this;
        }

        public GrpcRequest build() {
            return new GrpcRequest(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "🔌 gRPC Request: %s.%s\n" +
            "   Payload: %s\n" +
            "   Metadata: %s\n" +
            "   Timeout: %dms | Streaming: %s | Compression: %s",
            service, method, payload, metadata, timeout, streaming, compressionType
        );
    }
}

// ==================== DEMO ====================

public class BuilderPattern {
    public static void main(String[] args) {
        System.out.println("========== BUILDER PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: HTTP REQUEST BUILDER =====
        System.out.println("===== EXAMPLE 1: HTTP REQUEST BUILDER (REST API Client) =====\n");
        
        HttpRequest simpleGet = new HttpRequest.HttpRequestBuilder("GET", "https://api.example.com/users/123")
            .header("Authorization", "Bearer token123")
            .timeout(5000)
            .build();
        simpleGet.execute();
        
        System.out.println();
        
        HttpRequest complexPost = new HttpRequest.HttpRequestBuilder("POST", "https://api.example.com/orders")
            .header("Authorization", "Bearer token123")
            .header("Content-Type", "application/json")
            .header("X-Request-ID", UUID.randomUUID().toString())
            .queryParam("notify", "true")
            .queryParam("async", "false")
            .body("{\"productId\": \"PROD-456\", \"quantity\": 2}")
            .timeout(10000)
            .retries(5)
            .followRedirects(false)
            .build();
        complexPost.execute();

        // ===== EXAMPLE 2: SQL QUERY BUILDER =====
        System.out.println("\n\n===== EXAMPLE 2: SQL QUERY BUILDER (ORM/Query Engine) =====\n");
        
        SQLQuery simpleQuery = new SQLQuery.SQLQueryBuilder()
            .select("id", "name", "email")
            .from("users")
            .where("age > 18")
            .orderBy("name", "ASC")
            .limit(10)
            .build();
        System.out.println(simpleQuery);
        
        System.out.println();
        
        SQLQuery complexQuery = new SQLQuery.SQLQueryBuilder()
            .select("u.id", "u.name", "COUNT(o.id) as order_count", "SUM(o.total) as revenue")
            .from("users u")
            .join("orders o", "u.id = o.user_id")
            .where("o.status = 'completed'")
            .groupBy("u.id, u.name")
            .having("COUNT(o.id) > 5")
            .orderBy("revenue", "DESC")
            .limit(20)
            .offset(0)
            .build();
        System.out.println(complexQuery);
        
        System.out.println();
        
        SQLQuery pessimisticLock = new SQLQuery.SQLQueryBuilder()
            .select("*")
            .from("inventory")
            .where("product_id = 'PROD-789'")
            .forUpdate()
            .build();
        System.out.println(pessimisticLock);

        // ===== EXAMPLE 3: SERVICE CONFIGURATION =====
        System.out.println("\n\n===== EXAMPLE 3: SERVICE CONFIGURATION (Microservices) =====\n");
        
        ServiceConfiguration devConfig = new ServiceConfiguration.ConfigBuilder("user-service", "development")
            .database("localhost", 5432, "users_dev")
            .cache("localhost", 6379)
            .cpuLimit(500)
            .memoryLimit(512)
            .logLevel("DEBUG")
            .instances(1)
            .build();
        System.out.println(devConfig);
        
        System.out.println();
        
        ServiceConfiguration prodConfig = new ServiceConfiguration.ConfigBuilder("user-service", "production")
            .database("db-prod.example.com", 5432, "users_prod")
            .cache("redis-prod.example.com", 6379)
            .cpuLimit(2000)
            .memoryLimit(4096)
            .addDependency("auth-service")
            .addDependency("notification-service")
            .featureFlag("new_ui", true)
            .featureFlag("beta_features", false)
            .logLevel("INFO")
            .instances(10)
            .build();
        System.out.println(prodConfig);

        // ===== EXAMPLE 4: ELASTICSEARCH QUERY =====
        System.out.println("\n\n===== EXAMPLE 4: ELASTICSEARCH QUERY (Search System) =====\n");
        
        ElasticsearchQuery searchQuery = new ElasticsearchQuery.ESQueryBuilder()
            .index("products")
            .must("category", "electronics")
            .must("in_stock", true)
            .should("brand", "Apple")
            .filter("price_range", "100-500")
            .sort("price", "asc")
            .aggregation("avg_price", "avg", "price")
            .aggregation("brand_count", "terms", "brand")
            .size(20)
            .from(0)
            .build();
        System.out.println(searchQuery);

        // ===== EXAMPLE 5: ALERT RULE BUILDER =====
        System.out.println("\n\n===== EXAMPLE 5: ALERT RULE BUILDER (Monitoring System) =====\n");
        
        AlertRule cpuAlert = new AlertRule.AlertRuleBuilder("high_cpu_usage", "cpu_usage_percent")
            .greaterThan(80.0)
            .forDuration(300)
            .severity("CRITICAL")
            .notifyVia("pagerduty", "slack", "email")
            .addLabel("team", "platform")
            .addLabel("service", "api-server")
            .runbook("https://wiki.company.com/runbooks/high-cpu")
            .build();
        System.out.println(cpuAlert);
        
        System.out.println();
        
        AlertRule errorRateAlert = new AlertRule.AlertRuleBuilder("high_error_rate", "error_rate_percent")
            .greaterThan(5.0)
            .forDuration(60)
            .severity("WARNING")
            .notifyVia("slack")
            .addLabel("team", "backend")
            .build();
        System.out.println(errorRateAlert);

        // ===== EXAMPLE 6: GRPC REQUEST =====
        System.out.println("\n\n===== EXAMPLE 6: GRPC REQUEST (Microservices) =====\n");
        
        GrpcRequest userRequest = new GrpcRequest.GrpcRequestBuilder("UserService", "GetUser")
            .withField("user_id", "12345")
            .withField("include_profile", true)
            .withMetadata("x-request-id", UUID.randomUUID().toString())
            .withMetadata("x-trace-id", "trace-789")
            .timeout(3000)
            .build();
        System.out.println(userRequest);
        
        System.out.println();
        
        GrpcRequest streamRequest = new GrpcRequest.GrpcRequestBuilder("OrderService", "StreamOrders")
            .withField("status", "PROCESSING")
            .withField("created_after", LocalDateTime.now().minusDays(7).toString())
            .streaming(true)
            .compression("snappy")
            .timeout(60000)
            .build();
        System.out.println(streamRequest);

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• REST API clients and HTTP libraries");
        System.out.println("• ORM query builders (Hibernate, JPA, JOOQ)");
        System.out.println("• Service configuration management");
        System.out.println("• Elasticsearch and search engines");
        System.out.println("• Monitoring and alerting systems (Prometheus, Datadog)");
        System.out.println("• gRPC microservices communication");
    }
}
