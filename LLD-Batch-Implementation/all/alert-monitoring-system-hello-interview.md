# Alert Monitoring System - HELLO Interview Framework

> **Companies**: Microsoft (Azure Monitor), Amazon (CloudWatch), Datadog, PagerDuty, Splunk  
> **Difficulty**: Hard  
> **Pattern**: Strategy (window types) + Observer (action dispatch) + Chain of Responsibility (rule evaluation) + Template Method (window base)  
> **Time**: 45 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is an Alert Monitoring System?

A system that ingests **alerts** (events from services), evaluates them against **rules** (conditions with windows, thresholds, and time constraints), and triggers **actions** (email, SMS, Slack, PagerDuty) when conditions are met. The key challenge is supporting multiple **windowing strategies** — sliding windows (continuously moving time range) and tumbling windows (fixed non-overlapping intervals) — while keeping the design extensible for new rule types and action types.

**Real-world**: Azure Monitor Alerts, AWS CloudWatch Alarms, Datadog Monitors, PagerDuty Event Rules, Prometheus Alertmanager, Grafana Alerting, Splunk Alerts.

### For L63 Microsoft Interview

1. **Strategy Pattern**: Window types (SlidingWindow, TumblingWindow) — swappable evaluation strategies
2. **Observer Pattern**: Actions triggered when rule fires — EmailAction, SMSAction, SlackAction all notified independently
3. **Chain of Responsibility**: Multiple rules evaluated in sequence — alert passes through rule chain
4. **Template Method**: Base window class defines algorithm skeleton, subclasses implement specific window logic
5. **SOLID principles**: Every design decision justified — SRP, OCP, DIP throughout
6. **Extensibility**: New window type = new class, new action = new class, new rule condition = new class
7. **Testability**: All components testable in isolation — inject mock clock, mock actions, mock alert source

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What's an alert?" → An event with service name, metric name, value, severity, and timestamp
- "What window types?" → Sliding window (last N minutes continuously) and Tumbling window (fixed intervals, e.g., every 5 min)
- "What conditions?" → Threshold (value > X), count (N alerts in window), rate (alerts/sec > X)
- "What actions?" → Email, SMS, Slack webhook, PagerDuty — configurable per rule
- "Deduplication?" → Same rule shouldn't fire repeatedly for same condition — cooldown period
- "Multi-service?" → Alerts from different services, rules can filter by service

### Functional Requirements (P0)
1. **Ingest alerts** from various services (service name, metric, value, severity, timestamp)
2. **Define rules** with: condition (threshold/count/rate), window type (sliding/tumbling), window duration, and actions
3. **Evaluate rules** against incoming alerts in real-time
4. **Trigger actions** (email, SMS, Slack) when rule conditions are met
5. **Window management**: Sliding window (continuous) and Tumbling window (fixed intervals)
6. **Cooldown**: Prevent same rule from firing repeatedly within a cooldown period

### Functional Requirements (P1)
- Rule priority / severity-based routing
- Alert aggregation (group similar alerts)
- Escalation (if not acknowledged in N minutes, escalate)
- Dashboard / rule status API

### Non-Functional
- O(1) alert ingestion
- O(R) rule evaluation per alert (R = number of rules)
- Thread-safe for concurrent alert ingestion
- Extensible for new window types, conditions, and actions
- Testable with deterministic (injectable) clock

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌───────────────────────────────────────────────────────────────────┐
│                      AlertMonitoringSystem                         │
│  - rules: List<AlertRule>                                         │
│  - ruleEngine: RuleEngine                                         │
│  - ingestAlert(alert)                                             │
│  - addRule(rule)                                                  │
│  - removeRule(ruleId)                                             │
└──────────────┬────────────────────────────────────────────────────┘
               │ uses
     ┌─────────┼──────────┬──────────────┬──────────────┐
     ▼         ▼          ▼              ▼              ▼
┌──────────┐ ┌────────────────┐ ┌─────────────────┐ ┌──────────────┐
│  Alert   │ │   AlertRule    │ │  WindowStrategy  │ │    Action    │
│          │ │                │ │  (interface)     │ │  (interface) │
│ - id     │ │ - id           │ ├─────────────────┤ ├──────────────┤
│ - service│ │ - name         │ │ SlidingWindow   │ │ EmailAction  │
│ - metric │ │ - serviceFilter│ │ TumblingWindow  │ │ SMSAction    │
│ - value  │ │ - metricFilter │ │ CountWindow     │ │ SlackAction  │
│ - severity│ │ - window       │ └─────────────────┘ │ PagerDuty   │
│ - timestamp│ │ - condition    │                     │   Action    │
│ - metadata│ │ - actions      │ ┌─────────────────┐ │ LogAction   │
└──────────┘ │ - cooldown     │ │  Condition       │ └──────────────┘
             │ - lastFiredAt  │ │  (interface)     │
             └────────────────┘ ├─────────────────┤
                                │ ThresholdCondition│
                                │ CountCondition    │
                                │ RateCondition     │
                                │ CompositeCondition│
                                └─────────────────┘
