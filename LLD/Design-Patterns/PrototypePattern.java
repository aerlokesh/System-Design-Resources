import java.util.*;

// ==================== PROTOTYPE PATTERN ====================
// Definition: Creates new objects by copying an existing object (prototype), avoiding
// the cost of creating objects from scratch.
//
// REAL System Design Interview Examples:
// 1. Configuration Cloning - Clone base configs for different environments
// 2. Document Templates - Clone template documents for new users
// 3. Object Caching/Snapshot - Clone object state for versioning
// 4. Game Entity Spawning - Clone prototype enemies/items
// 5. Request/Response Cloning - Clone base requests for API calls
//
// Interview Use Cases:
// - Design Configuration Management: Clone configs per environment
// - Design Document System: Clone templates for new docs
// - Design Snapshot/Versioning: Clone state at points in time
// - Design A/B Testing: Clone experiment configurations
// - Design Distributed Cache: Clone cached objects for modification

// ==================== EXAMPLE 1: SERVER CONFIGURATION CLONING ====================
// Used in: Kubernetes pod specs, Terraform modules, Docker Compose
// Interview Question: Design a configuration management system

interface Cloneable<T> {
    T deepClone();
}

class ServerConfig implements Cloneable<ServerConfig> {
    private String name;
    private String region;
    private String instanceType;
    private int cpuCores;
    private int memoryGB;
    private Map<String, String> envVariables;
    private List<String> securityGroups;
    private Map<String, String> tags;

    public ServerConfig(String name, String region, String instanceType, int cpuCores, int memoryGB) {
        this.name = name;
        this.region = region;
        this.instanceType = instanceType;
        this.cpuCores = cpuCores;
        this.memoryGB = memoryGB;
        this.envVariables = new HashMap<>();
        this.securityGroups = new ArrayList<>();
        this.tags = new HashMap<>();
    }

    // Private copy constructor
    private ServerConfig(ServerConfig source) {
        this.name = source.name;
        this.region = source.region;
        this.instanceType = source.instanceType;
        this.cpuCores = source.cpuCores;
        this.memoryGB = source.memoryGB;
        this.envVariables = new HashMap<>(source.envVariables);
        this.securityGroups = new ArrayList<>(source.securityGroups);
        this.tags = new HashMap<>(source.tags);
    }

    @Override
    public ServerConfig deepClone() {
        return new ServerConfig(this);
    }

    // Fluent setters for modification after cloning
    public ServerConfig withName(String name) { this.name = name; return this; }
    public ServerConfig withRegion(String region) { this.region = region; return this; }
    public ServerConfig withInstanceType(String type) { this.instanceType = type; return this; }
    public ServerConfig withCpu(int cores) { this.cpuCores = cores; return this; }
    public ServerConfig withMemory(int gb) { this.memoryGB = gb; return this; }
    public ServerConfig addEnvVar(String key, String value) { envVariables.put(key, value); return this; }
    public ServerConfig addSecurityGroup(String sg) { securityGroups.add(sg); return this; }
    public ServerConfig addTag(String key, String value) { tags.put(key, value); return this; }

    @Override
    public String toString() {
        return String.format(
            "⚙️  Config: %s [%s]\n      Instance: %s (%d CPU, %dGB RAM)\n      Env: %s\n      SG: %s\n      Tags: %s",
            name, region, instanceType, cpuCores, memoryGB, envVariables, securityGroups, tags);
    }
}

class ConfigRegistry {
    private Map<String, ServerConfig> prototypes = new HashMap<>();

    public void registerPrototype(String key, ServerConfig config) {
        prototypes.put(key, config);
        System.out.printf("   📋 Registered prototype: '%s'%n", key);
    }

    public ServerConfig createFromPrototype(String key) {
        ServerConfig prototype = prototypes.get(key);
        if (prototype == null) throw new IllegalArgumentException("Unknown prototype: " + key);
        System.out.printf("   ♻️  Cloning prototype: '%s'%n", key);
        return prototype.deepClone();
    }
}

// ==================== EXAMPLE 2: DOCUMENT TEMPLATE ====================
// Used in: Google Docs templates, Notion, Confluence
// Interview Question: Design a document management system

class DocumentTemplate implements Cloneable<DocumentTemplate> {
    private String title;
    private String content;
    private Map<String, String> metadata;
    private List<String> sections;
    private Map<String, Object> formatting;

    public DocumentTemplate(String title) {
        this.title = title;
        this.content = "";
        this.metadata = new HashMap<>();
        this.sections = new ArrayList<>();
        this.formatting = new HashMap<>();
    }

    private DocumentTemplate(DocumentTemplate source) {
        this.title = source.title;
        this.content = source.content;
        this.metadata = new HashMap<>(source.metadata);
        this.sections = new ArrayList<>(source.sections);
        this.formatting = new HashMap<>(source.formatting);
    }

    @Override
    public DocumentTemplate deepClone() {
        return new DocumentTemplate(this);
    }

