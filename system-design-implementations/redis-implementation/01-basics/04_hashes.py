"""
Redis Hashes - Maps between string fields and string values
Perfect for representing objects (like JSON)

Use Cases:
- User profiles
- Session data
- Configuration settings
- Product catalogs
- Shopping carts
"""

import redis
import json

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

def basic_hash_operations():
    """Basic hash operations"""
    print("=" * 60)
    print("1. BASIC HASH OPERATIONS")
    print("=" * 60)
    
    # HSET - Set field value
    r.hset('user:1000', 'name', 'John Doe')
    r.hset('user:1000', 'email', 'john@example.com')
    r.hset('user:1000', 'age', '30')
    
    # HGET - Get field value
    name = r.hget('user:1000', 'name')
    print(f"Name: {name}")
    
    # HMSET - Set multiple fields (deprecated, use hset with mapping)
    r.hset('user:1000', mapping={
        'city': 'New York',
        'country': 'USA',
        'premium': 'true'
    })
    
    # HGETALL - Get all fields and values
    user = r.hgetall('user:1000')
    print(f"User data: {user}")
    
    # HMGET - Get multiple fields
    info = r.hmget('user:1000', 'name', 'email', 'age')
    print(f"User info: {info}")
    
    # HEXISTS - Check if field exists
    has_email = r.hexists('user:1000', 'email')
    print(f"Has email: {has_email}")
    
    # HKEYS - Get all field names
    fields = r.hkeys('user:1000')
    print(f"Fields: {fields}")
    
    # HVALS - Get all values
    values = r.hvals('user:1000')
    print(f"Values: {values}")
    
    # HLEN - Get number of fields
    field_count = r.hlen('user:1000')
    print(f"Field count: {field_count}")
    
    # HDEL - Delete fields
    r.hdel('user:1000', 'premium')
    print(f"After delete: {r.hgetall('user:1000')}")
    
    # Clean up
    r.delete('user:1000')
    print()

def hash_counter_operations():
    """Using hashes for counters"""
    print("=" * 60)
    print("2. HASH COUNTER OPERATIONS")
    print("=" * 60)
    
    # HINCRBY - Increment integer field
    r.hset('stats:page1', 'views', 0)
    views = r.hincrby('stats:page1', 'views', 1)
    print(f"Page views: {views}")
    
    views = r.hincrby('stats:page1', 'views', 10)
    print(f"After adding 10: {views}")
    
    # HINCRBYFLOAT - Increment float field
    r.hset('analytics', 'avg_time', 5.5)
    avg = r.hincrbyfloat('analytics', 'avg_time', 2.3)
    print(f"Average time: {avg}")
    
    # Multiple counters in one hash
    r.hincrby('stats:page1', 'likes', 1)
    r.hincrby('stats:page1', 'shares', 1)
    r.hincrby('stats:page1', 'comments', 1)
    
    stats = r.hgetall('stats:page1')
    print(f"Page stats: {stats}")
    
    # Clean up
    r.delete('stats:page1', 'analytics')
    print()

def use_case_user_profile():
    """Use Case 1: User Profile Storage"""
    print("=" * 60)
    print("3. USE CASE: USER PROFILE")
    print("=" * 60)
    
    user_id = 'user:1001'
    
    # Store user profile
    profile = {
        'username': 'alice',
        'email': 'alice@example.com',
        'full_name': 'Alice Johnson',
        'age': '28',
        'city': 'San Francisco',
        'country': 'USA',
        'joined_date': '2024-01-15',
        'premium': 'true',
        'points': '1500'
    }
    
    r.hset(user_id, mapping=profile)
    print("User profile created")
    
    # Update specific fields
    r.hset(user_id, 'city', 'Los Angeles')
    r.hincrby(user_id, 'points', 50)
    print("Profile updated")
    
    # Fetch user data
    user_data = r.hgetall(user_id)
    print(f"User data: {json.dumps(user_data, indent=2)}")
    
    # Fetch specific fields
    contact = r.hmget(user_id, 'email', 'city')
    print(f"Contact info: {contact}")
    
    # Check premium status
    is_premium = r.hget(user_id, 'premium') == 'true'
    print(f"Is premium: {is_premium}")
    
    # Clean up
    r.delete(user_id)
    print()