```

### Class: Alert
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique alert ID |
| service | String | "PaymentService", "AuthService" |
| metric | String | "cpu_usage", "error_rate", "latency_p99" |
| value | double | Metric value (95.5, 0.05, 250.0) |
| severity | Severity | LOW, MEDIUM, HIGH, CRITICAL |
| timestamp | long | When the alert was generated |
| metadata | Map<String, String> | Additional key-value pairs |

### Class: AlertRule
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Rule ID |
| name | String | "High CPU Alert" |
| serviceFilter | String | Which service this rule applies to (null = all) |
| metricFilter | String | Which metric to monitor (null = all) |
| window | WindowStrategy | Sliding or Tumbling window |
| condition | Condition | Threshold, count, or rate check |
| actions | List\<Action\> | What to do when rule fires |
| cooldownMs | long | Minimum time between firings |
| lastFiredAt | long | Last time this rule triggered |
| enabled | boolean | Is this rule active? |

### Enum: Severity
```
LOW(0) → Informational
MEDIUM(1) → Warning
HIGH(2) → Needs attention
CRITICAL(3) → Immediate action required
```

---

## 3️⃣ API Design

```java
class AlertMonitoringSystem {
    /** Ingest an alert from a service. Evaluates all matching rules. */
    List<RuleResult> ingestAlert(Alert alert);
    
    /** Add a new monitoring rule. */
    void addRule(AlertRule rule);
    
    /** Remove a rule by ID. */
    void removeRule(String ruleId);
    
    /** Enable/disable a rule. */
    void setRuleEnabled(String ruleId, boolean enabled);
    
    /** Get all rules and their current status. */
    List<RuleStatus> getRuleStatuses();
    
    /** Build a rule fluently. */
    static AlertRuleBuilder ruleBuilder();
}
```

### Fluent Rule Builder (Ease of Use)
```java
AlertRule rule = AlertMonitoringSystem.ruleBuilder()
    .id("RULE-001")
    .name("High CPU Alert")
    .service("PaymentService")
    .metric("cpu_usage")
    .slidingWindow(Duration.ofMinutes(5))
    .threshold(greaterThan(90.0))
    .action(new EmailAction("ops@company.com"))
    .action(new SlackAction("#alerts-critical"))
    .cooldown(Duration.ofMinutes(10))
    .build();
```

---

## 4️⃣ Data Flow

### Scenario 1: Threshold Alert with Sliding Window

```
Rule: "If cpu_usage > 90% in a 5-minute sliding window for PaymentService → email + Slack"

Timeline:
  T+0m: Alert(PaymentService, cpu_usage, 85.0)
  T+1m: Alert(PaymentService, cpu_usage, 88.0)
  T+2m: Alert(PaymentService, cpu_usage, 92.0)  ← exceeds threshold!
  T+3m: Alert(PaymentService, cpu_usage, 95.0)
  T+4m: Alert(PaymentService, cpu_usage, 91.0)

Processing at T+2m:
  │
  ├─ ingestAlert(Alert("PaymentService", "cpu_usage", 92.0, T+2m))
  │
  ├─ RuleEngine evaluates RULE-001:
  │   ├─ serviceFilter: "PaymentService" == alert.service? ✅
  │   ├─ metricFilter: "cpu_usage" == alert.metric? ✅
  │   │
  │   ├─ SlidingWindow (5 min) adds alert to window:
  │   │   Window contents at T+2m:
  │   │   [T+0m: 85.0, T+1m: 88.0, T+2m: 92.0]  (all within last 5 min)
  │   │
  │   ├─ ThresholdCondition: any value > 90.0?
  │   │   T+0m: 85.0 > 90? ❌
  │   │   T+1m: 88.0 > 90? ❌
  │   │   T+2m: 92.0 > 90? ✅ → CONDITION MET
  │   │
  │   ├─ Cooldown check: lastFiredAt = never → OK to fire
  │   │
  │   ├─ RULE FIRES! 🚨
  │   │   ├─ EmailAction.execute("High CPU Alert: 92.0 > 90.0")
  │   │   │   → Send email to ops@company.com
  │   │   ├─ SlackAction.execute("High CPU Alert: 92.0 > 90.0")
  │   │   │   → Post to #alerts-critical
  │   │   └─ lastFiredAt = T+2m
  │   │
  │   └─ RuleResult(RULE-001, FIRED, [EmailAction: sent, SlackAction: sent])
  │
  └─ Return: [RuleResult(RULE-001, FIRED)]

Processing at T+3m (Alert 95.0):
  ├─ Threshold: 95.0 > 90? ✅
  ├─ Cooldown: lastFired=T+2m, cooldown=10min → T+2m+10min=T+12m > T+3m
  ├─ SUPPRESSED by cooldown ⏳
  └─ RuleResult(RULE-001, SUPPRESSED_COOLDOWN)
```

### Scenario 2: Count-Based Alert with Tumbling Window

```
Rule: "If more than 5 errors in any 1-minute tumbling window for AuthService → PagerDuty"

Tumbling window: [T+0m to T+1m], [T+1m to T+2m], [T+2m to T+3m], ...

Timeline:
  T+0:10 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=1
  T+0:20 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=2
  T+0:30 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=3
  T+0:40 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=4
  T+0:50 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=5
  T+0:55 → Alert(AuthService, error_count, 1)   Window [0m-1m]: count=6 🚨

At T+0:55:
  ├─ TumblingWindow: current window [T+0:00 to T+1:00]
  ├─ CountCondition: count(6) > threshold(5)? ✅
  ├─ RULE FIRES! → PagerDutyAction.execute()
  └─ "6 errors in AuthService in last 1 minute"

At T+1:05: (new window starts)
  ├─ TumblingWindow RESET: window [T+1:00 to T+2:00], count=0
  ├─ Alert(AuthService, error_count, 1) → count=1
  ├─ CountCondition: count(1) > 5? ❌
  └─ No fire — fresh window

  ✅ Tumbling windows reset completely at boundaries
  ✅ No overlap between windows (unlike sliding)
```

### Scenario 3: Sliding Window vs Tumbling Window Comparison

```
Same alerts, different windows:

