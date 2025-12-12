"""
Redis Strings - The most basic data type
Strings can store text, numbers, serialized objects, binary data (up to 512MB)

Use Cases:
- Simple key-value storage
- Counters (page views, likes)
- Session tokens
- Cache API responses
"""

import redis
import time
from datetime import timedelta

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

def basic_operations():
    """Basic string operations"""
    print("=" * 60)
    print("1. BASIC STRING OPERATIONS")
    print("=" * 60)
    
    # SET and GET
    r.set('username', 'john_doe')
    username = r.get('username')
    print(f"Username: {username}")
    
    # SET with expiration (EX = seconds)
    r.setex('session_token', 3600, 'abc123xyz')  # Expires in 1 hour
    ttl = r.ttl('session_token')
    print(f"Session token TTL: {ttl} seconds")
    
    # SET NX (only if key doesn't exist) - INTERVIEW TIP: Distributed Locks
    # System Design Use Case: When multiple servers try to process the same task
    # Example: Payment processing - only ONE server should charge the customer
    # SETNX returns True only for the FIRST server, preventing duplicate charges
    result = r.setnx('distributed_lock', 'process_1')
    print(f"Lock acquired: {result}")
    
    # Try to set again - should fail
    # This prevents race conditions in distributed systems
    result = r.setnx('distributed_lock', 'process_2')
    print(f"Second lock attempt: {result}")
    
    # MSET and MGET (multiple keys at once)
    r.mset({
        'user:1:name': 'Alice',
        'user:1:email': 'alice@example.com',
        'user:1:age': '28'
    })
    user_data = r.mget('user:1:name', 'user:1:email', 'user:1:age')
    print(f"User data: {user_data}")
    
    # Check if key exists
    exists = r.exists('username')
    print(f"Username exists: {exists}")
    
    # Delete key
    r.delete('distributed_lock')
    print("Lock deleted")
    
    print()

def counter_operations():
    """Using strings as counters"""
    print("=" * 60)
    print("2. COUNTER OPERATIONS (Atomic)")
    print("=" * 60)
    
    # Initialize counter
    r.set('page_views', 0)
    
    # Increment by 1
    views = r.incr('page_views')
    print(f"Page views after increment: {views}")
    
    # Increment by 10
    views = r.incrby('page_views', 10)
    print(f"Page views after adding 10: {views}")
    
    # Decrement
    views = r.decr('page_views')
    print(f"Page views after decrement: {views}")
    
    # Float operations
    r.set('temperature', 20.5)
    temp = r.incrbyfloat('temperature', 2.3)
    print(f"Temperature after increase: {temp}")
    
    print()

def string_manipulation():
    """String manipulation operations"""
    print("=" * 60)
    print("3. STRING MANIPULATION")
    print("=" * 60)
    
    # APPEND
    r.set('message', 'Hello')
    length = r.append('message', ' World!')
    print(f"Message: {r.get('message')} (length: {length})")
    
    # GET RANGE
    r.set('story', 'The quick brown fox jumps over the lazy dog')
    part = r.getrange('story', 0, 8)  # Get first 9 characters
    print(f"Story excerpt: {part}")
    
    # SET RANGE
    r.setrange('story', 10, 'red')  # Replace 'brown' with 'red'
    print(f"Modified story: {r.get('story')}")
    
    # STRLEN
    length = r.strlen('story')
    print(f"Story length: {length}")
    
    print()

def expiration_and_ttl():
    """Working with expiration and TTL"""
    print("=" * 60)
    print("4. EXPIRATION AND TTL")
    print("=" * 60)
    
    # Set key with expiration
    r.setex('temp_data', 5, 'This will expire in 5 seconds')
    
    # Check TTL
    ttl = r.ttl('temp_data')
    print(f"Time to live: {ttl} seconds")
    
    # Persist (remove expiration)
    r.persist('temp_data')
    ttl = r.ttl('temp_data')
    print(f"TTL after persist: {ttl} (-1 means no expiration)")
    
    # Set expiration on existing key
    r.expire('temp_data', 10)
    ttl = r.ttl('temp_data')
    print(f"New TTL: {ttl} seconds")
    
    # Expire at specific timestamp
    future_time = int(time.time()) + 60  # 1 minute from now
    r.expireat('temp_data', future_time)
    ttl = r.ttl('temp_data')
    print(f"TTL (expire at): {ttl} seconds")
    
    print()

