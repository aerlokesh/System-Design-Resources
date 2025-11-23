# Caching Deep Dive - Comprehensive Guide with Examples

## Table of Contents
1. [Introduction to Caching](#introduction)
2. [Caching Fundamentals](#fundamentals)
3. [Caching Strategies with Code Examples](#strategies)
4. [Cache Invalidation Patterns](#invalidation)
5. [Real-World Implementation Examples](#real-world)
6. [Advanced Caching Patterns](#advanced-patterns)
7. [Performance Optimization](#performance)
8. [Common Pitfalls and Anti-Patterns](#pitfalls)
9. [Technology-Specific Examples](#technology-examples)
10. [Troubleshooting Guide](#troubleshooting)
11. [Interview Examples](#interview-examples)

---

## 1. Introduction to Caching

### What is Caching?

Caching is the practice of storing copies of data in a temporary storage location (cache) so future requests for that data can be served faster.

**Simple Analogy:**
```
Without Cache (Going to Library):
- Walk to library (10 minutes)
- Find book (5 minutes)
- Read page (1 minute)
- Total: 16 minutes per page

With Cache (Keep Book at Home):
- Read page (1 minute)
- Total: 1 minute per page
- 16x faster!
```

### Why Cache?

**Performance Metrics:**
```
Latency Comparison:
├── L1 CPU Cache:     0.5 ns
├── L2 CPU Cache:     7 ns
├── RAM:              100 ns
├── SSD:              150,000 ns (150 μs)
├── Network (LAN):    500,000 ns (500 μs)
├── SSD Disk:         10,000,000 ns (10 ms)
├── Network (Internet): 50,000,000 ns (50 ms)
└── Database Query:   100,000,000 ns (100 ms)

Cache Hit: 1-10ms
Cache Miss: 100-500ms
Speed Improvement: 10-500x faster
```

### Real Impact Example

**E-commerce Product Page:**
```
Without Cache:
1. Query Product Table (30ms)
2. Query Reviews Table (40ms)
3. Query Inventory Table (20ms)
4. Query Related Products (50ms)
5. Query Images Table (30ms)
───────────────────────────────
Total: 170ms per page load

With Cache:
1. Redis GET product:12345 (2ms)
───────────────────────────────
Total: 2ms per page load
Improvement: 85x faster!

Cost Savings:
- Database Load: 95% reduction
- Infrastructure: $50K → $10K/month
- ROI: 80% cost reduction
```

---

## 2. Caching Fundamentals

### 2.1 Cache Hit vs Miss

```python
def get_user_profile(user_id):
    """
    Cache Hit: Data found in cache
    Cache Miss: Data not in cache, must fetch from source
    """
    cache_key = f"user:{user_id}"
    
    # Try to get from cache
    user = cache.get(cache_key)
    
    if user is not None:
        # CACHE HIT
        print(f"Cache HIT for user {user_id}")
        metrics.increment('cache.hit')
        return user
    else:
        # CACHE MISS
        print(f"Cache MISS for user {user_id}")
        metrics.increment('cache.miss')
        
        # Fetch from database
        user = database.query(f"SELECT * FROM users WHERE id = {user_id}")
        
        # Store in cache for future requests
        cache.set(cache_key, user, ttl=600)  # 10 minutes
        
        return user

# Metrics to track
# Cache Hit Rate = Hits / (Hits + Misses)
# Target: >80% for good performance
```

### 2.2 Cache Layers

```
Application Architecture with Multiple Cache Layers:

┌─────────────────────────────────────────────┐
│ User Request                                │
└────────────────┬────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────┐
│ Layer 1: Browser Cache (Client)             │
│ - Cached HTML, CSS, JS, Images              │
│ - 0ms latency (instant)                     │
│ - Hit Rate: 30-40%                          │
└────────────────┬────────────────────────────┘
                 ↓ (miss)
┌─────────────────────────────────────────────┐
│ Layer 2: CDN (Edge)                         │
│ - Static assets cached at edge              │
│ - 10-50ms latency                           │
│ - Hit Rate: 85-95%                          │
└────────────────┬────────────────────────────┘
                 ↓ (miss)
┌─────────────────────────────────────────────┐
│ Layer 3: Application Cache (Redis)          │
│ - API responses, Database queries           │
│ - 1-5ms latency                             │
│ - Hit Rate: 70-90%                          │
└────────────────┬────────────────────────────┘
                 ↓ (miss)
┌─────────────────────────────────────────────┐
│ Layer 4: Database Query Cache               │
│ - Query result caching                      │
│ - 10-30ms latency                           │
│ - Hit Rate: 50-70%                          │
└────────────────┬────────────────────────────┘
                 ↓ (miss)
┌─────────────────────────────────────────────┐
│ Layer 5: Database (Source of Truth)         │
│ - Disk-based storage                        │
│ - 50-200ms latency                          │
│ - Always hit (100%)                         │
└─────────────────────────────────────────────┘

Cumulative Hit Rate:
Layer 1: 40% of requests (instant)
Layer 2: 45% of requests (fast)
Layer 3: 12% of requests (very fast)
Layer 4: 2% of requests (acceptable)
Layer 5: 1% of requests (slow)
Average Latency: ~15ms (vs 100ms without caching)
```

### 2.3 Cache Eviction Policies

```python
# Example: LRU (Least Recently Used) Cache Implementation

class LRUCache:
    def __init__(self, capacity: int):
        """
        Evicts least recently used items when capacity is reached
        """
        self.capacity = capacity
        self.cache = {}  # key -> value
        self.order = []  # tracks access order
    
    def get(self, key: str):
        """
        Get value and mark as recently used
        """
        if key not in self.cache:
            return None
        
        # Move to end (most recent)
        self.order.remove(key)
        self.order.append(key)
        
        return self.cache[key]
    
    def put(self, key: str, value):
        """
        Put value in cache, evict LRU if needed
        """
        # Update existing
        if key in self.cache:
            self.order.remove(key)
            self.order.append(key)
            self.cache[key] = value
            return
        
        # Evict if at capacity
        if len(self.cache) >= self.capacity:
            lru_key = self.order.pop(0)  # Remove oldest
            del self.cache[lru_key]
            print(f"Evicted: {lru_key} (LRU)")
        
        # Add new entry
        self.cache[key] = value
        self.order.append(key)

# Usage Example
cache = LRUCache(capacity=3)

cache.put("user:1", {"name": "Alice"})
cache.put("user:2", {"name": "Bob"})
cache.put("user:3", {"name": "Charlie"})

# Access user:1 (makes it most recent)
cache.get("user:1")

# Add user:4 (will evict user:2, the LRU)
cache.put("user:4", {"name": "David"})
# Output: Evicted: user:2 (LRU)

# Cache state: user:3, user:1, user:4
```

**Eviction Policy Comparison:**

```
1. LRU (Least Recently Used):
   - Evicts oldest accessed item
   - Good for: Temporal locality patterns
   - Hit Rate: High for recent access patterns
   - Example: User session data

2. LFU (Least Frequently Used):
   - Evicts least accessed item (by count)
   - Good for: Popular content caching
   - Hit Rate: High for hot items
   - Example: Trending videos

3. FIFO (First In First Out):
   - Evicts oldest inserted item
   - Good for: Simple time-based expiry
   - Hit Rate: Lower than LRU/LFU
   - Example: Log rotation

4. Random:
   - Evicts random item
   - Good for: When no pattern exists
   - Hit Rate: Unpredictable
   - Example: Simple implementation

5. TTL (Time To Live):
   - Evicts after fixed time
   - Good for: Time-sensitive data
   - Hit Rate: Depends on TTL choice
   - Example: API rate limits

Recommendation:
- General purpose: LRU (best balance)
- Hot content: LFU
- Simple: TTL with FIFO
```

---

## 3. Caching Strategies with Code Examples

### 3.1 Cache-Aside (Lazy Loading)

**Pattern:**
```
Read Flow:
1. Application checks cache
2. If HIT: Return cached data
3. If MISS: Query database
4. Store result in cache
5. Return data

Write Flow:
1. Application writes to database
2. Cache is NOT updated (invalidated or ignored)
3. Next read will cache new value
```

**Implementation:**

```python
import redis
import mysql.connector
import json
from typing import Optional, Dict, Any

class CacheAsidePattern:
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.db = mysql.connector.connect(
            host='localhost',
            user='app_user',
            password='password',
            database='app_db'
        )
    
    def get_product(self, product_id: int) -> Optional[Dict[str, Any]]:
        """
        Cache-Aside Read Pattern
        """
        cache_key = f"product:{product_id}"
        
        # Step 1: Check cache
        cached_data = self.cache.get(cache_key)
        
        if cached_data:
            # Cache HIT
            print(f"✓ Cache HIT for product {product_id}")
            return json.loads(cached_data)
        
        # Step 2: Cache MISS - Query database
        print(f"✗ Cache MISS for product {product_id}")
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            "SELECT * FROM products WHERE id = %s",
            (product_id,)
        )
        product = cursor.fetchone()
        cursor.close()
        
        if not product:
            return None
        
        # Step 3: Store in cache (10 minutes TTL)
        self.cache.setex(
            cache_key,
            600,  # 10 minutes
            json.dumps(product)
        )
        
        return product
    
    def update_product(self, product_id: int, data: Dict[str, Any]):
        """
        Cache-Aside Write Pattern (Invalidation)
        """
        cache_key = f"product:{product_id}"
        
        # Step 1: Update database
        cursor = self.db.cursor()
        cursor.execute(
            """
            UPDATE products 
            SET name = %s, price = %s, stock = %s
            WHERE id = %s
            """,
            (data['name'], data['price'], data['stock'], product_id)
        )
        self.db.commit()
        cursor.close()
        
        # Step 2: Invalidate cache (delete)
        self.cache.delete(cache_key)
        print(f"✓ Cache invalidated for product {product_id}")
        
        # Next read will fetch fresh data from DB

# Usage Example
cache_service = CacheAsidePattern()

# First request: Cache MISS, reads from DB
product = cache_service.get_product(1001)
# Output: ✗ Cache MISS for product 1001
# Query: SELECT * FROM products WHERE id = 1001 (100ms)

# Second request: Cache HIT, reads from cache
product = cache_service.get_product(1001)
# Output: ✓ Cache HIT for product 1001
# Time: 2ms (50x faster!)

# Update product
cache_service.update_product(1001, {
    'name': 'Updated Product',
    'price': 29.99,
    'stock': 100
})
# Output: ✓ Cache invalidated for product 1001

# Next request: Cache MISS again (reads fresh data)
product = cache_service.get_product(1001)
# Output: ✗ Cache MISS for product 1001
```

**When to Use:**
```
✓ Read-heavy workloads (90%+ reads)
✓ Data updated infrequently
✓ Acceptable to have slightly stale data
✓ Want simplicity (easiest to implement)

✗ Write-heavy workloads
✗ Strict consistency requirements
✗ Complex invalidation patterns
```

### 3.2 Read-Through Cache

**Pattern:**
```
Read Flow:
1. Application asks cache for data
2. Cache checks if it has data
3. If HIT: Cache returns data
4. If MISS: Cache queries database, stores result, returns data
5. Application doesn't know if it was hit or miss

Key Difference from Cache-Aside:
- Cache library handles database interaction
- Application only talks to cache
```

**Implementation:**

```python
import redis
import mysql.connector
import json
from functools import wraps
from typing import Callable, Any

class ReadThroughCache:
    """
    Read-Through Cache Implementation
    The cache automatically loads data from database on miss
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.db = mysql.connector.connect(
            host='localhost',
            user='app_user',
            password='password',
            database='app_db'
        )
    
    def cache_decorator(self, ttl: int = 600):
        """
        Decorator that implements read-through pattern
        """
        def decorator(func: Callable) -> Callable:
            @wraps(func)
            def wrapper(*args, **kwargs) -> Any:
                # Generate cache key from function and arguments
                cache_key = f"{func.__name__}:{str(args)}:{str(kwargs)}"
                
                # Try cache first
                cached_result = self.cache.get(cache_key)
                if cached_result:
                    print(f"✓ Cache HIT: {cache_key}")
                    return json.loads(cached_result)
                
                # Cache miss: Execute function (database query)
                print(f"✗ Cache MISS: {cache_key}")
                result = func(*args, **kwargs)
                
                # Store in cache
                if result is not None:
                    self.cache.setex(cache_key, ttl, json.dumps(result))
                    print(f"✓ Cached: {cache_key}")
                
                return result
            
            return wrapper
        return decorator

# Database access layer with read-through caching
class ProductRepository:
    def __init__(self, cache: ReadThroughCache):
        self.cache = cache
        self.db = cache.db
    
    @cache.cache_decorator(ttl=600)  # Cache for 10 minutes
    def get_product_by_id(self, product_id: int):
        """
        Automatically cached by decorator
        """
        cursor = self.db.cursor(dictionary=True)
        cursor.execute("SELECT * FROM products WHERE id = %s", (product_id,))
        product = cursor.fetchone()
        cursor.close()
        return product
    
    @cache.cache_decorator(ttl=300)  # Cache for 5 minutes
    def get_products_by_category(self, category: str):
        """
        Automatically cached by decorator
        """
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            "SELECT * FROM products WHERE category = %s LIMIT 20",
            (category,)
        )
        products = cursor.fetchall()
        cursor.close()
        return products
    
    @cache.cache_decorator(ttl=3600)  # Cache for 1 hour
    def get_product_reviews(self, product_id: int):
        """
        Automatically cached by decorator
        """
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            """
            SELECT r.*, u.name as user_name
            FROM reviews r
            JOIN users u ON r.user_id = u.id
            WHERE r.product_id = %s
            ORDER BY r.created_at DESC
            LIMIT 10
            """,
            (product_id,)
        )
        reviews = cursor.fetchall()
        cursor.close()
        return reviews

# Usage Example
cache = ReadThroughCache()
repo = ProductRepository(cache)

# First call: Cache MISS, queries database
product = repo.get_product_by_id(1001)
# Output:
# ✗ Cache MISS: get_product_by_id:(1001,):{}
# ✓ Cached: get_product_by_id:(1001,):{}

# Second call: Cache HIT, returns from cache
product = repo.get_product_by_id(1001)
# Output:
# ✓ Cache HIT: get_product_by_id:(1001,):{}

# Different product: Cache MISS
product2 = repo.get_product_by_id(1002)
# Output:
# ✗ Cache MISS: get_product_by_id:(1002,):{}
# ✓ Cached: get_product_by_id:(1002,):{}

# Category search: Cache MISS first time
products = repo.get_products_by_category("electronics")
# Output:
# ✗ Cache MISS: get_products_by_category:('electronics',):{}
# ✓ Cached: get_products_by_category:('electronics',):{}

# Same category: Cache HIT
products = repo.get_products_by_category("electronics")
# Output:
# ✓ Cache HIT: get_products_by_category:('electronics',):{}
```

**When to Use:**
```
✓ Want to centralize caching logic
✓ Multiple applications accessing same cache
✓ Want cache library to handle DB interaction
✓ Complex query caching

✗ Need fine-grained control over caching
✗ Custom invalidation logic required
```

### 3.3 Write-Through Cache

**Pattern:**
```
Write Flow:
1. Application writes data
2. Data written to cache first
3. Cache synchronously writes to database
4. Both cache and database updated together
5. Write confirmed only after both succeed

Read Flow:
1. Application reads from cache
2. Always up-to-date (just written)
```

**Implementation:**

```python
import redis
import mysql.connector
import json
from typing import Dict, Any
from contextlib import contextmanager

class WriteThroughCache:
    """
    Write-Through Cache Implementation
    Ensures cache and database are always in sync
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.db = mysql.connector.connect(
            host='localhost',
            user='app_user',
            password='password',
            database='app_db'
        )
    
    @contextmanager
    def transaction(self):
        """
        Ensures atomicity: Both cache and DB updated or neither
        """
        try:
            cursor = self.db.cursor()
            yield cursor
            self.db.commit()
        except Exception as e:
            self.db.rollback()
            raise e
        finally:
            cursor.close()
    
    def create_user(self, user_id: int, user_data: Dict[str, Any]):
        """
        Write-Through: Write to both cache and database
        """
        cache_key = f"user:{user_id}"
        
        try:
            # Step 1: Write to database
            with self.transaction() as cursor:
                cursor.execute(
                    """
                    INSERT INTO users (id, name, email, created_at)
                    VALUES (%s, %s, %s, NOW())
                    """,
                    (user_id, user_data['name'], user_data['email'])
                )
                print(f"✓ Database: User {user_id} created")
            
            # Step 2: Write to cache (after successful DB write)
            self.cache.setex(
                cache_key,
                3600,  # 1 hour TTL
                json.dumps(user_data)
            )
            print(f"✓ Cache: User {user_id} cached")
            
            return {"success": True, "user_id": user_id}
            
        except Exception as e:
            print(f"✗ Error: {e}")
            # Rollback already happened in transaction context
            # Don't cache if DB write failed
            return {"success": False, "error": str(e)}
    
    def update_user(self, user_id: int, updates: Dict[str, Any]):
        """
        Write-Through: Update both cache and database
        """
        cache_key = f"user:{user_id}"
        
        try:
            # Step 1: Update database
            with self.transaction() as cursor:
                set_clause = ", ".join([f"{k} = %s" for k in updates.keys()])
                values = list(updates.values()) + [user_id]
                
                cursor.execute(
                    f"UPDATE users SET {set_clause} WHERE id = %s",
                    values
                )
                
                if cursor.rowcount == 0:
                    raise ValueError(f"User {user_id} not found")
                
                print(f"✓ Database: User {user_id} updated")
            
            # Step 2: Update cache (get full user data first)
            cursor = self.db.cursor(dictionary=True)
            cursor.execute("SELECT * FROM users WHERE id = %s", (user_id,))
            user_data = cursor.fetchone()
            cursor.close()
            
            if user_data:
                self.cache.setex(
                    cache_key,
                    3600,
                    json.dumps(user_data)
                )
                print(f"✓ Cache: User {user_id} updated")
            
            return {"success": True}
            
        except Exception as e:
            print(f"✗ Error: {e}")
            return {"success": False, "error": str(e)}
    
    def get_user(self, user_id: int) -> Dict[str, Any]:
        """
        Read from cache (always up-to-date due to write-through)
        """
        cache_key = f"user:{user_id}"
        
        # Try cache first
        cached_data = self.cache.get(cache_key)
        if cached_data:
            print(f"✓ Cache HIT: User {user_id}")
            return json.loads(cached_data)
        
        # Cache miss: Read from database
        print(f"✗ Cache MISS: User {user_id}")
        cursor = self.db.cursor(dictionary=True)
        cursor.execute("SELECT * FROM users WHERE id = %s", (user_id,))
        user_data = cursor.fetchone()
        cursor.close()
        
        if user_data:
            # Populate cache
            self.cache.setex(cache_key, 3600, json.dumps(user_data))
        
        return user_data

# Usage Example
cache_service = WriteThroughCache()

# Create user: Writes to both DB and cache
result = cache_service.create_user(5001, {
    'name': 'Alice Smith',
    'email': 'alice@example.com'
})
# Output:
# ✓ Database: User 5001 created
# ✓ Cache: User 5001 cached

# Read user: Cache HIT (just written)
user = cache_service.get_user(5001)
# Output:
# ✓ Cache HIT: User 5001
# Latency: 2ms

# Update user: Updates both DB and cache
result = cache_service.update_user(5001, {
    'name': 'Alice Johnson',
    'email': 'alice.j@example.com'
})
# Output:
# ✓ Database: User 5001 updated
# ✓ Cache: User 5001 updated

# Read again: Cache HIT with updated data
user = cache_service.get_user(5001)
# Output:
# ✓ Cache HIT: User 5001
# Data: {'name': 'Alice Johnson', 'email': 'alice.j@example.com'}
```

**Performance Characteristics:**

```
Write Performance:
  Without Write-Through: 50ms (DB only)
  With Write-Through: 52ms (DB + cache)
  Overhead: 2ms (4%)
  
Read Performance:
  Without Cache: 50ms (DB query)
  With Cache (HIT): 2ms
  Improvement: 25x faster

Trade-offs:
✓ Strong consistency (cache always up-to-date)
✓ Simple read path (always read from cache)
✓ No cache invalidation complexity

✗ Slightly slower writes (2-5ms overhead)
✗ Writes data that might never be read
✗ More complex error handling
```

**When to Use:**
```
✓ Strong consistency required
✓ Read-heavy after write (90%+ reads)
✓ Simple consistency model preferred
✓ Can afford slight write overhead

✗ Write-heavy workloads
✗ Infrequently read data
✗ Need maximum write performance
```

### 3.4 Write-Back (Write-Behind) Cache

**Pattern:**
```
Write Flow:
1. Application writes to cache only
2. Write confirmed immediately (fast!)
3. Cache asynchronously writes to database (later)
4. Batch multiple writes for efficiency

Read Flow:
1. Application reads from cache
2. Always has latest data (just written)

Key Benefit: Ultra-fast writes!
Key Risk: Data loss if cache fails before DB write
```

**Implementation:**

```python
import redis
import mysql.connector
import json
import threading
import time
import queue
from typing import Dict, Any, List

class WriteBackCache:
    """
    Write-Back Cache Implementation
    Writes to cache immediately, database asynchronously
    """
    def __init__(self, batch_size: int = 10, batch_interval: int = 5):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.db = mysql.connector.connect(
            host='localhost',
            user='app_user',
            password='password',
            database='app_db'
        )
        
        # Write queue for async database updates
        self.write_queue = queue.Queue()
        self.batch_size = batch_size
        self.batch_interval = batch_interval
        
        # Start background writer thread
        self.writer_thread = threading.Thread(target=self._background_writer, daemon=True)
        self.writer_thread.start()
        print("✓ Background writer thread started")
    
    def _background_writer(self):
        """
        Background thread that batches writes to database
        """
        batch = []
        last_flush = time.time()
        
        while True:
            try:
                # Get write from queue (with timeout)
                write_op = self.write_queue.get(timeout=1)
                batch.append(write_op)
                
                # Flush batch if size or time threshold reached
                should_flush = (
                    len(batch) >= self.batch_size or
                    time.time() - last_flush >= self.batch_interval
                )
                
                if should_flush:
                    self._flush_batch(batch)
                    batch = []
                    last_flush = time.time()
                    
            except queue.Empty:
                # Timeout: Flush any pending writes
                if batch:
                    self._flush_batch(batch)
                    batch = []
                    last_flush = time.time()
            except Exception as e:
                print(f"✗ Background writer error: {e}")
    
    def _flush_batch(self, batch: List[Dict[str, Any]]):
        """
        Write batch of operations to database
        """
        if not batch:
            return
        
        try:
            cursor = self.db.cursor()
            
            for operation in batch:
                op_type = operation['type']
                
                if op_type == 'INSERT':
                    cursor.execute(
                        """
                        INSERT INTO products (id, name, price, stock)
                        VALUES (%s, %s, %s, %s)
                        ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        price = VALUES(price),
                        stock = VALUES(stock)
                        """,
                        (
                            operation['id'],
                            operation['data']['name'],
                            operation['data']['price'],
                            operation['data']['stock']
                        )
                    )
                elif op_type == 'UPDATE':
                    set_clause = ", ".join([f"{k} = %s" for k in operation['updates'].keys()])
                    values = list(operation['updates'].values()) + [operation['id']]
                    cursor.execute(
                        f"UPDATE products SET {set_clause} WHERE id = %s",
                        values
                    )
                elif op_type == 'DELETE':
                    cursor.execute(
                        "DELETE FROM products WHERE id = %s",
                        (operation['id'],)
                    )
            
            self.db.commit()
            cursor.close()
            print(f"✓ Flushed batch of {len(batch)} writes to database")
            
        except Exception as e:
            print(f"✗ Batch flush error: {e}")
            self.db.rollback()
    
    def create_product(self, product_id: int, product_data: Dict[str, Any]):
        """
        Write-Back: Write to cache immediately, database later
        """
        cache_key = f"product:{product_id}"
        
        # Step 1: Write to cache immediately (FAST!)
        self.cache.setex(
            cache_key,
            3600,  # 1 hour TTL
            json.dumps(product_data)
        )
        print(f"✓ Cache: Product {product_id} written (2ms)")
        
        # Step 2: Queue for async database write
        self.write_queue.put({
            'type': 'INSERT',
            'id': product_id,
            'data': product_data
        })
        print(f"✓ Queued for database write")
        
        return {"success": True, "product_id": product_id}
    
    def update_product(self, product_id: int, updates: Dict[str, Any]):
        """
        Write-Back: Update cache immediately, database later
        """
        cache_key = f"product:{product_id}"
        
        # Get current data
        cached_data = self.cache.get(cache_key)
        if cached_data:
            product_data = json.loads(cached_data)
            product_data.update(updates)
        else:
            product_data = updates
        
        # Update cache immediately
        self.cache.setex(cache_key, 3600, json.dumps(product_data))
        print(f"✓ Cache: Product {product_id} updated (2ms)")
        
        # Queue for async database write
        self.write_queue.put({
            'type': 'UPDATE',
            'id': product_id,
            'updates': updates
        })
        print(f"✓ Queued for database write")
        
        return {"success": True}
    
    def get_product(self, product_id: int) -> Dict[str, Any]:
        """
        Read from cache (always has latest writes)
        """
        cache_key = f"product:{product_id}"
        
        cached_data = self.cache.get(cache_key)
        if cached_data:
            print(f"✓ Cache HIT: Product {product_id}")
            return json.loads(cached_data)
        
        print(f"✗ Cache MISS: Product {product_id}")
        return None

# Usage Example
cache_service = WriteBackCache(batch_size=10, batch_interval=5)

# Create products: Ultra-fast writes!
for i in range(100):
    result = cache_service.create_product(2000 + i, {
        'name': f'Product {i}',
        'price': 19.99 + i,
        'stock': 100
    })
    # Output: ✓ Cache: Product 200X written (2ms)
    # Each write takes only 2ms!

# Reads are instant (cache hits)
product = cache_service.get_product(2001)
# Output: ✓ Cache HIT: Product 2001
# Data is immediately available

# Background: After 5 seconds or 10 writes, batch is flushed
# Output: ✓ Flushed batch of 10 writes to database
```

**Performance Characteristics:**

```
Write Performance:
  Without Write-Back: 50ms per write (DB latency)
  With Write-Back: 2ms per write (cache only)
  Improvement: 25x faster writes!
  
Throughput:
  Without: 20 writes/second
  With Write-Back: 500 writes/second (batched)
  Improvement: 25x throughput!

Data Durability:
  Risk: Data loss if cache crashes before DB write
  Mitigation: 
    - Persist Redis to disk (AOF/RDB)
    - Replicate to multiple cache nodes
    - Short flush intervals (5 seconds)
    - Use only for non-critical data
```

**When to Use:**

```
✓ Write-heavy workloads
✓ Can tolerate data loss (seconds)
✓ Need maximum write performance
✓ Non-critical data (likes, views, clicks)

✗ Financial transactions
✗ User-generated content (critical)
✗ Strict consistency requirements
✗ Can't risk any data loss

Examples:
✓ Page view counters
✓ Like counts
✓ Analytics events
✓ Temporary session data
✗ Payment processing
✗ User profile updates
✗ Order creation
```

---

## 4. Cache Invalidation Patterns

### 4.1 Time-Based Expiration (TTL)

**Simplest Pattern:**

```python
# Set with TTL
cache.setex("product:123", 600, product_data)  # 10 minutes

# After 600 seconds, key automatically deleted
# Next read will be cache miss

# Pros:
# ✓ Simple to implement
# ✓ Predictable memory usage
# ✓ No complex invalidation logic

# Cons:
# ✗ May serve stale data until expiry
# ✗ Cache miss storm when TTL expires
# ✗ Doesn't respond to data changes
```

**TTL Selection Guide:**

```python
def get_ttl_for_data_type(data_type: str) -> int:
    """
    Choose TTL based on data characteristics
    """
    ttl_map = {
        # High-frequency changes
        'stock_price': 1,           # 1 second (real-time)
        'inventory': 30,            # 30 seconds
        'trending_topics': 60,      # 1 minute
        
        # Medium-frequency changes
        'product_price': 300,       # 5 minutes
        'user_profile': 600,        # 10 minutes
        'search_results': 900,      # 15 minutes
        
        # Low-frequency changes
        'product_details': 3600,    # 1 hour
        'category_list': 7200,      # 2 hours
        'static_content': 86400,    # 24 hours
        
        # Rarely changes
        'product_images': 604800,   # 7 days
        'terms_of_service': 2592000 # 30 days
    }
    
    return ttl_map.get(data_type, 600)  # Default: 10 minutes

# Usage
cache.setex(
    f"product:{product_id}",
    get_ttl_for_data_type('product_details'),
    product_data
)
```

### 4.2 Event-Based Invalidation

**Pattern:**

```python
import redis
from typing import Set

class EventBasedInvalidation:
    """
    Invalidate cache based on data change events
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.pubsub = self.cache.pubsub()
    
    def invalidate_on_update(self, entity_type: str, entity_id: int):
        """
        Invalidate all related cache entries when entity updates
        """
        # Invalidate direct cache entry
        self.cache.delete(f"{entity_type}:{entity_id}")
        
        # Invalidate related caches
        if entity_type == "product":
            # Invalidate product details
            self.cache.delete(f"product:{entity_id}")
            
            # Invalidate product reviews (derived data)
            self.cache.delete(f"product:{entity_id}:reviews")
            
            # Invalidate category page (product is listed there)
            product = self.get_product_from_db(entity_id)
            if product:
                self.cache.delete(f"category:{product['category']}:products")
            
            # Invalidate search results that might contain this product
            self.invalidate_search_cache()
            
            print(f"✓ Invalidated all caches for product {entity_id}")
        
        # Publish event for other services
        self.cache.publish(
            f"{entity_type}:updated",
            entity_id
        )
    
    def invalidate_search_cache(self):
        """
        Invalidate all search result caches
        """
        # Use key pattern matching
        for key in self.cache.scan_iter("search:*"):
            self.cache.delete(key)
        print("✓ Invalidated all search caches")
    
    def get_product_from_db(self, product_id: int):
        # Placeholder for DB query
        return {'category': 'electronics'}

# Usage Example
invalidator = EventBasedInvalidation()

# When product updated
def update_product(product_id, new_data):
    # Update database
    db.update_product(product_id, new_data)
    
    # Invalidate all related caches
    invalidator.invalidate_on_update('product', product_id)

update_product(123, {'price': 29.99})
# Output:
# ✓ Invalidated all caches for product 123
```

### 4.3 Cache Stampede Prevention

**Problem:**

```
Cache Stampede (Thundering Herd):
1. Popular cache entry expires
2. 1000 requests arrive simultaneously
3. All 1000 requests see cache miss
4. All 1000 query database simultaneously
5. Database overloaded!

Example:
  Homepage cache expires at 12:00 PM
  1000 concurrent users hit site at 12:00 PM
  All query database simultaneously
  Database crashes!
```

**Solution: Locking Pattern**

```python
import redis
import time
from typing import Optional, Callable, Any

class StampedePrevent<br/>ion:
    """
    Prevents cache stampede using distributed locking
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
    
    def get_with_lock(
        self,
        cache_key: str,
        fetch_function: Callable,
        ttl: int = 600,
        lock_timeout: int = 10
    ) -> Any:
        """
        Get from cache with stampede prevention
        """
        # Try cache first
        cached_value = self.cache.get(cache_key)
        if cached_value:
            print(f"✓ Cache HIT: {cache_key}")
            return json.loads(cached_value)
        
        # Cache miss: Try to acquire lock
        lock_key = f"lock:{cache_key}"
        lock_acquired = self.cache.set(
            lock_key,
            "1",
            nx=True,  # Only set if doesn't exist
            ex=lock_timeout  # Lock expires after 10 seconds
        )
        
        if lock_acquired:
            print(f"✓ Lock acquired for {cache_key}")
            try:
                # This thread won the race - fetch from database
                value = fetch_function()
                
                # Cache the result
                self.cache.setex(cache_key, ttl, json.dumps(value))
                print(f"✓ Cached {cache_key}")
                
                return value
            finally:
                # Release lock
                self.cache.delete(lock_key)
                print(f"✓ Lock released for {cache_key}")
        else:
            print(f"⏳ Waiting for lock: {cache_key}")
            # Another thread is fetching - wait and retry
            time.sleep(0.1)  # Wait 100ms
            
            # Check cache again (should be populated now)
            cached_value = self.cache.get(cache_key)
            if cached_value:
                print(f"✓ Cache HIT after wait: {cache_key}")
                return json.loads(cached_value)
            
            # Still not cached? Recursive retry
            return self.get_with_lock(cache_key, fetch_function, ttl, lock_timeout)

# Usage Example
cache = StampedePrevention()

def expensive_database_query():
    """Simulates expensive DB query"""
    print("  ⚠️ Executing expensive database query (100ms)")
    time.sleep(0.1)  # Simulate 100ms query
    return {"data": "expensive result"}

# Simulate 100 concurrent requests for same data
import concurrent.futures

def worker(request_id):
    result = cache.get_with_lock(
        "homepage:data",
        expensive_database_query,
        ttl=600
    )
    return f"Request {request_id} completed"

with concurrent.futures.ThreadPoolExecutor(max_workers=100) as executor:
    futures = [executor.submit(worker, i) for i in range(100)]
    results = [f.result() for f in futures]

# Output:
# ✓ Lock acquired for homepage:data
#   ⚠️ Executing expensive database query (100ms) <- Only ONE query!
# ✓ Cached homepage:data
# ✓ Lock released for homepage:data
# ⏳ Waiting for lock: homepage:data (99 times)
# ✓ Cache HIT after wait: homepage:data (99 times)

# Result: Only 1 database query instead of 100!
```

### 4.4 Probabilistic Early Expiration

**Problem:**

```
All caches expire at same time:
- Set TTL to 3600 seconds
- 1000 items cached at 12:00 PM
- All expire at 1:00 PM
- Cache miss storm at 1:00 PM
```

**Solution:**

```python
import random
import time

class ProbabilisticCache:
    """
    Adds randomization to TTL to prevent synchronized expiration
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
    
    def set_with_jitter(
        self,
        key: str,
        value: Any,
        base_ttl: int,
        jitter_percent: float = 0.1
    ):
        """
        Set cache with randomized TTL to prevent thundering herd
        """
        # Add random jitter (±10% by default)
        jitter = int(base_ttl * jitter_percent * random.uniform(-1, 1))
        actual_ttl = base_ttl + jitter
        
        self.cache.setex(key, actual_ttl, json.dumps(value))
        print(f"✓ Cached {key} with TTL {actual_ttl}s (base: {base_ttl}s, jitter: {jitter}s)")
    
    def get_with_early_refresh(
        self,
        key: str,
        fetch_function: Callable,
        base_ttl: int = 600,
        beta: float = 1.0
    ) -> Any:
        """
        Probabilistically refresh before expiration
        Based on XFetch algorithm: https://cseweb.ucsd.edu/~avattani/papers/cache_stampede.pdf
        """
        # Get value and TTL
        value = self.cache.get(key)
        ttl = self.cache.ttl(key)
        
        if value and ttl > 0:
            # Calculate refresh probability
            # Higher probability as TTL decreases
            delta = base_ttl - ttl
            refresh_probability = delta * beta * random.random() / base_ttl
            
            if refresh_probability > 0.5:
                print(f"⚡ Early refresh triggered for {key} (TTL: {ttl}s, prob: {refresh_probability:.2f})")
                # Refresh in background
                new_value = fetch_function()
                self.set_with_jitter(key, new_value, base_ttl)
                return new_value
            
            return json.loads(value)
        
        # Cache miss or expired
        value = fetch_function()
        self.set_with_jitter(key, value, base_ttl)
        return value

# Usage Example
cache = ProbabilisticCache()

# Cache 1000 products with jitter
for i in range(1000):
    cache.set_with_jitter(
        f"product:{i}",
        {"name": f"Product {i}", "price": 19.99},
        base_ttl=3600,
        jitter_percent=0.1  # ±10% jitter
    )

# Output shows varied TTLs:
# ✓ Cached product:0 with TTL 3456s (base: 3600s, jitter: -144s)
# ✓ Cached product:1 with TTL 3712s (base: 3600s, jitter: +112s)
# ✓ Cached product:2 with TTL 3598s (base: 3600s, jitter: -2s)
# ...

# Expirations spread over 3456-3712 seconds (7.2 minute window)
# No stampede!
```

---

## 5. Real-World Implementation Examples

### 5.1 E-Commerce Product Catalog

**Complete Implementation:**

```python
import redis
import mysql.connector
import json
from typing import List, Dict, Any, Optional
from datetime import datetime

class ProductCatalogCache:
    """
    Real-world e-commerce product caching system
    """
    def __init__(self):
        self.cache = redis.Redis(
            host='localhost',
            port=6379,
            decode_responses=True,
            socket_connect_timeout=2,
            socket_timeout=2
        )
        self.db = mysql.connector.connect(
            host='localhost',
            user='ecommerce_user',
            password='password',
            database='ecommerce_db',
            pool_size=10
        )
    
    def get_product(self, product_id: int) -> Optional[Dict[str, Any]]:
        """
        Get product with multi-layer caching strategy
        """
        cache_key = f"product:{product_id}"
        
        try:
            # Layer 1: Try cache
            cached = self.cache.get(cache_key)
            if cached:
                product = json.loads(cached)
                product['cache_hit'] = True
                product['cached_at'] = cached.get('cached_at')
                return product
        except redis.ConnectionError:
            # Cache down - fallback to database
            print(f"⚠️ Cache unavailable, falling back to database")
        
        # Layer 2: Query database
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            """
            SELECT 
                p.*,
                c.name as category_name,
                b.name as brand_name,
                (SELECT AVG(rating) FROM reviews WHERE product_id = p.id) as avg_rating,
                (SELECT COUNT(*) FROM reviews WHERE product_id = p.id) as review_count
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN brands b ON p.brand_id = b.id
            WHERE p.id = %s
            """,
            (product_id,)
        )
        product = cursor.fetchone()
        cursor.close()
        
        if not product:
            return None
        
        # Enrich with additional data
        product['images'] = self.get_product_images(product_id)
        product['cache_hit'] = False
        product['cached_at'] = datetime.now().isoformat()
        
        # Cache for future requests
        try:
            # Different TTLs based on product status
            ttl = self.get_ttl_for_product(product)
            self.cache.setex(cache_key, ttl, json.dumps(product))
        except redis.ConnectionError:
            pass  # Cache unavailable, continue without caching
        
        return product
    
    def get_ttl_for_product(self, product: Dict[str, Any]) -> int:
        """
        Dynamic TTL based on product characteristics
        """
        # Hot products: Longer TTL (reduce DB load)
        if product.get('view_count', 0) > 10000:
            return 3600  # 1 hour
        
        # Low stock: Shorter TTL (fresher inventory data)
        if product.get('stock', 0) < 10:
            return 300  # 5 minutes
        
        # Price recently changed: Shorter TTL
        if product.get('price_updated_at'):
            last_update = datetime.fromisoformat(product['price_updated_at'])
            age = (datetime.now() - last_update).total_seconds()
            if age < 3600:  # Updated in last hour
                return 600  # 10 minutes
        
        # Default
        return 1800  # 30 minutes
    
    def get_product_images(self, product_id: int) -> List[str]:
        """
        Get product images with separate caching
        """
        cache_key = f"product:{product_id}:images"
        
        # Try cache (longer TTL for images - they rarely change)
        cached = self.cache.get(cache_key)
        if cached:
            return json.loads(cached)
        
        # Query database
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            "SELECT url FROM product_images WHERE product_id = %s ORDER BY position",
            (product_id,)
        )
        images = [row['url'] for row in cursor.fetchall()]
        cursor.close()
        
        # Cache for 24 hours (images rarely change)
        self.cache.setex(cache_key, 86400, json.dumps(images))
        
        return images
    
    def update_product_price(self, product_id: int, new_price: float):
        """
        Update price with cache invalidation
        """
        # Update database
        cursor = self.db.cursor()
        cursor.execute(
            """
            UPDATE products 
            SET price = %s, price_updated_at = NOW()
            WHERE id = %s
            """,
            (new_price, product_id)
        )
        self.db.commit()
        cursor.close()
        
        # Invalidate cache
        cache_key = f"product:{product_id}"
        self.cache.delete(cache_key)
        
        # Invalidate related caches
        self._invalidate_related_caches(product_id)
        
        print(f"✓ Updated price for product {product_id} to ${new_price}")
    
    def _invalidate_related_caches(self, product_id: int):
        """
        Invalidate all caches related to a product
        """
        # Get product to find category
        cursor = self.db.cursor(dictionary=True)
        cursor.execute("SELECT category_id FROM products WHERE id = %s", (product_id,))
        result = cursor.fetchone()
        cursor.close()
        
        if result:
            category_id = result['category_id']
            # Invalidate category page
            self.cache.delete(f"category:{category_id}:products")
            
            # Invalidate search results (simple approach - delete all)
            for key in self.cache.scan_iter("search:*"):
                self.cache.delete(key)
    
    def search_products(
        self,
        query: str,
        category_id: Optional[int] = None,
        page: int = 1,
        page_size: int = 20
    ) -> Dict[str, Any]:
        """
        Search products with caching
        """
        # Generate cache key from search parameters
        cache_key = f"search:{query}:{category_id}:{page}:{page_size}"
        
        # Try cache (5 minutes TTL)
        cached = self.cache.get(cache_key)
        if cached:
            results = json.loads(cached)
            results['from_cache'] = True
            return results
        
        # Build query
        sql = """
            SELECT p.*, c.name as category_name
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.name LIKE %s
        """
        params = [f"%{query}%"]
        
        if category_id:
            sql += " AND p.category_id = %s"
            params.append(category_id)
        
        sql += " ORDER BY p.view_count DESC LIMIT %s OFFSET %s"
        params.extend([page_size, (page - 1) * page_size])
        
        # Execute query
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(sql, params)
        products = cursor.fetchall()
        
        # Get total count
        count_sql = "SELECT COUNT(*) as total FROM products WHERE name LIKE %s"
        count_params = [f"%{query}%"]
        if category_id:
            count_sql += " AND category_id = %s"
            count_params.append(category_id)
        
        cursor.execute(count_sql, count_params)
        total = cursor.fetchone()['total']
        cursor.close()
        
        results = {
            'products': products,
            'total': total,
            'page': page,
            'page_size': page_size,
            'from_cache': False
        }
        
        # Cache results (5 minutes)
        self.cache.setex(cache_key, 300, json.dumps(results))
        
        return results

# Usage Example
catalog = ProductCatalogCache()

# Get product (cache miss first time)
product = catalog.get_product(12345)
# Query time: 50ms (database)
# Output: {'id': 12345, 'name': 'iPhone 15', 'cache_hit': False}

# Get same product (cache hit)
product = catalog.get_product(12345)
# Query time: 2ms (cache)
# Output: {'id': 12345, 'name': 'iPhone 15', 'cache_hit': True}

# Search products
results = catalog.search_products("iPhone", category_id=1, page=1)
# First search: 80ms (database + join)
# Subsequent searches: 2ms (cache)

# Update price (invalidates cache)
catalog.update_product_price(12345, 999.99)
# Next get_product will be cache miss (fresh data)
```

**Performance Metrics:**

```
Without Caching:
- Product page load: 120ms average
- Search results: 200ms average
- Database queries: 50,000/minute
- Server cost: $5,000/month

With Caching:
- Product page load: 15ms average (8x faster)
- Search results: 25ms average (8x faster)
- Database queries: 5,000/minute (90% reduction)
- Server cost: $1,500/month (70% savings)
- Cache hit rate: 92%

ROI: $3,500/month savings = $42,000/year
```

### 5.2 Social Media News Feed

```python
import redis
import json
from typing import List, Dict, Any
from datetime import datetime, timedelta

class NewsFeedCache:
    """
    Social media news feed with pre-computed caching
    """
    def __init__(self):
        self.cache = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.db = mysql.connector.connect(
            host='localhost',
            user='social_user',
            password='password',
            database='social_db'
        )
    
    def get_user_feed(
        self,
        user_id: int,
        page: int = 1,
        page_size: int = 20
    ) -> List[Dict[str, Any]]:
        """
        Get pre-computed feed for user
        """
        cache_key = f"feed:{user_id}"
        
        # Try to get from sorted set (pre-computed feed)
        start = (page - 1) * page_size
        end = start + page_size - 1
        
        post_ids = self.cache.zrevrange(cache_key, start, end)
        
        if post_ids:
            print(f"✓ Cache HIT: Feed for user {user_id}")
            # Get full post data
            posts = [self.get_post(int(post_id)) for post_id in post_ids]
            return [p for p in posts if p]  # Filter None
        
        print(f"✗ Cache MISS: Generating feed for user {user_id}")
        # Generate feed
        posts = self.generate_feed(user_id)
        
        # Cache for 30 minutes
        self.cache_feed(user_id, posts, ttl=1800)
        
        return posts[:page_size]
    
    def generate_feed(self, user_id: int) -> List[Dict[str, Any]]:
        """
        Generate feed by fetching posts from followed users
        """
        # Get list of users this user follows
        cursor = self.db.cursor(dictionary=True)
        cursor.execute(
            "SELECT followed_id FROM follows WHERE follower_id = %s",
            (user_id,)
        )
        followed_users = [row['followed_id'] for row in cursor.fetchall()]
        
        if not followed_users:
            return []
        
        # Get recent posts from followed users
        placeholders = ','.join(['%s'] * len(followed_users))
        cursor.execute(
            f"""
            SELECT p.*, u.name as author_name, u.avatar as author_avatar
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.user_id IN ({placeholders})
            AND p.created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
            ORDER BY p.created_at DESC
            LIMIT 100
            """,
            followed_users
        )
        posts = cursor.fetchall()
        cursor.close()
        
        # Enrich with engagement data
        for post in posts:
            post['likes_count'] = self.get_post_likes_count(post['id'])
            post['comments_count'] = self.get_post_comments_count(post['id'])
        
        return posts
    
    def cache_feed(self, user_id: int, posts: List[Dict[str, Any]], ttl: int):
        """
        Cache feed as sorted set (sorted by timestamp)
        """
        cache_key = f"feed:{user_id}"
        
        # Delete old feed
        self.cache.delete(cache_key)
        
        # Add posts to sorted set (score = timestamp)
        for post in posts:
            timestamp = post['created_at'].timestamp() if isinstance(post['created_at'], datetime) else 0
            self.cache.zadd(cache_key, {str(post['id']): timestamp})
        
        # Set expiration
        self.cache.expire(cache_key, ttl)
        
        # Also cache individual posts
        for post in posts:
            self.cache_post(post)
        
        print(f"✓ Cached feed for user {user_id} with {len(posts)} posts")
    
    def cache_post(self, post: Dict[str, Any]):
        """