Alerts at: T+0:30, T+0:45, T+1:10, T+1:20, T+1:40
Count threshold: > 3 in 1-minute window

TUMBLING WINDOW (1-minute intervals):
  [T+0:00 to T+1:00]: T+0:30, T+0:45 → count=2 ❌
  [T+1:00 to T+2:00]: T+1:10, T+1:20, T+1:40 → count=3 ❌
  Never fires! Alerts split across window boundaries.

SLIDING WINDOW (last 1 minute, evaluated at each alert):
  At T+1:40, window = [T+0:40 to T+1:40]:
    T+0:45 ✅, T+1:10 ✅, T+1:20 ✅, T+1:40 ✅ → count=4 🚨 FIRES!

  ✅ Sliding window catches patterns that span tumbling boundaries
  ⚠️ But sliding window is more expensive (continuous evaluation)
```

### Scenario 4: Multiple Rules, One Alert

```
Alert: (PaymentService, latency_p99, 500ms, CRITICAL)

RULE-001: latency > 200ms → email (5-min sliding window)
RULE-002: latency > 400ms → PagerDuty (1-min tumbling window)
RULE-003: any CRITICAL alert → Slack (no window needed)

ingestAlert(alert):
  ├─ RULE-001: 500 > 200? ✅ → EmailAction fires
  ├─ RULE-002: 500 > 400? ✅ → PagerDutyAction fires
  ├─ RULE-003: severity == CRITICAL? ✅ → SlackAction fires
  └─ Return: [RULE-001: FIRED, RULE-002: FIRED, RULE-003: FIRED]

  ✅ All matching rules fire independently
  ✅ Each rule has its own window, condition, and actions
```

### Scenario 5: Rate Condition (Alerts per Second)

```
Rule: "If error rate > 10 alerts/sec in 30-second sliding window → escalate"

Alerts arrive:
  T+0s to T+10s: 50 alerts (rate = 5/sec) ❌
  T+10s to T+20s: 150 alerts (rate = 15/sec)
  
At T+20s, evaluate:
  ├─ SlidingWindow(30s) contains: 200 alerts in last 20 seconds
  ├─ RateCondition: 200 / 20 = 10.0/sec → threshold 10? ✅ (equals counts)
  └─ Actually: rate = 10.0 ≥ 10.0? Depends on > vs >= → configurable
```

---

## 5️⃣ Design + Implementation

### Alert (Event Data)

```java
class Alert {
    private final String id;
    private final String service;
    private final String metric;
    private final double value;
    private final Severity severity;
    private final long timestamp;
    private final Map<String, String> metadata;
    
    public Alert(String service, String metric, double value, Severity severity) {
        this(service, metric, value, severity, System.currentTimeMillis());
    }
    
    public Alert(String service, String metric, double value, Severity severity, long timestamp) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.service = service;
        this.metric = metric;
        this.value = value;
        this.severity = severity;
        this.timestamp = timestamp;
        this.metadata = new HashMap<>();
    }
    
    // Getters...
    public String getService()   { return service; }
    public String getMetric()    { return metric; }
    public double getValue()     { return value; }
    public Severity getSeverity() { return severity; }
    public long getTimestamp()   { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("Alert[%s/%s=%.1f, %s]", service, metric, value, severity);
    }
}

enum Severity {
    LOW(0), MEDIUM(1), HIGH(2), CRITICAL(3);
    final int level;
    Severity(int level) { this.level = level; }
}
```

### Window Strategy (Strategy + Template Method)

```java
// ==================== WINDOW STRATEGY ====================
// 💬 "Strategy Pattern for window types. Each window maintains its own state
//    and defines how alerts are added, expired, and aggregated.
//    Template Method: addAlert() + expireOld() skeleton is shared,
//    concrete timing logic differs per window type."

interface WindowStrategy {
    /** Add an alert to this window. */
    void addAlert(Alert alert);
    
    /** Get all alerts currently in the window. */
    List<Alert> getAlerts();
    
    /** Get count of alerts in window. */
    int getCount();
    
    /** Get the window duration. */
    long getDurationMs();
    
    /** Reset the window (for tumbling windows at boundary). */
    void reset();
    
    /** Get window type name. */
    String getName();
}

// ==================== SLIDING WINDOW ====================
// 💬 "Sliding window: keeps alerts from [now - duration, now].
//    On each evaluation, expire alerts older than the window.
//    Uses ArrayDeque for O(1) add + O(K) expire (K = expired count)."

class SlidingWindow implements WindowStrategy {
    private final long durationMs;
    private final Deque<Alert> alerts;
    private final Clock clock;  // injectable for testing!
    
    public SlidingWindow(Duration duration) {
        this(duration, Clock.systemUTC());
    }
    
    public SlidingWindow(Duration duration, Clock clock) {
        this.durationMs = duration.toMillis();
        this.alerts = new ArrayDeque<>();
        this.clock = clock;
    }
    
    @Override
    public void addAlert(Alert alert) {
        alerts.addLast(alert);
        expireOldAlerts();
    }
    
    private void expireOldAlerts() {
        long cutoff = clock.millis() - durationMs;
        while (!alerts.isEmpty() && alerts.peekFirst().getTimestamp() < cutoff) {
            alerts.pollFirst();  // O(1) remove from front
        }
    }
    
    @Override
    public List<Alert> getAlerts() {
        expireOldAlerts();
        return new ArrayList<>(alerts);
    }
    
    @Override
    public int getCount() {
        expireOldAlerts();
        return alerts.size();
    }
    
    @Override
    public long getDurationMs() { return durationMs; }
    
    @Override
    public void reset() { alerts.clear(); }
    