    public DocumentTemplate withTitle(String title) { this.title = title; return this; }
    public DocumentTemplate withContent(String content) { this.content = content; return this; }
    public DocumentTemplate addSection(String section) { sections.add(section); return this; }
    public DocumentTemplate addMetadata(String key, String value) { metadata.put(key, value); return this; }
    public DocumentTemplate setFormatting(String key, Object value) { formatting.put(key, value); return this; }

    @Override
    public String toString() {
        return String.format("📄 '%s'\n      Sections: %s\n      Metadata: %s\n      Content: %s...",
            title, sections, metadata, content.substring(0, Math.min(50, content.length())));
    }
}

class TemplateRegistry {
    private Map<String, DocumentTemplate> templates = new HashMap<>();

    public void registerTemplate(String name, DocumentTemplate template) {
        templates.put(name, template);
        System.out.printf("   📋 Registered document template: '%s'%n", name);
    }

    public DocumentTemplate createFromTemplate(String name) {
        DocumentTemplate template = templates.get(name);
        if (template == null) throw new IllegalArgumentException("Unknown template: " + name);
        System.out.printf("   ♻️  Creating document from template: '%s'%n", name);
        return template.deepClone();
    }
}

// ==================== EXAMPLE 3: API REQUEST PROTOTYPE ====================
// Used in: API clients, HTTP libraries, test frameworks
// Interview Question: Design an API client library

class APIRequest implements Cloneable<APIRequest> {
    private String method;
    private String baseUrl;
    private String path;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;
    private int timeout;
    private int retries;

    public APIRequest(String method, String baseUrl) {
        this.method = method;
        this.baseUrl = baseUrl;
        this.path = "";
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
        this.body = "";
        this.timeout = 5000;
        this.retries = 3;
    }

    private APIRequest(APIRequest source) {
        this.method = source.method;
        this.baseUrl = source.baseUrl;
        this.path = source.path;
        this.headers = new HashMap<>(source.headers);
        this.queryParams = new HashMap<>(source.queryParams);
        this.body = source.body;
        this.timeout = source.timeout;
        this.retries = source.retries;
    }

    @Override
    public APIRequest deepClone() {
        return new APIRequest(this);
    }

    public APIRequest withPath(String path) { this.path = path; return this; }
    public APIRequest withMethod(String method) { this.method = method; return this; }
    public APIRequest addHeader(String key, String value) { headers.put(key, value); return this; }
    public APIRequest addParam(String key, String value) { queryParams.put(key, value); return this; }
    public APIRequest withBody(String body) { this.body = body; return this; }
    public APIRequest withTimeout(int ms) { this.timeout = ms; return this; }

    public void execute() {
        System.out.printf("   🌐 %s %s%s%n", method, baseUrl, path);
        System.out.printf("      Headers: %s%n", headers);
        if (!queryParams.isEmpty()) System.out.printf("      Params: %s%n", queryParams);
        if (!body.isEmpty()) System.out.printf("      Body: %s%n", body);
    }
}

// ==================== EXAMPLE 4: EXPERIMENT/A/B TEST CONFIG ====================
// Used in: Feature flagging, A/B testing platforms
// Interview Question: Design an A/B testing platform

class ExperimentConfig implements Cloneable<ExperimentConfig> {
    private String experimentId;
    private String name;
    private Map<String, Object> parameters;
    private double trafficPercentage;
    private List<String> targetSegments;
    private Map<String, String> metrics;

    public ExperimentConfig(String name) {
        this.experimentId = "EXP-" + UUID.randomUUID().toString().substring(0, 6);
        this.name = name;
        this.parameters = new HashMap<>();
        this.trafficPercentage = 50.0;
        this.targetSegments = new ArrayList<>();
        this.metrics = new HashMap<>();
    }

    private ExperimentConfig(ExperimentConfig source) {
        this.experimentId = "EXP-" + UUID.randomUUID().toString().substring(0, 6); // New ID
        this.name = source.name;
        this.parameters = new HashMap<>(source.parameters);
        this.trafficPercentage = source.trafficPercentage;
        this.targetSegments = new ArrayList<>(source.targetSegments);
        this.metrics = new HashMap<>(source.metrics);
    }

    @Override
    public ExperimentConfig deepClone() {
        return new ExperimentConfig(this);
    }

    public ExperimentConfig withName(String name) { this.name = name; return this; }
    public ExperimentConfig withTraffic(double pct) { this.trafficPercentage = pct; return this; }
    public ExperimentConfig addParam(String key, Object value) { parameters.put(key, value); return this; }
    public ExperimentConfig addSegment(String segment) { targetSegments.add(segment); return this; }
    public ExperimentConfig addMetric(String name, String type) { metrics.put(name, type); return this; }

    @Override
    public String toString() {
        return String.format("🧪 %s [%s]\n      Traffic: %.0f%% | Segments: %s\n      Params: %s\n      Metrics: %s",
            name, experimentId, trafficPercentage, targetSegments, parameters, metrics);
    }
}