def use_case_session_storage():
    """Use Case 2: Session Storage"""
    print("=" * 60)
    print("4. USE CASE: SESSION STORAGE")
    print("=" * 60)
    
    session_id = 'session:abc123'
    
    # Create session
    session_data = {
        'user_id': '1001',
        'login_time': '2024-01-15T10:30:00',
        'ip_address': '192.168.1.100',
        'device': 'desktop',
        'last_activity': '2024-01-15T10:45:00'
    }
    
    r.hset(session_id, mapping=session_data)
    r.expire(session_id, 1800)  # 30 minutes expiration
    print(f"Session created with 30-min expiration")
    
    # Update last activity
    r.hset(session_id, 'last_activity', '2024-01-15T10:50:00')
    print("Activity updated")
    
    # Check session
    if r.exists(session_id):
        user_id = r.hget(session_id, 'user_id')
        ttl = r.ttl(session_id)
        print(f"Session active for user {user_id}, expires in {ttl}s")
    
    # Clean up
    r.delete(session_id)
    print()

def use_case_shopping_cart():
    """Use Case 3: Shopping Cart"""
    print("=" * 60)
    print("5. USE CASE: SHOPPING CART")
    print("=" * 60)
    
    cart_key = 'cart:user1001'
    
    # Add items to cart (field = product_id, value = quantity)
    r.hset(cart_key, 'product_101', 2)
    r.hset(cart_key, 'product_205', 1)
    r.hset(cart_key, 'product_350', 3)
    print("Items added to cart")
    
    # Update quantity
    r.hincrby(cart_key, 'product_101', 1)  # Add one more
    print("Quantity updated")
    
    # Get cart items
    cart_items = r.hgetall(cart_key)
    print(f"Cart items: {cart_items}")
    
    # Get total items count
    total_items = sum(int(qty) for qty in cart_items.values())
    print(f"Total items: {total_items}")
    
    # Remove item
    r.hdel(cart_key, 'product_205')
    print("Item removed")
    
    # Check if item in cart
    has_item = r.hexists(cart_key, 'product_101')
    print(f"Has product_101: {has_item}")
    
    # Get cart size
    cart_size = r.hlen(cart_key)
    print(f"Unique products: {cart_size}")
    
    # Clear cart (checkout)
    r.delete(cart_key)
    print("Cart cleared")
    print()

def use_case_rate_limit_per_endpoint():
    """Use Case 4: Rate Limiting per Endpoint"""
    print("=" * 60)
    print("6. USE CASE: RATE LIMIT PER ENDPOINT")
    print("=" * 60)
    
    user_id = 'user_123'
    rate_key = f'rate_limit:{user_id}'
    
    # Track requests per endpoint
    endpoints = ['/api/users', '/api/posts', '/api/users', '/api/comments', '/api/users']
    
    for endpoint in endpoints:
        # Increment endpoint counter
        count = r.hincrby(rate_key, endpoint, 1)
        print(f"Request to {endpoint}: {count}")
        
        # Check rate limit (e.g., 5 requests per endpoint)
        if count > 5:
            print(f"  ⚠ Rate limit exceeded for {endpoint}")
    
    # Get all endpoint counts
    limits = r.hgetall(rate_key)
    print(f"\nRequest counts per endpoint: {limits}")
    
    # Set expiration
    r.expire(rate_key, 60)
    print("Rate limit window: 60 seconds")
    
    # Clean up
    r.delete(rate_key)
    print()

