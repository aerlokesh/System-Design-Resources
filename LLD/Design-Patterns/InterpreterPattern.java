import java.util.*;

// ==================== INTERPRETER PATTERN ====================
// Definition: Given a language, define a representation for its grammar along with
// an interpreter that uses the representation to interpret sentences in the language.
//
// REAL System Design Interview Examples:
// 1. Query Language Parser - SQL WHERE clause, Elasticsearch DSL
// 2. Rule Engine - Business rules, access control policies
// 3. Expression Evaluator - Math expressions, formula engines
// 4. Routing Rules - API gateway routing, message routing
// 5. Search Query Parser - Google-style search queries
//
// Interview Use Cases:
// - Design Search Engine: Parse "price > 100 AND category = electronics"
// - Design Rule Engine: Evaluate business/discount rules
// - Design API Gateway: Route matching expressions
// - Design Access Control: Parse permission rules
// - Design Monitoring: Alert rule expressions

// ==================== EXAMPLE 1: SEARCH QUERY INTERPRETER ====================
// Used in: Elasticsearch, Lucene, database WHERE clauses
// Interview Question: Design a search/filter system

interface SearchExpression {
    boolean interpret(Map<String, Object> context);
    String toQueryString();
}

class EqualsExpression implements SearchExpression {
    private String field;
    private Object value;

    public EqualsExpression(String field, Object value) {
        this.field = field;
        this.value = value;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        Object actual = context.get(field);
        boolean result = value.equals(actual) || value.toString().equals(String.valueOf(actual));
        return result;
    }

    @Override
    public String toQueryString() {
        return field + " = " + (value instanceof String ? "'" + value + "'" : value);
    }
}

class GreaterThanExpression implements SearchExpression {
    private String field;
    private double value;

    public GreaterThanExpression(String field, double value) {
        this.field = field;
        this.value = value;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        Object actual = context.get(field);
        if (actual instanceof Number) {
            return ((Number) actual).doubleValue() > value;
        }
        return false;
    }

    @Override
    public String toQueryString() {
        return field + " > " + value;
    }
}

class LessThanExpression implements SearchExpression {
    private String field;
    private double value;

    public LessThanExpression(String field, double value) {
        this.field = field;
        this.value = value;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        Object actual = context.get(field);
        if (actual instanceof Number) {
            return ((Number) actual).doubleValue() < value;
        }
        return false;
    }

    @Override
    public String toQueryString() {
        return field + " < " + value;
    }
}

class ContainsExpression implements SearchExpression {
    private String field;
    private String value;

    public ContainsExpression(String field, String value) {
        this.field = field;
        this.value = value.toLowerCase();
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        Object actual = context.get(field);
        if (actual instanceof String) {
            return ((String) actual).toLowerCase().contains(value);
        }
        return false;
    }

    @Override
    public String toQueryString() {
        return field + " CONTAINS '" + value + "'";
    }
}

class AndExpression implements SearchExpression {
    private SearchExpression left;
    private SearchExpression right;

    public AndExpression(SearchExpression left, SearchExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        return left.interpret(context) && right.interpret(context);
    }

    @Override
    public String toQueryString() {
        return "(" + left.toQueryString() + " AND " + right.toQueryString() + ")";
    }
}

class OrExpression implements SearchExpression {
    private SearchExpression left;
    private SearchExpression right;

    public OrExpression(SearchExpression left, SearchExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        return left.interpret(context) || right.interpret(context);
    }

    @Override
    public String toQueryString() {
        return "(" + left.toQueryString() + " OR " + right.toQueryString() + ")";
    }
}

class NotExpression implements SearchExpression {
    private SearchExpression expression;

    public NotExpression(SearchExpression expression) {
        this.expression = expression;
    }

    @Override
    public boolean interpret(Map<String, Object> context) {
        return !expression.interpret(context);
    }

    @Override
    public String toQueryString() {
        return "NOT " + expression.toQueryString();
    }
}

// ==================== EXAMPLE 2: BUSINESS RULE ENGINE ====================
// Used in: Discount engines, pricing rules, eligibility checks
// Interview Question: Design a rule engine / discount system

interface BusinessRule {
    boolean evaluate(Map<String, Object> facts);
    String describe();
}

class ThresholdRule implements BusinessRule {
    private String factName;
    private String operator;
    private double threshold;

    public ThresholdRule(String factName, String operator, double threshold) {
        this.factName = factName;
        this.operator = operator;
        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(Map<String, Object> facts) {
        Object val = facts.get(factName);
        if (!(val instanceof Number)) return false;
        double actual = ((Number) val).doubleValue();
        switch (operator) {
            case ">": return actual > threshold;
            case "<": return actual < threshold;
            case ">=": return actual >= threshold;
            case "<=": return actual <= threshold;
            case "==": return actual == threshold;
            default: return false;
        }
    }

    @Override
    public String describe() { return factName + " " + operator + " " + threshold; }
}

class MembershipRule implements BusinessRule {
    private String factName;
    private Set<String> allowedValues;

