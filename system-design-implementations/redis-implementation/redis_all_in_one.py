"""
REDIS ALL-IN-ONE COMPREHENSIVE GUIDE
====================================
Complete implementation of all Redis data structures with real-world use cases.

Data Structures Covered:
1. Strings - Basic key-value, counters, caching, distributed locks
2. Lists - Task queues, activity feeds, message queues
3. Sets - Unique tracking, tags, social networks
4. Hashes - User profiles, sessions, shopping carts
5. Sorted Sets - Leaderboards, priority queues, time series

Author: System Design Learning
"""

import redis
import time
import json
from datetime import timedelta

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

# ============================================================================
# REDIS STRINGS
# ============================================================================

def strings_basic_operations():
    """Basic string operations"""
    print("\n" + "=" * 60)
    print("STRINGS: Basic Operations")
    print("=" * 60)
    
    # SET and GET
    r.set('username', 'john_doe')
    username = r.get('username')
    print(f"✓ Username: {username}")
    
    # SET with expiration
    r.setex('session_token', 3600, 'abc123xyz')
    ttl = r.ttl('session_token')
    print(f"✓ Session token TTL: {ttl} seconds")
    
    # SET NX (distributed lock)
    result = r.setnx('distributed_lock', 'process_1')
    print(f"✓ Lock acquired: {result}")
    
    # MSET and MGET
    r.mset({'user:1:name': 'Alice', 'user:1:email': 'alice@example.com'})
    user_data = r.mget('user:1:name', 'user:1:email')
    print(f"✓ User data: {user_data}")
    
    # Cleanup
    r.delete('username', 'session_token', 'distributed_lock', 'user:1:name', 'user:1:email')

def strings_counters():
    """Counter operations"""
    print("\n" + "=" * 60)
    print("STRINGS: Counter Operations")
    print("=" * 60)
    
    r.set('page_views', 0)
    views = r.incr('page_views')
    print(f"✓ Page views: {views}")
    
    views = r.incrby('page_views', 10)
    print(f"✓ After adding 10: {views}")
    
    # Float increment
    r.set('temperature', 20.5)
    temp = r.incrbyfloat('temperature', 2.3)
    print(f"✓ Temperature: {temp}°C")
    
    r.delete('page_views', 'temperature')

def strings_use_cases():
    """Real-world string use cases"""
    print("\n" + "=" * 60)
    print("STRINGS: Real-World Use Cases")
    print("=" * 60)
    
    # 1. Session Management
    session_id = 'sess_abc123'
    r.setex(f'session:{session_id}', 1800, 'user_id:42')
    print("✓ Session stored (30-min expiration)")
    
    # 2. Rate Limiting
    user_id = 'user_123'
    key = f'rate_limit:{user_id}'
    r.setex(key, 60, 1)
    print("✓ Rate limit window started")
    
    # 3. Caching
    cache_key = 'api:weather:nyc'
    r.setex(cache_key, 300, '{"temp": 72, "condition": "sunny"}')
    print("✓ API response cached (5 minutes)")
    
    # 4. Distributed Lock
    lock_key = 'lock:process_payment'
    lock_acquired = r.set(lock_key, 'process_1', nx=True, ex=30)
    if lock_acquired:
        print("✓ Payment lock acquired")
        r.delete(lock_key)
    
    # Cleanup
    r.delete(f'session:{session_id}', key, cache_key)

# ============================================================================
# REDIS LISTS
# ============================================================================

def lists_basic_operations():
    """Basic list operations"""
    print("\n" + "=" * 60)
    print("LISTS: Basic Operations")
    print("=" * 60)
    
    # LPUSH and RPUSH
    r.lpush('tasks', 'task3', 'task2', 'task1')
    r.rpush('tasks', 'task4', 'task5')
    all_tasks = r.lrange('tasks', 0, -1)
    print(f"✓ Tasks: {all_tasks}")
    
    # LPOP and RPOP
    first = r.lpop('tasks')
    last = r.rpop('tasks')
    print(f"✓ Popped: first={first}, last={last}")
    
    # LLEN
    length = r.llen('tasks')
    print(f"✓ Remaining tasks: {length}")
    
    r.delete('tasks')

def lists_task_queue():
    """Task queue implementation"""
    print("\n" + "=" * 60)
    print("LISTS: Task Queue (Producer-Consumer)")
    print("=" * 60)
    
    # Producer adds tasks
    tasks = ['send_email', 'process_image', 'generate_report']
    for task in tasks:
        r.rpush('task_queue', json.dumps({'task': task, 'timestamp': time.time()}))
    print(f"✓ Producer: Added {len(tasks)} tasks")
    
    # Consumer processes tasks
    processed = 0
    while r.llen('task_queue') > 0:
        result = r.blpop('task_queue', timeout=1)
        if result:
            queue_name, task_data = result
            task = json.loads(task_data)
            print(f"  ✓ Processing: {task['task']}")
            processed += 1
    
    print(f"✓ Consumer: Processed {processed} tasks")

