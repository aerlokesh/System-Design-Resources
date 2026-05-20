# 📊 Topic 80: AWS CloudWatch Observability & Monitoring Java Implementation — Production Patterns

> Complete Java implementation guide for observability using **AWS CloudWatch**, **Micrometer**, **OpenTelemetry**, and **Structured Logging**. Covers custom metrics, alarms, dashboards, log insights, distributed tracing, and the **WHY** behind each pattern for production monitoring.

---

## 📋 Table of Contents

- [1. CloudWatch Client Setup](#1-cloudwatch-client-setup)
- [2. Custom Metrics (PutMetricData)](#2-custom-metrics-putmetricdata)
- [3. Micrometer + CloudWatch Integration](#3-micrometer--cloudwatch-integration)
- [4. EMF (Embedded Metric Format) Logging](#4-emf-embedded-metric-format-logging)
- [5. CloudWatch Alarms (Programmatic)](#5-cloudwatch-alarms-programmatic)
- [6. Structured Logging (JSON)](#6-structured-logging-json)
- [7. Log Insights Queries](#7-log-insights-queries)
- [8. Distributed Tracing (X-Ray)](#8-distributed-tracing-x-ray)
- [9. OpenTelemetry Integration](#9-opentelemetry-integration)
- [10. Dashboard Creation (Programmatic)](#10-dashboard-creation-programmatic)
- [11. Metric Filters & Subscriptions](#11-metric-filters--subscriptions)
- [12. Application Health Checks](#12-application-health-checks)
- [13. SLI/SLO Monitoring](#13-slislo-monitoring)
- [14. Request Latency Tracking](#14-request-latency-tracking)
- [15. Error Rate & Anomaly Detection](#15-error-rate--anomaly-detection)
- [16. Thread Pool & Connection Pool Metrics](#16-thread-pool--connection-pool-metrics)
- [17. Business Metrics (Revenue, Orders)](#17-business-metrics-revenue-orders)
- [18. Alerting Pipeline (Alarm → SNS → Lambda)](#18-alerting-pipeline-alarm--sns--lambda)
- [19. Cost-Effective Metric Strategies](#19-cost-effective-metric-strategies)
- [20. Full Production Observability Setup](#20-full-production-observability-setup)
- [🏆 Observability Cheat Sheet](#-observability-cheat-sheet)

---

## 1. CloudWatch Client Setup

```java
@Configuration
public class CloudWatchConfig {
    
    @Bean
    public CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .apiCallTimeout(Duration.ofSeconds(10))
                .build())
            .build();
    }
    
    @Bean
    public CloudWatchLogsClient logsClient() {
        return CloudWatchLogsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
    
    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

---

## 2. Custom Metrics (PutMetricData)

```java
@Service
public class CloudWatchMetricsService {
    private final CloudWatchClient cw;
    private static final String NAMESPACE = "MyApp/OrderService";
    
    // ====== Publish single metric ======
    public void publishMetric(String metricName, double value, StandardUnit unit,
                               Map<String, String> dimensions) {
        List<Dimension> dims = dimensions.entrySet().stream()
            .map(e -> Dimension.builder().name(e.getKey()).value(e.getValue()).build())
            .collect(Collectors.toList());
        
        cw.putMetricData(PutMetricDataRequest.builder()
            .namespace(NAMESPACE)
            .metricData(MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .timestamp(Instant.now())
                .dimensions(dims)
                .build())
            .build());
    }
    
    // ====== Publish latency with statistics (p50, p90, p99) ======
    public void publishLatencyStats(String operation, List<Double> latencies) {
        DoubleSummaryStatistics stats = latencies.stream()
            .mapToDouble(Double::doubleValue).summaryStatistics();
        
        cw.putMetricData(PutMetricDataRequest.builder()
            .namespace(NAMESPACE)
            .metricData(MetricDatum.builder()
                .metricName("Latency")
                .statisticValues(StatisticSet.builder()
                    .minimum(stats.getMin())
                    .maximum(stats.getMax())
                    .sum(stats.getSum())
                    .sampleCount((double) stats.getCount())
                    .build())
                .unit(StandardUnit.MILLISECONDS)
                .dimensions(Dimension.builder().name("Operation").value(operation).build())
                .timestamp(Instant.now())
                .build())
            .build());
    }
    
    // ====== Batch publish (up to 1000 metric values per call) ======
    public void publishBatch(List<MetricDatum> metrics) {
        List<List<MetricDatum>> chunks = Lists.partition(metrics, 1000);
        for (List<MetricDatum> chunk : chunks) {
            cw.putMetricData(PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(chunk)
                .build());
        }
    }
    
    // ====== High-resolution metric (1-second granularity) ======
    public void publishHighResolution(String metricName, double value) {
        cw.putMetricData(PutMetricDataRequest.builder()
            .namespace(NAMESPACE)
            .metricData(MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(StandardUnit.COUNT)
                .storageResolution(1)  // 1-second resolution (vs default 60s)
                .timestamp(Instant.now())
                .build())
            .build());
    }
}
```

---

## 3. Micrometer + CloudWatch Integration

```java
// ====== Spring Boot + Micrometer (recommended approach) ======
// application.yml:
// management.cloudwatch.metrics.export.namespace: MyApp/OrderService
// management.cloudwatch.metrics.export.step: 60s
// management.cloudwatch.metrics.export.enabled: true

@Configuration
public class MicrometerConfig {
    
    @Bean
    public MeterRegistryCustomizer<CloudWatchMeterRegistry> commonTags() {
        return registry -> registry.config().commonTags(
            "service", "order-service",
            "environment", "production",
            "region", System.getenv("AWS_REGION")
        );
    }
}

@Service
public class OrderMetricsService {
    private final MeterRegistry registry;
    
    // ====== Counter: Track events ======
    public void recordOrderCreated(String paymentMethod) {
        registry.counter("orders.created", 
            "payment_method", paymentMethod,
            "status", "success"
        ).increment();
    }
    
    // ====== Timer: Track latency with percentiles ======
    public <T> T timeOperation(String name, Map<String, String> tags, Supplier<T> operation) {
        Timer timer = Timer.builder(name)
            .tags(tags.entrySet().stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new))
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)  // p50, p90, p95, p99
            .publishPercentileHistogram()
            .register(registry);
        
        return timer.record(operation);
    }
    
    // ====== Gauge: Track current state ======
    @PostConstruct
    public void registerGauges() {
        // Thread pool metrics
        Gauge.builder("threadpool.active", executor, ThreadPoolExecutor::getActiveCount)
            .tag("pool", "order-processor")
            .register(registry);
        
        Gauge.builder("threadpool.queue_size", executor, 
            e -> e.getQueue().size())
            .tag("pool", "order-processor")
            .register(registry);
        
        // Connection pool
        Gauge.builder("db.connections.active", dataSource, 
            ds -> ((HikariDataSource) ds).getHikariPoolMXBean().getActiveConnections())
            .register(registry);
    }
    
    // ====== Distribution Summary: Track value distributions ======
    public void recordOrderAmount(double amount, String region) {
        DistributionSummary.builder("orders.amount")
            .tag("region", region)
            .publishPercentiles(0.5, 0.9, 0.99)
            .baseUnit("USD")
            .register(registry)
            .record(amount);
    }
    
    // ====== Long Task Timer: Track in-progress operations ======
    private final LongTaskTimer importTimer = LongTaskTimer.builder("data.import.duration")
        .register(registry);
    
    public void runImport(Runnable importJob) {
        LongTaskTimer.Sample sample = importTimer.start();
        try {
            importJob.run();
        } finally {
            sample.stop();
        }
    }
}
```

---

## 4. EMF (Embedded Metric Format) Logging

```java
// EMF: Publish metrics via structured log lines (no PutMetricData API calls needed!)
// CloudWatch auto-extracts metrics from log entries in EMF format

@Service
public class EmfMetricsService {
    private static final Logger emfLogger = LoggerFactory.getLogger("EMF");
    private final ObjectMapper mapper = new ObjectMapper();
    
    // ====== Publish metric via EMF log ======
    public void recordLatency(String service, String operation, double latencyMs) {
        Map<String, Object> emf = new LinkedHashMap<>();
        
        // AWS EMF metadata
        emf.put("_aws", Map.of(
            "Timestamp", Instant.now().toEpochMilli(),
            "CloudWatchMetrics", List.of(Map.of(
                "Namespace", "MyApp/OrderService",
                "Dimensions", List.of(List.of("Service", "Operation")),
                "Metrics", List.of(
                    Map.of("Name", "Latency", "Unit", "Milliseconds"),
                    Map.of("Name", "RequestCount", "Unit", "Count")
                )
            ))
        ));
        
        // Dimensions
        emf.put("Service", service);
        emf.put("Operation", operation);
        
        // Metric values
        emf.put("Latency", latencyMs);
        emf.put("RequestCount", 1);
        
        // Additional properties (searchable in Log Insights, not metric dimensions)
        emf.put("TraceId", MDC.get("traceId"));
        emf.put("UserId", MDC.get("userId"));
        
        emfLogger.info(mapper.writeValueAsString(emf));
    }
    
    // WHY EMF over PutMetricData?
    // - No API call overhead (just a log line)
    // - Free metric extraction from logs (no per-metric cost)
    // - Combines metric + log context in one entry
    // - Scales with log throughput (millions/sec)
}
```

---

## 5. CloudWatch Alarms (Programmatic)

```java
@Service
public class AlarmService {
    private final CloudWatchClient cw;
    
    // ====== Create alarm for high error rate ======
    public void createErrorRateAlarm(String serviceName) {
        cw.putMetricAlarm(PutMetricAlarmRequest.builder()
            .alarmName(serviceName + "-HighErrorRate")
            .alarmDescription("Error rate > 5% for " + serviceName)
            .namespace("MyApp/" + serviceName)
            .metricName("ErrorRate")
            .statistic(Statistic.AVERAGE)
            .period(300)  // 5-minute periods
            .evaluationPeriods(3)  // 3 consecutive periods
            .threshold(5.0)  // 5% error rate
            .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
            .treatMissingData("notBreaching")
            .actionsEnabled(true)
            .alarmActions("arn:aws:sns:us-east-1:123456:ops-alerts")  // SNS topic
            .okActions("arn:aws:sns:us-east-1:123456:ops-resolved")
            .dimensions(Dimension.builder().name("Service").value(serviceName).build())
            .build());
        
        log.info("Alarm created: {}-HighErrorRate", serviceName);
    }
    
    // ====== Create alarm for high latency (p99) ======
    public void createLatencyAlarm(String serviceName, double thresholdMs) {
        cw.putMetricAlarm(PutMetricAlarmRequest.builder()
            .alarmName(serviceName + "-HighP99Latency")
            .alarmDescription("p99 latency > " + thresholdMs + "ms")
            .namespace("MyApp/" + serviceName)
            .metricName("Latency")
            .extendedStatistic("p99")  // p99 percentile
            .period(60)  // 1-minute periods
            .evaluationPeriods(5)  // 5 minutes
            .threshold(thresholdMs)
            .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
            .alarmActions("arn:aws:sns:us-east-1:123456:ops-alerts")
            .build());
    }
    
    // ====== Composite alarm (multiple conditions) ======
    public void createCompositeAlarm(String serviceName) {
        cw.putCompositeAlarm(PutCompositeAlarmRequest.builder()
            .alarmName(serviceName + "-CriticalHealth")
            .alarmDescription("Service is critically unhealthy")
            .alarmRule(String.format(
                "ALARM(%s-HighErrorRate) AND ALARM(%s-HighP99Latency)",
                serviceName, serviceName))
            .actionsEnabled(true)
            .alarmActions("arn:aws:sns:us-east-1:123456:critical-pager")
            .build());
    }
    
    // ====== Anomaly detection alarm ======
    public void createAnomalyAlarm(String serviceName) {
        cw.putMetricAlarm(PutMetricAlarmRequest.builder()
            .alarmName(serviceName + "-LatencyAnomaly")
            .alarmDescription("Latency anomaly detected (outside 2 std dev)")
            .namespace("MyApp/" + serviceName)
            .metricName("Latency")
            .comparisonOperator(ComparisonOperator.GREATER_THAN_UPPER_THRESHOLD)
            .evaluationPeriods(3)
            .thresholdMetricId("anomaly_band")
            .metrics(
                MetricDataQuery.builder()
                    .id("latency")
                    .metricStat(MetricStat.builder()
                        .metric(Metric.builder()
                            .namespace("MyApp/" + serviceName)
                            .metricName("Latency")
                            .build())
                        .period(300)
                        .stat("Average")
                        .build())
                    .returnData(true)
                    .build(),
                MetricDataQuery.builder()
                    .id("anomaly_band")
                    .expression("ANOMALY_DETECTION_BAND(latency, 2)")  // 2 std deviations
                    .build()
            )
            .build());
    }
}
```

---

## 6. Structured Logging (JSON)

```java
// logback-spring.xml configuration for JSON structured logs
// <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>

@Service
public class StructuredLoggingService {
    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingService.class);
    
    // ====== Request-scoped structured logging ======
    public void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        // MDC (Mapped Diagnostic Context) adds fields to every log line
        MDC.put("traceId", request.getHeader("X-Trace-Id"));
        MDC.put("userId", extractUserId(request));
        MDC.put("requestId", UUID.randomUUID().toString());
        
        // Structured log with key-value pairs
        log.info("HTTP request completed",
            kv("method", request.getMethod()),
            kv("path", request.getRequestURI()),
            kv("status", response.getStatus()),
            kv("duration_ms", durationMs),
            kv("user_agent", request.getHeader("User-Agent")),
            kv("client_ip", request.getRemoteAddr()),
            kv("response_size", response.getBufferSize())
        );
        
        // Output (JSON):
        // {"timestamp":"2024-01-15T10:30:00Z","level":"INFO","logger":"StructuredLoggingService",
        //  "message":"HTTP request completed","traceId":"abc123","userId":"user456",
        //  "method":"GET","path":"/api/orders","status":200,"duration_ms":45}
        
        MDC.clear();
    }
    
    // ====== Error logging with context ======
    public void logError(String operation, Exception ex, Map<String, Object> context) {
        log.error("Operation failed: {}",
            kv("operation", operation),
            kv("error_type", ex.getClass().getSimpleName()),
            kv("error_message", ex.getMessage()),
            kv("context", context),
            ex  // Stack trace
        );
    }
    
    // ====== Business event logging ======
    public void logBusinessEvent(String eventType, Map<String, Object> data) {
        log.info("Business event",
            kv("event_type", eventType),
            kv("data", data),
            kv("timestamp", Instant.now().toString())
        );
    }
}
```

---

## 7. Log Insights Queries

```java
@Service
public class LogInsightsService {
    private final CloudWatchLogsClient logsClient;
    
    // ====== Run Log Insights query ======
    public List<Map<String, String>> queryLogs(String logGroup, String query, 
                                                 Instant start, Instant end) {
        StartQueryResponse startResponse = logsClient.startQuery(StartQueryRequest.builder()
            .logGroupNames(logGroup)
            .queryString(query)
            .startTime(start.getEpochSecond())
            .endTime(end.getEpochSecond())
            .limit(100)
            .build());
        
        String queryId = startResponse.queryId();
        
        // Poll for results
        GetQueryResultsResponse results;
        do {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            results = logsClient.getQueryResults(
                GetQueryResultsRequest.builder().queryId(queryId).build());
        } while (results.status() == QueryStatus.RUNNING);
        
        return results.results().stream()
            .map(row -> row.stream()
                .collect(Collectors.toMap(ResultField::field, ResultField::value)))
            .collect(Collectors.toList());
    }
    
    // ====== Common queries ======
    public List<Map<String, String>> getTopErrors(String logGroup, Duration lookback) {
        String query = """
            fields @timestamp, @message
            | filter @message like /ERROR/
            | stats count(*) as error_count by error_type
            | sort error_count desc
            | limit 10
            """;
        return queryLogs(logGroup, query, Instant.now().minus(lookback), Instant.now());
    }
    
    public List<Map<String, String>> getSlowestRequests(String logGroup, Duration lookback) {
        String query = """
            fields @timestamp, path, duration_ms, userId
            | filter duration_ms > 1000
            | sort duration_ms desc
            | limit 20
            """;
        return queryLogs(logGroup, query, Instant.now().minus(lookback), Instant.now());
    }
    
    public List<Map<String, String>> getErrorsByUser(String logGroup, String userId) {
        String query = String.format("""
            fields @timestamp, @message, error_type, path
            | filter userId = '%s' and @message like /ERROR/
            | sort @timestamp desc
            | limit 50
            """, userId);
        return queryLogs(logGroup, query, Instant.now().minus(Duration.ofHours(24)), Instant.now());
    }
}
```

---

## 8. Distributed Tracing (X-Ray)

```java
// ====== AWS X-Ray integration with Spring Boot ======
@Configuration
public class XRayConfig {
    
    @Bean
    public Filter tracingFilter() {
        return new AWSXRayServletFilter("order-service");
    }
    
    @Bean
    public AWSXRayRecorder xRayRecorder() {
        return AWSXRayRecorderBuilder.standard()
            .withSamplingStrategy(new CentralizedSamplingStrategy())
            .withPlugin(new EC2Plugin())
            .withPlugin(new ECSPlugin())
            .build();
    }
}

@Service
public class TracedOrderService {
    
    // ====== Manual subsegment for custom tracing ======
    public Order processOrder(String orderId) {
        // Creates subsegment in current trace
        return AWSXRay.createSubsegment("processOrder", subsegment -> {
            subsegment.putAnnotation("orderId", orderId);
            subsegment.putMetadata("service", "order-processing");
            
            try {
                Order order = fetchOrder(orderId);
                subsegment.putAnnotation("orderAmount", order.getAmount().doubleValue());
                
                validateOrder(order);  // Auto-traced if using interceptor
                chargePayment(order);
                
                return order;
            } catch (Exception e) {
                subsegment.addException(e);
                throw e;
            }
        });
    }
    
    // ====== Trace outgoing HTTP calls ======
    @Bean
    public RestTemplate tracedRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(new XRayClientInterceptor()));
        return restTemplate;
    }
    
    // ====== Trace async operations ======
    public CompletableFuture<EnrichedOrder> enrichAsync(Order order) {
        Segment segment = AWSXRay.getCurrentSegment();
        
        return CompletableFuture.supplyAsync(() -> {
            AWSXRay.setTraceEntity(segment);  // Propagate trace context
            return AWSXRay.createSubsegment("enrichOrder", ss -> {
                UserProfile profile = userService.getProfile(order.getUserId());
                return new EnrichedOrder(order, profile);
            });
        }, asyncExecutor);
    }
}
```

---

## 9. OpenTelemetry Integration

```java
// ====== OpenTelemetry SDK setup (vendor-neutral, exports to CloudWatch) ======
@Configuration
public class OpenTelemetryConfig {
    
    @Bean
    public OpenTelemetry openTelemetry() {
        // Export traces to X-Ray via OTLP
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://otel-collector:4317")
            .build();
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "order-service",
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production"
            )))
            .build();
        
        // Export metrics to CloudWatch
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint("http://otel-collector:4317")
            .build();
        
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                .setInterval(Duration.ofSeconds(60))
                .build())
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    }
}

@Service
public class OtelTracedService {
    private final Tracer tracer;
    private final Meter meter;
    
    public OtelTracedService(OpenTelemetry otel) {
        this.tracer = otel.getTracer("order-service");
        this.meter = otel.getMeter("order-service");
    }
    
    public Order processOrder(OrderRequest request) {
        Span span = tracer.spanBuilder("processOrder")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("order.id", request.getOrderId())
            .setAttribute("order.amount", request.getAmount().doubleValue())
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Order order = createOrder(request);
            span.setAttribute("order.status", order.getStatus().name());
            return order;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

---

## 10. Dashboard Creation (Programmatic)

```java
@Service
public class DashboardService {
    private final CloudWatchClient cw;
    
    // ====== Create operational dashboard ======
    public void createServiceDashboard(String serviceName) {
        String dashboardBody = mapper.writeValueAsString(Map.of(
            "widgets", List.of(
                // Request rate
                widget("Request Rate", "line", List.of(
                    metric(serviceName, "RequestCount", "Sum", 60)
                ), 0, 0, 12, 6),
                
                // Error rate
                widget("Error Rate (%)", "line", List.of(
                    metricExpression("errors/requests*100", "ErrorRate")
                ), 12, 0, 12, 6),
                
                // Latency percentiles
                widget("Latency (ms)", "line", List.of(
                    metric(serviceName, "Latency", "p50", 60),
                    metric(serviceName, "Latency", "p90", 60),
                    metric(serviceName, "Latency", "p99", 60)
                ), 0, 6, 12, 6),
                
                // Active connections
                widget("DB Connections", "line", List.of(
                    metric(serviceName, "DBConnectionsActive", "Average", 60),
                    metric(serviceName, "DBConnectionsIdle", "Average", 60)
                ), 12, 6, 12, 6)
            )
        ));
        
        cw.putDashboard(PutDashboardRequest.builder()
            .dashboardName(serviceName + "-operations")
            .dashboardBody(dashboardBody)
            .build());
        
        log.info("Dashboard created: {}-operations", serviceName);
    }
}
```

---

## 11-20: Additional Patterns (Key Code)

### 13. SLI/SLO Monitoring
```java
@Service
public class SloMonitoringService {
    private final MeterRegistry registry;
    
    // SLO: 99.9% of requests complete in < 500ms
    public void recordRequestForSlo(String endpoint, long durationMs, boolean success) {
        // SLI: % of requests meeting latency target
        boolean meetsSlo = success && durationMs < 500;
        
        registry.counter("slo.requests.total", "endpoint", endpoint).increment();
        if (meetsSlo) {
            registry.counter("slo.requests.good", "endpoint", endpoint).increment();
        }
        
        // Calculate error budget burn rate (per hour)
        // If SLO = 99.9%, error budget = 0.1% of requests
        // 1000 requests/min × 60 min = 60,000/hour
        // Error budget = 60 errors/hour
        // If current error rate > 60/hour → burning budget too fast → alert
    }
}
```

### 14. Request Latency Tracking (Interceptor)
```java
@Component
public class LatencyTrackingInterceptor implements HandlerInterceptor {
    private final MeterRegistry registry;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("startTime", System.nanoTime());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        long startTime = (long) request.getAttribute("startTime");
        double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;
        
        Timer.builder("http.request.duration")
            .tag("method", request.getMethod())
            .tag("uri", getTemplatedUri(request))
            .tag("status", String.valueOf(response.getStatus()))
            .tag("exception", ex != null ? ex.getClass().getSimpleName() : "none")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(registry)
            .record(Duration.ofNanos(System.nanoTime() - startTime));
    }
}
```

### 16. Thread Pool & Connection Pool Metrics
```java
@Component
public class InfrastructureMetrics {
    
    @Bean
    public MeterBinder threadPoolMetrics(ThreadPoolExecutor orderPool) {
        return new ExecutorServiceMetrics(orderPool, "order-processor", List.of());
    }
    
    @Bean
    public MeterBinder hikariMetrics(HikariDataSource dataSource) {
        return new HikariCPMetricsTrackerFactory(registry);
    }
    
    // JVM metrics (auto-registered with Spring Boot Actuator)
    // - jvm.memory.used, jvm.gc.pause, jvm.threads.live
    // - process.cpu.usage, system.cpu.usage
}
```

### 17. Business Metrics
```java
@Service
public class BusinessMetrics {
    private final MeterRegistry registry;
    
    public void recordOrder(Order order) {
        registry.counter("business.orders.created",
            "region", order.getRegion(),
            "payment_method", order.getPaymentMethod().name()
        ).increment();
        
        registry.summary("business.order.amount",
            "region", order.getRegion()
        ).record(order.getTotal().doubleValue());
        
        // Revenue gauge (total revenue today)
        registry.gauge("business.revenue.today", revenueService, 
            RevenueService::getTodayRevenue);
    }
}
```

### 20. Full Production Observability Setup
```java
// application.yml for complete observability:
// management:
//   endpoints.web.exposure.include: health,metrics,prometheus
//   cloudwatch.metrics.export:
//     namespace: MyApp/OrderService
//     step: 60s
//   tracing:
//     sampling.probability: 0.1  # Sample 10% of traces
//   metrics.tags:
//     service: order-service
//     env: production

// Three pillars setup:
// 1. METRICS → Micrometer → CloudWatch (counters, timers, gauges)
// 2. LOGS → Logback JSON → CloudWatch Logs → Log Insights
// 3. TRACES → OpenTelemetry → X-Ray (distributed tracing)
```

---

## 🏆 Observability Cheat Sheet

| What to Monitor | Metric Type | Tool | Alert Threshold |
|---|---|---|---|
| Request rate | Counter | Micrometer → CW | Sudden drop > 50% |
| Error rate | Counter ratio | Micrometer → CW | > 1% for 5 min |
| Latency p99 | Timer | Micrometer → CW | > SLO target |
| Saturation (CPU) | Gauge | CW Agent | > 80% for 5 min |
| Queue depth | Gauge | Custom metric | > 1000 messages |
| DB connections | Gauge | HikariCP metrics | > 80% pool used |
| Error budget | Calculated | Custom | Burn rate > 1 |
| Business KPIs | Counter/Gauge | EMF logs | Revenue drop > 20% |

| Layer | What | How |
|---|---|---|
| Infrastructure | CPU, Memory, Disk, Network | CloudWatch Agent |
| Application | Request rate, Errors, Latency | Micrometer + CW |
| Business | Orders, Revenue, Conversions | EMF structured logs |
| Dependencies | Downstream latency, errors | X-Ray / OpenTelemetry |
| User Experience | Error rate, page load | RUM (Real User Monitoring) |

| Cost Tips |
|---|
| Use EMF (free metric extraction from logs) instead of PutMetricData |
| Standard resolution (60s) unless you need 1s granularity |
| Limit custom metric dimensions (each combo = new metric = cost) |
| Use metric math instead of publishing derived metrics |
| Sample traces (10%) rather than capturing all |
| Set log retention (7-30 days) to control storage costs |

---

*All examples use AWS SDK v2 + Micrometer + Spring Boot. For ECS/EKS, add the CloudWatch Agent as a sidecar for infrastructure metrics.*