    public MembershipRule(String factName, String... values) {
        this.factName = factName;
        this.allowedValues = new HashSet<>(Arrays.asList(values));
    }

    @Override
    public boolean evaluate(Map<String, Object> facts) {
        Object val = facts.get(factName);
        return val != null && allowedValues.contains(val.toString());
    }

    @Override
    public String describe() { return factName + " IN " + allowedValues; }
}

class CompositeRule implements BusinessRule {
    private List<BusinessRule> rules;
    private String operator; // "AND" or "OR"

    public CompositeRule(String operator, BusinessRule... rules) {
        this.operator = operator;
        this.rules = Arrays.asList(rules);
    }

    @Override
    public boolean evaluate(Map<String, Object> facts) {
        if ("AND".equals(operator)) {
            return rules.stream().allMatch(r -> r.evaluate(facts));
        } else {
            return rules.stream().anyMatch(r -> r.evaluate(facts));
        }
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < rules.size(); i++) {
            sb.append(rules.get(i).describe());
            if (i < rules.size() - 1) sb.append(" ").append(operator).append(" ");
        }
        return sb.append(")").toString();
    }
}

class RuleEngine {
    private Map<String, BusinessRule> rules = new LinkedHashMap<>();
    private Map<String, String> ruleActions = new LinkedHashMap<>();

    public void addRule(String name, BusinessRule rule, String action) {
        rules.put(name, rule);
        ruleActions.put(name, action);
        System.out.printf("   📜 Added rule '%s': %s → %s%n", name, rule.describe(), action);
    }

    public List<String> evaluate(Map<String, Object> facts) {
        List<String> triggeredActions = new ArrayList<>();
        System.out.printf("   🔍 Evaluating %d rules against facts: %s%n", rules.size(), facts);

        for (Map.Entry<String, BusinessRule> entry : rules.entrySet()) {
            boolean result = entry.getValue().evaluate(facts);
            String action = ruleActions.get(entry.getKey());
            if (result) {
                triggeredActions.add(action);
                System.out.printf("      ✅ '%s' → MATCHED → %s%n", entry.getKey(), action);
            } else {
                System.out.printf("      ❌ '%s' → not matched%n", entry.getKey());
            }
        }
        return triggeredActions;
    }
}

// ==================== EXAMPLE 3: ROUTING RULE INTERPRETER ====================
// Used in: API Gateways, message routers, load balancers
// Interview Question: Design an API gateway routing system

interface RoutingRule {
    boolean matches(Map<String, String> request);
    String describe();
}

class PathMatchRule implements RoutingRule {
    private String pattern;

    public PathMatchRule(String pattern) { this.pattern = pattern; }

    @Override
    public boolean matches(Map<String, String> request) {
        String path = request.getOrDefault("path", "");
        if (pattern.endsWith("*")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return path.equals(pattern);
    }

    @Override
    public String describe() { return "path = '" + pattern + "'"; }
}

class HeaderMatchRule implements RoutingRule {
    private String header;
    private String value;

    public HeaderMatchRule(String header, String value) {
        this.header = header;
        this.value = value;
    }

    @Override
    public boolean matches(Map<String, String> request) {
        return value.equals(request.get("header." + header));
    }

    @Override
    public String describe() { return "header[" + header + "] = '" + value + "'"; }
}

class MethodMatchRule implements RoutingRule {
    private String method;

    public MethodMatchRule(String method) { this.method = method; }

    @Override
    public boolean matches(Map<String, String> request) {
        return method.equalsIgnoreCase(request.getOrDefault("method", ""));
    }

    @Override
    public String describe() { return "method = '" + method + "'"; }
}

class CompositeRoutingRule implements RoutingRule {
    private List<RoutingRule> rules;

    public CompositeRoutingRule(RoutingRule... rules) {
        this.rules = Arrays.asList(rules);
    }

    @Override
    public boolean matches(Map<String, String> request) {
        return rules.stream().allMatch(r -> r.matches(request));
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            sb.append(rules.get(i).describe());
            if (i < rules.size() - 1) sb.append(" AND ");
        }
        return sb.toString();
    }
}

class APIRouter {
    private List<Map.Entry<RoutingRule, String>> routes = new ArrayList<>();

    public void addRoute(RoutingRule rule, String backend) {
        routes.add(new AbstractMap.SimpleEntry<>(rule, backend));
        System.out.printf("   🔀 Route added: IF %s THEN → %s%n", rule.describe(), backend);
    }

    public String route(Map<String, String> request) {
        System.out.printf("   🌐 Routing request: %s%n", request);
        for (Map.Entry<RoutingRule, String> route : routes) {
            if (route.getKey().matches(request)) {
                System.out.printf("      ✅ Matched: %s → %s%n", route.getKey().describe(), route.getValue());
                return route.getValue();
            }
        }
        System.out.println("      ❌ No route matched, using default");
        return "default-backend";
    }
}

// ==================== DEMO ====================

