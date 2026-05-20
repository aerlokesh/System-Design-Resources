# ⚡ Topic 73: DynamoDB Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Amazon DynamoDB using **AWS SDK v2**, **Enhanced Client**, and **Spring Boot**. Every pattern includes production-ready code with single-table design, GSIs, transactions, streams, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration](#1-client-setup--configuration)
- [2. Basic CRUD (Enhanced Client)](#2-basic-crud-enhanced-client)
- [3. Single-Table Design Patterns](#3-single-table-design-patterns)
- [4. Query & Scan Operations](#4-query--scan-operations)
- [5. Global Secondary Index (GSI)](#5-global-secondary-index-gsi)
- [6. Conditional Writes (Optimistic Locking)](#6-conditional-writes-optimistic-locking)
- [7. Transactions (ACID)](#7-transactions-acid)
- [8. Batch Operations](#8-batch-operations)
- [9. TTL (Auto-Expiry)](#9-ttl-auto-expiry)
- [10. DynamoDB Streams (CDC)](#10-dynamodb-streams-cdc)
- [11. Pagination (LastEvaluatedKey)](#11-pagination-lastevaluatedkey)
- [12. Atomic Counters](#12-atomic-counters)
- [13. Sparse Index Pattern](#13-sparse-index-pattern)
- [14. Write Sharding (Hot Partition)](#14-write-sharding-hot-partition)
- [15. DAX Caching](#15-dax-caching)
- [16. Retry & Error Handling](#16-retry--error-handling)
- [17. PartiQL Queries](#17-partiql-queries)
- [18. Export to S3](#18-export-to-s3)
- [19. Point-in-Time Recovery](#19-point-in-time-recovery)
- [20. Cost Optimization Patterns](#20-cost-optimization-patterns)
- [🏆 Access Pattern Cheat Sheet](#-access-pattern-cheat-sheet)

---

## 1. Client Setup & Configuration

```java
@Configuration
public class DynamoDBConfig {
    
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(3)
                    .build())
                .apiCallTimeout(Duration.ofSeconds(5))
                .apiCallAttemptTimeout(Duration.ofSeconds(2))
                .build())
            .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)
                .connectionTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2)))
            .build();
    }
    
    @Bean
    public DynamoDbEnhancedClient enhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(client)
            .build();
    }
    
    // Async client for high-throughput operations
    @Bean
    public DynamoDbAsyncClient asyncClient() {
        return DynamoDbAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

---

## 2. Basic CRUD (Enhanced Client)

```java
// ====== Entity Definition ======
@DynamoDbBean
public class Order {
    private String pk;         // "ORDER#order123"
    private String sk;         // "METADATA"
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Long version;      // Optimistic locking
    
    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() { return pk; }
    
    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() { return sk; }
    
    @DynamoDbAttribute("GSI1PK")
    public String getGsi1Pk() { return "USER#" + userId; }
    
    @DynamoDbAttribute("GSI1SK")
    public String getGsi1Sk() { return "ORDER#" + createdAt; }
}

// ====== Repository ======
@Repository
public class OrderDynamoRepository {
    private final DynamoDbTable<Order> table;
    
    public OrderDynamoRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("OrdersTable", TableSchema.fromBean(Order.class));
    }
    
    // ====== PUT (Create/Replace) ======
    public void save(Order order) {
        table.putItem(order);
    }
    
    // ====== GET (by primary key) ======
    public Optional<Order> findById(String orderId) {
        Key key = Key.builder()
            .partitionValue("ORDER#" + orderId)
            .sortValue("METADATA")
            .build();
        
        Order order = table.getItem(r -> r.key(key));
        return Optional.ofNullable(order);
    }
    
    // ====== UPDATE (specific attributes) ======
    public void updateStatus(String orderId, String newStatus) {
        table.updateItem(r -> r
            .key(Key.builder()
                .partitionValue("ORDER#" + orderId)
                .sortValue("METADATA")
                .build())
            .ignoreNulls(true)  // Only update non-null fields
        );
    }
    
    // ====== DELETE ======
    public void delete(String orderId) {
        Key key = Key.builder()
            .partitionValue("ORDER#" + orderId)
            .sortValue("METADATA")
            .build();
        table.deleteItem(key);
    }
}
```

---

## 3. Single-Table Design Patterns

```java
// Single table: Orders + OrderItems + OrderEvents all in one table
// PK/SK patterns:
//   ORDER#123 / METADATA        → Order details
//   ORDER#123 / ITEM#sku456     → Order item
//   ORDER#123 / EVENT#timestamp → Order event (state change)
//   USER#alice / ORDER#123      → User's order (GSI)

@Repository
public class SingleTableOrderRepository {
    private final DynamoDbClient client;
    private static final String TABLE = "OrdersTable";
    
    // ====== Get order with all items (single query!) ======
    public OrderWithItems getOrderWithItems(String orderId) {
        QueryResponse response = client.query(QueryRequest.builder()
            .tableName(TABLE)
            .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
            .expressionAttributeValues(Map.of(
                ":pk", AttributeValue.fromS("ORDER#" + orderId),
                ":prefix", AttributeValue.fromS("")  // All SK values
            ))
            .build());
        
        Order order = null;
        List<OrderItem> items = new ArrayList<>();
        List<OrderEvent> events = new ArrayList<>();
        
        for (Map<String, AttributeValue> item : response.items()) {
            String sk = item.get("SK").s();
            if (sk.equals("METADATA")) {
                order = mapToOrder(item);
            } else if (sk.startsWith("ITEM#")) {
                items.add(mapToOrderItem(item));
            } else if (sk.startsWith("EVENT#")) {
                events.add(mapToOrderEvent(item));
            }
        }
        
        return new OrderWithItems(order, items, events);
    }
    
    // ====== Get user's recent orders (using GSI) ======
    public List<Order> getUserOrders(String userId, int limit) {
        QueryResponse response = client.query(QueryRequest.builder()
            .tableName(TABLE)
            .indexName("GSI1")
            .keyConditionExpression("GSI1PK = :pk")
            .expressionAttributeValues(Map.of(
                ":pk", AttributeValue.fromS("USER#" + userId)
            ))
            .scanIndexForward(false)  // Newest first
            .limit(limit)
            .build());
        
        return response.items().stream()
            .map(this::mapToOrder)
            .collect(Collectors.toList());
    }
    
    // ====== Add order item (within the same partition) ======
    public void addOrderItem(String orderId, OrderItem item) {
        Map<String, AttributeValue> dynamoItem = Map.of(
            "PK", AttributeValue.fromS("ORDER#" + orderId),
            "SK", AttributeValue.fromS("ITEM#" + item.getSku()),
            "productName", AttributeValue.fromS(item.getName()),
            "quantity", AttributeValue.fromN(String.valueOf(item.getQuantity())),
            "price", AttributeValue.fromN(item.getPrice().toString())
        );
        
        client.putItem(PutItemRequest.builder()
            .tableName(TABLE)
            .item(dynamoItem)
            .build());
    }
}
```

---

## 4. Query & Scan Operations

```java
@Repository
public class DynamoQueryService {
    private final DynamoDbClient client;
    
    // ====== Query with filter ======
    public List<Order> queryByStatusAndDate(String userId, String status, Instant since) {
        QueryResponse response = client.query(QueryRequest.builder()
            .tableName("OrdersTable")
            .indexName("GSI1")
            .keyConditionExpression("GSI1PK = :pk AND GSI1SK > :since")
            .filterExpression("#s = :status")
            .expressionAttributeNames(Map.of("#s", "status"))
            .expressionAttributeValues(Map.of(
                ":pk", AttributeValue.fromS("USER#" + userId),
                ":since", AttributeValue.fromS("ORDER#" + since.toString()),
                ":status", AttributeValue.fromS(status)
            ))
            .build());
        
        return response.items().stream().map(this::mapToOrder).collect(Collectors.toList());
    }
    
    // ====== Parallel Scan (for batch processing/export) ======
    public void parallelScanAll(int totalSegments, Consumer<List<Order>> batchProcessor) {
        ExecutorService executor = Executors.newFixedThreadPool(totalSegments);
        CountDownLatch latch = new CountDownLatch(totalSegments);
        
        for (int segment = 0; segment < totalSegments; segment++) {
            final int seg = segment;
            executor.submit(() -> {
                try {
                    Map<String, AttributeValue> lastKey = null;
                    do {
                        ScanRequest.Builder request = ScanRequest.builder()
                            .tableName("OrdersTable")
                            .totalSegments(totalSegments)
                            .segment(seg)
                            .limit(100);
                        
                        if (lastKey != null) request.exclusiveStartKey(lastKey);
                        
                        ScanResponse response = client.scan(request.build());
                        List<Order> batch = response.items().stream()
                            .map(this::mapToOrder).collect(Collectors.toList());
                        
                        batchProcessor.accept(batch);
                        lastKey = response.lastEvaluatedKey();
                        
                    } while (lastKey != null && !lastKey.isEmpty());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
    }
}
```

---

## 5. Global Secondary Index (GSI)

```java
// Table design with multiple access patterns via GSIs:
// Base table: PK=OrderId, SK=ItemType
// GSI1: GSI1PK=UserId, GSI1SK=CreatedAt (user's orders by date)
// GSI2: GSI2PK=Status, GSI2SK=CreatedAt (orders by status — for processing)

@Repository
public class GsiQueryRepository {
    
    // ====== GSI2: Find all pending orders (for background processor) ======
    public List<Order> findPendingOrders(Instant olderThan, int limit) {
        QueryResponse response = client.query(QueryRequest.builder()
            .tableName("OrdersTable")
            .indexName("GSI2")
            .keyConditionExpression("GSI2PK = :status AND GSI2SK < :cutoff")
            .expressionAttributeValues(Map.of(
                ":status", AttributeValue.fromS("PENDING"),
                ":cutoff", AttributeValue.fromS(olderThan.toString())
            ))
            .limit(limit)
            .build());
        
        return response.items().stream().map(this::mapToOrder).collect(Collectors.toList());
    }
    
    // ====== Overloaded GSI (multiple entity types in same index) ======
    // GSI1PK = "USER#alice" → all of alice's data
    // GSI1SK = "ORDER#2024-01-01" → orders sorted by date
    // GSI1SK = "PAYMENT#2024-01-01" → payments sorted by date
    // GSI1SK = "PROFILE" → user profile
    
    public UserDashboard getUserDashboard(String userId) {
        QueryResponse response = client.query(QueryRequest.builder()
            .tableName("OrdersTable")
            .indexName("GSI1")
            .keyConditionExpression("GSI1PK = :pk")
            .expressionAttributeValues(Map.of(
                ":pk", AttributeValue.fromS("USER#" + userId)
            ))
            .build());
        
        UserProfile profile = null;
        List<Order> orders = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        
        for (var item : response.items()) {
            String sk = item.get("GSI1SK").s();
            if (sk.equals("PROFILE")) profile = mapToProfile(item);
            else if (sk.startsWith("ORDER#")) orders.add(mapToOrder(item));
            else if (sk.startsWith("PAYMENT#")) payments.add(mapToPayment(item));
        }
        
        return new UserDashboard(profile, orders, payments);
    }
}
```

---

## 6. Conditional Writes (Optimistic Locking)

```java
@Repository
public class ConditionalWriteRepository {
    
    // ====== Update only if version matches (optimistic locking) ======
    public boolean updateOrderStatus(String orderId, String newStatus, long expectedVersion) {
        try {
            client.updateItem(UpdateItemRequest.builder()
                .tableName("OrdersTable")
                .key(Map.of(
                    "PK", AttributeValue.fromS("ORDER#" + orderId),
                    "SK", AttributeValue.fromS("METADATA")
                ))
                .updateExpression("SET #s = :newStatus, version = :newVersion, updatedAt = :now")
                .conditionExpression("version = :expectedVersion")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                    ":newStatus", AttributeValue.fromS(newStatus),
                    ":newVersion", AttributeValue.fromN(String.valueOf(expectedVersion + 1)),
                    ":expectedVersion", AttributeValue.fromN(String.valueOf(expectedVersion)),
                    ":now", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
            return true;
            
        } catch (ConditionalCheckFailedException e) {
            // Another process modified this item — version mismatch
            Metrics.counter("dynamo.conditional.conflict").increment();
            return false;
        }
    }
    
    // ====== Create only if not exists (idempotency) ======
    public boolean createIfNotExists(Order order) {
        try {
            client.putItem(PutItemRequest.builder()
                .tableName("OrdersTable")
                .item(toAttributeMap(order))
                .conditionExpression("attribute_not_exists(PK)")
                .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;  // Already exists
        }
    }
    
    // ====== Decrement stock only if sufficient ======
    public boolean decrementStock(String productId, int quantity) {
        try {
            client.updateItem(UpdateItemRequest.builder()
                .tableName("InventoryTable")
                .key(Map.of("PK", AttributeValue.fromS("PRODUCT#" + productId),
                            "SK", AttributeValue.fromS("STOCK")))
                .updateExpression("SET stock = stock - :qty")
                .conditionExpression("stock >= :qty")
                .expressionAttributeValues(Map.of(
                    ":qty", AttributeValue.fromN(String.valueOf(quantity))
                ))
                .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;  // Insufficient stock
        }
    }
}
```

---

## 7. Transactions (ACID)

```java
@Service
public class DynamoTransactionService {
    private final DynamoDbClient client;
    
    // ====== TransactWriteItems: All or nothing ======
    public void createOrderTransaction(Order order, List<OrderItem> items) {
        List<TransactWriteItem> actions = new ArrayList<>();
        
        // Action 1: Create order
        actions.add(TransactWriteItem.builder()
            .put(Put.builder()
                .tableName("OrdersTable")
                .item(toMap(order))
                .conditionExpression("attribute_not_exists(PK)")  // Ensure unique
                .build())
            .build());
        
        // Action 2-N: Create each order item
        for (OrderItem item : items) {
            actions.add(TransactWriteItem.builder()
                .put(Put.builder()
                    .tableName("OrdersTable")
                    .item(toItemMap(order.getId(), item))
                    .build())
                .build());
        }
        
        // Action N+1: Decrement inventory for each item
        for (OrderItem item : items) {
            actions.add(TransactWriteItem.builder()
                .update(Update.builder()
                    .tableName("InventoryTable")
                    .key(Map.of("PK", AttributeValue.fromS("PRODUCT#" + item.getSku()),
                                "SK", AttributeValue.fromS("STOCK")))
                    .updateExpression("SET stock = stock - :qty")
                    .conditionExpression("stock >= :qty")
                    .expressionAttributeValues(Map.of(
                        ":qty", AttributeValue.fromN(String.valueOf(item.getQuantity()))
                    ))
                    .build())
                .build());
        }
        
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build());
            log.info("Order transaction committed: {}", order.getId());
            
        } catch (TransactionCanceledException e) {
            // Identify which condition failed
            List<CancellationReason> reasons = e.cancellationReasons();
            for (int i = 0; i < reasons.size(); i++) {
                if (reasons.get(i).code().equals("ConditionalCheckFailed")) {
                    log.error("Transaction condition failed at action {}: {}", 
                        i, reasons.get(i).message());
                }
            }
            throw new OrderCreationException("Transaction failed", e);
        }
    }
    
    // ====== TransactGetItems: Consistent read across items ======
    public OrderWithInventory getOrderAndInventory(String orderId, String productId) {
        TransactGetItemsResponse response = client.transactGetItems(
            TransactGetItemsRequest.builder()
                .transactItems(List.of(
                    TransactGetItem.builder().get(Get.builder()
                        .tableName("OrdersTable")
                        .key(Map.of("PK", AttributeValue.fromS("ORDER#" + orderId),
                                    "SK", AttributeValue.fromS("METADATA")))
                        .build()).build(),
                    TransactGetItem.builder().get(Get.builder()
                        .tableName("InventoryTable")
                        .key(Map.of("PK", AttributeValue.fromS("PRODUCT#" + productId),
                                    "SK", AttributeValue.fromS("STOCK")))
                        .build()).build()
                ))
                .build());
        
        Order order = mapToOrder(response.responses().get(0).item());
        int stock = Integer.parseInt(response.responses().get(1).item().get("stock").n());
        
        return new OrderWithInventory(order, stock);
    }
}
```

---

## 8. Batch Operations

```java
@Repository
public class DynamoBatchRepository {
    
    // ====== BatchWriteItem (up to 25 items per call) ======
    public void batchWrite(List<Order> orders) {
        // Split into chunks of 25 (DynamoDB limit)
        List<List<Order>> chunks = Lists.partition(orders, 25);
        
        for (List<Order> chunk : chunks) {
            List<WriteRequest> writeRequests = chunk.stream()
                .map(order -> WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(toMap(order)).build())
                    .build())
                .collect(Collectors.toList());
            
            BatchWriteItemResponse response = client.batchWriteItem(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("OrdersTable", writeRequests))
                    .build());
            
            // Handle unprocessed items (retry with backoff)
            Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();
            if (!unprocessed.isEmpty()) {
                retryUnprocessed(unprocessed);
            }
        }
    }
    
    // ====== BatchGetItem (up to 100 keys per call) ======
    public List<Order> batchGet(List<String> orderIds) {
        List<List<String>> chunks = Lists.partition(orderIds, 100);
        List<Order> results = new ArrayList<>();
        
        for (List<String> chunk : chunks) {
            List<Map<String, AttributeValue>> keys = chunk.stream()
                .map(id -> Map.of(
                    "PK", AttributeValue.fromS("ORDER#" + id),
                    "SK", AttributeValue.fromS("METADATA")
                ))
                .collect(Collectors.toList());
            
            BatchGetItemResponse response = client.batchGetItem(
                BatchGetItemRequest.builder()
                    .requestItems(Map.of("OrdersTable", 
                        KeysAndAttributes.builder().keys(keys).build()))
                    .build());
            
            response.responses().get("OrdersTable").stream()
                .map(this::mapToOrder)
                .forEach(results::add);
        }
        
        return results;
    }
    
    private void retryUnprocessed(Map<String, List<WriteRequest>> unprocessed) {
        int retries = 0;
        while (!unprocessed.isEmpty() && retries < 5) {
            try { Thread.sleep((long) Math.pow(2, retries) * 100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            
            BatchWriteItemResponse response = client.batchWriteItem(
                BatchWriteItemRequest.builder().requestItems(unprocessed).build());
            unprocessed = response.unprocessedItems();
            retries++;
        }
        
        if (!unprocessed.isEmpty()) {
            log.error("Failed to write {} items after retries", 
                unprocessed.values().stream().mapToInt(List::size).sum());
        }
    }
}
```

---

## 9. TTL (Auto-Expiry)

```java
@Repository
public class DynamoTTLRepository {
    
    // ====== Store with TTL (auto-deleted by DynamoDB after expiry) ======
    public void storeSession(String sessionId, String userId, Duration ttl) {
        long expiryEpoch = Instant.now().plus(ttl).getEpochSecond();
        
        client.putItem(PutItemRequest.builder()
            .tableName("SessionsTable")
            .item(Map.of(
                "PK", AttributeValue.fromS("SESSION#" + sessionId),
                "SK", AttributeValue.fromS("DATA"),
                "userId", AttributeValue.fromS(userId),
                "createdAt", AttributeValue.fromS(Instant.now().toString()),
                "ttl", AttributeValue.fromN(String.valueOf(expiryEpoch))  // TTL attribute
            ))
            .build());
    }
    
    // ====== Idempotency key with TTL ======
    public boolean markIdempotencyKey(String key, Duration ttl) {
        long expiryEpoch = Instant.now().plus(ttl).getEpochSecond();
        
        try {
            client.putItem(PutItemRequest.builder()
                .tableName("IdempotencyTable")
                .item(Map.of(
                    "PK", AttributeValue.fromS(key),
                    "SK", AttributeValue.fromS("LOCK"),
                    "ttl", AttributeValue.fromN(String.valueOf(expiryEpoch))
                ))
                .conditionExpression("attribute_not_exists(PK)")
                .build());
            return true;  // Successfully marked
        } catch (ConditionalCheckFailedException e) {
            return false;  // Already exists (duplicate request)
        }
    }
}
```

---

## 10. DynamoDB Streams (CDC)

```java
// ====== Lambda handler for DynamoDB Streams ======
public class OrderStreamHandler implements RequestHandler<DynamodbEvent, Void> {
    
    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            String eventName = record.getEventName();  // INSERT, MODIFY, REMOVE
            
            Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
            Map<String, AttributeValue> oldImage = record.getDynamodb().getOldImage();
            
            switch (eventName) {
                case "INSERT":
                    handleNewOrder(newImage);
                    break;
                case "MODIFY":
                    handleOrderUpdate(oldImage, newImage);
                    break;
                case "REMOVE":
                    handleOrderDeletion(oldImage);
                    break;
            }
        }
        return null;
    }
    
    private void handleOrderUpdate(Map<String, AttributeValue> oldImage, 
                                    Map<String, AttributeValue> newImage) {
        String oldStatus = oldImage.get("status").s();
        String newStatus = newImage.get("status").s();
        
        if (!oldStatus.equals(newStatus)) {
            // Status changed — trigger downstream processing
            String orderId = newImage.get("PK").s().replace("ORDER#", "");
            notificationService.notifyStatusChange(orderId, oldStatus, newStatus);
            analyticsService.recordStatusTransition(orderId, oldStatus, newStatus);
        }
    }
}
```

---

## 11. Pagination (LastEvaluatedKey)

```java
@Repository
public class PaginatedDynamoRepository {
    
    // ====== Cursor-based pagination ======
    public PageResult<Order> getOrdersPage(String userId, String cursor, int pageSize) {
        QueryRequest.Builder request = QueryRequest.builder()
            .tableName("OrdersTable")
            .indexName("GSI1")
            .keyConditionExpression("GSI1PK = :pk")
            .expressionAttributeValues(Map.of(
                ":pk", AttributeValue.fromS("USER#" + userId)
            ))
            .scanIndexForward(false)
            .limit(pageSize);
        
        // If cursor provided, resume from there
        if (cursor != null) {
            Map<String, AttributeValue> startKey = decodeCursor(cursor);
            request.exclusiveStartKey(startKey);
        }
        
        QueryResponse response = client.query(request.build());
        
        List<Order> orders = response.items().stream()
            .map(this::mapToOrder)
            .collect(Collectors.toList());
        
        // Encode next cursor (LastEvaluatedKey)
        String nextCursor = response.hasLastEvaluatedKey() 
            ? encodeCursor(response.lastEvaluatedKey()) 
            : null;
        
        return new PageResult<>(orders, nextCursor, response.hasLastEvaluatedKey());
    }
    
    private String encodeCursor(Map<String, AttributeValue> lastKey) {
        // Base64 encode the key for safe URL passing
        return Base64.getUrlEncoder().encodeToString(
            mapper.writeValueAsBytes(lastKey));
    }
    
    private Map<String, AttributeValue> decodeCursor(String cursor) {
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        return mapper.readValue(decoded, new TypeReference<>() {});
    }
}
```

---

## 12. Atomic Counters

```java
@Repository
public class DynamoAtomicCounter {
    
    // ====== Increment counter atomically ======
    public long incrementViewCount(String videoId) {
        UpdateItemResponse response = client.updateItem(UpdateItemRequest.builder()
            .tableName("CountersTable")
            .key(Map.of(
                "PK", AttributeValue.fromS("VIDEO#" + videoId),
                "SK", AttributeValue.fromS("VIEWS")
            ))
            .updateExpression("ADD viewCount :inc")
            .expressionAttributeValues(Map.of(
                ":inc", AttributeValue.fromN("1")
            ))
            .returnValues(ReturnValue.UPDATED_NEW)
            .build());
        
        return Long.parseLong(response.attributes().get("viewCount").n());
    }
    
    // ====== Conditional increment (e.g., only increment if user hasn't already) ======
    public boolean incrementUniqueView(String videoId, String userId) {
        try {
            // First: record unique viewer
            client.putItem(PutItemRequest.builder()
                .tableName("CountersTable")
                .item(Map.of(
                    "PK", AttributeValue.fromS("VIDEO#" + videoId + "#VIEWERS"),
                    "SK", AttributeValue.fromS("USER#" + userId),
                    "ttl", AttributeValue.fromN(String.valueOf(
                        Instant.now().plus(Duration.ofHours(24)).getEpochSecond()))
                ))
                .conditionExpression("attribute_not_exists(PK)")
                .build());
            
            // Second: increment counter (only if above succeeded)
            incrementViewCount(videoId);
            return true;
            
        } catch (ConditionalCheckFailedException e) {
            return false;  // User already viewed
        }
    }
}
```

---

## 13-20: Additional Patterns (Key Code)

### 14. Write Sharding (Hot Partition)
```java
// Problem: "POPULAR_ITEM#xyz" gets too many writes (hot partition)
// Solution: Shard the key across N shards

public void incrementPopularCounter(String itemId) {
    int shard = ThreadLocalRandom.current().nextInt(10);  // 10 shards
    String shardedKey = "ITEM#" + itemId + "#SHARD#" + shard;
    
    client.updateItem(UpdateItemRequest.builder()
        .tableName("CountersTable")
        .key(Map.of("PK", AttributeValue.fromS(shardedKey), 
                    "SK", AttributeValue.fromS("COUNT")))
        .updateExpression("ADD cnt :inc")
        .expressionAttributeValues(Map.of(":inc", AttributeValue.fromN("1")))
        .build());
}

public long getTotalCount(String itemId) {
    long total = 0;
    for (int shard = 0; shard < 10; shard++) {
        // Query each shard and sum
        GetItemResponse r = client.getItem(GetItemRequest.builder()
            .tableName("CountersTable")
            .key(Map.of("PK", AttributeValue.fromS("ITEM#" + itemId + "#SHARD#" + shard),
                        "SK", AttributeValue.fromS("COUNT")))
            .build());
        if (r.hasItem()) total += Long.parseLong(r.item().get("cnt").n());
    }
    return total;
}
```

### 16. Retry & Error Handling
```java
public <T> T executeWithRetry(Supplier<T> operation, int maxRetries) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return operation.get();
        } catch (ProvisionedThroughputExceededException | RequestLimitExceededException e) {
            if (attempt == maxRetries) throw e;
            long backoff = (long) Math.pow(2, attempt) * 100 + ThreadLocalRandom.current().nextLong(50);
            try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            Metrics.counter("dynamo.throttle.retry").increment();
        }
    }
    throw new RuntimeException("Unreachable");
}
```

---

## 🏆 Access Pattern Cheat Sheet

| Access Pattern | PK | SK | Index |
|---|---|---|---|
| Get order by ID | `ORDER#123` | `METADATA` | Base table |
| Get order items | `ORDER#123` | `begins_with(ITEM#)` | Base table |
| User's orders by date | `USER#alice` | `ORDER#2024-01-01` | GSI1 |
| Orders by status | `STATUS#PENDING` | `2024-01-01T10:00` | GSI2 |
| Product inventory | `PRODUCT#sku` | `STOCK` | Base table |
| Session by ID | `SESSION#abc` | `DATA` | Base table + TTL |

| Operation | Max Items | Notes |
|---|---|---|
| BatchWriteItem | 25 per call | Auto-retry unprocessed |
| BatchGetItem | 100 keys per call | Eventual consistency default |
| TransactWriteItems | 100 items | ACID across tables |
| Query | 1MB per response | Use pagination |
| Scan | Full table | Parallel scan for large tables |

---

*All examples use AWS SDK v2 for Java. Enhanced Client annotations simplify mapping. Use DAX for sub-millisecond reads on hot data.*
