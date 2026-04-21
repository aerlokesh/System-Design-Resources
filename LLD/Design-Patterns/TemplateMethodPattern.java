import java.time.*;
import java.util.*;

// ==================== TEMPLATE METHOD PATTERN ====================
// Definition: Defines the skeleton of an algorithm in a method, deferring some steps
// to subclasses. Lets subclasses redefine certain steps without changing the algorithm's structure.
//
// REAL System Design Interview Examples:
// 1. Data Pipeline ETL - Extract→Transform→Load with different sources/sinks
// 2. Authentication Flow - Same structure, different providers (OAuth, SAML, LDAP)
// 3. Report Generation - Same steps, different formats (PDF, CSV, HTML)
// 4. API Request Handling - Parse→Validate→Process→Respond template
// 5. Deployment Pipeline - Build→Test→Deploy with different targets
//
// Interview Use Cases:
// - Design ETL Pipeline: Common flow, pluggable stages
// - Design Auth System: Unified flow for OAuth, SAML, JWT
// - Design CI/CD Pipeline: Standard stages, different implementations
// - Design Data Export: Same pipeline, different output formats
// - Design Notification System: Common send flow, different channels

// ==================== EXAMPLE 1: DATA PIPELINE ETL ====================
// Used in: Apache Spark, Apache Beam, AWS Glue, Airflow
// Interview Question: Design a data processing pipeline

abstract class DataPipeline {
    // TEMPLATE METHOD - defines the skeleton
    public final void runPipeline(String pipelineName) {
        System.out.printf("%n🔄 Pipeline '%s' [%s] starting...%n", pipelineName, getSourceType());
        System.out.println("─".repeat(50));

        Map<String, Object> rawData = extract();
        List<Map<String, Object>> transformedData = transform(rawData);
        validate(transformedData);
        int recordsLoaded = load(transformedData);
        onComplete(pipelineName, recordsLoaded);

        System.out.println("─".repeat(50));
        System.out.printf("✅ Pipeline '%s' complete: %d records processed%n", pipelineName, recordsLoaded);
    }

    // Abstract steps - subclasses MUST implement
    protected abstract Map<String, Object> extract();
    protected abstract List<Map<String, Object>> transform(Map<String, Object> rawData);
    protected abstract int load(List<Map<String, Object>> data);
    protected abstract String getSourceType();

    // Hook methods - subclasses CAN override
    protected void validate(List<Map<String, Object>> data) {
        System.out.printf("   ✔️  Validated %d records (default validation)%n", data.size());
    }

    protected void onComplete(String pipelineName, int recordCount) {
        System.out.printf("   📊 Pipeline stats: %d records loaded%n", recordCount);
    }
}

class MySQLToS3Pipeline extends DataPipeline {
    @Override
    protected Map<String, Object> extract() {
        System.out.println("   📥 [Extract] Querying MySQL: SELECT * FROM orders WHERE date > '2024-01-01'");
        Map<String, Object> data = new HashMap<>();
        data.put("orders", Arrays.asList(
            Map.of("id", 1, "amount", 99.99, "status", "completed"),
            Map.of("id", 2, "amount", 149.50, "status", "pending"),
            Map.of("id", 3, "amount", 29.99, "status", "completed")
        ));
        return data;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> transform(Map<String, Object> rawData) {
        System.out.println("   🔄 [Transform] Filtering completed orders, adding computed fields...");
        List<Map<String, Object>> orders = (List<Map<String, Object>>) rawData.get("orders");
        List<Map<String, Object>> transformed = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            if ("completed".equals(order.get("status"))) {
                Map<String, Object> row = new HashMap<>(order);
                row.put("tax", (double) order.get("amount") * 0.08);
                transformed.add(row);
            }
        }
        System.out.printf("   🔄 [Transform] %d → %d records after filtering%n", orders.size(), transformed.size());
        return transformed;
    }

    @Override
    protected int load(List<Map<String, Object>> data) {
        System.out.printf("   📤 [Load] Writing %d records to S3: s3://data-lake/orders/parquet/%n", data.size());
        return data.size();
    }

    @Override
    protected String getSourceType() { return "MySQL → S3"; }
}