def lists_activity_feed():
    """Activity feed implementation"""
    print("\n" + "=" * 60)
    print("LISTS: Activity Feed")
    print("=" * 60)
    
    user_id = 'user_123'
    feed_key = f'feed:{user_id}'
    
    activities = ['liked a photo', 'commented on post', 'shared article']
    for activity in activities:
        r.lpush(feed_key, json.dumps({'action': activity, 'timestamp': time.time()}))
        r.ltrim(feed_key, 0, 49)  # Keep only last 50
    
    recent = r.lrange(feed_key, 0, 2)
    print("✓ Recent activities:")
    for item in recent:
        activity = json.loads(item)
        print(f"  - {activity['action']}")
    
    r.delete(feed_key)

# ============================================================================
# REDIS SETS
# ============================================================================

def sets_basic_operations():
    """Basic set operations"""
    print("\n" + "=" * 60)
    print("SETS: Basic Operations")
    print("=" * 60)
    
    # SADD
    r.sadd('colors', 'red', 'blue', 'green')
    print(f"✓ Colors: {r.smembers('colors')}")
    
    # SCARD
    count = r.scard('colors')
    print(f"✓ Color count: {count}")
    
    # SISMEMBER
    is_member = r.sismember('colors', 'red')
    print(f"✓ Is 'red' a member: {is_member}")
    
    # SPOP
    random_color = r.spop('colors')
    print(f"✓ Random pop: {random_color}")
    
    r.delete('colors')

def sets_operations():
    """Set theory operations"""
    print("\n" + "=" * 60)
    print("SETS: Set Operations (Union/Intersection/Difference)")
    print("=" * 60)
    
    r.sadd('users:web', 'user1', 'user2', 'user3')
    r.sadd('users:mobile', 'user3', 'user4', 'user5')
    
    # Intersection
    both = r.sinter('users:web', 'users:mobile')
    print(f"✓ Users on both platforms: {both}")
    
    # Union
    all_users = r.sunion('users:web', 'users:mobile')
    print(f"✓ All unique users: {all_users}")
    
    # Difference
    web_only = r.sdiff('users:web', 'users:mobile')
    print(f"✓ Web only: {web_only}")
    
    r.delete('users:web', 'users:mobile')

def sets_unique_visitors():
    """Unique visitor tracking"""
    print("\n" + "=" * 60)
    print("SETS: Unique Visitor Tracking")
    print("=" * 60)
    
    day_key = 'visitors:2024-01-15'
    visitors = ['ip1', 'ip2', 'ip3', 'ip2', 'ip1', 'ip4']
    
    for visitor_ip in visitors:
        r.sadd(day_key, visitor_ip)
    
    unique_count = r.scard(day_key)
    print(f"✓ Total visits: {len(visitors)}")
    print(f"✓ Unique visitors: {unique_count}")
    
    r.delete(day_key)

def sets_social_network():
    """Social network relationships"""
    print("\n" + "=" * 60)
    print("SETS: Social Network Relationships")
    print("=" * 60)
    
    r.sadd('user:alice:followers', 'bob', 'charlie', 'dave')
    r.sadd('user:alice:following', 'bob', 'charlie')
    
    # Mutual friends
    friends = r.sinter('user:alice:followers', 'user:alice:following')
    print(f"✓ Alice's mutual friends: {friends}")
    
    # Follower count
    followers = r.scard('user:alice:followers')
    print(f"✓ Alice has {followers} followers")
    
    r.delete('user:alice:followers', 'user:alice:following')

# ============================================================================
# REDIS HASHES
# ============================================================================

def hashes_basic_operations():
    """Basic hash operations"""
    print("\n" + "=" * 60)
    print("HASHES: Basic Operations")
    print("=" * 60)
    
    # HSET and HGET
    r.hset('user:1000', mapping={
        'name': 'John Doe',
        'email': 'john@example.com',
        'age': '30'
    })
    
    name = r.hget('user:1000', 'name')
    print(f"✓ Name: {name}")
    
    # HGETALL
    user = r.hgetall('user:1000')
    print(f"✓ User data: {user}")
    
    # HINCRBY
    r.hincrby('user:1000', 'age', 1)
    print(f"✓ Age after increment: {r.hget('user:1000', 'age')}")
    
    r.delete('user:1000')