// ==================== DEMO ====================

public class PrototypePattern {
    public static void main(String[] args) {
        System.out.println("========== PROTOTYPE PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: SERVER CONFIGURATION =====
        System.out.println("===== EXAMPLE 1: SERVER CONFIG CLONING (Kubernetes/Terraform) =====\n");

        ConfigRegistry registry = new ConfigRegistry();

        // Create base prototype
        ServerConfig baseConfig = new ServerConfig("base-web-server", "us-east-1", "t3.medium", 2, 4)
            .addEnvVar("LOG_LEVEL", "INFO")
            .addEnvVar("APP_ENV", "production")
            .addSecurityGroup("sg-web-default")
            .addSecurityGroup("sg-internal")
            .addTag("team", "platform");
        registry.registerPrototype("web-server", baseConfig);

        // Clone and customize for different environments
        ServerConfig devServer = registry.createFromPrototype("web-server")
            .withName("dev-web-server")
            .withRegion("us-west-2")
            .withInstanceType("t3.small")
            .withCpu(1).withMemory(2)
            .addEnvVar("APP_ENV", "development")
            .addEnvVar("DEBUG", "true");
        System.out.println(devServer);

        System.out.println();
        ServerConfig prodServer = registry.createFromPrototype("web-server")
            .withName("prod-web-server")
            .withInstanceType("c5.2xlarge")
            .withCpu(8).withMemory(16)
            .addSecurityGroup("sg-prod-waf")
            .addTag("environment", "production");
        System.out.println(prodServer);

        // ===== EXAMPLE 2: DOCUMENT TEMPLATES =====
        System.out.println("\n\n===== EXAMPLE 2: DOCUMENT TEMPLATES (Google Docs/Notion) =====\n");

        TemplateRegistry docRegistry = new TemplateRegistry();

        DocumentTemplate designDocTemplate = new DocumentTemplate("Design Document")
            .withContent("# Design Document\n## Overview\n...")
            .addSection("Overview").addSection("Requirements").addSection("Architecture")
            .addSection("API Design").addSection("Data Model").addSection("Trade-offs")
            .addMetadata("type", "design-doc")
            .setFormatting("font", "Arial");
        docRegistry.registerTemplate("design-doc", designDocTemplate);

        // Create new docs from template
        DocumentTemplate myDoc = docRegistry.createFromTemplate("design-doc")
            .withTitle("Payment Service Design")
            .addMetadata("author", "alice")
            .addMetadata("status", "draft");
        System.out.println(myDoc);

        System.out.println();
        DocumentTemplate teamDoc = docRegistry.createFromTemplate("design-doc")
            .withTitle("Cache Layer Redesign")
            .addMetadata("author", "bob")
            .addSection("Migration Plan");
        System.out.println(teamDoc);

        // ===== EXAMPLE 3: API REQUEST PROTOTYPE =====
        System.out.println("\n\n===== EXAMPLE 3: API REQUEST CLONING (HTTP Client) =====\n");

        // Base request with common headers
        APIRequest baseRequest = new APIRequest("GET", "https://api.example.com")
            .addHeader("Authorization", "Bearer token123")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-API-Version", "v2")
            .withTimeout(5000);

        System.out.println("Clone for different endpoints:");
        baseRequest.deepClone().withPath("/users/123").execute();
        System.out.println();
        baseRequest.deepClone().withPath("/orders").addParam("status", "active").execute();
        System.out.println();
        baseRequest.deepClone().withMethod("POST").withPath("/users")
            .withBody("{\"name\":\"Alice\"}").execute();

        // ===== EXAMPLE 4: A/B TEST CONFIG =====
        System.out.println("\n\n===== EXAMPLE 4: A/B TEST CONFIG CLONING =====\n");

        ExperimentConfig baseExperiment = new ExperimentConfig("Checkout Flow Optimization")
            .addParam("button_color", "blue")
            .addParam("show_discount", true)
            .addSegment("US")
            .addSegment("premium_users")
            .addMetric("conversion_rate", "ratio")
            .addMetric("revenue_per_user", "mean");

        System.out.println("Base experiment:");
        System.out.println(baseExperiment);

        System.out.println("\nVariant A (green button):");
        ExperimentConfig variantA = baseExperiment.deepClone()
            .withName("Checkout Flow - Green Button")
            .addParam("button_color", "green")
            .withTraffic(25.0);
        System.out.println(variantA);

        System.out.println("\nVariant B (no discount):");
        ExperimentConfig variantB = baseExperiment.deepClone()
            .withName("Checkout Flow - No Discount")
            .addParam("show_discount", false)
            .withTraffic(25.0);
        System.out.println(variantB);

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Infrastructure config (Kubernetes, Terraform, Docker)");
        System.out.println("• Document templates (Google Docs, Notion, Confluence)");
        System.out.println("• API client libraries (base request cloning)");
        System.out.println("• A/B testing platforms (experiment variants)");
    }
}