class KafkaToElasticsearchPipeline extends DataPipeline {
    @Override
    protected Map<String, Object> extract() {
        System.out.println("   📥 [Extract] Consuming from Kafka topic 'user-events' (batch: 1000 msgs)");
        Map<String, Object> data = new HashMap<>();
        data.put("events", Arrays.asList(
            Map.of("userId", "u1", "event", "page_view", "page", "/products"),
            Map.of("userId", "u2", "event", "add_to_cart", "productId", "P100"),
            Map.of("userId", "u1", "event", "purchase", "orderId", "O200")
        ));
        return data;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> transform(Map<String, Object> rawData) {
        System.out.println("   🔄 [Transform] Enriching events with user profiles, sessionizing...");
        List<Map<String, Object>> events = (List<Map<String, Object>>) rawData.get("events");
        List<Map<String, Object>> transformed = new ArrayList<>();
        for (Map<String, Object> event : events) {
            Map<String, Object> enriched = new HashMap<>(event);
            enriched.put("timestamp", LocalDateTime.now().toString());
            enriched.put("session_id", "sess-" + event.get("userId"));
            transformed.add(enriched);
        }
        return transformed;
    }

    @Override
    protected int load(List<Map<String, Object>> data) {
        System.out.printf("   📤 [Load] Bulk indexing %d documents to Elasticsearch index 'user-events'%n", data.size());
        return data.size();
    }

    @Override
    protected void validate(List<Map<String, Object>> data) {
        System.out.printf("   ✔️  Schema validation: %d events match event schema%n", data.size());
    }

    @Override
    protected String getSourceType() { return "Kafka → Elasticsearch"; }
}

class APIToDataWarehousePipeline extends DataPipeline {
    @Override
    protected Map<String, Object> extract() {
        System.out.println("   📥 [Extract] Calling REST API: GET /api/v1/analytics?period=daily");
        Map<String, Object> data = new HashMap<>();
        data.put("metrics", Arrays.asList(
            Map.of("date", "2024-01-15", "dau", 50000, "revenue", 125000.0),
            Map.of("date", "2024-01-16", "dau", 52000, "revenue", 131000.0)
        ));
        return data;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> transform(Map<String, Object> rawData) {
        System.out.println("   🔄 [Transform] Computing ARPU, growth rate, moving averages...");
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) rawData.get("metrics");
        List<Map<String, Object>> transformed = new ArrayList<>();
        for (Map<String, Object> metric : metrics) {
            Map<String, Object> enriched = new HashMap<>(metric);
            enriched.put("arpu", (double) metric.get("revenue") / (int) metric.get("dau"));
            transformed.add(enriched);
        }
        return transformed;
    }

    @Override
    protected int load(List<Map<String, Object>> data) {
        System.out.printf("   📤 [Load] Inserting %d rows into Redshift table 'daily_metrics'%n", data.size());
        return data.size();
    }

    @Override
    protected String getSourceType() { return "API → Redshift"; }
}

// ==================== EXAMPLE 2: AUTHENTICATION FLOW ====================
// Used in: OAuth2, SAML, LDAP, JWT authentication
// Interview Question: Design a user authentication system

abstract class AuthenticationFlow {
    // TEMPLATE METHOD
    public final Map<String, Object> authenticate(String credentials) {
        System.out.printf("%n🔐 Authentication [%s] starting...%n", getProviderName());

        Map<String, Object> parsedCredentials = parseCredentials(credentials);
        boolean valid = validateCredentials(parsedCredentials);

        if (!valid) {
            onAuthFailure(parsedCredentials);
            throw new RuntimeException("Authentication failed");
        }

        Map<String, Object> userInfo = fetchUserInfo(parsedCredentials);
        String token = generateToken(userInfo);
        onAuthSuccess(userInfo);

        Map<String, Object> result = new HashMap<>(userInfo);
        result.put("token", token);
        System.out.printf("✅ Authentication successful for %s%n", userInfo.get("email"));
        return result;
    }

    protected abstract Map<String, Object> parseCredentials(String credentials);
    protected abstract boolean validateCredentials(Map<String, Object> credentials);
    protected abstract Map<String, Object> fetchUserInfo(Map<String, Object> credentials);
    protected abstract String getProviderName();

    // Hook methods with defaults
    protected String generateToken(Map<String, Object> userInfo) {
        String token = "jwt_" + UUID.randomUUID().toString().substring(0, 16);
        System.out.printf("   🎟️  Generated JWT token: %s...%n", token.substring(0, 12));
        return token;
    }