def hashes_user_profile():
    """User profile storage"""
    print("\n" + "=" * 60)
    print("HASHES: User Profile")
    print("=" * 60)
    
    user_id = 'user:1001'
    profile = {
        'username': 'alice',
        'email': 'alice@example.com',
        'city': 'San Francisco',
        'points': '1500'
    }
    
    r.hset(user_id, mapping=profile)
    print("✓ Profile created")
    
    # Update fields
    r.hset(user_id, 'city', 'Los Angeles')
    r.hincrby(user_id, 'points', 50)
    print("✓ Profile updated")
    
    user_data = r.hgetall(user_id)
    print(f"✓ User data: {user_data}")
    
    r.delete(user_id)

def hashes_shopping_cart():
    """Shopping cart implementation"""
    print("\n" + "=" * 60)
    print("HASHES: Shopping Cart")
    print("=" * 60)
    
    cart_key = 'cart:user1001'
    
    # Add items (product_id: quantity)
    r.hset(cart_key, 'product_101', 2)
    r.hset(cart_key, 'product_205', 1)
    r.hset(cart_key, 'product_350', 3)
    print("✓ Items added to cart")
    
    # Update quantity
    r.hincrby(cart_key, 'product_101', 1)
    print("✓ Quantity updated")
    
    # Get cart items
    cart_items = r.hgetall(cart_key)
    total_items = sum(int(qty) for qty in cart_items.values())
    print(f"✓ Total items: {total_items}")
    print(f"✓ Cart: {cart_items}")
    
    r.delete(cart_key)

def hashes_feature_flags():
    """Feature flags system"""
    print("\n" + "=" * 60)
    print("HASHES: Feature Flags")
    print("=" * 60)
    
    flags_key = 'feature_flags:app'
    features = {
        'new_ui': 'true',
        'dark_mode': 'true',
        'beta_feature': 'false'
    }
    
    r.hset(flags_key, mapping=features)
    print("✓ Feature flags set")
    
    # Check features
    new_ui_enabled = r.hget(flags_key, 'new_ui') == 'true'
    print(f"✓ New UI enabled: {new_ui_enabled}")
    
    all_flags = r.hgetall(flags_key)
    print(f"✓ All flags: {all_flags}")
    
    r.delete(flags_key)

# ============================================================================
# REDIS SORTED SETS
# ============================================================================

def sorted_sets_basic_operations():
    """Basic sorted set operations"""
    print("\n" + "=" * 60)
    print("SORTED SETS: Basic Operations")
    print("=" * 60)
    
    # ZADD
    r.zadd('scores', {'alice': 100, 'bob': 85, 'charlie': 92})
    
    # ZCARD
    count = r.zcard('scores')
    print(f"✓ Total members: {count}")
    
    # ZSCORE
    alice_score = r.zscore('scores', 'alice')
    print(f"✓ Alice's score: {alice_score}")
    
    # ZRANK
    alice_rank = r.zrank('scores', 'alice')
    print(f"✓ Alice's rank: {alice_rank}")
    
    # ZINCRBY
    new_score = r.zincrby('scores', 10, 'bob')
    print(f"✓ Bob's new score: {new_score}")
    
    r.delete('scores')

def sorted_sets_leaderboard():
    """Gaming leaderboard"""
    print("\n" + "=" * 60)
    print("SORTED SETS: Gaming Leaderboard")
    print("=" * 60)
    
    leaderboard = 'game:leaderboard'
    game_results = {
        'player_alice': 2500,
        'player_bob': 1800,
        'player_charlie': 3200,
        'player_dave': 2100
    }
    
    r.zadd(leaderboard, game_results)
    print("✓ Leaderboard created")
    
    # Get top 3
    top_players = r.zrevrange(leaderboard, 0, 2, withscores=True)
    print("\n🏆 Top 3 Players:")
    for rank, (player, score) in enumerate(top_players, 1):
        print(f"  #{rank}: {player} - {int(score)} points")
    
    r.delete(leaderboard)

def sorted_sets_priority_queue():
    """Priority queue implementation"""
    print("\n" + "=" * 60)
    print("SORTED SETS: Priority Queue")
    print("=" * 60)
    
    queue = 'tasks:priority'
    tasks = {
        'send_email': 3,
        'process_payment': 1,      # Highest priority
        'generate_report': 2
    }
    
    r.zadd(queue, tasks)
    print(f"✓ Added {r.zcard(queue)} tasks")
    
    # Process by priority
    print("\n✓ Processing tasks:")
    for i in range(3):
        task = r.zpopmin(queue)
        if task:
            task_name, priority = task[0]
            print(f"  - {task_name} (priority: {int(priority)})")
    
    r.delete(queue)