def use_case_feature_flags():
    """Use Case 5: Feature Flags"""
    print("=" * 60)
    print("7. USE CASE: FEATURE FLAGS")
    print("=" * 60)
    
    flags_key = 'feature_flags:app'
    
    # Set feature flags
    features = {
        'new_ui': 'true',
        'dark_mode': 'true',
        'beta_feature': 'false',
        'premium_only': 'false',
        'maintenance_mode': 'false'
    }
    
    r.hset(flags_key, mapping=features)
    print("Feature flags set")
    
    # Check if feature enabled
    def is_feature_enabled(feature_name):
        return r.hget(flags_key, feature_name) == 'true'
    
    print(f"New UI enabled: {is_feature_enabled('new_ui')}")
    print(f"Beta feature enabled: {is_feature_enabled('beta_feature')}")
    
    # Toggle feature
    r.hset(flags_key, 'dark_mode', 'false')
    print("Dark mode toggled off")
    
    # Get all flags
    all_flags = r.hgetall(flags_key)
    print(f"All flags: {all_flags}")
    
    # Clean up
    r.delete(flags_key)
    print()

def use_case_product_inventory():
    """Use Case 6: Product Inventory"""
    print("=" * 60)
    print("8. USE CASE: PRODUCT INVENTORY")
    print("=" * 60)
    
    # Store product details
    product_key = 'product:SKU12345'
    
    product_data = {
        'name': 'Wireless Mouse',
        'price': '29.99',
        'stock': '150',
        'category': 'Electronics',
        'brand': 'TechCo',
        'rating': '4.5'
    }
    
    r.hset(product_key, mapping=product_data)
    print("Product created")
    
    # Purchase (decrease stock)
    quantity_sold = 5
    new_stock = r.hincrby(product_key, 'stock', -quantity_sold)
    print(f"Sold {quantity_sold} units, stock remaining: {new_stock}")
    
    # Restock
    r.hincrby(product_key, 'stock', 100)
    print(f"Restocked, new stock: {r.hget(product_key, 'stock')}")
    
    # Update price
    r.hset(product_key, 'price', '24.99')
    print("Price updated")
    
    # Get product info
    product = r.hgetall(product_key)
    print(f"Product details: {json.dumps(product, indent=2)}")
    
    # Low stock check
    stock = int(r.hget(product_key, 'stock'))
    if stock < 50:
        print("⚠ Low stock alert!")
    
    # Clean up
    r.delete(product_key)
    print()

def use_case_metrics_tracking():
    """Use Case 7: Metrics Tracking"""
    print("=" * 60)
    print("9. USE CASE: METRICS TRACKING")
    print("=" * 60)
    
    metrics_key = 'metrics:api:2024-01-15'
    
    # Track various metrics
    r.hincrby(metrics_key, 'requests', 1000)
    r.hincrby(metrics_key, 'errors', 15)
    r.hincrby(metrics_key, 'cache_hits', 850)
    r.hincrby(metrics_key, 'cache_misses', 150)
    r.hincrbyfloat(metrics_key, 'avg_response_time', 45.5)
    
    # Get all metrics
    metrics = r.hgetall(metrics_key)
    print("Daily metrics:")
    for key, value in metrics.items():
        print(f"  {key}: {value}")
    
    # Calculate derived metrics
    total_cache = int(metrics['cache_hits']) + int(metrics['cache_misses'])
    hit_rate = (int(metrics['cache_hits']) / total_cache) * 100
    error_rate = (int(metrics['errors']) / int(metrics['requests'])) * 100
    
    print(f"\nCache hit rate: {hit_rate:.2f}%")
    print(f"Error rate: {error_rate:.2f}%")
    
    # Clean up
    r.delete(metrics_key)
    print()

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("REDIS HASHES - COMPREHENSIVE EXAMPLES")
    print("=" * 60 + "\n")
    
    try:
        r.ping()
        
        basic_hash_operations()
        hash_counter_operations()
        use_case_user_profile()
        use_case_session_storage()
        use_case_shopping_cart()
        use_case_rate_limit_per_endpoint()
        use_case_feature_flags()
        use_case_product_inventory()
        use_case_metrics_tracking()
        
        print("=" * 60)
        print("✓ All examples completed successfully!")
        print("=" * 60)
        
    except redis.ConnectionError:
        print("\n⚠ Please start Redis server before running this script:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
    except Exception as e:
        print(f"\n✗ Error: {e}")