    protected void onAuthSuccess(Map<String, Object> userInfo) {
        System.out.printf("   📊 Logged successful auth for %s%n", userInfo.get("email"));
    }

    protected void onAuthFailure(Map<String, Object> credentials) {
        System.out.println("   ⚠️  Authentication failure logged");
    }
}

class OAuthFlow extends AuthenticationFlow {
    @Override
    protected Map<String, Object> parseCredentials(String credentials) {
        System.out.println("   🔑 [OAuth] Parsing authorization code: " + credentials.substring(0, 10) + "...");
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("auth_code", credentials);
        parsed.put("provider", "google");
        return parsed;
    }

    @Override
    protected boolean validateCredentials(Map<String, Object> credentials) {
        System.out.println("   🔑 [OAuth] Exchanging auth code for access token with Google...");
        System.out.println("   🔑 [OAuth] Verifying token with Google OAuth2 server...");
        return true;
    }

    @Override
    protected Map<String, Object> fetchUserInfo(Map<String, Object> credentials) {
        System.out.println("   👤 [OAuth] Fetching user profile from Google API...");
        Map<String, Object> user = new HashMap<>();
        user.put("email", "alice@gmail.com");
        user.put("name", "Alice Johnson");
        user.put("provider", "google");
        return user;
    }

    @Override
    protected String getProviderName() { return "OAuth2/Google"; }
}

class LDAPFlow extends AuthenticationFlow {
    @Override
    protected Map<String, Object> parseCredentials(String credentials) {
        String[] parts = credentials.split(":");
        System.out.println("   🔑 [LDAP] Parsing username/password...");
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("username", parts[0]);
        parsed.put("password", parts.length > 1 ? parts[1] : "");
        return parsed;
    }

    @Override
    protected boolean validateCredentials(Map<String, Object> credentials) {
        System.out.printf("   🔑 [LDAP] Binding to LDAP server as %s...%n", credentials.get("username"));
        System.out.println("   🔑 [LDAP] Credentials verified against Active Directory");
        return true;
    }

    @Override
    protected Map<String, Object> fetchUserInfo(Map<String, Object> credentials) {
        System.out.printf("   👤 [LDAP] Fetching user attributes for %s...%n", credentials.get("username"));
        Map<String, Object> user = new HashMap<>();
        user.put("email", credentials.get("username") + "@company.com");
        user.put("name", "Bob Smith");
        user.put("department", "Engineering");
        user.put("groups", Arrays.asList("developers", "admin"));
        return user;
    }

    @Override
    protected String getProviderName() { return "LDAP/ActiveDirectory"; }
}

// ==================== EXAMPLE 3: REPORT GENERATION ====================
// Used in: Business intelligence, Analytics dashboards
// Interview Question: Design a report generation system

abstract class ReportGenerator {
    // TEMPLATE METHOD
    public final String generateReport(String reportName, Map<String, Object> params) {
        System.out.printf("%n📋 Generating %s report '%s'%n", getFormat(), reportName);

        List<Map<String, Object>> data = fetchData(params);
        List<Map<String, Object>> processedData = processData(data);
        String header = createHeader(reportName);
        String body = createBody(processedData);
        String footer = createFooter(processedData.size());
        String report = assembleReport(header, body, footer);

        System.out.printf("✅ Report generated: %d records in %s format%n", processedData.size(), getFormat());
        return report;
    }

    protected abstract String getFormat();
    protected abstract String createHeader(String title);
    protected abstract String createBody(List<Map<String, Object>> data);
    protected abstract String createFooter(int totalRecords);

    // Common steps with default implementations
    protected List<Map<String, Object>> fetchData(Map<String, Object> params) {
        System.out.println("   📥 Fetching data from database...");
        return Arrays.asList(
            Map.of("name", "Product A", "revenue", 50000, "units", 500),
            Map.of("name", "Product B", "revenue", 75000, "units", 300),
            Map.of("name", "Product C", "revenue", 30000, "units", 800)
        );
    }

    protected List<Map<String, Object>> processData(List<Map<String, Object>> data) {
        System.out.println("   🔄 Processing and sorting data...");
        return data; // Subclasses can override for custom processing
    }

    protected String assembleReport(String header, String body, String footer) {
        return header + "\n" + body + "\n" + footer;
    }
}

class CSVReportGenerator extends ReportGenerator {
    @Override
    protected String getFormat() { return "CSV"; }