def sorted_sets_rate_limiting():
    """Rate limiting with sliding window"""
    print("\n" + "=" * 60)
    print("SORTED SETS: Rate Limiting (Sliding Window)")
    print("=" * 60)
    
    def check_rate_limit(user_id, max_requests=5):
        key = f'rate_limit:{user_id}'
        current_time = time.time()
        
        # Add current request
        request_id = f'req_{current_time}'
        r.zadd(key, {request_id: current_time})
        
        # Remove old requests
        cutoff = current_time - 60
        r.zremrangebyscore(key, '-inf', cutoff)
        
        count = r.zcard(key)
        r.expire(key, 60)
        
        return count <= max_requests, count
    
    user_id = 'user_789'
    print("✓ Simulating API requests:")
    for i in range(7):
        allowed, count = check_rate_limit(user_id)
        status = "✓ Allowed" if allowed else "✗ Blocked"
        print(f"  Request {i+1}: {status} ({count}/5)")
    
    r.delete(f'rate_limit:{user_id}')

# ============================================================================
# MAIN MENU AND EXECUTION
# ============================================================================

def print_menu():
    """Display main menu"""
    print("\n" + "=" * 70)
    print("REDIS ALL-IN-ONE COMPREHENSIVE GUIDE".center(70))
    print("=" * 70)
    print("""
Choose a data structure to explore:

1. STRINGS       - Basic key-value, counters, caching, distributed locks
2. LISTS         - Task queues, activity feeds, message queues
3. SETS          - Unique tracking, tags, social networks
4. HASHES        - User profiles, sessions, shopping carts
5. SORTED SETS   - Leaderboards, priority queues, time series

0. RUN ALL       - Execute all examples
q. QUIT          - Exit program
""")
    print("=" * 70)

def run_strings():
    """Run all string examples"""
    print("\n" + "▶" * 35)
    print("REDIS STRINGS - EXAMPLES")
    print("▶" * 35)
    strings_basic_operations()
    strings_counters()
    strings_use_cases()

def run_lists():
    """Run all list examples"""
    print("\n" + "▶" * 35)
    print("REDIS LISTS - EXAMPLES")
    print("▶" * 35)
    lists_basic_operations()
    lists_task_queue()
    lists_activity_feed()

def run_sets():
    """Run all set examples"""
    print("\n" + "▶" * 35)
    print("REDIS SETS - EXAMPLES")
    print("▶" * 35)
    sets_basic_operations()
    sets_operations()
    sets_unique_visitors()
    sets_social_network()

def run_hashes():
    """Run all hash examples"""
    print("\n" + "▶" * 35)
    print("REDIS HASHES - EXAMPLES")
    print("▶" * 35)
    hashes_basic_operations()
    hashes_user_profile()
    hashes_shopping_cart()
    hashes_feature_flags()

def run_sorted_sets():
    """Run all sorted set examples"""
    print("\n" + "▶" * 35)
    print("REDIS SORTED SETS - EXAMPLES")
    print("▶" * 35)
    sorted_sets_basic_operations()
    sorted_sets_leaderboard()
    sorted_sets_priority_queue()
    sorted_sets_rate_limiting()

def run_all():
    """Run all examples"""
    run_strings()
    run_lists()
    run_sets()
    run_hashes()
    run_sorted_sets()
    print("\n" + "=" * 70)
    print("✓ ALL EXAMPLES COMPLETED SUCCESSFULLY!".center(70))
    print("=" * 70)

def main():
    """Main program loop"""
    try:
        # Test connection
        r.ping()
        print("\n✓ Connected to Redis successfully!")
        
        while True:
            print_menu()
            choice = input("Enter your choice: ").strip().lower()
            
            if choice == '1':
                run_strings()
            elif choice == '2':
                run_lists()
            elif choice == '3':
                run_sets()
            elif choice == '4':
                run_hashes()
            elif choice == '5':
                run_sorted_sets()
            elif choice == '0':
                run_all()
            elif choice == 'q':
                print("\n👋 Thanks for learning Redis! Goodbye!\n")
                break
            else:
                print("\n⚠ Invalid choice. Please try again.")
            
            input("\nPress Enter to continue...")
    
    except redis.ConnectionError:
        print("\n" + "=" * 70)
        print("✗ ERROR: Could not connect to Redis")
        print("=" * 70)
        print("\n📋 To start Redis server:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
        print("\n📋 Or if you have Redis installed locally:")
        print("   redis-server")
        print()
    except KeyboardInterrupt:
        print("\n\n👋 Interrupted by user. Goodbye!\n")
    except Exception as e:
        print(f"\n✗ Unexpected error: {e}\n")

if __name__ == '__main__':
    main()
