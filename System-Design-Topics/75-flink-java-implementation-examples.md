# 🌊 Topic 75: Apache Flink Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Apache Flink using the **DataStream API**, **Table API**, **CEP**, and **Stateful Functions**. Every pattern includes production-ready code with windowing, state management, checkpointing, exactly-once processing, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Job Setup & Configuration](#1-job-setup--configuration)
- [2. Basic DataStream Operations](#2-basic-datastream-operations)
- [3. Windowing (Tumbling, Sliding, Session)](#3-windowing-tumbling-sliding-session)
- [4. Keyed State & ValueState](#4-keyed-state--valuestate)
- [5. Event Time & Watermarks](#5-event-time--watermarks)
- [6. Kafka Source & Sink](#6-kafka-source--sink)
- [7. Exactly-Once with Checkpointing](#7-exactly-once-with-checkpointing)
- [8. Complex Event Processing (CEP)](#8-complex-event-processing-cep)
- [9. Async I/O (External Service Calls)](#9-async-io-external-service-calls)
- [10. Broadcast State (Dynamic Rules)](#10-broadcast-state-dynamic-rules)
- [11. Side Outputs (Split Streams)](#11-side-outputs-split-streams)
- [12. Connected Streams (Join)](#12-connected-streams-join)
- [13. Custom Triggers & Evictors](#13-custom-triggers--evictors)
- [14. Fraud Detection Pipeline](#14-fraud-detection-pipeline)
- [15. Real-Time Metrics Aggregation](#15-real-time-metrics-aggregation)
- [16. Deduplication (Exactly-Once Events)](#16-deduplication-exactly-once-events)
- [17. Late Data Handling](#17-late-data-handling)
- [18. Table API & SQL](#18-table-api--sql)
- [19. Savepoints & Job Upgrades](#19-savepoints--job-upgrades)
- [20. Monitoring & Metrics](#20-monitoring--metrics)
- [🏆 Operator Selection Cheat Sheet](#-operator-selection-cheat-sheet)

---

## 1. Job Setup & Configuration

```java
public class FlinkJobConfig {
    
    public static StreamExecutionEnvironment createProductionEnv() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Parallelism
        env.setParallelism(4);  // Default parallelism for all operators
        env.setMaxParallelism(128);  // Max for rescaling (set once, never change)
        
        // Checkpointing (exactly-once)
        env.enableCheckpointing(60_000);  // Checkpoint every 60 seconds
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30_000);  // Min 30s between
        env.getCheckpointConfig().setCheckpointTimeout(300_000);  // 5 min timeout
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);
        
        // State backend (RocksDB for large state)
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));  // Incremental checkpoints
        env.getCheckpointConfig().setCheckpointStorage("s3://flink-checkpoints/prod/");
        
        // Restart strategy
        env.setRestartStrategy(RestartStrategies.exponentialDelayRestart(
            Time.seconds(1),    // Initial delay
            Time.minutes(5),    // Max delay
            2.0,                // Backoff multiplier
            Time.hours(1),      // Reset period
            0.1                 // Jitter
        ));
        
        // Event time
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        
        return env;
    }
}
```

---

## 2. Basic DataStream Operations

```java
public class BasicStreamOperations {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        
        DataStream<String> rawStream = env.addSource(createKafkaSource());
        
        // ====== MAP: Transform each element ======
        DataStream<OrderEvent> orders = rawStream
            .map(json -> objectMapper.readValue(json, OrderEvent.class))
            .name("deserialize-orders");
        
        // ====== FILTER: Keep only completed orders ======
        DataStream<OrderEvent> completed = orders
            .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
            .name("filter-completed");
        
        // ====== FLATMAP: One input → multiple outputs ======
        DataStream<OrderItem> items = orders
            .flatMap((OrderEvent order, Collector<OrderItem> out) -> {
                for (OrderItem item : order.getItems()) {
                    out.collect(item);
                }
            })
            .returns(TypeInformation.of(OrderItem.class))
            .name("flatten-items");
        
        // ====== KEYBY + REDUCE: Sum revenue per user ======
        DataStream<UserRevenue> revenuePerUser = completed
            .keyBy(OrderEvent::getUserId)
            .reduce((a, b) -> new UserRevenue(a.getUserId(), 
                a.getRevenue().add(b.getRevenue())))
            .name("sum-revenue-per-user");
        
        // ====== PROCESS: Full control with state + timers ======
        DataStream<Alert> alerts = orders
            .keyBy(OrderEvent::getUserId)
            .process(new OrderAlertFunction())
            .name("detect-high-value-orders");
        
        alerts.addSink(createAlertSink());
        
        env.execute("Order Processing Pipeline");
    }
}
```

---

## 3. Windowing (Tumbling, Sliding, Session)

```java
public class WindowingExamples {
    
    // ====== TUMBLING WINDOW: Count orders every 5 minutes ======
    public static DataStream<OrderCount> tumblingWindow(DataStream<OrderEvent> orders) {
        return orders
            .keyBy(OrderEvent::getCategory)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(new OrderCountAggregate())
            .name("5min-tumbling-order-count");
    }
    
    // ====== SLIDING WINDOW: Moving average over 1 hour, slide every 5 min ======
    public static DataStream<CategoryAvg> slidingWindow(DataStream<OrderEvent> orders) {
        return orders
            .keyBy(OrderEvent::getCategory)
            .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5)))
            .aggregate(new AverageAggregate(), new WindowResultFunction())
            .name("1h-sliding-avg");
    }
    
    // ====== SESSION WINDOW: Group user activity with 30-min gap ======
    public static DataStream<UserSession> sessionWindow(DataStream<ClickEvent> clicks) {
        return clicks
            .keyBy(ClickEvent::getUserId)
            .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
            .process(new SessionWindowFunction())
            .name("session-window-30min-gap");
    }
    
    // ====== GLOBAL WINDOW with custom trigger (every 100 events OR every 10s) ======
    public static DataStream<BatchResult> globalWindow(DataStream<Event> events) {
        return events
            .keyBy(Event::getPartitionKey)
            .window(GlobalWindows.create())
            .trigger(new CountOrTimeTrigger(100, Time.seconds(10)))
            .process(new BatchProcessFunction())
            .name("global-window-count-or-time");
    }
    
    // ====== Custom AggregateFunction ======
    public static class OrderCountAggregate implements AggregateFunction<OrderEvent, Long, OrderCount> {
        @Override public Long createAccumulator() { return 0L; }
        @Override public Long add(OrderEvent event, Long acc) { return acc + 1; }
        @Override public OrderCount getResult(Long acc) { return new OrderCount(acc); }
        @Override public Long merge(Long a, Long b) { return a + b; }
    }
    
    // ====== ProcessWindowFunction (access to window metadata) ======
    public static class SessionWindowFunction 
            extends ProcessWindowFunction<ClickEvent, UserSession, String, TimeWindow> {
        @Override
        public void process(String userId, Context context, 
                           Iterable<ClickEvent> events, Collector<UserSession> out) {
            List<ClickEvent> clicks = new ArrayList<>();
            events.forEach(clicks::add);
            
            long sessionStart = context.window().getStart();
            long sessionEnd = context.window().getEnd();
            Duration duration = Duration.ofMillis(sessionEnd - sessionStart);
            
            out.collect(new UserSession(userId, clicks.size(), duration, 
                Instant.ofEpochMilli(sessionStart)));
        }
    }
}
```

---

## 4. Keyed State & ValueState

```java
// ====== Stateful Function: Track user's last order and detect anomalies ======
public class UserOrderTracker extends KeyedProcessFunction<String, OrderEvent, Alert> {
    
    // State: persisted per key, survives failures (checkpointed)
    private ValueState<OrderEvent> lastOrderState;
    private ValueState<Long> orderCountState;
    private ValueState<BigDecimal> totalSpentState;
    private MapState<String, Integer> itemFrequencyState;
    
    @Override
    public void open(Configuration parameters) {
        lastOrderState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-order", OrderEvent.class));
        orderCountState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("order-count", Long.class));
        totalSpentState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("total-spent", BigDecimal.class));
        itemFrequencyState = getRuntimeContext().getMapState(
            new MapStateDescriptor<>("item-frequency", String.class, Integer.class));
    }
    
    @Override
    public void processElement(OrderEvent order, Context ctx, Collector<Alert> out) throws Exception {
        // Read current state
        OrderEvent lastOrder = lastOrderState.value();
        Long count = orderCountState.value();
        BigDecimal totalSpent = totalSpentState.value();
        
        if (count == null) count = 0L;
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;
        
        // Anomaly detection: order amount > 10x average
        BigDecimal avgOrderAmount = count > 0 ? totalSpent.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (count > 5 && order.getAmount().compareTo(avgOrderAmount.multiply(BigDecimal.TEN)) > 0) {
            out.collect(new Alert("HIGH_VALUE_ANOMALY", order.getUserId(),
                "Order $" + order.getAmount() + " is 10x above average $" + avgOrderAmount));
        }
        
        // Anomaly: rapid orders (< 1 minute apart)
        if (lastOrder != null) {
            long timeBetween = order.getTimestamp() - lastOrder.getTimestamp();
            if (timeBetween < 60_000) {  // Less than 1 minute
                out.collect(new Alert("RAPID_ORDERING", order.getUserId(),
                    "Two orders within " + timeBetween + "ms"));
            }
        }
        
        // Update state
        lastOrderState.update(order);
        orderCountState.update(count + 1);
        totalSpentState.update(totalSpent.add(order.getAmount()));
        
        // Track item frequencies
        for (String item : order.getItemIds()) {
            Integer freq = itemFrequencyState.get(item);
            itemFrequencyState.put(item, (freq != null ? freq : 0) + 1);
        }
        
        // Register timer: clear state if user inactive for 24 hours (memory management)
        ctx.timerService().registerEventTimeTimer(order.getTimestamp() + 86_400_000);
    }
    
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) throws Exception {
        // Clear state for inactive users (prevents unbounded state growth)
        OrderEvent lastOrder = lastOrderState.value();
        if (lastOrder != null && timestamp - lastOrder.getTimestamp() >= 86_400_000) {
            lastOrderState.clear();
            orderCountState.clear();
            totalSpentState.clear();
            itemFrequencyState.clear();
        }
    }
}
```

---

## 5. Event Time & Watermarks

```java
public class EventTimeConfig {
    
    // ====== Watermark strategy: bounded out-of-orderness ======
    public static DataStream<OrderEvent> configureEventTime(DataStream<String> raw) {
        return raw
            .map(json -> objectMapper.readValue(json, OrderEvent.class))
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                    .withTimestampAssigner((event, timestamp) -> event.getEventTimeMs())
                    .withIdleness(Duration.ofMinutes(1))  // Handle idle partitions
            )
            .name("assign-watermarks");
    }
    
    // ====== Custom watermark generator (for irregular data) ======
    public static WatermarkStrategy<OrderEvent> customWatermark() {
        return WatermarkStrategy.forGenerator(ctx -> new WatermarkGenerator<OrderEvent>() {
            private long maxTimestamp = Long.MIN_VALUE;
            private static final long MAX_DELAY = 30_000;  // 30s max out-of-order
            
            @Override
            public void onEvent(OrderEvent event, long eventTimestamp, WatermarkOutput output) {
                maxTimestamp = Math.max(maxTimestamp, event.getEventTimeMs());
            }
            
            @Override
            public void onPeriodicEmit(WatermarkOutput output) {
                output.emitWatermark(new Watermark(maxTimestamp - MAX_DELAY));
            }
        }).withTimestampAssigner((event, ts) -> event.getEventTimeMs());
    }
}
```

---

## 6. Kafka Source & Sink

```java
public class KafkaIntegration {
    
    // ====== Kafka Source (Flink 1.17+ — new source API) ======
    public static KafkaSource<OrderEvent> createKafkaSource() {
        return KafkaSource.<OrderEvent>builder()
            .setBootstrapServers("kafka-1:9092,kafka-2:9092,kafka-3:9092")
            .setTopics("order-events")
            .setGroupId("flink-order-processor")
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setDeserializer(new OrderEventDeserializer())
            .setProperty("partition.discovery.interval.ms", "30000")  // Discover new partitions
            .build();
    }
    
    // ====== Kafka Sink (exactly-once with transactions) ======
    public static KafkaSink<EnrichedOrder> createKafkaSink() {
        return KafkaSink.<EnrichedOrder>builder()
            .setBootstrapServers("kafka-1:9092,kafka-2:9092,kafka-3:9092")
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic("enriched-orders")
                .setKeySerializationSchema(new OrderKeySerializer())
                .setValueSerializationSchema(new JsonSerializationSchema<>())
                .build())
            .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
            .setTransactionalIdPrefix("flink-enriched-orders")
            .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "900000")  // 15 min
            .build();
    }
    
    // ====== Custom Deserializer ======
    public static class OrderEventDeserializer implements KafkaRecordDeserializationSchema<OrderEvent> {
        private final ObjectMapper mapper = new ObjectMapper();
        
        @Override
        public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<OrderEvent> out) {
            try {
                OrderEvent event = mapper.readValue(record.value(), OrderEvent.class);
                event.setKafkaPartition(record.partition());
                event.setKafkaOffset(record.offset());
                out.collect(event);
            } catch (Exception e) {
                // Skip malformed records (or send to DLQ side output)
                log.warn("Failed to deserialize record: partition={}, offset={}", 
                    record.partition(), record.offset(), e);
            }
        }
        
        @Override
        public TypeInformation<OrderEvent> getProducedType() {
            return TypeInformation.of(OrderEvent.class);
        }
    }
}
```

---

## 7. Exactly-Once with Checkpointing

```java
public class ExactlyOnceJob {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        
        // Source: Kafka (exactly-once via offsets in checkpoints)
        KafkaSource<OrderEvent> source = KafkaIntegration.createKafkaSource();
        DataStream<OrderEvent> orders = env.fromSource(source, 
            WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((e, t) -> e.getEventTimeMs()),
            "kafka-source");
        
        // Process with state
        DataStream<EnrichedOrder> enriched = orders
            .keyBy(OrderEvent::getUserId)
            .process(new EnrichmentFunction())  // Stateful: uses ValueState
            .name("enrich-orders");
        
        // Sink: Kafka (exactly-once via transactions)
        KafkaSink<EnrichedOrder> sink = KafkaIntegration.createKafkaSink();
        enriched.sinkTo(sink).name("kafka-sink-exactly-once");
        
        // Sink: JDBC (exactly-once via two-phase commit)
        enriched.addSink(JdbcSink.exactlyOnceSink(
            "INSERT INTO orders (id, user_id, amount, status) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status",
            (ps, order) -> {
                ps.setString(1, order.getId());
                ps.setString(2, order.getUserId());
                ps.setBigDecimal(3, order.getAmount());
                ps.setString(4, order.getStatus());
            },
            JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(1000)
                .withMaxRetries(3)
                .build(),
            JdbcExactlyOnceOptions.builder()
                .withTransactionPerConnection(true)
                .build(),
            () -> DriverManager.getConnection("jdbc:postgresql://db:5432/orders", "user", "pass")
        )).name("jdbc-sink-exactly-once");
        
        env.execute("Exactly-Once Order Pipeline");
    }
}
```

---

## 8. Complex Event Processing (CEP)

```java
public class FraudDetectionCEP {
    
    // ====== Detect: 3+ failed logins within 5 minutes → alert ======
    public static DataStream<Alert> detectBruteForce(DataStream<LoginEvent> logins) {
        Pattern<LoginEvent, ?> pattern = Pattern.<LoginEvent>begin("first-failure")
            .where(new SimpleCondition<LoginEvent>() {
                @Override
                public boolean filter(LoginEvent event) {
                    return !event.isSuccess();
                }
            })
            .timesOrMore(3)              // 3 or more failures
            .within(Time.minutes(5));     // Within 5 minutes
        
        PatternStream<LoginEvent> patternStream = CEP.pattern(
            logins.keyBy(LoginEvent::getUserId), pattern);
        
        return patternStream.select((Map<String, List<LoginEvent>> match) -> {
            List<LoginEvent> failures = match.get("first-failure");
            return new Alert("BRUTE_FORCE",
                failures.get(0).getUserId(),
                failures.size() + " failed logins in 5 minutes",
                Instant.now());
        });
    }
    
    // ====== Detect: Large withdrawal followed by account closure within 1 hour ======
    public static DataStream<Alert> detectSuspiciousWithdrawal(DataStream<BankEvent> events) {
        Pattern<BankEvent, ?> pattern = Pattern.<BankEvent>begin("large-withdrawal")
            .where(new SimpleCondition<BankEvent>() {
                @Override
                public boolean filter(BankEvent event) {
                    return event.getType().equals("WITHDRAWAL") && 
                           event.getAmount().compareTo(new BigDecimal("10000")) > 0;
                }
            })
            .followedBy("account-closure")
            .where(new SimpleCondition<BankEvent>() {
                @Override
                public boolean filter(BankEvent event) {
                    return event.getType().equals("ACCOUNT_CLOSURE");
                }
            })
            .within(Time.hours(1));
        
        PatternStream<BankEvent> patternStream = CEP.pattern(
            events.keyBy(BankEvent::getAccountId), pattern);
        
        return patternStream.select((Map<String, List<BankEvent>> match) -> {
            BankEvent withdrawal = match.get("large-withdrawal").get(0);
            BankEvent closure = match.get("account-closure").get(0);
            return new Alert("SUSPICIOUS_WITHDRAWAL_AND_CLOSE",
                withdrawal.getAccountId(),
                "Withdrawal of $" + withdrawal.getAmount() + " followed by account closure",
                Instant.ofEpochMilli(withdrawal.getTimestamp()));
        });
    }
}
```

---

## 9. Async I/O (External Service Calls)

```java
// ====== Call external service without blocking pipeline ======
public class AsyncEnrichmentFunction extends RichAsyncFunction<OrderEvent, EnrichedOrder> {
    
    private transient HttpClient httpClient;
    
    @Override
    public void open(Configuration parameters) {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }
    
    @Override
    public void asyncInvoke(OrderEvent order, ResultFuture<EnrichedOrder> resultFuture) {
        // Non-blocking HTTP call to user service
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create("http://user-service/users/" + order.getUserId()))
                .timeout(Duration.ofSeconds(3))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        
        future.thenAccept(response -> {
            UserProfile profile = objectMapper.readValue(response.body(), UserProfile.class);
            EnrichedOrder enriched = new EnrichedOrder(order, profile);
            resultFuture.complete(Collections.singleton(enriched));
        }).exceptionally(ex -> {
            // Fallback: proceed without enrichment
            resultFuture.complete(Collections.singleton(
                new EnrichedOrder(order, UserProfile.UNKNOWN)));
            return null;
        });
    }
    
    @Override
    public void timeout(OrderEvent order, ResultFuture<EnrichedOrder> resultFuture) {
        resultFuture.complete(Collections.singleton(
            new EnrichedOrder(order, UserProfile.UNKNOWN)));
    }
}

// Usage:
DataStream<EnrichedOrder> enriched = AsyncDataStream.unorderedWait(
    orders,
    new AsyncEnrichmentFunction(),
    5, TimeUnit.SECONDS,  // Timeout per element
    100                    // Max concurrent async requests
).name("async-user-enrichment");
```

---

## 10. Broadcast State (Dynamic Rules)

```java
// ====== Dynamic fraud rules that can be updated without restarting the job ======
public class DynamicRuleProcessor extends BroadcastProcessFunction<Transaction, FraudRule, Alert> {
    
    private final MapStateDescriptor<String, FraudRule> rulesDescriptor = 
        new MapStateDescriptor<>("fraud-rules", String.class, FraudRule.class);
    
    // Called for each transaction (main stream)
    @Override
    public void processElement(Transaction tx, ReadOnlyContext ctx, Collector<Alert> out) throws Exception {
        ReadOnlyBroadcastState<String, FraudRule> rules = ctx.getBroadcastState(rulesDescriptor);
        
        // Evaluate all active rules against this transaction
        for (Map.Entry<String, FraudRule> entry : rules.immutableEntries()) {
            FraudRule rule = entry.getValue();
            if (rule.matches(tx)) {
                out.collect(new Alert(rule.getName(), tx.getAccountId(),
                    "Rule triggered: " + rule.getDescription(), Instant.now()));
            }
        }
    }
    
    // Called when a new rule arrives (broadcast stream)
    @Override
    public void processBroadcastElement(FraudRule rule, Context ctx, Collector<Alert> out) throws Exception {
        BroadcastState<String, FraudRule> state = ctx.getBroadcastState(rulesDescriptor);
        
        if (rule.isActive()) {
            state.put(rule.getId(), rule);
            log.info("Rule activated: {}", rule.getName());
        } else {
            state.remove(rule.getId());
            log.info("Rule deactivated: {}", rule.getName());
        }
    }
}

// Usage:
DataStream<Transaction> transactions = /* ... */;
DataStream<FraudRule> rules = env.fromSource(rulesKafkaSource, 
    WatermarkStrategy.noWatermarks(), "rules-source");

BroadcastStream<FraudRule> broadcastRules = rules.broadcast(rulesDescriptor);

DataStream<Alert> alerts = transactions
    .connect(broadcastRules)
    .process(new DynamicRuleProcessor())
    .name("dynamic-fraud-detection");
```

---

## 11. Side Outputs (Split Streams)

```java
public class SideOutputExample extends ProcessFunction<OrderEvent, OrderEvent> {
    
    // Define side output tags
    static final OutputTag<OrderEvent> HIGH_VALUE = new OutputTag<>("high-value") {};
    static final OutputTag<OrderEvent> FAILED = new OutputTag<>("failed") {};
    static final OutputTag<String> ERRORS = new OutputTag<>("errors") {};
    
    @Override
    public void processElement(OrderEvent order, Context ctx, Collector<OrderEvent> out) {
        try {
            if (order.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                ctx.output(HIGH_VALUE, order);  // Route to high-value stream
            }
            
            if (order.getStatus() == OrderStatus.FAILED) {
                ctx.output(FAILED, order);      // Route to failed orders stream
            }
            
            out.collect(order);  // Main output (all orders)
            
        } catch (Exception e) {
            ctx.output(ERRORS, "Error processing order " + order.getId() + ": " + e.getMessage());
        }
    }
}

// Usage:
SingleOutputStreamOperator<OrderEvent> mainStream = orders
    .process(new SideOutputExample());

// Access side outputs
DataStream<OrderEvent> highValue = mainStream.getSideOutput(SideOutputExample.HIGH_VALUE);
DataStream<OrderEvent> failed = mainStream.getSideOutput(SideOutputExample.FAILED);
DataStream<String> errors = mainStream.getSideOutput(SideOutputExample.ERRORS);

highValue.sinkTo(highValueAlertSink);
failed.sinkTo(failedOrdersRetryQueue);
errors.sinkTo(errorLogSink);
```

---

## 12. Connected Streams (Join)

```java
// ====== Interval Join: Match orders with payments within time window ======
public class OrderPaymentJoin {
    
    public static DataStream<OrderWithPayment> joinOrdersAndPayments(
            DataStream<OrderEvent> orders, DataStream<PaymentEvent> payments) {
        
        return orders.keyBy(OrderEvent::getOrderId)
            .intervalJoin(payments.keyBy(PaymentEvent::getOrderId))
            .between(Time.seconds(-5), Time.minutes(30))  // Payment within -5s to +30min of order
            .process(new ProcessJoinFunction<OrderEvent, PaymentEvent, OrderWithPayment>() {
                @Override
                public void processElement(OrderEvent order, PaymentEvent payment, 
                                           Context ctx, Collector<OrderWithPayment> out) {
                    out.collect(new OrderWithPayment(order, payment));
                }
            })
            .name("order-payment-interval-join");
    }
    
    // ====== CoProcess: Enrich stream with lookup table ======
    public static DataStream<EnrichedClick> enrichClicks(
            DataStream<ClickEvent> clicks, DataStream<UserProfile> profiles) {
        
        return clicks.connect(profiles)
            .keyBy(ClickEvent::getUserId, UserProfile::getUserId)
            .process(new CoProcessFunction<ClickEvent, UserProfile, EnrichedClick>() {
                private ValueState<UserProfile> profileState;
                
                @Override
                public void open(Configuration params) {
                    profileState = getRuntimeContext().getState(
                        new ValueStateDescriptor<>("profile", UserProfile.class));
                }
                
                @Override
                public void processElement1(ClickEvent click, Context ctx, 
                                           Collector<EnrichedClick> out) throws Exception {
                    UserProfile profile = profileState.value();
                    out.collect(new EnrichedClick(click, profile));  // May be null if not yet received
                }
                
                @Override
                public void processElement2(UserProfile profile, Context ctx, 
                                           Collector<EnrichedClick> out) throws Exception {
                    profileState.update(profile);  // Store latest profile
                }
            })
            .name("enrich-clicks-with-profile");
    }
}
```

---

## 13. Custom Triggers & Evictors

```java
// ====== Custom Trigger: Fire on count OR timeout (whichever comes first) ======
public class CountOrTimeTrigger extends Trigger<Event, GlobalWindow> {
    private final int maxCount;
    private final long timeoutMs;
    private final ReducingStateDescriptor<Long> countDesc = 
        new ReducingStateDescriptor<>("count", Long::sum, Long.class);
    
    public CountOrTimeTrigger(int maxCount, Time timeout) {
        this.maxCount = maxCount;
        this.timeoutMs = timeout.toMilliseconds();
    }
    
    @Override
    public TriggerResult onElement(Event event, long timestamp, 
                                    GlobalWindow window, TriggerContext ctx) throws Exception {
        ReducingState<Long> count = ctx.getPartitionedState(countDesc);
        count.add(1L);
        
        if (count.get() >= maxCount) {
            count.clear();
            ctx.deleteProcessingTimeTimer(ctx.getCurrentProcessingTime() + timeoutMs);
            return TriggerResult.FIRE_AND_PURGE;  // Fire window and clear state
        }
        
        if (count.get() == 1) {
            // First element: register timeout timer
            ctx.registerProcessingTimeTimer(ctx.getCurrentProcessingTime() + timeoutMs);
        }
        
        return TriggerResult.CONTINUE;
    }
    
    @Override
    public TriggerResult onProcessingTime(long time, GlobalWindow window, 
                                           TriggerContext ctx) throws Exception {
        // Timeout fired — emit whatever we have
        ctx.getPartitionedState(countDesc).clear();
        return TriggerResult.FIRE_AND_PURGE;
    }
    
    @Override
    public TriggerResult onEventTime(long time, GlobalWindow window, TriggerContext ctx) {
        return TriggerResult.CONTINUE;
    }
    
    @Override
    public void clear(GlobalWindow window, TriggerContext ctx) throws Exception {
        ctx.getPartitionedState(countDesc).clear();
    }
}
```

---

## 14. Fraud Detection Pipeline

```java
public class FraudDetectionPipeline {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        
        // Source: transactions from Kafka
        DataStream<Transaction> transactions = env.fromSource(
            KafkaIntegration.createTransactionSource(),
            WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((tx, ts) -> tx.getTimestamp()),
            "transactions-source"
        );
        
        // Rule 1: Velocity check (>5 transactions in 1 minute)
        DataStream<Alert> velocityAlerts = transactions
            .keyBy(Transaction::getCardNumber)
            .window(TumblingEventTimeWindows.of(Time.minutes(1)))
            .aggregate(new CountAggregate())
            .filter(count -> count.getValue() > 5)
            .map(count -> new Alert("VELOCITY", count.getKey(), 
                count.getValue() + " transactions in 1 minute"))
            .name("velocity-check");
        
        // Rule 2: Geographic impossibility (2 transactions >500km apart within 30 min)
        DataStream<Alert> geoAlerts = transactions
            .keyBy(Transaction::getCardNumber)
            .process(new GeoImpossibilityDetector())
            .name("geo-impossibility");
        
        // Rule 3: Amount anomaly (>3 std deviations from user's average)
        DataStream<Alert> amountAlerts = transactions
            .keyBy(Transaction::getCardNumber)
            .process(new AmountAnomalyDetector())
            .name("amount-anomaly");
        
        // Merge all alerts
        DataStream<Alert> allAlerts = velocityAlerts
            .union(geoAlerts, amountAlerts);
        
        // Deduplicate alerts (same card, same rule, within 5 min)
        DataStream<Alert> dedupedAlerts = allAlerts
            .keyBy(a -> a.getCardNumber() + ":" + a.getRuleId())
            .process(new DeduplicationFunction(Duration.ofMinutes(5)))
            .name("dedup-alerts");
        
        dedupedAlerts.sinkTo(createAlertKafkaSink());
        
        env.execute("Fraud Detection Pipeline");
    }
}
```

---

## 15. Real-Time Metrics Aggregation

```java
public class MetricsAggregationJob {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        
        DataStream<MetricEvent> metrics = env.fromSource(createMetricsSource(),
            WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((m, ts) -> m.getTimestamp()),
            "metrics-source");
        
        // 1-minute aggregations (p50, p95, p99, avg, count)
        DataStream<AggregatedMetric> oneMinAgg = metrics
            .keyBy(MetricEvent::getMetricName)
            .window(TumblingEventTimeWindows.of(Time.minutes(1)))
            .aggregate(new PercentileAggregate())
            .name("1min-percentile-agg");
        
        // 5-minute aggregations
        DataStream<AggregatedMetric> fiveMinAgg = metrics
            .keyBy(MetricEvent::getMetricName)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(new PercentileAggregate())
            .name("5min-percentile-agg");
        
        // Alert on p99 > threshold
        DataStream<Alert> latencyAlerts = oneMinAgg
            .filter(m -> m.getP99() > m.getThreshold())
            .map(m -> new Alert("LATENCY_SLA_BREACH", m.getMetricName(),
                "p99=" + m.getP99() + "ms > threshold=" + m.getThreshold() + "ms"))
            .name("sla-breach-alerts");
        
        oneMinAgg.sinkTo(createTimeseriesDBSink("metrics_1min"));
        fiveMinAgg.sinkTo(createTimeseriesDBSink("metrics_5min"));
        latencyAlerts.sinkTo(createAlertSink());
        
        env.execute("Real-Time Metrics Aggregation");
    }
}
```

---

## 16. Deduplication (Exactly-Once Events)

```java
public class DeduplicationFunction extends KeyedProcessFunction<String, Event, Event> {
    
    private final Duration ttl;
    private ValueState<Boolean> seenState;
    
    public DeduplicationFunction(Duration ttl) {
        this.ttl = ttl;
    }
    
    @Override
    public void open(Configuration parameters) {
        StateTtlConfig ttlConfig = StateTtlConfig.newBuilder(
                org.apache.flink.api.common.time.Time.milliseconds(ttl.toMillis()))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
            .cleanupFullSnapshot()
            .build();
        
        ValueStateDescriptor<Boolean> descriptor = new ValueStateDescriptor<>("seen", Boolean.class);
        descriptor.enableTimeToLive(ttlConfig);
        seenState = getRuntimeContext().getState(descriptor);
    }
    
    @Override
    public void processElement(Event event, Context ctx, Collector<Event> out) throws Exception {
        if (seenState.value() == null) {
            // First time seeing this event
            seenState.update(true);
            out.collect(event);
        }
        // Duplicate — silently drop
    }
}

// Usage: deduplicate by event ID
DataStream<Event> deduped = events
    .keyBy(Event::getEventId)
    .process(new DeduplicationFunction(Duration.ofHours(24)))
    .name("dedup-24h-window");
```

---

## 17. Late Data Handling

```java
public class LateDataHandling {
    
    static final OutputTag<OrderEvent> LATE_ORDERS = new OutputTag<>("late-orders") {};
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        
        DataStream<OrderEvent> orders = /* ... */;
        
        SingleOutputStreamOperator<OrderCount> windowedCounts = orders
            .keyBy(OrderEvent::getCategory)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .allowedLateness(Time.minutes(2))           // Allow 2 min late data (re-fires window)
            .sideOutputLateData(LATE_ORDERS)             // Data later than allowed → side output
            .aggregate(new OrderCountAggregate())
            .name("5min-count-with-late-handling");
        
        // Main output: on-time + allowed-late results
        windowedCounts.sinkTo(createResultSink());
        
        // Side output: extremely late data (>2 min after window closed)
        DataStream<OrderEvent> lateOrders = windowedCounts.getSideOutput(LATE_ORDERS);
        lateOrders.sinkTo(createLateDataSink());  // Store for manual reconciliation
        
        env.execute("Late Data Handling Pipeline");
    }
}
```

---

## 18. Table API & SQL

```java
public class FlinkSQLExample {
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = FlinkJobConfig.createProductionEnv();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        
        // ====== Define Kafka source table via SQL ======
        tableEnv.executeSql("""
            CREATE TABLE orders (
                order_id STRING,
                user_id STRING,
                amount DECIMAL(10,2),
                status STRING,
                event_time TIMESTAMP(3),
                WATERMARK FOR event_time AS event_time - INTERVAL '10' SECOND
            ) WITH (
                'connector' = 'kafka',
                'topic' = 'order-events',
                'properties.bootstrap.servers' = 'kafka:9092',
                'properties.group.id' = 'flink-sql-orders',
                'format' = 'json',
                'scan.startup.mode' = 'latest-offset'
            )
        """);
        
        // ====== Tumbling window aggregation via SQL ======
        Table result = tableEnv.sqlQuery("""
            SELECT 
                user_id,
                TUMBLE_START(event_time, INTERVAL '5' MINUTE) as window_start,
                COUNT(*) as order_count,
                SUM(amount) as total_revenue,
                AVG(amount) as avg_order_value
            FROM orders
            WHERE status = 'COMPLETED'
            GROUP BY user_id, TUMBLE(event_time, INTERVAL '5' MINUTE)
            HAVING COUNT(*) > 3
        """);
        
        // Convert back to DataStream
        DataStream<Row> resultStream = tableEnv.toDataStream(result);
        resultStream.print();
        
        env.execute("Flink SQL Aggregation");
    }
}
```

---

## 19. Savepoints & Job Upgrades

```java
// ====== Assign UIDs for state compatibility across upgrades ======
public class UpgradeableJob {
    
    public static void buildPipeline(StreamExecutionEnvironment env) {
        DataStream<OrderEvent> orders = env.fromSource(source, watermarkStrategy, "kafka-source")
            .uid("order-kafka-source")       // UID for state mapping
            .name("Order Kafka Source");
        
        DataStream<EnrichedOrder> enriched = orders
            .keyBy(OrderEvent::getUserId)
            .process(new EnrichmentFunction())
            .uid("enrichment-function")       // UID preserves state on upgrade
            .name("Order Enrichment");
        
        DataStream<AggResult> aggregated = enriched
            .keyBy(EnrichedOrder::getCategory)
            .window(TumblingEventTimeWindows.of(Time.minutes(5)))
            .aggregate(new RevenueAggregate())
            .uid("revenue-5min-window")       // UID for window state
            .name("5-min Revenue Window");
        
        aggregated.sinkTo(sink)
            .uid("output-sink")
            .name("Output Sink");
    }
}

// Commands for savepoints:
// Take savepoint:  flink savepoint <jobId> s3://savepoints/
// Resume from:     flink run -s s3://savepoints/<savepoint-path> job.jar
// Cancel with SP:  flink cancel -s s3://savepoints/ <jobId>
```

---

## 20. Monitoring & Metrics

```java
public class MonitoredProcessFunction extends KeyedProcessFunction<String, Event, Result> {
    
    private transient Counter processedCounter;
    private transient Counter errorCounter;
    private transient Histogram latencyHistogram;
    
    @Override
    public void open(Configuration parameters) {
        MetricGroup metrics = getRuntimeContext().getMetricGroup();
        
        processedCounter = metrics.counter("events_processed");
        errorCounter = metrics.counter("events_errors");
        latencyHistogram = metrics.histogram("processing_latency", 
            new DescriptiveStatisticsHistogram(1000));
    }
    
    @Override
    public void processElement(Event event, Context ctx, Collector<Result> out) throws Exception {
        long start = System.nanoTime();
        
        try {
            Result result = process(event);
            out.collect(result);
            processedCounter.inc();
        } catch (Exception e) {
            errorCounter.inc();
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            latencyHistogram.update(durationMs);
        }
    }
}
```

---

## 🏆 Operator Selection Cheat Sheet

| Use Case | Operator / Pattern | Why |
|---|---|---|
| Transform each element | `map()` | 1:1 transformation |
| One input → many outputs | `flatMap()` | 1:N expansion |
| Stateful per-key logic | `KeyedProcessFunction` | Full control: state + timers |
| Time-based aggregation | `window()` + `aggregate()` | Tumbling/sliding/session windows |
| Pattern detection | CEP library | Sequence of events detection |
| External service call | `AsyncDataStream` | Non-blocking I/O enrichment |
| Dynamic config | `BroadcastState` | Update rules without restart |
| Split stream | Side outputs (`OutputTag`) | Route events to different sinks |
| Join two streams | `intervalJoin()` or `connect()` | Time-bounded correlation |
| Deduplication | `KeyedProcessFunction` + TTL state | Exactly-once event delivery |
| Late data | `allowedLateness()` + side output | Handle out-of-order events |
| Exactly-once to Kafka | `DeliveryGuarantee.EXACTLY_ONCE` | Transactional sink |
| SQL queries | Table API / Flink SQL | Declarative stream processing |

| Performance Tuning | Setting | Impact |
|---|---|---|
| Checkpoint interval | 60s (not 10s) | Less overhead, larger state loss on failure |
| State backend | RocksDB (large state) vs Heap (fast, small) | Trade memory vs disk |
| Parallelism | Match Kafka partitions | 1 Flink task per partition |
| Network buffers | `taskmanager.network.memory.fraction: 0.15` | More for shuffles |
| Watermark interval | 200ms default | Lower = more accurate, higher overhead |
| Max parallelism | Set once, never change | Determines max rescale factor |

---

*All examples use Flink 1.17+ APIs. For older versions, adjust source/sink connectors accordingly. Always assign `.uid()` to stateful operators for savepoint compatibility.*