    @Override
    public String getName() { return "SlidingWindow(" + durationMs / 1000 + "s)"; }
}

// ==================== TUMBLING WINDOW ====================
// 💬 "Tumbling window: fixed intervals that don't overlap.
//    Window [0-5m], [5m-10m], [10m-15m], etc.
//    When an alert arrives past the window boundary, reset and start new window."

class TumblingWindow implements WindowStrategy {
    private final long durationMs;
    private final List<Alert> alerts;
    private long windowStart;
    private final Clock clock;
    
    public TumblingWindow(Duration duration) {
        this(duration, Clock.systemUTC());
    }
    
    public TumblingWindow(Duration duration, Clock clock) {
        this.durationMs = duration.toMillis();
        this.alerts = new ArrayList<>();
        this.clock = clock;
        this.windowStart = clock.millis();
    }
    
    @Override
    public void addAlert(Alert alert) {
        checkWindowBoundary();
        alerts.add(alert);
    }
    
    private void checkWindowBoundary() {
        long now = clock.millis();
        if (now >= windowStart + durationMs) {
            // Crossed boundary → reset window
            alerts.clear();
            // Align to boundary (not current time)
            windowStart = windowStart + durationMs * ((now - windowStart) / durationMs);
        }
    }
    
    @Override
    public List<Alert> getAlerts() {
        checkWindowBoundary();
        return new ArrayList<>(alerts);
    }
    
    @Override
    public int getCount() {
        checkWindowBoundary();
        return alerts.size();
    }
    
    @Override
    public long getDurationMs() { return durationMs; }
    
    @Override
    public void reset() {
        alerts.clear();
        windowStart = clock.millis();
    }
    
    @Override
    public String getName() { return "TumblingWindow(" + durationMs / 1000 + "s)"; }
}

// ==================== NO WINDOW (Instant evaluation) ====================
class InstantWindow implements WindowStrategy {
    private Alert lastAlert;
    
    @Override
    public void addAlert(Alert alert) { this.lastAlert = alert; }
    
    @Override
    public List<Alert> getAlerts() {
        return lastAlert != null ? List.of(lastAlert) : List.of();
    }
    
    @Override
    public int getCount() { return lastAlert != null ? 1 : 0; }
    
    @Override
    public long getDurationMs() { return 0; }
    
    @Override
    public void reset() { lastAlert = null; }
    
    @Override
    public String getName() { return "Instant"; }
}
```

### Condition Interface (Strategy Pattern)

```java
// ==================== CONDITION ====================
// 💬 "Condition evaluates whether window contents trigger the rule.
//    Strategy Pattern — each condition type is independently testable."

interface Condition {
    /** Evaluate the condition against alerts in the window. */
    boolean evaluate(List<Alert> windowAlerts, WindowStrategy window);
    
    /** Description for logging/display. */
    String describe();
}

// ==================== THRESHOLD CONDITION ====================
// 💬 "Fire if ANY alert in window exceeds the threshold."
class ThresholdCondition implements Condition {
    private final double threshold;
    private final ComparisonOperator operator;
    
    public ThresholdCondition(ComparisonOperator operator, double threshold) {
        this.operator = operator;
        this.threshold = threshold;
    }
    
    @Override
    public boolean evaluate(List<Alert> alerts, WindowStrategy window) {
        return alerts.stream().anyMatch(a -> operator.compare(a.getValue(), threshold));
    }
    
    @Override
    public String describe() { return "value " + operator.symbol + " " + threshold; }
}

// ==================== COUNT CONDITION ====================
// 💬 "Fire if alert count in window exceeds threshold."
class CountCondition implements Condition {
    private final int countThreshold;
    
    public CountCondition(int countThreshold) {
        this.countThreshold = countThreshold;
    }
    
    @Override
    public boolean evaluate(List<Alert> alerts, WindowStrategy window) {
        return alerts.size() > countThreshold;
    }
    
    @Override
    public String describe() { return "count > " + countThreshold; }
}

// ==================== RATE CONDITION ====================
// 💬 "Fire if alert rate (count/seconds) exceeds threshold."
class RateCondition implements Condition {
    private final double rateThreshold;  // alerts per second
    
    public RateCondition(double rateThreshold) {
        this.rateThreshold = rateThreshold;
    }
    
    @Override
    public boolean evaluate(List<Alert> alerts, WindowStrategy window) {
        if (alerts.isEmpty()) return false;
        double windowSeconds = window.getDurationMs() / 1000.0;
        double rate = alerts.size() / windowSeconds;
        return rate > rateThreshold;
    }
    
    @Override
    public String describe() { return "rate > " + rateThreshold + "/sec"; }
}

// ==================== SEVERITY CONDITION ====================
class SeverityCondition implements Condition {
    private final Severity minSeverity;
    
    public SeverityCondition(Severity minSeverity) {
        this.minSeverity = minSeverity;
    }
    
    @Override
    public boolean evaluate(List<Alert> alerts, WindowStrategy window) {
        return alerts.stream().anyMatch(a -> a.getSeverity().level >= minSeverity.level);
    }
    
    @Override
    public String describe() { return "severity >= " + minSeverity; }
}

// ==================== COMPOSITE CONDITION (AND/OR) ====================
// 💬 "Combine conditions: 'cpu > 90 AND error_count > 5'"
class CompositeCondition implements Condition {
    private final List<Condition> conditions;
    private final LogicOperator operator;  // AND, OR
    
    public CompositeCondition(LogicOperator operator, Condition... conditions) {
        this.operator = operator;
        this.conditions = List.of(conditions);
    }
    
