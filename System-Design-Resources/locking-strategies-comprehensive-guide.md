# Locking Strategies: Comprehensive Guide for System Design Interviews

## Table of Contents
1. [Introduction](#introduction)
2. [Fundamental Locking Concepts](#fundamental-locking-concepts)
3. [Pessimistic Locking](#pessimistic-locking)
4. [Optimistic Locking](#optimistic-locking)
5. [Distributed Locking](#distributed-locking)
6. [Database-Level Locking](#database-level-locking)
7. [Advanced Locking Strategies](#advanced-locking-strategies)
8. [Deadlock Prevention & Resolution](#deadlock-prevention--resolution)
9. [Real-World Interview Examples](#real-world-interview-examples)
10. [Trade-offs & Decision Matrix](#trade-offs--decision-matrix)

---

## Introduction

Locking strategies are critical for maintaining data consistency in concurrent systems. This guide covers all locking mechanisms you'll encounter in system design interviews, with practical examples from real-world systems.

### Why Locking Matters
- **Data Consistency**: Prevent race conditions and maintain ACID properties
- **Concurrency Control**: Allow multiple users/processes to work simultaneously
- **Performance**: Balance between consistency and throughput
- **Scalability**: Different locks scale differently in distributed systems

---

## Fundamental Locking Concepts

### 1. Race Conditions
```
User A reads balance: $100
User B reads balance: $100
User A withdraws $50, writes $50
User B withdraws $30, writes $70
Final balance: $70 (should be $20)
```

### 2. Critical Section
```java
// Critical section - only one thread should execute
synchronized void updateInventory(String productId, int quantity) {
    int current = inventory.get(productId);
    inventory.put(productId, current - quantity);
}
```

### 3. Lock Granularity
- **Coarse-grained**: Lock entire table/database (simple, low concurrency)
- **Fine-grained**: Lock individual rows/records (complex, high concurrency)

---

## Pessimistic Locking

### Concept
Acquire locks BEFORE reading/writing data. Assumes conflicts are likely.

### Types

#### 1. Exclusive Locks (Write Locks)
```sql
-- SQL Example
BEGIN TRANSACTION;
SELECT * FROM accounts WHERE id = 123 FOR UPDATE;  -- Acquires exclusive lock
UPDATE accounts SET balance = balance - 100 WHERE id = 123;
COMMIT;
```

#### 2. Shared Locks (Read Locks)
```sql
-- Multiple readers allowed
SELECT * FROM products WHERE id = 456 FOR SHARE;  -- Shared lock
-- Other transactions can read but not write
```

### Implementation Example: Java Synchronized

```java
public class BankAccount {
    private double balance;
    private final Object lock = new Object();
    
    public void withdraw(double amount) {
        synchronized(lock) {  // Pessimistic lock
            if (balance >= amount) {
                balance -= amount;
            }
        }
    }
    
    public void deposit(double amount) {
        synchronized(lock) {
            balance += amount;
        }
    }
}
```

### Use Cases
✅ High contention scenarios (e.g., ticket booking, inventory management)
✅ Financial transactions requiring strict consistency
✅ Short transactions with frequent conflicts

❌ Long-running transactions (holds locks too long)
❌ Read-heavy workloads (blocks readers unnecessarily)

---

## Optimistic Locking

### Concept
Don't acquire locks upfront. Check for conflicts at COMMIT time. Assumes conflicts are rare.

### Version-Based Approach

```sql
-- Table with version column
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    quantity INT,
    version INT  -- Optimistic lock version
);

-- Read with version
SELECT id, quantity, version FROM products WHERE id = 100;
-- version = 5, quantity = 10

-- Update with version check
UPDATE products 
SET quantity = 9, version = 6 
WHERE id = 100 AND version = 5;  -- Only succeeds if version unchanged

-- If 0 rows affected, conflict detected - retry
```

### Timestamp-Based Approach

```java
public class Product {
    private int quantity;
    private long lastModified;
    
    public boolean updateQuantity(int newQty, long expectedTimestamp) {
        synchronized(this) {
            if (this.lastModified == expectedTimestamp) {
                this.quantity = newQty;
                this.lastModified = System.currentTimeMillis();
                return true;  // Success
            }
            return false;  // Conflict - retry
        }
    }
}
```

### CAS (Compare-And-Swap)

```java
import java.util.concurrent.atomic.AtomicInteger;

public class Counter {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        int current, updated;
        do {
            current = count.get();
            updated = current + 1;
        } while (!count.compareAndSet(current, updated));  // CAS retry loop
    }
}
```

### Use Cases
✅ Low contention scenarios
✅ Read-heavy workloads
✅ Long-running transactions
✅ Distributed systems (avoid distributed locks)

❌ High contention (too many retries)
❌ Critical operations requiring immediate consistency

---

## Distributed Locking

### 1. Redis-Based Distributed Lock (Redlock)

```python
import redis
import uuid
import time

class RedisDistributedLock:
    def __init__(self, redis_clients):
        self.redis_clients = redis_clients  # Multiple Redis instances
        self.lock_value = str(uuid.uuid4())
        
    def acquire_lock(self, resource_name, ttl=30000):
        """Acquire lock across majority of Redis instances"""
        acquired_count = 0
        start_time = time.time()
        
        for client in self.redis_clients:
            # SET resource_name unique_value NX PX ttl
            result = client.set(
                f"lock:{resource_name}",
                self.lock_value,
                nx=True,  # Only set if not exists
                px=ttl    # Expire after ttl milliseconds
            )
            if result:
                acquired_count += 1
        
        # Need majority consensus
        quorum = len(self.redis_clients) // 2 + 1
        if acquired_count >= quorum:
            return True
        
        # Failed - release any acquired locks
        self.release_lock(resource_name)
        return False
    
    def release_lock(self, resource_name):
        """Release lock using Lua script (atomic)"""
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """
        for client in self.redis_clients:
            client.eval(lua_script, 1, f"lock:{resource_name}", self.lock_value)
```

### 2. ZooKeeper-Based Distributed Lock

```java
public class ZookeeperLock {
    private final ZooKeeper zk;
    private final String lockPath = "/locks";
    private String myNode;
    
    public void acquireLock(String resourceId) throws Exception {
        String path = lockPath + "/" + resourceId + "/lock-";
        
        // Create ephemeral sequential node
        myNode = zk.create(
            path,
            new byte[0],
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL_SEQUENTIAL
        );
        
        while (true) {
            List<String> children = zk.getChildren(lockPath + "/" + resourceId, false);
            Collections.sort(children);
            
            // If my node is the smallest, I have the lock
            if (myNode.endsWith(children.get(0))) {
                return;  // Lock acquired
            }
            
            // Watch the node before mine
            String nodeToWatch = children.get(children.indexOf(myNode) - 1);
            CountDownLatch latch = new CountDownLatch(1);
            
            zk.exists(lockPath + "/" + resourceId + "/" + nodeToWatch, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                    latch.countDown();
                }
            });
            
            latch.await();  // Wait for previous node to release
        }
    }
    
    public void releaseLock() throws Exception {
        zk.delete(myNode, -1);  // Release lock
    }
}
```

### 3. Database-Based Distributed Lock

```sql
-- Simple table-based lock
CREATE TABLE distributed_locks (
    resource_name VARCHAR(100) PRIMARY KEY,
    owner_id VARCHAR(100),
    acquired_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- Acquire lock
INSERT INTO distributed_locks (resource_name, owner_id, acquired_at, expires_at)
VALUES ('order_123', 'worker_5', NOW(), NOW() + INTERVAL 30 SECOND)
ON CONFLICT (resource_name) DO NOTHING;

-- Check if acquired (1 row = success, 0 rows = failure)
-- Release lock
DELETE FROM distributed_locks 
WHERE resource_name = 'order_123' AND owner_id = 'worker_5';
```

### Distributed Lock Comparison

| Approach | Pros | Cons | Use Case |
|----------|------|------|----------|
| **Redis Redlock** | Fast, simple | Split-brain issues, clock skew | Short-lived locks, high throughput |
| **ZooKeeper** | Strong consistency, leader election | Complex, higher latency | Critical operations, coordination |
| **Database** | Simple, existing infrastructure | Slower, table contention | Low-frequency locks |
| **etcd** | Strong consistency, K8s native | Operational overhead | Cloud-native apps |

---

## Database-Level Locking

### 1. Row-Level Locks

```sql
-- PostgreSQL: Lock specific rows
BEGIN;
SELECT * FROM orders WHERE order_id = 123 FOR UPDATE;  -- Row lock
UPDATE orders SET status = 'processing' WHERE order_id = 123;
COMMIT;

-- MySQL: Similar syntax
SELECT * FROM inventory WHERE product_id = 456 FOR UPDATE;
```

### 2. Table-Level Locks

```sql
-- Lock entire table
LOCK TABLES orders WRITE;  -- Exclusive lock on orders table
-- Perform operations
UNLOCK TABLES;
```

### 3. Advisory Locks (PostgreSQL)

```sql
-- Application-level cooperative locks
SELECT pg_advisory_lock(12345);  -- Acquire advisory lock with key 12345
-- Do work
SELECT pg_advisory_unlock(12345);  -- Release lock

-- Try lock (non-blocking)
SELECT pg_try_advisory_lock(12345);  -- Returns true/false
```

### 4. Named Locks (MySQL)

```sql
-- Application-level named locks
SELECT GET_LOCK('order_processing_123', 10);  -- Timeout 10 seconds
-- Returns 1 if successful, 0 if timeout, NULL if error

-- Do work
SELECT RELEASE_LOCK('order_processing_123');
```

### 5. Isolation Levels & Locking

```sql
-- Serializable: Strictest, most locking
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- Repeatable Read: Locks rows read
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- Read Committed: No read locks (default in many DBs)
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- Read Uncommitted: No locks, allows dirty reads
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
```

---

## Advanced Locking Strategies

### 1. Two-Phase Locking (2PL)

**Growing Phase**: Acquire locks, cannot release
**Shrinking Phase**: Release locks, cannot acquire

```java
public class TwoPhaseTransaction {
    private Set<Resource> lockedResources = new HashSet<>();
    private boolean canAcquire = true;
    
    public void acquireLock(Resource resource) {
        if (!canAcquire) {
            throw new IllegalStateException("Already in shrinking phase");
        }
        resource.lock();
        lockedResources.add(resource);
    }
    
    public void commit() {
        canAcquire = false;  // Enter shrinking phase
        // Release all locks
        for (Resource resource : lockedResources) {
            resource.unlock();
        }
    }
}
```

### 2. Multi-Version Concurrency Control (MVCC)

```
Time:     T1              T2              T3
-----------------------------------------------
         Read(X=10)
                       Write(X=20)
                                      Read(X=?)
                       
With MVCC:
T3 reads X=10 (old version) if T2 not committed
T3 reads X=20 if T2 committed

No locks needed for reads!
```

**PostgreSQL Implementation:**
```sql
-- Each row has multiple versions with timestamps
-- Readers see snapshot at transaction start time
BEGIN;  -- Snapshot timestamp = T1
SELECT * FROM accounts WHERE id = 100;  -- Reads version at T1

-- Concurrent writer creates new version
-- Original reader still sees old version
```

### 3. Read-Write Locks (ReentrantReadWriteLock)

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public V get(K key) {
        lock.readLock().lock();  // Multiple readers allowed
        try {
            return cache.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        lock.writeLock().lock();  // Exclusive write access
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

### 4. Lock-Free Programming

```java
// Using AtomicReference for lock-free stack
public class LockFreeStack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
        Node(T value) { this.value = value; }
    }
    
    private AtomicReference<Node<T>> top = new AtomicReference<>();
    
    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        Node<T> oldTop;
        do {
            oldTop = top.get();
            newNode.next = oldTop;
        } while (!top.compareAndSet(oldTop, newNode));  // Lock-free CAS
    }
    
    public T pop() {
        Node<T> oldTop, newTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));
        return oldTop.value;
    }
}
```

### 5. Lease-Based Locking

```python
class LeaseBasedLock:
    """Lock expires automatically after TTL"""
    
    def acquire_lock(self, resource_id, owner_id, ttl_seconds=30):
        lock_key = f"lease:{resource_id}"
        acquired = redis_client.set(
            lock_key,
            owner_id,
            nx=True,
            ex=ttl_seconds  # Auto-expire
        )
        return acquired
    
    def renew_lease(self, resource_id, owner_id, ttl_seconds=30):
        """Extend lock if still owner"""
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("expire", KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        return redis_client.eval(
            lua_script, 
            1, 
            f"lease:{resource_id}", 
            owner_id, 
            ttl_seconds
        )
```

---

## Deadlock Prevention & Resolution

### What is Deadlock?

```
Transaction T1:        Transaction T2:
Lock(A)                Lock(B)
...                    ...
Lock(B)  [WAIT]        Lock(A)  [WAIT]

Both waiting forever! Deadlock.
```

### 1. Deadlock Prevention: Lock Ordering

```java
public class BankTransfer {
    // Always acquire locks in consistent order (by account ID)
    public void transfer(Account from, Account to, double amount) {
        Account first = from.getId() < to.getId() ? from : to;
        Account second = from.getId() < to.getId() ? to : from;
        
        synchronized(first) {
            synchronized(second) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }
}
```

### 2. Deadlock Detection: Wait-For Graph

```python
class DeadlockDetector:
    def __init__(self):
        self.wait_for_graph = defaultdict(set)  # Transaction -> Set of transactions
    
    def add_wait(self, waiting_tx, holding_tx):
        """Record that waiting_tx is waiting for holding_tx"""
        self.wait_for_graph[waiting_tx].add(holding_tx)
        
        # Check for cycle (deadlock)
        if self.has_cycle(waiting_tx):
            return True  # Deadlock detected
        return False
    
    def has_cycle(self, start_tx):
        """DFS to detect cycle"""
        visited = set()
        stack = {start_tx}
        
        def dfs(tx):
            if tx in stack:
                return True  # Cycle found
            if tx in visited:
                return False
            
            visited.add(tx)
            stack.add(tx)
            
            for waiting_for in self.wait_for_graph[tx]:
                if dfs(waiting_for):
                    return True
            
            stack.remove(tx)
            return False
        
        return dfs(start_tx)
```

### 3. Timeout-Based Deadlock Resolution

```java
public class TimeoutLock {
    private final Lock lock = new ReentrantLock();
    
    public boolean executeWithTimeout(Runnable task, long timeout) {
        try {
            if (lock.tryLock(timeout, TimeUnit.SECONDS)) {
                try {
                    task.run();
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            return false;  // Timeout - possible deadlock
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

### 4. Deadlock Recovery: Victim Selection

```python
def resolve_deadlock(deadlocked_transactions):
    """Select victim transaction to abort"""
    # Strategy 1: Abort youngest transaction (least work lost)
    victim = min(deadlocked_transactions, key=lambda tx: tx.start_time)
    
    # Strategy 2: Abort transaction with fewest locks
    # victim = min(deadlocked_transactions, key=lambda tx: len(tx.locks))
    
    # Strategy 3: Abort transaction with lowest priority
    # victim = min(deadlocked_transactions, key=lambda tx: tx.priority)
    
    victim.abort()
    return victim
```

---

## Real-World Interview Examples

### Interview Example 1: E-commerce Flash Sale (High Contention)

**Problem**: 1000 users trying to buy 10 remaining items simultaneously.

**Bad Approach (Race Condition)**:
```python
def purchase(user_id, product_id):
    stock = db.query("SELECT stock FROM products WHERE id = ?", product_id)
    if stock > 0:
        # Race condition! Multiple users pass this check
        db.execute("UPDATE products SET stock = stock - 1 WHERE id = ?", product_id)
        create_order(user_id, product_id)
```

**Solution 1: Pessimistic Locking**
```sql
BEGIN TRANSACTION;
SELECT stock FROM products WHERE id = 123 FOR UPDATE;  -- Lock row
-- Only first transaction gets here, others wait
IF stock > 0:
    UPDATE products SET stock = stock - 1 WHERE id = 123;
    INSERT INTO orders ...
COMMIT;
```

**Solution 2: Optimistic Locking with Retry**
```python
def purchase_with_retry(user_id, product_id, max_retries=3):
    for attempt in range(max_retries):
        product = db.query(
            "SELECT stock, version FROM products WHERE id = ?", 
            product_id
        )
        
        if product.stock <= 0:
            return False  # Out of stock
        
        # Atomic update with version check
        rows_affected = db.execute(
            """
            UPDATE products 
            SET stock = stock - 1, version = version + 1 
            WHERE id = ? AND version = ?
            """,
            product_id, product.version
        )
        
        if rows_affected > 0:
            create_order(user_id, product_id)
            return True  # Success
        
        # Conflict - retry
        time.sleep(0.01 * (2 ** attempt))  # Exponential backoff
    
    return False  # Failed after retries
```

**Solution 3: Queue-Based (Best for Flash Sales)**
```python
# Use Redis/Kafka queue
def purchase_queue_based(user_id, product_id):
    queue_position = redis.rpush(f"purchase_queue:{product_id}", user_id)
    
    # Background worker processes queue
    if queue_position <= get_available_stock(product_id):
        return True  # Will process
    return False  # Too late
```

**Recommendation for Interview**:
- High contention (> 100 TPS): Queue-based
- Medium contention: Optimistic locking with retry
- Low contention: Pessimistic locking

---

### Interview Example 2: Bank Account Transfer (Deadlock Prevention)

**Problem**: Transfer money between accounts without deadlock.

**Bad Approach (Deadlock)**:
```python
def transfer(from_account, to_account, amount):
    lock(from_account)
    lock(to_account)  # If another transfer does reverse, DEADLOCK!
    
    from_account.balance -= amount
    to_account.balance += amount
    
    unlock(to_account)
    unlock(from_account)
```

**Solution: Lock Ordering**
```python
def transfer(from_account, to_account, amount):
    # Always lock in consistent order (by ID)
    first = min(from_account, to_account, key=lambda a: a.id)
    second = max(from_account, to_account, key=lambda a: a.id)
    
    with lock(first):
        with lock(second):
            if from_account.balance >= amount:
                from_account.balance -= amount
                to_account.balance += amount
                log_transaction(from_account, to_account, amount)
                return True
    return False
```

**Alternative: Two-Phase Commit**
```python
def transfer_2pc(from_account, to_account, amount):
    # Phase 1: Prepare
    with lock(from_account):
        if from_account.balance >= amount:
            from_account.reserve(amount)  # Tentative deduction
            prepare_success = True
        else:
            prepare_success = False
    
    if not prepare_success:
        return False
    
    # Phase 2: Commit
    with lock(from_account):
        from_account.commit_reserve(amount)
    
    with lock(to_account):
        to_account.balance += amount
    
    return True
```

---

### Interview Example 3: Distributed Seat Booking (Cinema/Flight)

**Problem**: Book seats across distributed system without double-booking.

**Solution 1: Distributed Lock with Redis**
```python
def book_seat(booking_id, seat_id):
    lock = RedisDistributedLock(redis_clients)
    
    if lock.acquire_lock(f"seat:{seat_id}", ttl=5000):
        try:
            # Check availability in database
            seat = db.query("SELECT * FROM seats WHERE id = ? FOR UPDATE", seat_id)
            
            if seat.status == 'available':
                db.execute(
                    "UPDATE seats SET status = 'booked', booking_id = ? WHERE id = ?",
                    booking_id, seat_id
                )
                return True
            return False
        finally:
            lock.release_lock(f"seat:{seat_id}")
    else:
        return False  # Couldn't acquire lock
```

**Solution 2: Optimistic Locking (Better for Low Contention)**
```python
def book_seat_optimistic(booking_id, seat_id):
    max_retries = 3
    for attempt in range(max_retries):
        seat = db.query(
            "SELECT status, version FROM seats WHERE id = ?", 
            seat_id
        )
        
        if seat.status != 'available':
            return False
        
        rows = db.execute(
            """
            UPDATE seats 
            SET status = 'booked', booking_id = ?, version = version + 1
            WHERE id = ? AND version = ? AND status = 'available'
            """,
            booking_id, seat_id, seat.version
        )
        
        if rows > 0:
            return True  # Successfully booked
        
        time.sleep(0.01 * (2 ** attempt))
    
    return False
```

**Solution 3: Pre-allocation Strategy**
```python
# Allocate seat blocks to each server
def book_seat_preallocated(booking_id, server_id):
    # Each server owns a range of seats
    seat_ranges = {
        'server_1': range(1, 101),
        'server_2': range(101, 201),
        # ...
    }
    
    my_seats = seat_ranges[server_id]
    
    # No distributed lock needed - each server owns its seats
    seat_id = find_available_seat_in_range(my_seats)
    if seat_id:
        db.execute(
            "UPDATE seats SET status = 'booked', booking_id = ? WHERE id = ?",
            booking_id, seat_id
        )
        return seat_id
    return None
```

---

### Interview Example 4: Inventory Management (Multi-Warehouse)

**Problem**: Update inventory across multiple warehouses atomically.

**Solution: Saga Pattern with Compensating Transactions**
```python
def order_product(order_id, product_id, quantity):
    warehouses = get_warehouses_with_stock(product_id, quantity)
    allocated = []
    
    try:
        # Phase 1: Reserve inventory in each warehouse
        for warehouse in warehouses:
            lock = acquire_distributed_lock(f"warehouse:{warehouse.id}")
            try:
                success = warehouse.reserve_inventory(product_id, quantity)
                if success:
                    allocated.append((warehouse, quantity))
                else:
                    raise InsufficientInventoryError()
            finally:
                release_lock(lock)
        
        # Phase 2: Commit all reservations
        for warehouse, qty in allocated:
            warehouse.commit_reservation(order_id, product_id, qty)
        
        return True
        
    except Exception as e:
        # Compensating transactions: rollback all allocations
        for warehouse, qty in allocated:
            warehouse.release_reservation(product_id, qty)
        return False
```

---

### Interview Example 5: Rate Limiter with Distributed Locks

**Problem**: Implement rate limiter (100 requests per minute per user) across multiple servers.

**Solution 1: Sliding Window with Redis**
```python
def is_allowed(user_id, limit=100, window_seconds=60):
    now = time.time()
    window_start = now - window_seconds
    
    key = f"rate_limit:{user_id}"
    
    # Use Redis transaction (no distributed lock needed!)
    pipe = redis.pipeline()
    
    # Remove old entries
    pipe.zremrangebyscore(key, 0, window_start)
    
    # Count current window
    pipe.zcard(key)
    
    # Add current request
    pipe.zadd(key, {str(now): now})
    
    # Set expiry
    pipe.expire(key, window_seconds)
    
    results = pipe.execute()
    current_count = results[1]
    
    return current_count < limit
```

**Solution 2: Token Bucket with Distributed Lock**
```python
def consume_token(user_id, tokens=1):
    lock_key = f"ratelimit_lock:{user_id}"
    bucket_key = f"ratelimit_bucket:{user_id}"
    
    # Acquire distributed lock
    lock_acquired = redis.set(lock_key, "1", nx=True, ex=1)
    
    if not lock_acquired:
        return False  # Couldn't acquire lock
    
    try:
        # Get current bucket state
        bucket = redis.get(bucket_key)
        if bucket is None:
            bucket = {'tokens': 100, 'last_refill': time.time()}
        else:
            bucket = json.loads(bucket)
        
        # Refill tokens based on time elapsed
        now = time.time()
        elapsed = now - bucket['last_refill']
        refill_tokens = int(elapsed * 100 / 60)  # 100 tokens per 60 seconds
        
        bucket['tokens'] = min(100, bucket['tokens'] + refill_tokens)
        bucket['last_refill'] = now
        
        # Try to consume tokens
        if bucket['tokens'] >= tokens:
            bucket['tokens'] -= tokens
            redis.set(bucket_key, json.dumps(bucket), ex=120)
            return True
        
        return False
        
    finally:
        redis.delete(lock_key)  # Release lock
```

---

### Interview Example 6: Leader Election with Distributed Locks

**Problem**: Elect a leader among distributed workers to perform a singleton task.

**Solution: ZooKeeper-based Leader Election**
```python
class LeaderElection:
    def __init__(self, zk_client, election_path="/election"):
        self.zk = zk_client
        self.election_path = election_path
        self.my_node = None
        
    def participate(self):
        # Create ephemeral sequential node
        self.my_node = self.zk.create(
            f"{self.election_path}/candidate-",
            ephemeral=True,
            sequence=True
        )
        
        while True:
            children = self.zk.get_children(self.election_path)
            children.sort()
            
            # Lowest sequence number is leader
            if self.my_node == f"{self.election_path}/{children[0]}":
                return True  # I am leader!
            
            # Watch the previous node
            my_index = children.index(self.my_node.split('/')[-1])
            if my_index > 0:
                watch_node = f"{self.election_path}/{children[my_index - 1]}"
                # Wait for previous node to be deleted
                self.zk.exists(watch_node, watch=self._node_deleted_watcher)
```

**Solution: Redis-based Leader Election (Simpler)**
```python
def try_become_leader(worker_id, lease_seconds=30):
    """Try to become leader using Redis"""
    leader_key = "system:leader"
    
    # Try to set as leader with expiry
    became_leader = redis.set(
        leader_key,
        worker_id,
        nx=True,  # Only if not exists
        ex=lease_seconds
    )
    
    if became_leader:
        # I'm the leader! Start heartbeat to renew lease
        threading.Thread(target=renew_leadership, args=(worker_id, lease_seconds)).start()
        return True
    
    return False  # Someone else is leader

def renew_leadership(worker_id, lease_seconds):
    """Renew leadership lease periodically"""
    while True:
        time.sleep(lease_seconds / 2)  # Renew at half the lease time
        
        # Only renew if still leader
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("expire", KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        result = redis.eval(lua_script, 1, "system:leader", worker_id, lease_seconds)
        
        if not result:
            break  # Lost leadership
```

---

### Interview Example 7: Distributed Cache Invalidation

**Problem**: Invalidate cache entries across multiple servers when data changes.

**Solution 1: Write-Through Cache with Distributed Lock**
```python
def update_user(user_id, new_data):
    cache_key = f"user:{user_id}"
    lock_key = f"lock:user:{user_id}"
    
    # Acquire distributed lock
    lock = redis.set(lock_key, "1", nx=True, ex=5)
    
    if not lock:
        raise Exception("Could not acquire lock")
    
    try:
        # Update database
        db.update("UPDATE users SET ... WHERE id = ?", user_id, new_data)
        
        # Invalidate cache on all servers
        redis.delete(cache_key)
        
        # Alternatively, update cache directly (write-through)
        # redis.set(cache_key, json.dumps(new_data), ex=3600)
        
    finally:
        redis.delete(lock_key)
```

**Solution 2: Versioned Cache (Optimistic)**
```python
def get_user_with_cache(user_id):
    cache_key = f"user:{user_id}"
    
    # Try cache first
    cached = redis.get(cache_key)
    if cached:
        user_data = json.loads(cached)
        return user_data
    
    # Cache miss - load from DB with version
    user = db.query(
        "SELECT id, name, email, version FROM users WHERE id = ?",
        user_id
    )
    
    # Store in cache with version
    redis.set(cache_key, json.dumps(user), ex=3600)
    return user

def update_user_versioned(user_id, new_data, expected_version):
    # Optimistic update with version check
    rows = db.execute(
        """
        UPDATE users 
        SET name = ?, email = ?, version = version + 1
        WHERE id = ? AND version = ?
        """,
        new_data['name'], new_data['email'], user_id, expected_version
    )
    
    if rows > 0:
        # Invalidate cache - next read will refresh
        redis.delete(f"user:{user_id}")
        return True
    
    return False  # Version conflict
```

---

### Interview Example 8: Idempotent API with Distributed Locks

**Problem**: Ensure payment processing is idempotent (no double charges from retries).

**Solution: Idempotency Key with Distributed Lock**
```python
def process_payment(idempotency_key, user_id, amount):
    """Process payment exactly once using idempotency key"""
    
    # Check if already processed
    result_key = f"payment_result:{idempotency_key}"
    cached_result = redis.get(result_key)
    if cached_result:
        return json.loads(cached_result)  # Return cached result
    
    # Acquire lock for this idempotency key
    lock_key = f"payment_lock:{idempotency_key}"
    lock_acquired = redis.set(lock_key, "1", nx=True, ex=30)
    
    if not lock_acquired:
        # Another request with same key is processing
        # Wait and retry to get cached result
        time.sleep(0.1)
        cached_result = redis.get(result_key)
        if cached_result:
            return json.loads(cached_result)
        raise Exception("Payment processing timeout")
    
    try:
        # Double-check database for idempotency
        existing = db.query(
            "SELECT * FROM payments WHERE idempotency_key = ?",
            idempotency_key
        )
        if existing:
            result = existing.to_dict()
        else:
            # Process payment
            result = charge_credit_card(user_id, amount)
            
            # Store in database
            db.execute(
                """
                INSERT INTO payments (idempotency_key, user_id, amount, status)
                VALUES (?, ?, ?, ?)
                """,
                idempotency_key, user_id, amount, result['status']
            )
        
        # Cache result for future retries
        redis.set(result_key, json.dumps(result), ex=86400)  # 24 hours
        return result
        
    finally:
        redis.delete(lock_key)
```

---

### Interview Example 9: Collaborative Document Editing

**Problem**: Multiple users editing same document simultaneously (Google Docs style).

**Solution: Operational Transformation with Optimistic Locking**
```python
class Document:
    def __init__(self, doc_id):
        self.doc_id = doc_id
        self.version = 0
        self.content = ""
        
    def apply_operation(self, operation, client_version):
        """Apply operation using OT"""
        
        # Get current version from DB
        doc = db.query(
            "SELECT content, version FROM documents WHERE id = ? FOR UPDATE",
            self.doc_id
        )
        
        if client_version < doc.version:
            # Transform operation against missed operations
            missed_ops = get_operations_since(self.doc_id, client_version)
            operation = transform_operation(operation, missed_ops)
        
        # Apply operation
        new_content = apply_op_to_content(doc.content, operation)
        
        # Update with version check (optimistic)
        rows = db.execute(
            """
            UPDATE documents 
            SET content = ?, version = version + 1
            WHERE id = ? AND version = ?
            """,
            new_content, self.doc_id, doc.version
        )
        
        if rows == 0:
            raise ConflictError("Document modified, retry")
        
        # Store operation for future transformations
        store_operation(self.doc_id, doc.version + 1, operation)
        
        return doc.version + 1
```

**Alternative: Pessimistic Section Locking**
```python
def edit_document_section(doc_id, section_id, user_id, changes):
    """Lock specific document section for editing"""
    
    lock_key = f"doc_section:{doc_id}:{section_id}"
    
    # Try to acquire section lock
    lock_acquired = redis.set(
        lock_key,
        user_id,
        nx=True,
        ex=300  # 5 minute editing lock
    )
    
    if not lock_acquired:
        current_editor = redis.get(lock_key)
        raise Exception(f"Section locked by user {current_editor}")
    
    try:
        # User has exclusive edit access to this section
        # Apply changes
        update_section(doc_id, section_id, changes)
        
        # Broadcast to other users
        broadcast_section_update(doc_id, section_id, changes)
        
    finally:
        # Release lock when done
        redis.delete(lock_key)
```

---

### Interview Example 10: Job Queue with Worker Coordination

**Problem**: Multiple workers processing jobs from queue without duplication.

**Solution: Distributed Lock per Job**
```python
def process_job_queue():
    while True:
        # Get next job from queue
        job = redis.blpop("job_queue", timeout=1)
        
        if not job:
            continue
        
        job_id = job[1]
        lock_key = f"job_lock:{job_id}"
        
        # Try to acquire lock for this job
        lock_acquired = redis.set(
            lock_key,
            worker_id,
            nx=True,
            ex=300  # 5 minute processing timeout
        )
        
        if not lock_acquired:
            # Another worker got this job
            continue
        
        try:
            # Process job
            result = process_job(job_id)
            
            # Mark complete
            db.execute(
                "UPDATE jobs SET status = 'completed', result = ? WHERE id = ?",
                result, job_id
            )
            
        except Exception as e:
            # On failure, requeue job
            redis.rpush("job_queue", job_id)
            
        finally:
            redis.delete(lock_key)
```

**Alternative: Lease-Based Job Processing**
```python
def claim_and_process_job():
    """Claim job with lease, auto-releases if worker dies"""
    
    # Atomic operation: get and lock job
    lua_script = """
    local job = redis.call('lpop', 'job_queue')
    if job then
        redis.call('setex', 'job_lease:' .. job, 300, ARGV[1])
        return job
    end
    return nil
    """
    
    job_id = redis.eval(lua_script, 0, worker_id)
    
    if not job_id:
        return  # No jobs available
    
    try:
        # Process job with periodic lease renewal
        result = process_job_with_heartbeat(job_id, worker_id)
        
        # Mark complete and release lease
        db.execute("UPDATE jobs SET status = 'completed' WHERE id = ?", job_id)
        redis.delete(f"job_lease:{job_id}")
        
    except Exception as e:
        # Lease will auto-expire, job can be retried
        log_error(f"Job {job_id} failed: {e}")
```

---

## Trade-offs & Decision Matrix

### When to Use Each Locking Strategy

| Scenario | Recommended Approach | Why |
|----------|---------------------|-----|
| **High read, low write** | Optimistic Locking / MVCC | Readers don't block, rare conflicts |
| **High contention writes** | Queue-based / Pessimistic | Avoid retry storms |
| **Financial transactions** | Pessimistic + 2PC | Need strict consistency |
| **Microservices coordination** | Distributed locks (Redis/ZK) | Cross-service synchronization |
| **Flash sales / inventory** | Queue + pre-allocation | Scale to high concurrency |
| **Collaborative editing** | OT + optimistic | Real-time, eventual consistency OK |
| **Leader election** | ZooKeeper / etcd | Need strong consensus |
| **Rate limiting** | Sliding window (no locks) | Lock-free is faster |
| **Idempotency** | Distributed lock + cache | Prevent duplicates |
| **Long transactions** | Optimistic / Saga | Don't hold locks long |

---

### Performance Comparison

```
Throughput (ops/sec):

Lock-free (CAS)           : ████████████████████ 100,000+
Optimistic (low contention): ███████████████████ 50,000+
Pessimistic (DB row lock) : ███████████████     30,000
Distributed lock (Redis)  : ██████████          10,000
Distributed lock (ZK)     : ████                 2,000
2PC / Saga                : ██                   1,000
```

---

### Latency Comparison

```
Typical Latency:

Local lock (synchronized)  : <0.1ms
DB row lock (single DB)    : 1-5ms
Optimistic (no conflict)   : 1-5ms
Redis distributed lock     : 2-10ms
ZooKeeper distributed lock : 10-50ms
2PC across services        : 50-200ms
```

---

### Consistency vs Availability Trade-off

```
Strong Consistency (CP):
- Pessimistic locking
- Distributed locks (ZooKeeper, etcd)
- 2PC
Use for: Financial, inventory, seat booking

Eventual Consistency (AP):
- Optimistic locking
- MVCC
- CRDTs
Use for: Social media, analytics, caching
```

---

## Interview Tips

### 1. Always Ask About Contention Level
```
Low contention (< 10 TPS):
  → Optimistic locking

Medium contention (10-100 TPS):
  → Pessimistic locking or optimistic with retry

High contention (> 100 TPS):
  → Queue-based or pre-allocation
```

### 2. Consider Failure Scenarios
```
What if lock holder crashes?
  → Use leases with TTL
  → Use ephemeral nodes (ZooKeeper)

What if network partitions?
  → Fencing tokens
  → Generation numbers
```

### 3. Mention CAP Theorem
```
Consistency over Availability:
  → Pessimistic locks, 2PC

Availability over Consistency:
  → Optimistic locks, eventual consistency
```

### 4. Scale Considerations
```
Single datacenter:
  → Database locks, Redis

Multi-datacenter:
  → Avoid distributed locks if possible
  → Use conflict-free data structures (CRDTs)
  → Consider operational transformation
```

---

## Quick Reference: Lock Selection Flowchart

```
Is it read-heavy?
  ├─ Yes → Use MVCC or optimistic locking
  └─ No → Continue

Is contention high (> 100 TPS)?
  ├─ Yes → Use queue-based or pre-allocation
  └─ No → Continue

Is transaction short (< 100ms)?
  ├─ Yes → Use pessimistic locking
  └─ No → Use optimistic locking

Is it distributed?
  ├─ Yes → Use distributed locks (Redis/ZK)
  │         or avoid locks with sharding
  └─ No → Use database locks

Need strong consistency?
  ├─ Yes → Pessimistic + 2PC
  └─ No → Optimistic + eventual consistency
```

---

## Common Interview Mistakes to Avoid

❌ **Using SELECT then UPDATE without locking**
```sql
-- WRONG: Race condition
SELECT balance FROM accounts WHERE id = 123;
UPDATE accounts SET balance = balance - 100 WHERE id = 123;

-- RIGHT: Lock for update
SELECT balance FROM accounts WHERE id = 123 FOR UPDATE;
UPDATE accounts SET balance = balance - 100 WHERE id = 123;
```

❌ **Holding locks during external API calls**
```python
# WRONG: Holding lock during slow operation
with lock:
    data = db.query(...)
    response = external_api_call()  # Slow!
    db.update(...)

# RIGHT: Release lock during slow operation
data = None
with lock:
    data = db.query(...)

response = external_api_call()  # No lock held

with lock:
    db.update(...)
```

❌ **Not handling lock acquisition failure**
```python
# WRONG: Assume lock always acquired
lock.acquire()
try:
    do_work()
finally:
    lock.release()

# RIGHT: Handle failure
if lock.try_acquire(timeout=5):
    try:
        do_work()
    finally:
        lock.release()
else:
    handle_failure()
```

❌ **Ignoring deadlock prevention**
```python
# WRONG: Can deadlock
def transfer(from, to, amount):
    lock(from)
    lock(to)  # Deadlock if reverse transfer happening

# RIGHT: Lock ordering
def transfer(from, to, amount):
    first = min(from, to, key=lambda x: x.id)
    second = max(from, to, key=lambda x: x.id)
    lock(first)
    lock(second)
```

---

## Additional Resources

### Recommended Reading
- "Database Internals" by Alex Petrov (MVCC, 2PL)
- "Designing Data-Intensive Applications" by Martin Kleppmann (Distributed locks)
- "Transaction Processing" by Gray & Reuter (2PC, deadlock)

### Key Papers
- "Leases: An Efficient Fault-Tolerant Mechanism" (Lease-based locking)
- "How to Do Distributed Locking" by Martin Kleppmann (Redlock criticism)
- "The Chubby Lock Service" by Google (Distributed locking at scale)

### Practice Systems
- Redis Redlock implementation
- PostgreSQL advisory locks
- ZooKeeper recipes
- etcd distributed locks

---

## Summary

This comprehensive guide covers:
✅ Pessimistic vs Optimistic locking strategies
✅ Distributed locking mechanisms (Redis, ZooKeeper, Database)
✅ Deadlock prevention and resolution
✅ 10 real-world interview examples with multiple solutions
✅ Trade-off analysis and decision matrix
✅ Performance benchmarks
✅ Common mistakes to avoid

**Key Takeaway**: There's no one-size-fits-all solution. Always consider:
1. Contention level
2. Read vs write ratio
3. Transaction duration
4. Consistency requirements
5. System scale (single vs distributed)

Choose the simplest solution that meets your requirements!