    @Override
    protected String createHeader(String title) {
        return "name,revenue,units";
    }

    @Override
    protected String createBody(List<Map<String, Object>> data) {
        StringBuilder csv = new StringBuilder();
        for (Map<String, Object> row : data) {
            csv.append(String.format("%s,%s,%s%n", row.get("name"), row.get("revenue"), row.get("units")));
        }
        System.out.println("   📝 Generated CSV body (" + data.size() + " rows)");
        return csv.toString();
    }

    @Override
    protected String createFooter(int totalRecords) {
        return "# Total Records: " + totalRecords;
    }
}

class HTMLReportGenerator extends ReportGenerator {
    @Override
    protected String getFormat() { return "HTML"; }

    @Override
    protected String createHeader(String title) {
        return "<html><head><title>" + title + "</title></head><body><h1>" + title + "</h1>";
    }

    @Override
    protected String createBody(List<Map<String, Object>> data) {
        StringBuilder html = new StringBuilder("<table><tr><th>Name</th><th>Revenue</th><th>Units</th></tr>");
        for (Map<String, Object> row : data) {
            html.append(String.format("<tr><td>%s</td><td>$%s</td><td>%s</td></tr>",
                row.get("name"), row.get("revenue"), row.get("units")));
        }
        html.append("</table>");
        System.out.println("   📝 Generated HTML table (" + data.size() + " rows)");
        return html.toString();
    }

    @Override
    protected String createFooter(int totalRecords) {
        return "<p>Total records: " + totalRecords + "</p></body></html>";
    }
}

class JSONReportGenerator extends ReportGenerator {
    @Override
    protected String getFormat() { return "JSON"; }

    @Override
    protected String createHeader(String title) {
        return "{\"report\": \"" + title + "\", \"data\": [";
    }

    @Override
    protected String createBody(List<Map<String, Object>> data) {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            json.append(String.format("{\"name\":\"%s\",\"revenue\":%s,\"units\":%s}",
                row.get("name"), row.get("revenue"), row.get("units")));
            if (i < data.size() - 1) json.append(",");
        }
        System.out.println("   📝 Generated JSON array (" + data.size() + " objects)");
        return json.toString();
    }

    @Override
    protected String createFooter(int totalRecords) {
        return "], \"totalRecords\": " + totalRecords + "}";
    }
}

// ==================== EXAMPLE 4: CI/CD DEPLOYMENT PIPELINE ====================
// Used in: Jenkins, GitHub Actions, GitLab CI
// Interview Question: Design a CI/CD pipeline system

abstract class DeploymentPipeline {
    // TEMPLATE METHOD
    public final boolean deploy(String version) {
        System.out.printf("%n🚀 Deployment Pipeline [%s] v%s%n", getTargetName(), version);
        System.out.println("─".repeat(50));

        boolean built = buildArtifact(version);
        if (!built) return false;

        boolean tested = runTests();
        if (!tested) return false;

        boolean securityPassed = securityScan();
        if (!securityPassed) return false;

        boolean deployed = deployToTarget(version);
        if (!deployed) return false;

        runHealthCheck();
        onDeploySuccess(version);

        System.out.println("─".repeat(50));
        System.out.printf("✅ v%s deployed to %s successfully%n", version, getTargetName());
        return true;
    }

    protected abstract boolean buildArtifact(String version);
    protected abstract boolean deployToTarget(String version);
    protected abstract String getTargetName();

    // Common steps with defaults
    protected boolean runTests() {
        System.out.println("   🧪 Running unit tests... 142/142 passed");
        System.out.println("   🧪 Running integration tests... 38/38 passed");
        return true;
    }

    protected boolean securityScan() {
        System.out.println("   🔒 Security scan: 0 critical, 0 high, 2 medium vulnerabilities");
        return true;
    }

    protected void runHealthCheck() {
        System.out.println("   🏥 Health check passed: all endpoints responding");
    }

    protected void onDeploySuccess(String version) {
        System.out.printf("   📊 Deployment metrics recorded for v%s%n", version);
    }
}

class DockerDeployment extends DeploymentPipeline {
    @Override
    protected boolean buildArtifact(String version) {
        System.out.printf("   🐳 [Docker] Building image: myapp:%s%n", version);
        System.out.printf("   🐳 [Docker] Pushing to ECR: 123456.dkr.ecr.us-east-1.amazonaws.com/myapp:%s%n", version);
        return true;
    }