    @Override
    public boolean evaluate(List<Alert> alerts, WindowStrategy window) {
        if (operator == LogicOperator.AND) {
            return conditions.stream().allMatch(c -> c.evaluate(alerts, window));
        } else {
            return conditions.stream().anyMatch(c -> c.evaluate(alerts, window));
        }
    }
    
    @Override
    public String describe() {
        return conditions.stream()
            .map(Condition::describe)
            .collect(Collectors.joining(" " + operator + " ", "(", ")"));
    }
}

enum ComparisonOperator {
    GREATER_THAN(">")   { boolean compare(double a, double b) { return a > b; } },
    LESS_THAN("<")      { boolean compare(double a, double b) { return a < b; } },
    GREATER_EQ(">=")    { boolean compare(double a, double b) { return a >= b; } },
    LESS_EQ("<=")       { boolean compare(double a, double b) { return a <= b; } },
    EQUALS("==")        { boolean compare(double a, double b) { return Math.abs(a - b) < 0.001; } };
    
    final String symbol;
    ComparisonOperator(String symbol) { this.symbol = symbol; }
    abstract boolean compare(double a, double b);
}

enum LogicOperator { AND, OR }
```

### Action Interface (Observer Pattern)

```java
// ==================== ACTION ====================
// 💬 "Observer Pattern — each action is independently notified when a rule fires.
//    New action type = new class implementing Action. Zero changes to existing code."

interface Action {
    /** Execute the action when a rule fires. */
    ActionResult execute(AlertRule rule, List<Alert> triggeringAlerts);
    
    /** Action type for display. */
    String getName();
}

class ActionResult {
    private final String actionName;
    private final boolean success;
    private final String message;
    
    // Constructor, getters...
    public ActionResult(String name, boolean success, String message) {
        this.actionName = name; this.success = success; this.message = message;
    }
}

// ==================== EMAIL ACTION ====================
class EmailAction implements Action {
    private final String recipient;
    
    public EmailAction(String recipient) { this.recipient = recipient; }
    
    @Override
    public ActionResult execute(AlertRule rule, List<Alert> alerts) {
        String subject = "🚨 Alert: " + rule.getName();
        String body = String.format("Rule '%s' fired. %d alerts matched condition: %s",
            rule.getName(), alerts.size(), rule.getCondition().describe());
        
        System.out.println("    📧 Email → " + recipient + ": " + subject);
        // In production: SMTP client send
        return new ActionResult("Email", true, "Sent to " + recipient);
    }
    
    @Override
    public String getName() { return "Email(" + recipient + ")"; }
}

// ==================== SMS ACTION ====================
class SMSAction implements Action {
    private final String phoneNumber;
    
    public SMSAction(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    @Override
    public ActionResult execute(AlertRule rule, List<Alert> alerts) {
        String message = String.format("[ALERT] %s: %s (%d events)",
            rule.getName(), rule.getCondition().describe(), alerts.size());
        
        System.out.println("    📱 SMS → " + phoneNumber + ": " + message);
        return new ActionResult("SMS", true, "Sent to " + phoneNumber);
    }
    
    @Override
    public String getName() { return "SMS(" + phoneNumber + ")"; }
}

// ==================== SLACK ACTION ====================
class SlackAction implements Action {
    private final String channel;
    
    public SlackAction(String channel) { this.channel = channel; }
    
    @Override
    public ActionResult execute(AlertRule rule, List<Alert> alerts) {
        System.out.println("    💬 Slack → " + channel + ": Rule '" + rule.getName() + "' fired");
        return new ActionResult("Slack", true, "Posted to " + channel);
    }
    
    @Override
    public String getName() { return "Slack(" + channel + ")"; }
}

// ==================== LOG ACTION (for testing) ====================
class LogAction implements Action {
    private final List<String> log = new ArrayList<>();
    
    @Override
    public ActionResult execute(AlertRule rule, List<Alert> alerts) {
        String entry = "FIRED: " + rule.getName() + " with " + alerts.size() + " alerts";
        log.add(entry);
        System.out.println("    📝 Log: " + entry);
        return new ActionResult("Log", true, entry);
    }
    
    public List<String> getLog() { return Collections.unmodifiableList(log); }
    
    @Override
    public String getName() { return "Log"; }
}
```

### AlertRule

```java
// ==================== ALERT RULE ====================
class AlertRule {
    private final String id;
    private final String name;
    private final String serviceFilter;   // null = match all services
    private final String metricFilter;    // null = match all metrics
    private final WindowStrategy window;
    private final Condition condition;
    private final List<Action> actions;
    private final long cooldownMs;
    private long lastFiredAt;
    private boolean enabled;
    private final Clock clock;
    
    // Constructor via builder (see builder below)
    AlertRule(String id, String name, String serviceFilter, String metricFilter,
             WindowStrategy window, Condition condition, List<Action> actions,
             long cooldownMs, Clock clock) {
        this.id = id;
        this.name = name;
        this.serviceFilter = serviceFilter;
        this.metricFilter = metricFilter;
        this.window = window;
        this.condition = condition;
        this.actions = new ArrayList<>(actions);
        this.cooldownMs = cooldownMs;
        this.lastFiredAt = 0;
        this.enabled = true;
        this.clock = clock;
    }
    
    /** Check if this rule applies to the given alert (service + metric filter). */
    public boolean matches(Alert alert) {
        if (!enabled) return false;
        if (serviceFilter != null && !serviceFilter.equalsIgnoreCase(alert.getService())) return false;
        if (metricFilter != null && !metricFilter.equalsIgnoreCase(alert.getMetric())) return false;
        return true;
    }
    