def real_world_examples():
    """Real-world use cases"""
    print("=" * 60)
    print("5. REAL-WORLD USE CASES")
    print("=" * 60)
    
    # Use Case 1: Session Management
    session_id = 'sess_abc123'
    r.setex(f'session:{session_id}', 1800, 'user_id:42')  # 30 min
    print(f"Session stored with 30-minute expiration")
    
    # Use Case 2: Rate Limiting (Simple)
    user_id = 'user_123'
    key = f'rate_limit:{user_id}:requests'
    
    # Check current count
    current = r.get(key)
    if current is None:
        # First request
        r.setex(key, 60, 1)  # 1 request in 60 seconds window
        print("First request - allowed")
    elif int(current) < 10:
        # Under limit
        r.incr(key)
        print(f"Request #{int(current) + 1} - allowed")
    else:
        print("Rate limit exceeded!")
    
    # Use Case 3: Caching API Response
    cache_key = 'api:weather:nyc'
    cached = r.get(cache_key)
    
    if cached:
        print(f"Cache hit: {cached}")
    else:
        # Simulate API call
        api_response = '{"temp": 72, "condition": "sunny"}'
        r.setex(cache_key, 300, api_response)  # Cache for 5 minutes
        print(f"Cache miss - stored: {api_response}")
    
    # Use Case 4: Distributed Lock with Timeout
    lock_key = 'lock:process_payment'
    lock_acquired = r.set(lock_key, 'process_1', nx=True, ex=30)
    
    if lock_acquired:
        print("Lock acquired - processing payment...")
        # Do work here
        time.sleep(1)
        r.delete(lock_key)
        print("Lock released")
    else:
        print("Could not acquire lock - another process is working")
    
    # Use Case 5: Unique Visitor Counter
    visitor_ip = '192.168.1.100'
    visited_today = r.setnx(f'visitor:{visitor_ip}:today', 1)
    
    if visited_today:
        r.incr('unique_visitors:today')
        r.expire(f'visitor:{visitor_ip}:today', 86400)  # 24 hours
        count = r.get('unique_visitors:today')
        print(f"New unique visitor! Total today: {count}")
    else:
        print("Returning visitor")
    
    print()

def connection_management():
    """Connection and error handling"""
    print("=" * 60)
    print("6. CONNECTION MANAGEMENT")
    print("=" * 60)
    
    try:
        # Test connection
        r.ping()
        print("✓ Connected to Redis successfully")
        
        # Get server info
        info = r.info('server')
        print(f"Redis version: {info['redis_version']}")
        print(f"OS: {info['os']}")
        
        # Check memory usage
        memory = r.info('memory')
        print(f"Used memory: {memory['used_memory_human']}")
        
        # Number of keys in current database
        db_size = r.dbsize()
        print(f"Keys in current database: {db_size}")
        
    except redis.ConnectionError:
        print("✗ Could not connect to Redis")
        print("Make sure Redis is running:")
        print("  docker run -d --name redis-learning -p 6379:6379 redis:latest")
    
    print()

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("REDIS STRINGS - COMPREHENSIVE EXAMPLES")
    print("=" * 60 + "\n")
    
    try:
        connection_management()
        basic_operations()
        counter_operations()
        string_manipulation()
        expiration_and_ttl()
        real_world_examples()
        
        print("=" * 60)
        print("✓ All examples completed successfully!")
        print("=" * 60)
        
    except redis.ConnectionError:
        print("\n⚠ Please start Redis server before running this script:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
    except Exception as e:
        print(f"\n✗ Error: {e}")
