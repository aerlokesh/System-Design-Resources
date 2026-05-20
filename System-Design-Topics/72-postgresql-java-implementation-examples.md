# 🐘 Topic 72: PostgreSQL Java Implementation Examples — Production Patterns

> Complete Java implementation guide for PostgreSQL using **JDBC**, **HikariCP**, **Spring Data JPA**, and **jOOQ**. Every pattern includes production-ready code with connection pooling, transactions, batch operations, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Connection Pool (HikariCP)](#1-connection-pool-hikaricp)
- [2. Basic CRUD with JDBC](#2-basic-crud-with-jdbc)
- [3. Spring Data JPA Repository](#3-spring-data-jpa-repository)
- [4. Transactions & Isolation Levels](#4-transactions--isolation-levels)
- [5. Batch Insert (10K+ rows)](#5-batch-insert-10k-rows)
- [6. Pagination (Cursor & Offset)](#6-pagination-cursor--offset)
- [7. Optimistic Locking (Version Column)](#7-optimistic-locking-version-column)
- [8. Pessimistic Locking (SELECT FOR UPDATE)](#8-pessimistic-locking-select-for-update)
- [9. JSONB Queries](#9-jsonb-queries)
- [10. Full-Text Search](#10-full-text-search)
- [11. Advisory Locks (Distributed)](#11-advisory-locks-distributed)
- [12. LISTEN/NOTIFY (Real-Time)](#12-listennotify-real-time)
- [13. Upsert (ON CONFLICT)](#13-upsert-on-conflict)
- [14. CTE & Window Functions](#14-cte--window-functions)
- [15. Connection per Request Pattern](#15-connection-per-request-pattern)
- [16. Read Replica Routing](#16-read-replica-routing)
- [17. Database Migration (Flyway)](#17-database-migration-flyway)
- [18. Monitoring & Metrics](#18-monitoring--metrics)
- [19. Row-Level Security](#19-row-level-security)
- [20. Partitioned Tables](#20-partitioned-tables)
- [🏆 Configuration Cheat Sheet](#-configuration-cheat-sheet)

---

## 1. Connection Pool (HikariCP)

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Connection
        config.setJdbcUrl("jdbc:postgresql://pg-primary.prod:5432/orders_db");
        config.setUsername("app_user");
        config.setPassword("${DB_PASSWORD}");
        config.setDriverClassName("org.postgresql.Driver");
        
        // Pool sizing (Formula: connections = cores × 2 + effective_spindle_count)
        // For 8-core server with SSD: 8 × 2 + 1 = 17 (round to 20)
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        
        // Timeouts
        config.setConnectionTimeout(5000);      // 5s max wait for connection
        config.setIdleTimeout(300000);           // 5 min idle before eviction
        config.setMaxLifetime(1800000);          // 30 min max connection lifetime
        config.setValidationTimeout(3000);       // 3s for validation query
        
        // Performance
        config.setAutoCommit(false);             // Explicit transactions
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        // PostgreSQL specific
        config.addDataSourceProperty("reWriteBatchedInserts", "true");  // Batch optimization
        config.addDataSourceProperty("ApplicationName", "order-service");
        
        // Monitoring
        config.setPoolName("orders-db-pool");
        config.setMetricRegistry(meterRegistry);  // Micrometer integration
        
        // Leak detection
        config.setLeakDetectionThreshold(60000);  // Alert if connection held > 60s
        
        return new HikariDataSource(config);
    }
}
```

---

## 2. Basic CRUD with JDBC

```java
@Repository
public class OrderJdbcRepository {
    private final JdbcTemplate jdbc;
    
    // ====== INSERT ======
    public String createOrder(Order order) {
        String sql = """
            INSERT INTO orders (id, user_id, status, total_amount, created_at)
            VALUES (?, ?, ?::order_status, ?, NOW())
            RETURNING id
            """;
        
        return jdbc.queryForObject(sql, String.class,
            order.getId(), order.getUserId(), order.getStatus().name(), order.getTotalAmount());
    }
    
    // ====== SELECT by ID ======
    public Optional<Order> findById(String orderId) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        
        try {
            Order order = jdbc.queryForObject(sql, orderRowMapper(), orderId);
            return Optional.ofNullable(order);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    // ====== UPDATE ======
    public int updateStatus(String orderId, OrderStatus newStatus) {
        String sql = "UPDATE orders SET status = ?::order_status, updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, newStatus.name(), orderId);
    }
    
    // ====== DELETE (soft delete) ======
    public int softDelete(String orderId) {
        String sql = "UPDATE orders SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL";
        return jdbc.update(sql, orderId);
    }
    
    // ====== QUERY with multiple conditions ======
    public List<Order> findByUserAndStatus(String userId, OrderStatus status, 
                                            int limit, int offset) {
        String sql = """
            SELECT * FROM orders 
            WHERE user_id = ? AND status = ?::order_status AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        return jdbc.query(sql, orderRowMapper(), userId, status.name(), limit, offset);
    }
    
    private RowMapper<Order> orderRowMapper() {
        return (rs, rowNum) -> new Order(
            rs.getString("id"),
            rs.getString("user_id"),
            OrderStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("total_amount"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
        );
    }
}
```

---

## 3. Spring Data JPA Repository

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user_id", columnList = "userId"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_created", columnList = "createdAt DESC")
})
public class OrderEntity {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;
    
    @Version  // Optimistic locking
    private Long version;
    
    @CreationTimestamp
    private Instant createdAt;
    
    @UpdateTimestamp
    private Instant updatedAt;
    
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private Map<String, Object> metadata;
}

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    
    // Derived queries
    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatus status);
    
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.createdAt > :since")
    Page<OrderEntity> findRecentByUser(@Param("userId") String userId, 
                                        @Param("since") Instant since, Pageable pageable);
    
    // Native query for complex operations
    @Query(value = """
        SELECT o.* FROM orders o
        WHERE o.status = 'PENDING' 
        AND o.created_at < NOW() - INTERVAL '30 minutes'
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """, nativeQuery = true)
    List<OrderEntity> findStaleOrdersForProcessing(@Param("limit") int limit);
    
    // Modifying queries
    @Modifying
    @Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id AND o.version = :version")
    int updateStatusOptimistic(@Param("id") String id, @Param("status") OrderStatus status, 
                               @Param("version") Long version);
    
    // Aggregation
    @Query("SELECT o.status, COUNT(o), SUM(o.totalAmount) FROM OrderEntity o " +
           "WHERE o.createdAt > :since GROUP BY o.status")
    List<Object[]> getOrderStats(@Param("since") Instant since);
}
```

---

## 4. Transactions & Isolation Levels

```java
@Service
public class OrderTransactionService {
    
    // ====== Default: READ COMMITTED (Postgres default) ======
    @Transactional
    public Order createOrderWithItems(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        for (ItemRequest item : request.getItems()) {
            orderItemRepository.save(new OrderItem(order.getId(), item));
        }
        
        // If any save fails, entire transaction rolls back
        paymentService.reserveAmount(order.getUserId(), order.getTotal());
        
        return order;
    }
    
    // ====== SERIALIZABLE: For financial operations ======
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferResult transferFunds(String fromAccount, String toAccount, BigDecimal amount) {
        Account from = accountRepository.findById(fromAccount).orElseThrow();
        Account to = accountRepository.findById(toAccount).orElseThrow();
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        
        accountRepository.save(from);
        accountRepository.save(to);
        
        // Under SERIALIZABLE: if concurrent transaction modifies same accounts,
        // one will be rolled back with serialization failure → retry
        return TransferResult.success();
    }
    
    // ====== REPEATABLE READ: Consistent reporting ======
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public ReportData generateDailyReport(LocalDate date) {
        // All queries in this transaction see the same snapshot
        // Even if data is modified by other transactions during report generation
        long orderCount = orderRepository.countByDate(date);
        BigDecimal revenue = orderRepository.sumRevenueByDate(date);
        Map<String, Long> statusBreakdown = orderRepository.countByStatusAndDate(date);
        
        return new ReportData(date, orderCount, revenue, statusBreakdown);
    }
    
    // ====== Manual transaction with savepoints ======
    @Autowired private PlatformTransactionManager txManager;
    
    public OrderResult processOrderWithSavepoints(Order order) {
        TransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus tx = txManager.getTransaction(def);
        
        try {
            orderRepository.save(order);
            
            // Savepoint: can roll back just inventory reservation
            Object savepoint = tx.createSavepoint();
            try {
                inventoryService.reserve(order);
            } catch (InventoryException e) {
                tx.rollbackToSavepoint(savepoint);
                order.setStatus(OrderStatus.BACKORDERED);
                orderRepository.save(order);
            }
            
            txManager.commit(tx);
            return OrderResult.success(order);
            
        } catch (Exception e) {
            txManager.rollback(tx);
            throw e;
        }
    }
}
```

---

## 5. Batch Insert (10K+ rows)

```java
@Repository
public class BatchInsertRepository {
    private final JdbcTemplate jdbc;
    
    // ====== JDBC Batch Insert (fastest for large datasets) ======
    public int[] batchInsertEvents(List<Event> events) {
        String sql = """
            INSERT INTO events (id, type, payload, user_id, created_at)
            VALUES (?, ?, ?::jsonb, ?, ?)
            """;
        
        return jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Event event = events.get(i);
                ps.setString(1, event.getId());
                ps.setString(2, event.getType());
                ps.setObject(3, event.getPayload(), Types.OTHER);
                ps.setString(4, event.getUserId());
                ps.setTimestamp(5, Timestamp.from(event.getCreatedAt()));
            }
            
            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
    }
    
    // ====== Chunked batch (for very large datasets — avoids memory issues) ======
    public void batchInsertChunked(List<Event> events, int chunkSize) {
        List<List<Event>> chunks = Lists.partition(events, chunkSize);
        
        for (List<Event> chunk : chunks) {
            batchInsertEvents(chunk);
        }
        
        log.info("Inserted {} events in {} chunks", events.size(), chunks.size());
    }
    
    // ====== COPY command (fastest possible — bulk loading) ======
    public long copyInsert(List<Event> events) throws SQLException {
        DataSource ds = jdbc.getDataSource();
        try (Connection conn = ds.getConnection()) {
            PGConnection pgConn = conn.unwrap(PGConnection.class);
            
            String copySql = "COPY events (id, type, payload, user_id, created_at) FROM STDIN WITH (FORMAT csv)";
            CopyManager copyManager = pgConn.getCopyAPI();
            
            StringBuilder csv = new StringBuilder();
            for (Event event : events) {
                csv.append(String.format("%s,%s,\"%s\",%s,%s\n",
                    event.getId(), event.getType(), 
                    event.getPayload().replace("\"", "\"\""),
                    event.getUserId(), event.getCreatedAt()));
            }
            
            return copyManager.copyIn(copySql, new StringReader(csv.toString()));
        }
    }
}
```

---

## 6. Pagination (Cursor & Offset)

```java
@Repository
public class PaginatedQueryRepository {
    
    // ====== OFFSET pagination (simple but slow for deep pages) ======
    public Page<Order> findOrdersOffset(String userId, int page, int size) {
        String countSql = "SELECT COUNT(*) FROM orders WHERE user_id = ?";
        long total = jdbc.queryForObject(countSql, Long.class, userId);
        
        String sql = """
            SELECT * FROM orders WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        List<Order> orders = jdbc.query(sql, orderRowMapper(), userId, size, page * size);
        
        return new PageImpl<>(orders, PageRequest.of(page, size), total);
    }
    
    // ====== CURSOR pagination (fast for any depth — production preferred) ======
    public CursorPage<Order> findOrdersCursor(String userId, String cursor, int size) {
        Instant cursorTime = cursor != null ? Instant.parse(cursor) : Instant.now();
        
        String sql = """
            SELECT * FROM orders 
            WHERE user_id = ? AND created_at < ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        
        List<Order> orders = jdbc.query(sql, orderRowMapper(), 
            userId, Timestamp.from(cursorTime), size + 1);  // Fetch one extra
        
        boolean hasMore = orders.size() > size;
        if (hasMore) orders = orders.subList(0, size);
        
        String nextCursor = hasMore ? orders.get(orders.size() - 1).getCreatedAt().toString() : null;
        
        return new CursorPage<>(orders, nextCursor, hasMore);
    }
    
    // ====== KEYSET pagination (for sorted unique columns) ======
    public List<Order> findOrdersKeyset(String userId, String lastOrderId, 
                                         Instant lastCreatedAt, int size) {
        if (lastOrderId == null) {
            String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?";
            return jdbc.query(sql, orderRowMapper(), userId, size);
        }
        
        String sql = """
            SELECT * FROM orders 
            WHERE user_id = ? AND (created_at, id) < (?, ?)
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """;
        return jdbc.query(sql, orderRowMapper(), userId, 
            Timestamp.from(lastCreatedAt), lastOrderId, size);
    }
}
```

---

## 7. Optimistic Locking (Version Column)

```java
@Service
public class OptimisticLockingService {
    
    // ====== JPA @Version automatic ======
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        OrderEntity order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        order.setStatus(newStatus);
        
        try {
            return orderRepository.save(order);  // @Version auto-increments
        } catch (OptimisticLockException e) {
            // Another transaction modified this order concurrently
            throw new ConcurrentModificationException(
                "Order " + orderId + " was modified by another process. Please retry.");
        }
    }
    
    // ====== Manual optimistic locking with retry ======
    @Retryable(value = ConcurrentModificationException.class, maxAttempts = 3)
    @Transactional
    public Order updateWithRetry(String orderId, Function<Order, Order> modifier) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        OrderEntity modified = modifier.apply(order);
        
        int updated = orderRepository.updateStatusOptimistic(
            orderId, modified.getStatus(), order.getVersion());
        
        if (updated == 0) {
            throw new ConcurrentModificationException("Concurrent modification on order: " + orderId);
        }
        
        return modified;
    }
}
```

---

## 8. Pessimistic Locking (SELECT FOR UPDATE)

```java
@Service
public class PessimisticLockingService {
    
    // ====== SELECT FOR UPDATE — block until lock acquired ======
    @Transactional
    public void processNextPendingOrder() {
        // SKIP LOCKED: skip rows already locked by other transactions
        String sql = """
            SELECT * FROM orders 
            WHERE status = 'PENDING' AND created_at < NOW() - INTERVAL '5 minutes'
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """;
        
        List<Order> orders = jdbc.query(sql, orderRowMapper());
        if (orders.isEmpty()) return;
        
        Order order = orders.get(0);
        // We now hold an exclusive lock on this row until transaction ends
        processOrder(order);
        updateStatus(order.getId(), OrderStatus.PROCESSING);
    }
    
    // ====== FOR UPDATE NOWAIT — fail immediately if locked ======
    @Transactional
    public Order lockOrderOrFail(String orderId) {
        String sql = "SELECT * FROM orders WHERE id = ? FOR UPDATE NOWAIT";
        
        try {
            return jdbc.queryForObject(sql, orderRowMapper(), orderId);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof PSQLException && 
                ((PSQLException)e.getCause()).getSQLState().equals("55P03")) {
                throw new OrderLockedException("Order " + orderId + " is being processed");
            }
            throw e;
        }
    }
    
    // ====== JPA @Lock annotation ======
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") String id);
}
```

---

## 9. JSONB Queries

```java
@Repository
public class JsonbQueryRepository {
    private final JdbcTemplate jdbc;
    
    // ====== Store JSONB ======
    public void saveWithMetadata(String orderId, Map<String, Object> metadata) {
        String sql = "UPDATE orders SET metadata = ?::jsonb WHERE id = ?";
        jdbc.update(sql, new ObjectMapper().writeValueAsString(metadata), orderId);
    }
    
    // ====== Query by JSONB field ======
    public List<Order> findByMetadataField(String key, String value) {
        String sql = "SELECT * FROM orders WHERE metadata->>? = ?";
        return jdbc.query(sql, orderRowMapper(), key, value);
    }
    
    // ====== Query nested JSONB ======
    public List<Order> findByNestedField(String value) {
        String sql = "SELECT * FROM orders WHERE metadata->'shipping'->>'method' = ?";
        return jdbc.query(sql, orderRowMapper(), value);
    }
    
    // ====== JSONB contains (@>) ======
    public List<Order> findByMetadataContains(Map<String, Object> criteria) {
        String sql = "SELECT * FROM orders WHERE metadata @> ?::jsonb";
        return jdbc.query(sql, orderRowMapper(), new ObjectMapper().writeValueAsString(criteria));
    }
    
    // ====== Update specific JSONB field ======
    public void updateMetadataField(String orderId, String key, String value) {
        String sql = "UPDATE orders SET metadata = jsonb_set(metadata, ?, ?::jsonb) WHERE id = ?";
        jdbc.update(sql, "{" + key + "}", "\"" + value + "\"", orderId);
    }
    
    // ====== Aggregate JSONB arrays ======
    public List<String> findDistinctTags() {
        String sql = """
            SELECT DISTINCT jsonb_array_elements_text(metadata->'tags') as tag
            FROM orders
            WHERE metadata ? 'tags'
            ORDER BY tag
            """;
        return jdbc.queryForList(sql, String.class);
    }
}
```

---

## 10. Full-Text Search

```java
@Repository
public class FullTextSearchRepository {
    
    // ====== Search products with ranking ======
    public List<Product> searchProducts(String query) {
        String sql = """
            SELECT *, ts_rank(search_vector, plainto_tsquery('english', ?)) as rank
            FROM products
            WHERE search_vector @@ plainto_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT 20
            """;
        return jdbc.query(sql, productRowMapper(), query, query);
    }
    
    // ====== Search with highlights ======
    public List<SearchResult> searchWithHighlights(String query) {
        String sql = """
            SELECT id, name,
                ts_headline('english', description, plainto_tsquery('english', ?),
                    'StartSel=<b>, StopSel=</b>, MaxWords=50') as highlighted,
                ts_rank(search_vector, plainto_tsquery('english', ?)) as rank
            FROM products
            WHERE search_vector @@ plainto_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT 20
            """;
        return jdbc.query(sql, searchResultMapper(), query, query, query);
    }
    
    // ====== Prefix search (autocomplete) ======
    public List<String> autocomplete(String prefix) {
        String sql = """
            SELECT name FROM products
            WHERE search_vector @@ to_tsquery('english', ? || ':*')
            ORDER BY popularity DESC
            LIMIT 10
            """;
        return jdbc.queryForList(sql, String.class, prefix);
    }
}
```

---

## 11. Advisory Locks (Distributed)

```java
@Service
public class PostgresAdvisoryLock {
    private final JdbcTemplate jdbc;
    
    // ====== Session-level advisory lock (held until session ends or explicit unlock) ======
    public boolean tryAcquireLock(long lockId) {
        Boolean acquired = jdbc.queryForObject(
            "SELECT pg_try_advisory_lock(?)", Boolean.class, lockId);
        return Boolean.TRUE.equals(acquired);
    }
    
    public void releaseLock(long lockId) {
        jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, lockId);
    }
    
    // ====== Transaction-level advisory lock (auto-released on commit/rollback) ======
    @Transactional
    public void processWithLock(String resourceId, Runnable action) {
        long lockId = resourceId.hashCode();  // Convert string to long
        
        // Blocks until lock acquired (or use pg_try_advisory_xact_lock for non-blocking)
        jdbc.execute("SELECT pg_advisory_xact_lock(" + lockId + ")");
        
        action.run();
        // Lock automatically released when transaction commits
    }
    
    // ====== Distributed cron job (only one instance runs) ======
    @Scheduled(cron = "0 0 * * * *")  // Every hour
    @Transactional
    public void hourlyJob() {
        long lockId = "hourly-cleanup-job".hashCode();
        
        Boolean acquired = jdbc.queryForObject(
            "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lockId);
        
        if (Boolean.TRUE.equals(acquired)) {
            log.info("Acquired lock for hourly job. Running...");
            performCleanup();
        } else {
            log.info("Another instance is running hourly job. Skipping.");
        }
    }
}
```

---

## 12. LISTEN/NOTIFY (Real-Time)

```java
@Service
public class PostgresNotifyService {
    private final DataSource dataSource;
    
    // ====== NOTIFY: Publish event ======
    @Transactional
    public void notifyOrderUpdate(String orderId, String status) {
        String payload = orderId + ":" + status;
        jdbc.execute("NOTIFY order_updates, '" + payload + "'");
    }
    
    // ====== LISTEN: Subscribe to channel ======
    @PostConstruct
    public void startListening() {
        Thread.startVirtualThread(() -> {
            try (Connection conn = dataSource.getConnection()) {
                PGConnection pgConn = conn.unwrap(PGConnection.class);
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN order_updates");
                }
                
                while (true) {
                    // Check for notifications (non-blocking with timeout)
                    PGNotification[] notifications = pgConn.getNotifications(5000);
                    
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            String[] parts = notification.getParameter().split(":");
                            handleOrderUpdate(parts[0], parts[1]);
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("LISTEN connection failed", e);
            }
        });
    }
}
```

---

## 13. Upsert (ON CONFLICT)

```java
@Repository
public class UpsertRepository {
    
    // ====== Insert or update on conflict ======
    public void upsertUserProfile(UserProfile profile) {
        String sql = """
            INSERT INTO user_profiles (user_id, name, email, preferences, updated_at)
            VALUES (?, ?, ?, ?::jsonb, NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                preferences = user_profiles.preferences || EXCLUDED.preferences,
                updated_at = NOW()
            """;
        
        jdbc.update(sql, profile.getUserId(), profile.getName(), 
            profile.getEmail(), mapper.writeValueAsString(profile.getPreferences()));
    }
    
    // ====== Insert or ignore ======
    public void insertIfNotExists(String key, String value) {
        String sql = "INSERT INTO kv_store (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING";
        jdbc.update(sql, key, value);
    }
    
    // ====== Bulk upsert ======
    public void bulkUpsertMetrics(List<Metric> metrics) {
        String sql = """
            INSERT INTO metrics (name, value, timestamp)
            VALUES (?, ?, ?)
            ON CONFLICT (name, timestamp) DO UPDATE SET
                value = metrics.value + EXCLUDED.value
            """;
        
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Metric m = metrics.get(i);
                ps.setString(1, m.getName());
                ps.setDouble(2, m.getValue());
                ps.setTimestamp(3, Timestamp.from(m.getTimestamp()));
            }
            @Override
            public int getBatchSize() { return metrics.size(); }
        });
    }
}
```

---

## 14. CTE & Window Functions

```java
@Repository
public class AdvancedQueryRepository {
    
    // ====== CTE: Find user's order history with running total ======
    public List<OrderWithRunningTotal> getOrdersWithRunningTotal(String userId) {
        String sql = """
            WITH user_orders AS (
                SELECT id, total_amount, created_at,
                    SUM(total_amount) OVER (ORDER BY created_at) as running_total,
                    ROW_NUMBER() OVER (ORDER BY created_at DESC) as order_number
                FROM orders
                WHERE user_id = ? AND deleted_at IS NULL
            )
            SELECT * FROM user_orders
            ORDER BY created_at DESC
            LIMIT 50
            """;
        return jdbc.query(sql, orderWithTotalMapper(), userId);
    }
    
    // ====== Window: Rank products by sales ======
    public List<ProductRanking> getProductRankings(String category) {
        String sql = """
            SELECT product_id, name, sales_count,
                RANK() OVER (ORDER BY sales_count DESC) as rank,
                PERCENT_RANK() OVER (ORDER BY sales_count DESC) as percentile
            FROM products
            WHERE category = ?
            """;
        return jdbc.query(sql, rankingMapper(), category);
    }
    
    // ====== Recursive CTE: Organization hierarchy ======
    public List<OrgNode> getOrgTree(String rootManagerId) {
        String sql = """
            WITH RECURSIVE org_tree AS (
                SELECT id, name, manager_id, 1 as depth
                FROM employees WHERE id = ?
                
                UNION ALL
                
                SELECT e.id, e.name, e.manager_id, ot.depth + 1
                FROM employees e
                JOIN org_tree ot ON e.manager_id = ot.id
                WHERE ot.depth < 10  -- Safety: max depth
            )
            SELECT * FROM org_tree ORDER BY depth, name
            """;
        return jdbc.query(sql, orgNodeMapper(), rootManagerId);
    }
}
```

---

## 15-20: Additional Patterns (Key Code)

### 16. Read Replica Routing
```java
@Configuration
public class ReadReplicaRoutingConfig {
    
    @Bean
    public DataSource routingDataSource() {
        Map<Object, Object> dataSources = Map.of(
            "primary", createDataSource("pg-primary.prod:5432"),
            "replica", createDataSource("pg-replica.prod:5432")
        );
        
        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(dataSources);
        routing.setDefaultTargetDataSource(dataSources.get("primary"));
        return routing;
    }
}

public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() 
            ? "replica" : "primary";
    }
}

// Usage:
@Transactional(readOnly = true)  // Routes to replica automatically
public List<Order> getOrders(String userId) { ... }
```

### 17. Flyway Migration
```java
// src/main/resources/db/migration/V1__create_orders.sql
// V2__add_metadata_column.sql etc.

@Configuration
public class FlywayConfig {
    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .load();
    }
}
```

### 20. Partitioned Tables
```java
// Create partitioned table (in migration)
// CREATE TABLE events (id UUID, type TEXT, created_at TIMESTAMPTZ, payload JSONB)
// PARTITION BY RANGE (created_at);

// Query automatically uses partition pruning
public List<Event> findEventsByDateRange(Instant start, Instant end) {
    String sql = """
        SELECT * FROM events 
        WHERE created_at BETWEEN ? AND ?
        ORDER BY created_at DESC
        LIMIT 100
        """;
    // PostgreSQL optimizer automatically queries only relevant partitions
    return jdbc.query(sql, eventMapper(), Timestamp.from(start), Timestamp.from(end));
}
```

---

## 🏆 Configuration Cheat Sheet

| Scenario | Setting | Value | Why |
|---|---|---|---|
| Connection pool | `maximumPoolSize` | cores × 2 + 1 | More connections ≠ better; adds overhead |
| Write-heavy | `synchronous_commit` | `off` | 2x write throughput (risk: last few ms of writes) |
| Read-heavy | Read replica + routing | `@Transactional(readOnly=true)` | Offload reads |
| Batch inserts | `reWriteBatchedInserts` | `true` | Rewrites multi-row insert for efficiency |
| Long queries | `statement_timeout` | `30s` | Kill queries that run too long |
| Deadlock prevention | `lock_timeout` | `5s` | Don't wait forever for locks |
| Large tables | Table partitioning | RANGE/LIST/HASH | Faster queries + easy data retention |
| JSONB | GIN index | `CREATE INDEX ON t USING GIN(data)` | Fast JSONB containment queries |

---

*All examples use Spring Boot + HikariCP + PostgreSQL 15+. For raw JDBC, remove Spring annotations and manage connections manually.*