    /** Add alert to window, evaluate condition, fire actions if met. */
    public RuleResult evaluate(Alert alert) {
        // Add to window
        window.addAlert(alert);
        List<Alert> windowAlerts = window.getAlerts();
        
        // Evaluate condition
        if (!condition.evaluate(windowAlerts, window)) {
            return new RuleResult(id, name, RuleResult.Status.NOT_MET);
        }
        
        // Cooldown check
        long now = clock.millis();
        if (cooldownMs > 0 && (now - lastFiredAt) < cooldownMs) {
            return new RuleResult(id, name, RuleResult.Status.SUPPRESSED_COOLDOWN);
        }
        
        // FIRE! Execute all actions
        lastFiredAt = now;
        List<ActionResult> actionResults = new ArrayList<>();
        for (Action action : actions) {
            try {
                actionResults.add(action.execute(this, windowAlerts));
            } catch (Exception e) {
                actionResults.add(new ActionResult(action.getName(), false, e.getMessage()));
                // One failing action doesn't stop others (isolation)
            }
        }
        
        return new RuleResult(id, name, RuleResult.Status.FIRED, actionResults);
    }
    
    // Getters
    public String getId()             { return id; }
    public String getName()           { return name; }
    public Condition getCondition()   { return condition; }
    public WindowStrategy getWindow() { return window; }
    public boolean isEnabled()        { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
}
```

### Rule Engine + Monitoring System

```java
// ==================== RULE ENGINE ====================
class RuleEngine {
    private final List<AlertRule> rules;
    
    public RuleEngine() {
        this.rules = new CopyOnWriteArrayList<>();  // thread-safe iteration
    }
    
    /** Evaluate all matching rules for an alert. */
    public List<RuleResult> evaluate(Alert alert) {
        List<RuleResult> results = new ArrayList<>();
        
        for (AlertRule rule : rules) {
            if (rule.matches(alert)) {
                RuleResult result = rule.evaluate(alert);
                results.add(result);
                
                if (result.getStatus() == RuleResult.Status.FIRED) {
                    System.out.println("  🚨 Rule '" + rule.getName() + "' FIRED for " + alert);
                }
            }
        }
        
        return results;
    }
    
    public void addRule(AlertRule rule) { rules.add(rule); }
    public void removeRule(String ruleId) { rules.removeIf(r -> r.getId().equals(ruleId)); }
}

// ==================== MONITORING SYSTEM (Facade) ====================
class AlertMonitoringSystem {
    private final RuleEngine ruleEngine;
    
    public AlertMonitoringSystem() {
        this.ruleEngine = new RuleEngine();
    }
    
    public List<RuleResult> ingestAlert(Alert alert) {
        System.out.println("\n📥 Ingested: " + alert);
        return ruleEngine.evaluate(alert);
    }
    
    public void addRule(AlertRule rule) {
        ruleEngine.addRule(rule);
        System.out.println("  ➕ Added rule: " + rule.getName() 
            + " [" + rule.getWindow().getName() + ", " + rule.getCondition().describe() + "]");
    }
    
    public void removeRule(String ruleId) {
        ruleEngine.removeRule(ruleId);
    }
}
```

### Fluent Builder (Ease of Use)

```java
class AlertRuleBuilder {
    private String id, name, serviceFilter, metricFilter;
    private WindowStrategy window = new InstantWindow();
    private Condition condition;
    private List<Action> actions = new ArrayList<>();
    private long cooldownMs = 0;
    private Clock clock = Clock.systemUTC();
    
    public AlertRuleBuilder id(String id)           { this.id = id; return this; }
    public AlertRuleBuilder name(String name)       { this.name = name; return this; }
    public AlertRuleBuilder service(String s)       { this.serviceFilter = s; return this; }
    public AlertRuleBuilder metric(String m)        { this.metricFilter = m; return this; }
    
    public AlertRuleBuilder slidingWindow(Duration d) {
        this.window = new SlidingWindow(d, clock); return this;
    }
    public AlertRuleBuilder tumblingWindow(Duration d) {
        this.window = new TumblingWindow(d, clock); return this;
    }
    
    public AlertRuleBuilder threshold(ComparisonOperator op, double val) {
        this.condition = new ThresholdCondition(op, val); return this;
    }
    public AlertRuleBuilder countThreshold(int count) {
        this.condition = new CountCondition(count); return this;
    }
    public AlertRuleBuilder rateThreshold(double rate) {
        this.condition = new RateCondition(rate); return this;
    }
    public AlertRuleBuilder severityAtLeast(Severity sev) {
        this.condition = new SeverityCondition(sev); return this;
    }
    
    public AlertRuleBuilder action(Action a)        { this.actions.add(a); return this; }
    public AlertRuleBuilder cooldown(Duration d)    { this.cooldownMs = d.toMillis(); return this; }
    public AlertRuleBuilder clock(Clock c)          { this.clock = c; return this; }
    
