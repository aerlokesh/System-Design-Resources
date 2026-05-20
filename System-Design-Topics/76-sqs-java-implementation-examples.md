# 📬 Topic 76: AWS SQS Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Amazon SQS using **AWS SDK v2**, **Spring Cloud AWS**, and **JMS**. Every pattern includes production-ready code with standard/FIFO queues, DLQ, batching, long polling, visibility timeout, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration](#1-client-setup--configuration)
- [2. Send Messages (Standard Queue)](#2-send-messages-standard-queue)
- [3. Receive & Delete Messages](#3-receive--delete-messages)
- [4. FIFO Queue (Exactly-Once Ordering)](#4-fifo-queue-exactly-once-ordering)
- [5. Batch Operations](#5-batch-operations)
- [6. Long Polling](#6-long-polling)
- [7. Visibility Timeout & Processing](#7-visibility-timeout--processing)
- [8. Dead Letter Queue (DLQ)](#8-dead-letter-queue-dlq)
- [9. Message Attributes & Filtering](#9-message-attributes--filtering)
- [10. Delay Queues & Scheduled Messages](#10-delay-queues--scheduled-messages)
- [11. Spring Cloud AWS Integration](#11-spring-cloud-aws-integration)
- [12. Consumer Worker Pattern](#12-consumer-worker-pattern)
- [13. Fan-Out with SNS + SQS](#13-fan-out-with-sns--sqs)
- [14. Idempotent Consumer](#14-idempotent-consumer)
- [15. Graceful Shutdown](#15-graceful-shutdown)
- [16. Metrics & Monitoring](#16-metrics--monitoring)
- [17. FIFO Deduplication](#17-fifo-deduplication)
- [18. Large Messages (Extended Client)](#18-large-messages-extended-client)
- [19. Request-Reply Pattern](#19-request-reply-pattern)
- [20. Error Handling & Retry Strategy](#20-error-handling--retry-strategy)
- [🏆 Configuration Cheat Sheet](#-configuration-cheat-sheet)

---

## 1. Client Setup & Configuration

```java
@Configuration
public class SqsConfig {
    
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .apiCallTimeout(Duration.ofSeconds(10))
                .build())
            .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                .maxConcurrency(50)
                .connectionTimeout(Duration.ofSeconds(5)))
            .build();
    }
    
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

---

## 2. Send Messages (Standard Queue)

```java
@Service
public class SqsProducerService {
    private final SqsClient sqs;
    private final String queueUrl;
    private final ObjectMapper mapper;
    
    // ====== Send single message ======
    public String sendMessage(OrderEvent event) {
        String body = mapper.writeValueAsString(event);
        
        SendMessageResponse response = sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(Map.of(
                "eventType", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(event.getType().name())
                    .build(),
                "priority", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(event.getPriority()))
                    .build()
            ))
            .build());
        
        log.info("Sent message: messageId={}", response.messageId());
        return response.messageId();
    }
    
    // ====== Send with delay (schedule for later) ======
    public String sendDelayed(OrderEvent event, int delaySeconds) {
        String body = mapper.writeValueAsString(event);
        
        SendMessageResponse response = sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .delaySeconds(delaySeconds)  // 0-900 seconds (max 15 min)
            .build());
        
        return response.messageId();
    }
}
```

---

## 3. Receive & Delete Messages

```java
@Service
public class SqsConsumerService {
    private final SqsClient sqs;
    private final String queueUrl;
    
    // ====== Receive + Process + Delete (standard pattern) ======
    public void pollAndProcess() {
        ReceiveMessageResponse response = sqs.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(10)            // Max 10 per poll
            .waitTimeSeconds(20)                 // Long polling (20s max)
            .visibilityTimeout(60)               // 60s to process before retry
            .messageAttributeNames("All")
            .attributeNames(QueueAttributeName.ALL)
            .build());
        
        for (Message message : response.messages()) {
            try {
                processMessage(message);
                
                // Delete after successful processing
                sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
                
                Metrics.counter("sqs.processed").increment();
                
            } catch (Exception e) {
                log.error("Failed to process message: {}", message.messageId(), e);
                Metrics.counter("sqs.processing.error").increment();
                // Don't delete — message becomes visible again after visibility timeout
            }
        }
    }
    
    // ====== Change visibility timeout (extend processing time) ======
    public void extendVisibility(String receiptHandle, int additionalSeconds) {
        sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(additionalSeconds)
            .build());
    }
}
```

---

## 4. FIFO Queue (Exactly-Once Ordering)

```java
@Service
public class FifoQueueService {
    private final SqsClient sqs;
    private final String fifoQueueUrl;  // Must end with .fifo
    
    // ====== Send to FIFO queue (ordered by group) ======
    public String sendFifo(OrderEvent event) {
        SendMessageResponse response = sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(fifoQueueUrl)
            .messageBody(mapper.writeValueAsString(event))
            .messageGroupId(event.getUserId())  // Messages with same group are ordered
            .messageDeduplicationId(event.getEventId())  // Prevents duplicates (5-min window)
            .build());
        
        return response.messageId();
    }
    
    // WHY FIFO:
    // Standard queue: order-created might arrive AFTER order-shipped (out of order)
    // FIFO queue: grouped by userId → all events for one user arrive in exact order
    // Trade-off: FIFO = 300 msg/sec (vs Standard = unlimited throughput)
    
    // ====== High-throughput FIFO (multiple groups) ======
    public void sendHighThroughputFifo(List<OrderEvent> events) {
        // Each unique messageGroupId gets its own ordered sequence
        // Different groups process in parallel → more throughput
        List<SendMessageBatchRequestEntry> entries = events.stream()
            .map(event -> SendMessageBatchRequestEntry.builder()
                .id(event.getEventId())
                .messageBody(mapper.writeValueAsString(event))
                .messageGroupId(event.getOrderId())  // Group by order (not user) for more parallelism
                .messageDeduplicationId(event.getEventId())
                .build())
            .collect(Collectors.toList());
        
        // Batch send (up to 10 per call)
        Lists.partition(entries, 10).forEach(batch -> {
            sqs.sendMessageBatch(SendMessageBatchRequest.builder()
                .queueUrl(fifoQueueUrl)
                .entries(batch)
                .build());
        });
    }
}
```

---

## 5. Batch Operations

```java
@Service
public class SqsBatchService {
    
    // ====== Batch send (up to 10 messages per call) ======
    public void batchSend(List<OrderEvent> events) {
        List<List<OrderEvent>> chunks = Lists.partition(events, 10);
        
        for (List<OrderEvent> chunk : chunks) {
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                entries.add(SendMessageBatchRequestEntry.builder()
                    .id(String.valueOf(i))
                    .messageBody(mapper.writeValueAsString(chunk.get(i)))
                    .build());
            }
            
            SendMessageBatchResponse response = sqs.sendMessageBatch(
                SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build());
            
            // Handle failures
            if (!response.failed().isEmpty()) {
                response.failed().forEach(f -> 
                    log.error("Batch send failed: id={}, code={}, msg={}", 
                        f.id(), f.code(), f.message()));
            }
        }
    }
    
    // ====== Batch delete (after processing) ======
    public void batchDelete(List<Message> processedMessages) {
        List<DeleteMessageBatchRequestEntry> entries = processedMessages.stream()
            .map(msg -> DeleteMessageBatchRequestEntry.builder()
                .id(msg.messageId())
                .receiptHandle(msg.receiptHandle())
                .build())
            .collect(Collectors.toList());
        
        Lists.partition(entries, 10).forEach(batch -> {
            sqs.deleteMessageBatch(DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(batch)
                .build());
        });
    }
}
```

---

## 6. Long Polling

```java
// ====== Long polling consumer loop (production pattern) ======
@Service
public class LongPollingConsumer {
    private volatile boolean running = true;
    private final ExecutorService pollerPool = Executors.newFixedThreadPool(4);
    
    @PostConstruct
    public void startPolling() {
        for (int i = 0; i < 4; i++) {  // 4 polling threads
            pollerPool.submit(this::pollingLoop);
        }
    }
    
    private void pollingLoop() {
        while (running) {
            try {
                ReceiveMessageResponse response = sqs.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)      // Long poll: wait up to 20s for messages
                        .visibilityTimeout(120)    // 2 min to process
                        .build());
                
                if (response.messages().isEmpty()) {
                    continue;  // No messages — loop back to long poll immediately
                }
                
                List<Message> processed = new ArrayList<>();
                for (Message msg : response.messages()) {
                    try {
                        processMessage(msg);
                        processed.add(msg);
                    } catch (Exception e) {
                        log.error("Processing failed: {}", msg.messageId(), e);
                    }
                }
                
                if (!processed.isEmpty()) {
                    batchDelete(processed);
                }
                
            } catch (SqsException e) {
                log.error("SQS polling error", e);
                sleep(1000);  // Back off on errors
            }
        }
    }
    
    @PreDestroy
    public void shutdown() {
        running = false;
        pollerPool.shutdown();
        try { pollerPool.awaitTermination(30, TimeUnit.SECONDS); }
        catch (InterruptedException e) { pollerPool.shutdownNow(); }
    }
}
```

---

## 7. Visibility Timeout & Processing

```java
@Service
public class VisibilityTimeoutHandler {
    
    // ====== Extend visibility for long-running tasks ======
    public void processLongRunningTask(Message message) {
        String receiptHandle = message.receiptHandle();
        
        // Start heartbeat: extend visibility every 30s while processing
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .visibilityTimeout(60)  // Extend by 60s each time
                    .build());
            } catch (Exception e) {
                log.warn("Failed to extend visibility", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        try {
            // Long-running processing (could take minutes)
            processHeavyTask(message);
            
            // Success: delete message
            sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());
        } finally {
            heartbeat.cancel(false);  // Stop heartbeat
        }
    }
}
```

---

## 8. Dead Letter Queue (DLQ)

```java
@Service
public class DlqService {
    
    // ====== Process DLQ messages (manual review/retry) ======
    public void processDlqMessages() {
        ReceiveMessageResponse response = sqs.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .attributeNames(QueueAttributeName.ALL)  // Get retry count etc.
                .build());
        
        for (Message msg : response.messages()) {
            // Check how many times it was received (from attributes)
            String receiveCount = msg.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
            log.warn("DLQ message: id={}, receiveCount={}", msg.messageId(), receiveCount);
            
            // Attempt to reprocess or send alert
            try {
                reprocessMessage(msg);
                sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(dlqUrl).receiptHandle(msg.receiptHandle()).build());
            } catch (Exception e) {
                // Still failing — alert ops team
                alertService.sendAlert("DLQ message unprocessable: " + msg.messageId());
            }
        }
    }
    
    // ====== Redrive messages from DLQ back to source queue ======
    public int redriveAll() {
        int redriven = 0;
        while (true) {
            ReceiveMessageResponse response = sqs.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(dlqUrl).maxNumberOfMessages(10).waitTimeSeconds(1).build());
            
            if (response.messages().isEmpty()) break;
            
            for (Message msg : response.messages()) {
                sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sourceQueueUrl)
                    .messageBody(msg.body())
                    .build());
                sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(dlqUrl).receiptHandle(msg.receiptHandle()).build());
                redriven++;
            }
        }
        log.info("Redrove {} messages from DLQ to source queue", redriven);
        return redriven;
    }
}
```

---

## 9. Message Attributes & Filtering

```java
// ====== Send with attributes (for SNS filtering) ======
public void sendWithAttributes(OrderEvent event) {
    sqs.sendMessage(SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(mapper.writeValueAsString(event))
        .messageAttributes(Map.of(
            "eventType", attr("String", event.getType().name()),
            "region", attr("String", event.getRegion()),
            "amount", attr("Number", event.getAmount().toString()),
            "isPrime", attr("String", String.valueOf(event.isPrime()))
        ))
        .build());
}

// ====== Read attributes on receive ======
public void processWithAttributes(Message message) {
    Map<String, MessageAttributeValue> attrs = message.messageAttributes();
    String eventType = attrs.get("eventType").stringValue();
    String region = attrs.get("region").stringValue();
    
    // Route based on attributes
    switch (eventType) {
        case "ORDER_CREATED" -> orderCreatedHandler.handle(message);
        case "ORDER_SHIPPED" -> orderShippedHandler.handle(message);
        case "ORDER_CANCELLED" -> orderCancelledHandler.handle(message);
    }
}

private MessageAttributeValue attr(String type, String value) {
    return MessageAttributeValue.builder().dataType(type).stringValue(value).build();
}
```

---

## 10. Delay Queues & Scheduled Messages

```java
@Service
public class DelayedMessageService {
    
    // ====== Schedule retry after failure ======
    public void scheduleRetry(String originalMessageBody, int attempt) {
        int delaySeconds = (int) Math.min(Math.pow(2, attempt) * 30, 900);  // Max 15 min
        
        sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(originalMessageBody)
            .delaySeconds(delaySeconds)
            .messageAttributes(Map.of(
                "retryAttempt", attr("Number", String.valueOf(attempt + 1))
            ))
            .build());
        
        log.info("Scheduled retry #{} with {}s delay", attempt + 1, delaySeconds);
    }
    
    // ====== Schedule future task (e.g., reminder email after 1 hour) ======
    public void scheduleReminder(String userId, String message) {
        // SQS max delay is 15 minutes. For longer delays, use Step Functions or EventBridge
        // For 15 min or less:
        sqs.sendMessage(SendMessageRequest.builder()
            .queueUrl(reminderQueueUrl)
            .messageBody(mapper.writeValueAsString(new Reminder(userId, message)))
            .delaySeconds(900)  // 15 minutes
            .build());
    }
}
```

---

## 11. Spring Cloud AWS Integration

```java
// ====== Spring Cloud AWS SQS Listener ======
@Configuration
@EnableSqs
public class SpringSqsConfig {
    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient client) {
        return SqsMessageListenerContainerFactory.builder()
            .sqsAsyncClient(client)
            .build();
    }
}

@Service
public class SpringSqsConsumer {
    
    @SqsListener("order-events-queue")
    public void handleOrderEvent(@Payload OrderEvent event, 
                                  @Header("eventType") String eventType,
                                  Acknowledgement ack) {
        try {
            log.info("Processing order event: {} type={}", event.getOrderId(), eventType);
            orderService.process(event);
            ack.acknowledge();  // Manual ack
        } catch (Exception e) {
            log.error("Failed to process", e);
            // Don't ack → message returns to queue after visibility timeout
            throw e;
        }
    }
    
    @SqsListener(value = "high-priority-queue", maxConcurrentMessages = "5", maxMessagesPerPoll = "5")
    public void handleHighPriority(@Payload String body) {
        processHighPriority(body);
        // Auto-acknowledged on success
    }
}

// ====== Spring Cloud AWS SQS Template (producer) ======
@Service
public class SpringSqsProducer {
    private final SqsTemplate sqsTemplate;
    
    public void send(OrderEvent event) {
        sqsTemplate.send(to -> to
            .queue("order-events-queue")
            .payload(event)
            .header("eventType", event.getType().name())
        );
    }
}
```

---

## 12. Consumer Worker Pattern

```java
@Service
public class SqsWorkerPool {
    private final int workerCount = 8;
    private final ExecutorService workers = Executors.newFixedThreadPool(workerCount);
    private final BlockingQueue<Message> internalQueue = new LinkedBlockingQueue<>(1000);
    private volatile boolean running = true;
    
    @PostConstruct
    public void start() {
        // 2 pollers feed the internal queue
        for (int i = 0; i < 2; i++) {
            Thread.startVirtualThread(this::pollerLoop);
        }
        // N workers process from internal queue
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
    }
    
    private void pollerLoop() {
        while (running) {
            try {
                ReceiveMessageResponse resp = sqs.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(20).build());
                for (Message msg : resp.messages()) {
                    internalQueue.put(msg);  // Blocks if internal queue full (backpressure)
                }
            } catch (Exception e) {
                log.error("Poller error", e); sleep(1000);
            }
        }
    }
    
    private void workerLoop() {
        while (running) {
            try {
                Message msg = internalQueue.poll(1, TimeUnit.SECONDS);
                if (msg == null) continue;
                
                processMessage(msg);
                sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl).receiptHandle(msg.receiptHandle()).build());
            } catch (Exception e) {
                log.error("Worker error", e);
            }
        }
    }
}
```

---

## 13-20: Additional Patterns (Key Code)

### 13. Fan-Out with SNS + SQS
```java
// SNS publishes to multiple SQS queues (fan-out)
public void publishToSns(OrderEvent event) {
    snsClient.publish(PublishRequest.builder()
        .topicArn("arn:aws:sns:us-east-1:123:order-events")
        .message(mapper.writeValueAsString(event))
        .messageAttributes(Map.of(
            "eventType", MessageAttributeValue.builder()
                .dataType("String").stringValue(event.getType().name()).build()
        ))
        .build());
    // SNS delivers to: order-processing-queue, analytics-queue, notification-queue
}
```

### 14. Idempotent Consumer
```java
public void processIdempotent(Message message) {
    String messageId = message.messageId();
    // Check if already processed (DynamoDB/Redis)
    if (idempotencyStore.exists(messageId)) {
        log.info("Duplicate message skipped: {}", messageId);
        deleteMessage(message);
        return;
    }
    processMessage(message);
    idempotencyStore.markProcessed(messageId, Duration.ofHours(24));
    deleteMessage(message);
}
```

### 18. Large Messages (Extended Client)
```java
// Messages > 256KB: store body in S3, send reference in SQS
AmazonSQSExtendedClient extendedSqs = new AmazonSQSExtendedClient(sqsClient,
    new ExtendedClientConfiguration()
        .withLargePayloadSupportEnabled(s3Client, "sqs-large-messages-bucket")
        .withAlwaysThroughS3(false)  // Only use S3 if > 256KB
        .withPayloadSizeThreshold(262144));  // 256KB threshold
```

---

## 🏆 Configuration Cheat Sheet

| Scenario | Setting | Value | Why |
|---|---|---|---|
| Reduce empty polls | `WaitTimeSeconds` | 20 (max) | Long polling — reduces API calls by 90% |
| Processing time | `VisibilityTimeout` | 6× avg processing time | Prevents premature retry |
| Retry before DLQ | `maxReceiveCount` | 3-5 | Transient failures resolve after retries |
| Message ordering | Queue type | FIFO | Guarantees per-group ordering |
| Max throughput | Queue type | Standard | Nearly unlimited throughput |
| Duplicate prevention | FIFO + `messageDeduplicationId` | Event ID | 5-minute dedup window |
| Schedule later | `DelaySeconds` | 0-900 | Max 15 min per-message delay |
| Batch efficiency | `MaxNumberOfMessages` | 10 | Reduces API calls |

| Standard vs FIFO |
|---|
| Standard: unlimited throughput, at-least-once, best-effort ordering |
| FIFO: 300 msg/sec (3000 with batching), exactly-once, strict ordering per group |

---

*All examples use AWS SDK v2 for Java. For Spring Boot, Spring Cloud AWS provides annotation-based listeners.*
