# 📨 Topic 71: Kafka Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Apache Kafka using the **official Kafka client**, **Spring Kafka**, and **Kafka Streams**. Every pattern includes production-ready code with proper serialization, error handling, exactly-once semantics, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Producer Setup & Configuration](#1-producer-setup--configuration)
- [2. Consumer Setup & Consumer Groups](#2-consumer-setup--consumer-groups)
- [3. Spring Kafka Integration](#3-spring-kafka-integration)
- [4. Exactly-Once Semantics (Transactions)](#4-exactly-once-semantics-transactions)
- [5. Custom Serialization (Avro/JSON)](#5-custom-serialization-avrojson)
- [6. Error Handling & Dead Letter Queue](#6-error-handling--dead-letter-queue)
- [7. Retry with Exponential Backoff](#7-retry-with-exponential-backoff)
- [8. Batch Processing Consumer](#8-batch-processing-consumer)
- [9. Kafka Streams — Word Count](#9-kafka-streams--word-count)
- [10. Kafka Streams — Join & Aggregation](#10-kafka-streams--join--aggregation)
- [11. Event Sourcing Pattern](#11-event-sourcing-pattern)
- [12. Saga / Choreography Pattern](#12-saga--choreography-pattern)
- [13. Outbox Pattern (DB + Kafka)](#13-outbox-pattern-db--kafka)
- [14. Consumer Rebalance Listener](#14-consumer-rebalance-listener)
- [15. Schema Registry Integration](#15-schema-registry-integration)
- [16. Partition Assignment Strategy](#16-partition-assignment-strategy)
- [17. Idempotent Consumer](#17-idempotent-consumer)
- [18. Metrics & Monitoring](#18-metrics--monitoring)
- [19. Multi-Cluster Replication (MirrorMaker)](#19-multi-cluster-replication)
- [20. Testing Kafka (Embedded/Testcontainers)](#20-testing-kafka)
- [🏆 Configuration Cheat Sheet](#-configuration-cheat-sheet)

---

## 1. Producer Setup & Configuration

```java
public class KafkaProducerFactory {
    
    // ====== Production-grade producer configuration ======
    public static KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        
        // Connection
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-1:9092,kafka-2:9092,kafka-3:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "order-service-producer");
        
        // Serialization
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Reliability: Wait for ALL replicas to acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // -1 = all ISR replicas
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        
        // Idempotent producer (prevent duplicate messages on retry)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // Batching for throughput
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);        // 16KB batch
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);             // Wait 5ms for batch fill
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);  // 32MB buffer
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Compress batches
        
        // Timeouts
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        return new KafkaProducer<>(props);
    }
    
    // ====== SEND with callback (async) ======
    public static void sendAsync(KafkaProducer<String, String> producer, 
                                  String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Send failed: topic={}, key={}", topic, key, exception);
                Metrics.counter("kafka.send.error", "topic", topic).increment();
            } else {
                log.debug("Sent: topic={}, partition={}, offset={}", 
                    metadata.topic(), metadata.partition(), metadata.offset());
                Metrics.counter("kafka.send.success", "topic", topic).increment();
            }
        });
    }
    
    // ====== SEND sync (blocking — for critical messages) ======
    public static RecordMetadata sendSync(KafkaProducer<String, String> producer,
                                           String topic, String key, String value) 
            throws ExecutionException, InterruptedException {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        return producer.send(record).get();  // Blocks until acknowledged
    }
    
    // ====== SEND with headers (for tracing/metadata) ======
    public static void sendWithHeaders(KafkaProducer<String, String> producer,
                                        String topic, String key, String value,
                                        Map<String, String> headers) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        headers.forEach((k, v) -> record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
        
        // Add trace context
        record.headers().add("trace-id", MDC.get("traceId").getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes());
        
        producer.send(record);
    }
}
```

---

## 2. Consumer Setup & Consumer Groups

```java
public class KafkaConsumerFactory {
    
    // ====== Production-grade consumer configuration ======
    public static KafkaConsumer<String, String> createConsumer(String groupId) {
        Properties props = new Properties();
        
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-1:9092,kafka-2:9092,kafka-3:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, groupId + "-" + UUID.randomUUID().toString().substring(0, 8));
        
        // Deserialization
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        
        // Offset management
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning if no offset
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);       // Manual commit for exactly-once
        
        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);          // Max records per poll
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);   // 5 min max processing time
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);          // Min 1KB per fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);         // Wait 500ms for min bytes
        
        // Session management
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);      // 30s session timeout
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);   // 10s heartbeat
        
        return new KafkaConsumer<>(props);
    }
    
    // ====== CONSUME LOOP — Production pattern ======
    public static void consumeLoop(String groupId, String topic) {
        KafkaConsumer<String, String> consumer = createConsumer(groupId);
        consumer.subscribe(List.of(topic));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down consumer...");
            consumer.wakeup();  // Breaks out of poll()
        }));
        
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        log.error("Failed to process: partition={}, offset={}", 
                            record.partition(), record.offset(), e);
                        handleFailedRecord(record, e);
                    }
                }
                
                // Manual commit after successful processing
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            // Expected on shutdown
        } finally {
            consumer.close();
            log.info("Consumer closed.");
        }
    }
    
    // ====== PER-PARTITION COMMIT (finer control) ======
    public static void consumeWithPerPartitionCommit(String groupId, String topic) {
        KafkaConsumer<String, String> consumer = createConsumer(groupId);
        consumer.subscribe(List.of(topic));
        
        Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
        int processedCount = 0;
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                processRecord(record);
                
                currentOffsets.put(
                    new TopicPartition(record.topic(), record.partition()),
                    new OffsetAndMetadata(record.offset() + 1)
                );
                
                // Commit every 100 records
                if (++processedCount % 100 == 0) {
                    consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                        if (exception != null) {
                            log.error("Commit failed", exception);
                        }
                    });
                }
            }
        }
    }
}
```

---

## 3. Spring Kafka Integration

```java
// ====== Spring Kafka Configuration ======
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        );
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
            ConsumerConfig.GROUP_ID_CONFIG, "order-processing-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );
        return new DefaultKafkaConsumerFactory<>(config);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);  // 3 consumer threads
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate()),
            new FixedBackOff(1000L, 3)  // 3 retries, 1s apart
        ));
        return factory;
    }
}

// ====== PRODUCER SERVICE ======
@Service
public class OrderEventProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    
    public CompletableFuture<SendResult<String, String>> publishOrderEvent(OrderEvent event) {
        String payload = mapper.writeValueAsString(event);
        
        return kafkaTemplate.send("order-events", event.getOrderId(), payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish order event: {}", event.getOrderId(), ex);
                } else {
                    log.info("Published: topic={}, partition={}, offset={}", 
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}

// ====== CONSUMER (Listener) ======
@Service
public class OrderEventConsumer {
    
    @KafkaListener(topics = "order-events", groupId = "order-processing-group")
    public void handleOrderEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        try {
            OrderEvent event = mapper.readValue(payload, OrderEvent.class);
            log.info("Processing order: {} (partition={}, offset={})", 
                event.getOrderId(), partition, offset);
            
            orderService.process(event);
            ack.acknowledge();  // Manual ACK after successful processing
            
        } catch (Exception e) {
            log.error("Failed to process event at partition={}, offset={}", partition, offset, e);
            // Don't ack — will be retried or sent to DLQ by error handler
            throw e;
        }
    }
    
    // ====== BATCH LISTENER ======
    @KafkaListener(topics = "click-events", groupId = "analytics-group",
                   containerFactory = "batchKafkaListenerFactory")
    public void handleClickEvents(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Received batch of {} click events", records.size());
        
        List<ClickEvent> events = records.stream()
            .map(r -> mapper.readValue(r.value(), ClickEvent.class))
            .collect(Collectors.toList());
        
        analyticsService.processBatch(events);
        ack.acknowledge();
    }
}
```

---

## 4. Exactly-Once Semantics (Transactions)

```java
@Service
public class TransactionalKafkaProducer {
    
    private final KafkaProducer<String, String> producer;
    
    public TransactionalKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-tx-" + UUID.randomUUID());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        this.producer = new KafkaProducer<>(props);
        this.producer.initTransactions();  // Required for transactional producer
    }
    
    // ====== Send multiple messages atomically (all or nothing) ======
    public void sendTransactional(String orderId, OrderEvent orderEvent, 
                                   PaymentEvent paymentEvent, InventoryEvent inventoryEvent) {
        producer.beginTransaction();
        try {
            producer.send(new ProducerRecord<>("order-events", orderId, 
                serialize(orderEvent)));
            producer.send(new ProducerRecord<>("payment-events", orderId, 
                serialize(paymentEvent)));
            producer.send(new ProducerRecord<>("inventory-events", orderId, 
                serialize(inventoryEvent)));
            
            producer.commitTransaction();
            log.info("Transaction committed for order: {}", orderId);
            
        } catch (Exception e) {
            producer.abortTransaction();
            log.error("Transaction aborted for order: {}", orderId, e);
            throw new KafkaTransactionException("Failed to publish events", e);
        }
    }
    
    // ====== Consume-Transform-Produce (exactly-once) ======
    public void consumeTransformProduce(KafkaConsumer<String, String> consumer) {
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            if (records.isEmpty()) continue;
            
            producer.beginTransaction();
            try {
                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                
                for (ConsumerRecord<String, String> record : records) {
                    // Transform
                    String enriched = enrich(record.value());
                    
                    // Produce to output topic
                    producer.send(new ProducerRecord<>("enriched-events", record.key(), enriched));
                    
                    offsets.put(
                        new TopicPartition(record.topic(), record.partition()),
                        new OffsetAndMetadata(record.offset() + 1)
                    );
                }
                
                // Commit consumer offsets within the transaction
                producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
                producer.commitTransaction();
                
            } catch (Exception e) {
                producer.abortTransaction();
                log.error("Transform transaction failed", e);
            }
        }
    }
}
```

---

## 5. Custom Serialization (Avro/JSON)

```java
// ====== JSON Serializer/Deserializer ======
public class JsonSerializer<T> implements Serializer<T> {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) return null;
        try {
            return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing to JSON", e);
        }
    }
}

public class JsonDeserializer<T> implements Deserializer<T> {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    private final Class<T> targetType;
    
    public JsonDeserializer(Class<T> targetType) {
        this.targetType = targetType;
    }
    
    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            return mapper.readValue(data, targetType);
        } catch (IOException e) {
            throw new SerializationException("Error deserializing JSON", e);
        }
    }
}

// ====== Usage with typed producer ======
public class TypedKafkaProducer {
    private final KafkaProducer<String, OrderEvent> producer;
    
    public TypedKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        this.producer = new KafkaProducer<>(props);
    }
    
    public void send(OrderEvent event) {
        producer.send(new ProducerRecord<>("order-events", event.getOrderId(), event));
    }
}
```

---

## 6. Error Handling & Dead Letter Queue

```java
@Configuration
public class KafkaDLQConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        
        // Dead Letter Topic recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + ".DLQ", record.partition())
        );
        
        // Retry 3 times with backoff, then send to DLQ
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new ExponentialBackOff(1000L, 2.0) {{  // 1s, 2s, 4s
                setMaxElapsedTime(10000L);  // Max 10s total
            }}
        );
        
        // Don't retry for certain exceptions (non-transient)
        errorHandler.addNotRetryableExceptions(
            DeserializationException.class,
            ValidationException.class
        );
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}

// ====== DLQ Consumer (process failed messages) ======
@Service
public class DLQProcessor {
    
    @KafkaListener(topics = "order-events.DLQ", groupId = "dlq-processor")
    public void processDLQ(ConsumerRecord<String, String> record) {
        log.warn("DLQ message: topic={}, key={}, headers={}", 
            record.topic(), record.key(), record.headers());
        
        // Extract original exception from headers
        Header exceptionHeader = record.headers().lastHeader("kafka_dlt-exception-message");
        String error = exceptionHeader != null ? new String(exceptionHeader.value()) : "unknown";
        
        // Alert ops team
        alertService.sendAlert("DLQ message received", 
            "Key: " + record.key() + ", Error: " + error);
        
        // Store for manual review
        dlqRepository.save(new DLQEntry(record.key(), record.value(), error, Instant.now()));
    }
}
```

---

## 7. Retry with Exponential Backoff

```java
@Service
public class RetryableKafkaConsumer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    // Manual retry with dedicated retry topics
    @KafkaListener(topics = "order-events", groupId = "order-processor")
    public void processOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            orderService.process(record.value());
            ack.acknowledge();
        } catch (TransientException e) {
            // Transient error — send to retry topic
            int retryCount = getRetryCount(record);
            if (retryCount < 3) {
                sendToRetryTopic(record, retryCount + 1);
            } else {
                sendToDLQ(record, e);
            }
            ack.acknowledge();  // Ack original to avoid reprocessing
        }
    }
    
    // Retry topics: order-events.retry.1, order-events.retry.2, order-events.retry.3
    @KafkaListener(topics = {"order-events.retry.1", "order-events.retry.2", "order-events.retry.3"},
                   groupId = "order-retry-processor")
    public void processRetry(ConsumerRecord<String, String> record, Acknowledgment ack) {
        int retryCount = getRetryCount(record);
        long delay = (long) Math.pow(2, retryCount) * 1000;  // 2s, 4s, 8s
        
        try {
            Thread.sleep(delay);  // Backoff delay
            orderService.process(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            if (retryCount >= 3) {
                sendToDLQ(record, e);
            } else {
                sendToRetryTopic(record, retryCount + 1);
            }
            ack.acknowledge();
        }
    }
    
    private void sendToRetryTopic(ConsumerRecord<String, String> record, int retryCount) {
        ProducerRecord<String, String> retry = new ProducerRecord<>(
            record.topic() + ".retry." + retryCount, record.key(), record.value());
        retry.headers().add("retry-count", String.valueOf(retryCount).getBytes());
        retry.headers().add("original-topic", record.topic().getBytes());
        kafkaTemplate.send(retry);
    }
}
```

---

## 8. Batch Processing Consumer

```java
@Service
public class BatchKafkaConsumer {
    
    @KafkaListener(topics = "analytics-events", groupId = "analytics-batch",
                   containerFactory = "batchFactory")
    public void processBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Processing batch of {} records", records.size());
        
        // Group by partition for ordered processing
        Map<Integer, List<ConsumerRecord<String, String>>> byPartition = records.stream()
            .collect(Collectors.groupingBy(ConsumerRecord::partition));
        
        List<AnalyticsEvent> events = new ArrayList<>(records.size());
        
        for (var entry : byPartition.entrySet()) {
            for (var record : entry.getValue()) {
                try {
                    events.add(mapper.readValue(record.value(), AnalyticsEvent.class));
                } catch (Exception e) {
                    log.warn("Skipping malformed record: partition={}, offset={}", 
                        record.partition(), record.offset());
                }
            }
        }
        
        // Bulk write to database
        if (!events.isEmpty()) {
            analyticsRepository.bulkInsert(events);
            Metrics.counter("analytics.batch.processed").increment(events.size());
        }
        
        ack.acknowledge();
    }
}

// Batch factory configuration
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = 
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setBatchListener(true);  // Enable batch mode
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.getContainerProperties().setIdleBetweenPolls(100);
    return factory;
}
```

---

## 9. Kafka Streams — Word Count

```java
public class KafkaStreamsWordCount {
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "word-count-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        
        StreamsBuilder builder = new StreamsBuilder();
        
        // Read from input topic
        KStream<String, String> textLines = builder.stream("text-input");
        
        // Word count logic
        KTable<String, Long> wordCounts = textLines
            .flatMapValues(line -> Arrays.asList(line.toLowerCase().split("\\W+")))
            .groupBy((key, word) -> word)
            .count(Materialized.as("word-counts-store"));
        
        // Write results to output topic
        wordCounts.toStream().to("word-counts-output",
            Produced.with(Serdes.String(), Serdes.Long()));
        
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
    }
}
```

---

## 10. Kafka Streams — Join & Aggregation

```java
public class OrderEnrichmentStream {
    
    public Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        
        // Orders stream
        KStream<String, Order> orders = builder.stream("orders",
            Consumed.with(Serdes.String(), orderSerde));
        
        // User profiles table (compacted topic)
        KTable<String, UserProfile> users = builder.table("user-profiles",
            Consumed.with(Serdes.String(), userProfileSerde));
        
        // JOIN: Enrich order with user profile
        KStream<String, EnrichedOrder> enriched = orders
            .selectKey((key, order) -> order.getUserId())  // Re-key by userId
            .join(users,
                (order, profile) -> new EnrichedOrder(order, profile),
                Joined.with(Serdes.String(), orderSerde, userProfileSerde)
            );
        
        // AGGREGATE: Revenue per user in 1-hour windows
        KTable<Windowed<String>, Double> revenuePerUser = enriched
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
            .aggregate(
                () -> 0.0,  // Initializer
                (userId, order, total) -> total + order.getOrder().getTotal(),  // Aggregator
                Materialized.with(Serdes.String(), Serdes.Double())
            );
        
        revenuePerUser.toStream()
            .map((windowed, revenue) -> KeyValue.pair(windowed.key(), revenue))
            .to("user-hourly-revenue", Produced.with(Serdes.String(), Serdes.Double()));
        
        return builder.build();
    }
}
```

---

## 11. Event Sourcing Pattern

```java
@Service
public class EventSourcingWithKafka {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    
    // ====== Publish domain event ======
    public void publishEvent(String aggregateId, DomainEvent event) {
        String topic = "events." + event.getAggregateType();
        
        EventEnvelope envelope = new EventEnvelope(
            UUID.randomUUID().toString(),
            aggregateId,
            event.getClass().getSimpleName(),
            event.getVersion(),
            Instant.now(),
            mapper.writeValueAsString(event)
        );
        
        kafkaTemplate.send(topic, aggregateId, mapper.writeValueAsString(envelope));
    }
    
    // ====== Rebuild aggregate from events ======
    @KafkaListener(topics = "events.Order", groupId = "order-projection")
    public void buildOrderProjection(ConsumerRecord<String, String> record, Acknowledgment ack) {
        EventEnvelope envelope = mapper.readValue(record.value(), EventEnvelope.class);
        String aggregateId = envelope.getAggregateId();
        
        // Apply event to current state
        OrderState current = orderStateStore.get(aggregateId);
        if (current == null) current = new OrderState();
        
        switch (envelope.getEventType()) {
            case "OrderCreated":
                OrderCreated created = mapper.readValue(envelope.getPayload(), OrderCreated.class);
                current.apply(created);
                break;
            case "ItemAdded":
                ItemAdded added = mapper.readValue(envelope.getPayload(), ItemAdded.class);
                current.apply(added);
                break;
            case "OrderShipped":
                OrderShipped shipped = mapper.readValue(envelope.getPayload(), OrderShipped.class);
                current.apply(shipped);
                break;
        }
        
        orderStateStore.put(aggregateId, current);
        ack.acknowledge();
    }
}
```

---

## 12. Saga / Choreography Pattern

```java
@Service
public class OrderSagaOrchestrator {
    private final KafkaTemplate<String, String> kafka;
    
    // Step 1: Start saga — publish OrderCreated
    public void startOrderSaga(Order order) {
        publish("order-events", order.getId(), new OrderCreated(order));
    }
    
    // Step 2: Payment service listens → charges card → publishes result
    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        OrderCreated event = deserialize(record.value(), OrderCreated.class);
        try {
            paymentService.charge(event.getOrder());
            publish("payment-events", event.getOrderId(), new PaymentCompleted(event.getOrderId()));
        } catch (Exception e) {
            publish("payment-events", event.getOrderId(), new PaymentFailed(event.getOrderId(), e.getMessage()));
        }
    }
    
    // Step 3: Inventory service listens to payment result
    @KafkaListener(topics = "payment-events", groupId = "inventory-service")
    public void onPaymentResult(ConsumerRecord<String, String> record) {
        if (record.value().contains("PaymentCompleted")) {
            PaymentCompleted event = deserialize(record.value(), PaymentCompleted.class);
            try {
                inventoryService.reserve(event.getOrderId());
                publish("inventory-events", event.getOrderId(), new InventoryReserved(event.getOrderId()));
            } catch (Exception e) {
                // Compensate: refund payment
                publish("compensation-events", event.getOrderId(), new RefundRequested(event.getOrderId()));
            }
        }
    }
    
    // Compensation handler
    @KafkaListener(topics = "compensation-events", groupId = "payment-service")
    public void onCompensation(ConsumerRecord<String, String> record) {
        RefundRequested event = deserialize(record.value(), RefundRequested.class);
        paymentService.refund(event.getOrderId());
        publish("order-events", event.getOrderId(), new OrderCancelled(event.getOrderId(), "Inventory unavailable"));
    }
}
```

---

## 13. Outbox Pattern (DB + Kafka)

```java
@Service
@Transactional
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    
    // Step 1: Write business entity + outbox event in same DB transaction
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        // Outbox entry (same transaction as order)
        outboxRepository.save(new OutboxEvent(
            UUID.randomUUID().toString(),
            "Order",
            order.getId(),
            "OrderCreated",
            mapper.writeValueAsString(new OrderCreated(order)),
            OutboxStatus.PENDING,
            Instant.now()
        ));
        
        return order;
    }
}

// Step 2: Background poller publishes outbox events to Kafka
@Service
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafka;
    
    @Scheduled(fixedDelay = 100)  // Poll every 100ms
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAt(
            OutboxStatus.PENDING, PageRequest.of(0, 100));
        
        for (OutboxEvent event : pending) {
            try {
                kafka.send("events." + event.getAggregateType(), 
                    event.getAggregateId(), event.getPayload()).get();  // Sync send
                
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
                event.setRetryCount(event.getRetryCount() + 1);
                outboxRepository.save(event);
            }
        }
    }
}
```

---

## 14-20: Additional Patterns (Key Code)

### 14. Consumer Rebalance Listener
```java
consumer.subscribe(List.of("orders"), new ConsumerRebalanceListener() {
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        log.info("Partitions revoked: {}. Committing offsets...", partitions);
        consumer.commitSync();  // Commit before losing partitions
    }
    
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        log.info("Partitions assigned: {}", partitions);
        // Optionally seek to specific offsets
    }
});
```

### 17. Idempotent Consumer
```java
@KafkaListener(topics = "payment-events")
public void processPayment(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String eventId = new String(record.headers().lastHeader("event-id").value());
    
    // Deduplication check
    if (processedEventStore.exists(eventId)) {
        log.info("Duplicate event skipped: {}", eventId);
        ack.acknowledge();
        return;
    }
    
    // Process
    paymentService.process(record.value());
    processedEventStore.markProcessed(eventId, Duration.ofDays(7));
    ack.acknowledge();
}
```

### 20. Testing with Embedded Kafka
```java
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"test-orders"})
class OrderProducerTest {
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    
    @Test
    void shouldPublishOrderEvent() throws Exception {
        kafka.send("test-orders", "order-1", "{\"status\":\"CREATED\"}").get();
        
        Consumer<String, String> consumer = createTestConsumer(embeddedKafka);
        consumer.subscribe(List.of("test-orders"));
        
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        assertThat(records.iterator().next().value()).contains("CREATED");
    }
}
```

---

## 🏆 Configuration Cheat Sheet

| Scenario | Key Config | Value | Why |
|---|---|---|---|
| No data loss | `acks` | `all` | All ISR replicas acknowledge |
| No duplicates | `enable.idempotence` | `true` | Producer deduplication |
| High throughput | `batch.size` + `linger.ms` | 16KB + 5ms | Batch before sending |
| Exactly-once | `transactional.id` + `isolation.level` | set + `read_committed` | Atomic produce + consume |
| Fast consumer | `fetch.min.bytes` + `max.poll.records` | 1KB + 500 | Batch fetching |
| Ordering guarantee | Partition key | Same key → same partition | Per-key ordering |
| Manual offset | `enable.auto.commit` | `false` | Commit after processing |
| Quick rebalance | `session.timeout.ms` | 10000 | Detect dead consumers faster |

---

*All examples use Spring Kafka where applicable, with raw client examples for advanced patterns. Kafka Streams examples use the Streams DSL.*