    public AlertRule build() {
        Objects.requireNonNull(id, "Rule ID required");
        Objects.requireNonNull(condition, "Condition required");
        if (actions.isEmpty()) throw new IllegalStateException("At least one action required");
        return new AlertRule(id, name != null ? name : id, serviceFilter, metricFilter,
            window, condition, actions, cooldownMs, clock);
    }
}
```

### Demo

```java
public class AlertMonitoringDemo {
    public static void main(String[] args) {
        AlertMonitoringSystem system = new AlertMonitoringSystem();
        
        // Rule 1: High CPU with sliding window
        system.addRule(new AlertRuleBuilder()
            .id("RULE-001").name("High CPU Alert")
            .service("PaymentService").metric("cpu_usage")
            .slidingWindow(Duration.ofMinutes(5))
            .threshold(ComparisonOperator.GREATER_THAN, 90.0)
            .action(new EmailAction("ops@company.com"))
            .action(new SlackAction("#alerts"))
            .cooldown(Duration.ofMinutes(10))
            .build());
        
        // Rule 2: Error count with tumbling window
        system.addRule(new AlertRuleBuilder()
            .id("RULE-002").name("Error Burst")
            .service("AuthService").metric("error_count")
            .tumblingWindow(Duration.ofMinutes(1))
            .countThreshold(5)
            .action(new SMSAction("+1-555-0100"))
            .build());
        
        // Rule 3: Any critical alert — instant
        system.addRule(new AlertRuleBuilder()
            .id("RULE-003").name("Critical Alert")
            .severityAtLeast(Severity.CRITICAL)
            .action(new SlackAction("#incidents"))
            .build());
        
        // Ingest alerts
        system.ingestAlert(new Alert("PaymentService", "cpu_usage", 85.0, Severity.MEDIUM));
        system.ingestAlert(new Alert("PaymentService", "cpu_usage", 92.0, Severity.HIGH));  // RULE-001 fires!
        system.ingestAlert(new Alert("AuthService", "error_count", 1.0, Severity.LOW));
        system.ingestAlert(new Alert("PaymentService", "latency", 500.0, Severity.CRITICAL));  // RULE-003 fires!
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: SOLID Principles Applied

| Principle | Where Applied | How |
|-----------|--------------|-----|
| **Single Responsibility** | Alert (data), Window (time logic), Condition (evaluation), Action (notification) | Each class has one reason to change |
| **Open/Closed** | New window type = new class, new condition = new class, new action = new class | Extend via interfaces, never modify existing code |
| **Liskov Substitution** | Any WindowStrategy works in AlertRule — SlidingWindow, TumblingWindow, InstantWindow | Substitutable via interface |
| **Interface Segregation** | Condition has only `evaluate()`, Action has only `execute()` | Small, focused interfaces |
| **Dependency Inversion** | AlertRule depends on WindowStrategy interface, not SlidingWindow | High-level module doesn't depend on low-level details |

### Deep Dive 2: Sliding vs Tumbling Window Trade-offs

| Feature | Sliding Window | Tumbling Window |
|---------|---------------|----------------|
| **Overlap** | Continuous overlap — every alert re-evaluates full window | No overlap — fixed non-overlapping intervals |
| **Sensitivity** | Catches patterns across boundaries | May miss patterns split across boundaries |
| **Memory** | Keeps all alerts in window (higher memory) | Only current interval (lower memory) |
| **CPU** | Re-evaluates on every alert | Re-evaluates on every alert, but resets at boundary |
| **Use case** | Real-time anomaly detection | Periodic aggregation (dashboard metrics) |
| **Example** | "Alert if CPU > 90 in any 5-min window" | "Count errors per 1-minute bucket" |

### Deep Dive 3: Testability Design

```java
// ==================== TESTABLE DESIGN ====================
// 💬 "Every component is testable in isolation because of DI and interfaces."

class AlertRuleTest {
    // 1. INJECTABLE CLOCK — deterministic time in tests
    @Test
    void slidingWindow_expiresOldAlerts() {
        MutableClock clock = new MutableClock(0);
        SlidingWindow window = new SlidingWindow(Duration.ofMinutes(5), clock);
        
        window.addAlert(alertAt(clock.millis()));
        clock.advance(Duration.ofMinutes(6));    // past window
        window.addAlert(alertAt(clock.millis()));
        
        assertEquals(1, window.getCount());       // old alert expired
    }
    
    // 2. MOCK ACTIONS — verify firing without sending real emails
    @Test
    void rule_firesActions_whenConditionMet() {
        LogAction logAction = new LogAction();    // captures all firings
        AlertRule rule = new AlertRuleBuilder()
            .id("test-rule")
            .threshold(ComparisonOperator.GREATER_THAN, 90.0)
            .action(logAction)
            .build();
        
        rule.evaluate(new Alert("svc", "cpu", 95.0, Severity.HIGH));
        
        assertEquals(1, logAction.getLog().size());
        assertTrue(logAction.getLog().get(0).contains("FIRED"));
    }
    
    // 3. CONDITION ISOLATION — test each condition independently
    @Test
    void countCondition_firesWhenExceeded() {
        CountCondition condition = new CountCondition(3);
        List<Alert> alerts = List.of(alert(), alert(), alert(), alert());  // 4 > 3
        
        assertTrue(condition.evaluate(alerts, mockWindow()));
    }
    
    // 4. COOLDOWN — test suppression with controlled clock
    @Test
    void cooldown_suppressesRepeatedFiring() {
        MutableClock clock = new MutableClock(0);
        AlertRule rule = ruleWithCooldown(Duration.ofMinutes(10), clock);
        
        rule.evaluate(highCpuAlert());  // FIRES
        rule.evaluate(highCpuAlert());  // SUPPRESSED (within cooldown)
        
        clock.advance(Duration.ofMinutes(11));
        rule.evaluate(highCpuAlert());  // FIRES again (cooldown expired)
    }
}
```

**Key testability features**:
1. **Injectable Clock**: No `System.currentTimeMillis()` in business logic — inject `Clock` for deterministic tests
2. **LogAction**: Captures all firings in a list for assertions — no real emails/SMS in tests
3. **Interface-based**: Every dependency is an interface — easy to mock
4. **Pure functions**: Conditions are stateless — give input, check output

### Deep Dive 4: Composite Conditions

```java
// "High CPU AND high memory" — both must be true
Condition composite = new CompositeCondition(LogicOperator.AND,
    new ThresholdCondition(GREATER_THAN, 90.0),   // cpu > 90
    new ThresholdCondition(GREATER_THAN, 85.0)    // memory > 85
);

// "Critical OR count > 10" — either triggers
Condition either = new CompositeCondition(LogicOperator.OR,
    new SeverityCondition(Severity.CRITICAL),
    new CountCondition(10)
);

// Nested: "(cpu > 90 AND memory > 85) OR critical"
Condition nested = new CompositeCondition(LogicOperator.OR,
    composite,
    new SeverityCondition(Severity.CRITICAL)
);
```

**Why Composite Pattern?** Rules in production often have complex conditions. Composite lets us build arbitrarily complex condition trees from simple building blocks — each leaf is independently testable.

### Deep Dive 5: Action Isolation (Error Handling)

```java
// One failing action MUST NOT prevent others from executing

for (Action action : actions) {
    try {
        actionResults.add(action.execute(this, windowAlerts));
    } catch (Exception e) {
        // Log failure but CONTINUE to next action
        actionResults.add(new ActionResult(action.getName(), false, e.getMessage()));
    }
}

// If EmailAction throws (SMTP timeout), SlackAction still executes!
// This is critical for reliability — imagine PagerDuty fails but email works.
```

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Alert for service with no matching rules | No rules evaluated, no actions fired |
| Rule with no window (instant evaluation) | InstantWindow — evaluates single alert |
| Cooldown = 0 | No suppression, fires every time condition met |
| Multiple rules fire for same alert | Each rule independent, all fire |
| Action throws exception | Caught per-action, other actions still execute |
| Empty window (no alerts yet) | Condition evaluates with empty list → false |
| Tumbling window boundary exactly at alert time | Alert goes in new window (boundary inclusive) |
| Rule added while alerts are being processed | CopyOnWriteArrayList — safe concurrent modification |
| Disabled rule | matches() returns false, skipped entirely |
| Clock skew (alert timestamp in future) | Sliding window handles gracefully (won't expire future alerts) |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| ingestAlert | O(R × W) | O(1) | R=rules, W=avg window size (expire old) |
| Sliding window add | O(K) | O(W) | K=expired alerts removed, W=window contents |
| Tumbling window add | O(1) | O(W) | W=current window contents |
| Condition evaluate | O(W) | O(1) | Scan window alerts |
| Action execute | O(A) | O(1) | A=actions per rule |
| Total per alert | O(R × (W + A)) | O(R × W) | R rules, W window size, A actions |

### Deep Dive 8: Production Enhancements

| Enhancement | Approach | Benefit |
|-------------|----------|---------|
| **Escalation** | If alert not acknowledged in N min, escalate to next level | Ensure critical alerts get response |
| **Alert grouping** | Aggregate similar alerts into single notification | Reduce alert fatigue |
| **Runbook links** | Attach remediation URL to each rule | Faster incident response |
| **Metric dashboard** | Track rule fire rate, false positive rate | Tune thresholds over time |
| **ML anomaly detection** | Replace static thresholds with learned baselines | Adapt to changing patterns |
| **Distributed evaluation** | Partition rules across workers (e.g., by service) | Scale to thousands of rules |
| **Alert correlation** | Link related alerts across services ("CPU spike → latency increase → errors") | Root cause analysis |
| **Silence/maintenance** | Temporarily suppress rules during deployments | Avoid false alerts |

---

## 📋 Interview Checklist (L63)

- [ ] **Strategy Pattern**: WindowStrategy (Sliding, Tumbling, Instant) — swappable per rule
- [ ] **Observer Pattern**: Actions (Email, SMS, Slack) — independently notified when rule fires
- [ ] **Composite Pattern**: CompositeCondition (AND/OR) — arbitrarily complex conditions from simple leaves
- [ ] **Sliding vs Tumbling**: Explained trade-offs — overlap vs boundary, sensitivity vs memory
- [ ] **Cooldown**: Prevent alert fatigue — same rule doesn't fire within cooldown period
- [ ] **Action isolation**: One failing action doesn't prevent others from executing
- [ ] **Testability**: Injectable Clock, LogAction for assertions, interface-based dependencies
- [ ] **Builder pattern**: Fluent API for rule creation — ease of use
- [ ] **SOLID**: Every principle demonstrated with specific example from the design
- [ ] **Extensibility**: New window/condition/action = new class, zero changes to existing code

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 5 min |
| Core Entities (Alert, Rule, Window, Condition, Action) | 7 min |
| Window Strategy (Sliding + Tumbling) | 8 min |
| Condition Hierarchy (Threshold, Count, Rate, Composite) | 7 min |
| Actions + Rule Engine | 6 min |
| Testability + Builder | 5 min |
| Deep Dives (SOLID, edge cases, production) | 7 min |
| **Total** | **~45 min** |

### Design Pattern Summary

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Strategy** | WindowStrategy (3 types), Condition (4 types) | Swappable algorithms for windowing and evaluation |
| **Observer** | Action interface — multiple actions notified on rule fire | Decouple notification targets from rule logic |
| **Composite** | CompositeCondition (AND/OR tree) | Build complex conditions from simple, testable leaves |
| **Builder** | AlertRuleBuilder with fluent API | Ease of use — readable rule creation |
| **Template Method** | Window base behavior (add + expire + evaluate) | Shared algorithm skeleton, specific timing per window type |
| **Facade** | AlertMonitoringSystem wraps RuleEngine | Simple public API hiding internal complexity |

See `AlertMonitoringSystem.java` for full implementation with sliding window, tumbling window, composite conditions, and cooldown demos.