public class InterpreterPattern {
    public static void main(String[] args) {
        System.out.println("========== INTERPRETER PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: SEARCH QUERY =====
        System.out.println("===== EXAMPLE 1: SEARCH QUERY INTERPRETER (Elasticsearch) =====\n");

        // Build query: category = 'electronics' AND price > 100 AND (brand = 'Apple' OR brand = 'Samsung')
        SearchExpression query = new AndExpression(
            new AndExpression(
                new EqualsExpression("category", "electronics"),
                new GreaterThanExpression("price", 100)
            ),
            new OrExpression(
                new EqualsExpression("brand", "Apple"),
                new EqualsExpression("brand", "Samsung")
            )
        );
        System.out.println("Query: " + query.toQueryString());

        List<Map<String, Object>> products = Arrays.asList(
            Map.of("name", "iPhone 15", "category", "electronics", "price", 999, "brand", "Apple"),
            Map.of("name", "Galaxy S24", "category", "electronics", "price", 799, "brand", "Samsung"),
            Map.of("name", "Pixel 8", "category", "electronics", "price", 699, "brand", "Google"),
            Map.of("name", "USB Cable", "category", "electronics", "price", 10, "brand", "Generic"),
            Map.of("name", "Novel", "category", "books", "price", 15, "brand", "Publisher")
        );

        System.out.println("\nResults:");
        for (Map<String, Object> product : products) {
            boolean match = query.interpret(product);
            System.out.printf("   %s %s: %s%n", match ? "✅" : "❌", product.get("name"), product);
        }

        // Another query with NOT
        System.out.println();
        SearchExpression notExpensive = new AndExpression(
            new EqualsExpression("category", "electronics"),
            new NotExpression(new GreaterThanExpression("price", 500))
        );
        System.out.println("Query: " + notExpensive.toQueryString());
        for (Map<String, Object> product : products) {
            boolean match = notExpensive.interpret(product);
            if (match) System.out.printf("   ✅ %s ($%s)%n", product.get("name"), product.get("price"));
        }

        // ===== EXAMPLE 2: BUSINESS RULE ENGINE =====
        System.out.println("\n\n===== EXAMPLE 2: BUSINESS RULE ENGINE (Discount/Pricing) =====\n");

        RuleEngine engine = new RuleEngine();

        engine.addRule("premium_discount",
            new CompositeRule("AND",
                new MembershipRule("tier", "gold", "platinum"),
                new ThresholdRule("order_total", ">=", 100)
            ),
            "Apply 20% discount"
        );

        engine.addRule("free_shipping",
            new CompositeRule("OR",
                new ThresholdRule("order_total", ">=", 50),
                new MembershipRule("tier", "platinum")
            ),
            "Free shipping"
        );

        engine.addRule("flash_sale",
            new CompositeRule("AND",
                new MembershipRule("category", "electronics"),
                new ThresholdRule("price", "<", 500)
            ),
            "Flash sale: extra 10% off"
        );

        System.out.println("\n--- Customer 1: Gold member, $150 order ---");
        List<String> actions1 = engine.evaluate(Map.of("tier", "gold", "order_total", 150.0, "category", "electronics", "price", 299.0));
        System.out.println("   🎯 Actions: " + actions1);

        System.out.println("\n--- Customer 2: Silver member, $30 order ---");
        List<String> actions2 = engine.evaluate(Map.of("tier", "silver", "order_total", 30.0, "category", "books", "price", 30.0));
        System.out.println("   🎯 Actions: " + actions2);

        // ===== EXAMPLE 3: API GATEWAY ROUTING =====
        System.out.println("\n\n===== EXAMPLE 3: API GATEWAY ROUTING RULES =====\n");

        APIRouter router = new APIRouter();
        router.addRoute(
            new CompositeRoutingRule(new PathMatchRule("/api/v2/*"), new MethodMatchRule("GET")),
            "api-v2-read-service"
        );
        router.addRoute(
            new CompositeRoutingRule(new PathMatchRule("/api/v2/*"), new MethodMatchRule("POST")),
            "api-v2-write-service"
        );
        router.addRoute(new PathMatchRule("/api/v1/*"), "api-v1-legacy-service");
        router.addRoute(
            new CompositeRoutingRule(new PathMatchRule("/admin/*"), new HeaderMatchRule("X-Admin-Token", "secret")),
            "admin-service"
        );

        System.out.println();
        router.route(Map.of("path", "/api/v2/users", "method", "GET"));
        System.out.println();
        router.route(Map.of("path", "/api/v2/orders", "method", "POST"));
        System.out.println();
        router.route(Map.of("path", "/api/v1/legacy", "method", "GET"));
        System.out.println();
        router.route(Map.of("path", "/admin/dashboard", "method", "GET", "header.X-Admin-Token", "secret"));

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Search query parsing (Elasticsearch, SQL WHERE)");
        System.out.println("• Business rule engines (discount, eligibility)");
        System.out.println("• API gateway routing (Kong, Nginx, Envoy)");
        System.out.println("• Access control policies (RBAC/ABAC rules)");
    }
}