    @Override
    protected boolean deployToTarget(String version) {
        System.out.printf("   🐳 [Docker] Updating ECS service with image tag: %s%n", version);
        System.out.println("   🐳 [Docker] Rolling update: 0/3 → 1/3 → 2/3 → 3/3");
        return true;
    }

    @Override
    protected String getTargetName() { return "ECS/Docker"; }
}

class KubernetesDeployment extends DeploymentPipeline {
    @Override
    protected boolean buildArtifact(String version) {
        System.out.printf("   ☸️  [K8s] Building container image: myapp:%s%n", version);
        System.out.printf("   ☸️  [K8s] Helm chart packaged: myapp-chart-%s.tgz%n", version);
        return true;
    }

    @Override
    protected boolean deployToTarget(String version) {
        System.out.printf("   ☸️  [K8s] helm upgrade myapp ./chart --set image.tag=%s%n", version);
        System.out.println("   ☸️  [K8s] Rollout status: deployment/myapp successfully rolled out");
        return true;
    }

    @Override
    protected void runHealthCheck() {
        System.out.println("   🏥 [K8s] Liveness probe: OK | Readiness probe: OK");
        System.out.println("   🏥 [K8s] Pod status: 3/3 Running");
    }

    @Override
    protected String getTargetName() { return "Kubernetes"; }
}

class LambdaDeployment extends DeploymentPipeline {
    @Override
    protected boolean buildArtifact(String version) {
        System.out.printf("   ⚡ [Lambda] Packaging function: myapp-lambda-%s.zip%n", version);
        System.out.println("   ⚡ [Lambda] Uploading to S3: s3://deploy-bucket/lambda/");
        return true;
    }

    @Override
    protected boolean deployToTarget(String version) {
        System.out.printf("   ⚡ [Lambda] Updating function code to version %s%n", version);
        System.out.printf("   ⚡ [Lambda] Publishing version and updating alias 'prod' → v%s%n", version);
        return true;
    }

    @Override
    protected boolean runTests() {
        System.out.println("   🧪 Running unit tests... 42/42 passed");
        System.out.println("   🧪 Running lambda-specific integration tests... 12/12 passed");
        return true;
    }

    @Override
    protected String getTargetName() { return "AWS Lambda"; }
}

// ==================== DEMO ====================

public class TemplateMethodPattern {
    public static void main(String[] args) {
        System.out.println("========== TEMPLATE METHOD PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: DATA PIPELINE ETL =====
        System.out.println("===== EXAMPLE 1: DATA PIPELINE ETL =====");
        new MySQLToS3Pipeline().runPipeline("daily-orders-export");
        new KafkaToElasticsearchPipeline().runPipeline("user-events-indexing");
        new APIToDataWarehousePipeline().runPipeline("daily-metrics-sync");

        // ===== EXAMPLE 2: AUTHENTICATION FLOWS =====
        System.out.println("\n\n===== EXAMPLE 2: AUTHENTICATION FLOWS =====");
        new OAuthFlow().authenticate("google_auth_code_abc123xyz");
        new LDAPFlow().authenticate("bob.smith:password123");

        // ===== EXAMPLE 3: REPORT GENERATION =====
        System.out.println("\n\n===== EXAMPLE 3: REPORT GENERATION =====");
        Map<String, Object> params = Map.of("period", "monthly");
        new CSVReportGenerator().generateReport("Sales Report", params);
        new HTMLReportGenerator().generateReport("Sales Report", params);
        new JSONReportGenerator().generateReport("Sales Report", params);

        // ===== EXAMPLE 4: CI/CD DEPLOYMENT =====
        System.out.println("\n\n===== EXAMPLE 4: CI/CD DEPLOYMENT PIPELINE =====");
        new DockerDeployment().deploy("2.1.0");
        new KubernetesDeployment().deploy("2.1.0");
        new LambdaDeployment().deploy("2.1.0");

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• ETL pipelines (Spark, Beam, Glue, Airflow)");
        System.out.println("• Authentication (OAuth2, LDAP, SAML, JWT)");
        System.out.println("• Report generation (PDF, CSV, HTML, JSON)");
        System.out.println("• CI/CD pipelines (Jenkins, GitHub Actions, GitLab CI)");
    }
}
