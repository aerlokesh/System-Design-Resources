"""
Redis Sets - Unordered collections of unique strings
Sets provide O(1) add, remove, and membership testing

Use Cases:
- Unique visitors tracking
- Tags and categories
- Friend relationships (social networks)
- Intersection/union operations (common interests)
- Lottery/raffle systems
"""

import redis
import random

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

def basic_set_operations():
    """Basic set operations"""
    print("=" * 60)
    print("1. BASIC SET OPERATIONS")
    print("=" * 60)
    
    # SADD - Add members
    r.sadd('colors', 'red', 'blue', 'green')
    r.sadd('colors', 'red')  # Duplicate ignored
    print(f"Colors: {r.smembers('colors')}")
    
    # SCARD - Get cardinality (count)
    count = r.scard('colors')
    print(f"Color count: {count}")
    
    # SISMEMBER - Check membership
    is_member = r.sismember('colors', 'red')
    print(f"Is 'red' a member: {is_member}")
    
    # SREM - Remove members
    r.srem('colors', 'green')
    print(f"After removing green: {r.smembers('colors')}")
    
    # SPOP - Remove and return random member
    random_color = r.spop('colors')
    print(f"Random pop: {random_color}")
    print(f"Remaining: {r.smembers('colors')}")
    
    # SRANDMEMBER - Get random member without removing
    r.sadd('numbers', '1', '2', '3', '4', '5')
    random_num = r.srandmember('numbers')
    print(f"Random member: {random_num}")
    print(f"Set unchanged: {r.smembers('numbers')}")
    
    # Get multiple random members
    random_nums = r.srandmember('numbers', 3)
    print(f"3 random members: {random_nums}")
    
    # Clean up
    r.delete('colors', 'numbers')
    print()

def set_operations():
    """Set theory operations"""
    print("=" * 60)
    print("2. SET THEORY OPERATIONS")
    print("=" * 60)
    
    # Setup sets
    r.sadd('users:online:web', 'user1', 'user2', 'user3', 'user4')
    r.sadd('users:online:mobile', 'user3', 'user4', 'user5', 'user6')
    
    print(f"Web users: {r.smembers('users:online:web')}")
    print(f"Mobile users: {r.smembers('users:online:mobile')}")
    
    # SINTER - Intersection (users on both platforms)
    both = r.sinter('users:online:web', 'users:online:mobile')
    print(f"Users on both: {both}")
    
    # SUNION - Union (all unique users)
    all_users = r.sunion('users:online:web', 'users:online:mobile')
    print(f"All users: {all_users}")
    
    # SDIFF - Difference (web only)
    web_only = r.sdiff('users:online:web', 'users:online:mobile')
    print(f"Web only: {web_only}")
    
    # SINTERSTORE - Store intersection result
    r.sinterstore('users:online:both', 'users:online:web', 'users:online:mobile')
    print(f"Stored intersection: {r.smembers('users:online:both')}")
    
    # SMOVE - Move member between sets
    r.smove('users:online:web', 'users:online:mobile', 'user1')
    print(f"After moving user1 - Web: {r.smembers('users:online:web')}")
    print(f"Mobile: {r.smembers('users:online:mobile')}")
    
    # Clean up
    r.delete('users:online:web', 'users:online:mobile', 'users:online:both')
    print()

def use_case_unique_visitors():
    """Use Case 1: Unique Visitor Tracking"""
    print("=" * 60)
    print("3. USE CASE: UNIQUE VISITORS")
    print("=" * 60)
    
    # Track unique visitors per day
    day_key = 'visitors:2024-01-15'
    
    # Simulate visits
    visitors = ['ip1', 'ip2', 'ip3', 'ip2', 'ip1', 'ip4', 'ip3', 'ip5']
    for visitor_ip in visitors:
        r.sadd(day_key, visitor_ip)
    
    unique_count = r.scard(day_key)
    print(f"Total visits: {len(visitors)}")
    print(f"Unique visitors: {unique_count}")
    
    # Check if specific IP visited
    has_visited = r.sismember(day_key, 'ip1')
    print(f"Did ip1 visit: {has_visited}")
    
    # Compare two days
    yesterday_key = 'visitors:2024-01-14'
    r.sadd(yesterday_key, 'ip1', 'ip2', 'ip6', 'ip7')
    
    # Returning visitors
    returning = r.sinter(yesterday_key, day_key)
    print(f"Returning visitors: {returning}")
    
    # New visitors
    new = r.sdiff(day_key, yesterday_key)
    print(f"New visitors: {new}")
    
    # Clean up
    r.delete(day_key, yesterday_key)
    print()

def use_case_tagging_system():
    """Use Case 2: Tagging System"""
    print("=" * 60)
    print("4. USE CASE: TAGGING SYSTEM")
    print("=" * 60)
    
    # Articles with tags
    r.sadd('article:1:tags', 'python', 'redis', 'database')
    r.sadd('article:2:tags', 'python', 'flask', 'web')
    r.sadd('article:3:tags', 'redis', 'caching', 'performance')
    
    # Track articles per tag
    r.sadd('tag:python:articles', 'article:1', 'article:2')
    r.sadd('tag:redis:articles', 'article:1', 'article:3')
    r.sadd('tag:database:articles', 'article:1')
    
    # Find articles with specific tag
    redis_articles = r.smembers('tag:redis:articles')
    print(f"Articles tagged with 'redis': {redis_articles}")
    
    # Find articles with multiple tags (AND operation)
    python_and_redis = r.sinter('tag:python:articles', 'tag:redis:articles')
    print(f"Articles with both Python AND Redis: {python_and_redis}")
    
    # Find articles with any of the tags (OR operation)
    python_or_redis = r.sunion('tag:python:articles', 'tag:redis:articles')
    print(f"Articles with Python OR Redis: {python_or_redis}")
    
    # Get all tags for an article
    article_tags = r.smembers('article:1:tags')
    print(f"Tags for article:1: {article_tags}")
    
    # Clean up
    for i in range(1, 4):
        r.delete(f'article:{i}:tags')
    r.delete('tag:python:articles', 'tag:redis:articles', 'tag:database:articles')
    print()

def use_case_social_network():
    """Use Case 3: Social Network Relationships"""
    print("=" * 60)
    print("5. USE CASE: SOCIAL NETWORK")
    print("=" * 60)
    
    # User followers
    r.sadd('user:alice:followers', 'bob', 'charlie', 'dave')
    r.sadd('user:bob:followers', 'alice', 'charlie', 'eve')
    r.sadd('user:charlie:followers', 'alice', 'bob', 'dave', 'eve')
    
    # User following
    r.sadd('user:alice:following', 'bob', 'charlie')
    r.sadd('user:bob:following', 'alice', 'charlie')
    
    # Get followers count
    alice_followers = r.scard('user:alice:followers')
    print(f"Alice has {alice_followers} followers")
    
    # Check if following
    is_following = r.sismember('user:alice:following', 'bob')
    print(f"Does Alice follow Bob: {is_following}")
    
    # Mutual followers (friends)
    alice_friends = r.sinter('user:alice:followers', 'user:alice:following')
    print(f"Alice's mutual friends: {alice_friends}")
    
    # Suggested friends (friends of friends)
    alice_following = r.smembers('user:alice:following')
    suggested = set()
    for friend in alice_following:
        friend_following = r.smembers(f'user:{friend}:following')
        suggested.update(friend_following)
    
    # Remove alice and existing friends
    suggested.discard('alice')
    suggested -= alice_following
    print(f"Suggested friends for Alice: {suggested}")
    
    # Common followers between two users
    common = r.sinter('user:alice:followers', 'user:bob:followers')
    print(f"Common followers of Alice and Bob: {common}")
    
    # Clean up
    r.delete('user:alice:followers', 'user:bob:followers', 'user:charlie:followers',
             'user:alice:following', 'user:bob:following')
    print()

def use_case_lottery_system():
    """Use Case 4: Lottery/Raffle System"""
    print("=" * 60)
    print("6. USE CASE: LOTTERY SYSTEM")
    print("=" * 60)
    
    lottery_key = 'lottery:jan2024:participants'
    
    # Add participants
    participants = [f'user{i}' for i in range(1, 11)]
    r.sadd(lottery_key, *participants)
    print(f"Total participants: {r.scard(lottery_key)}")
    
    # Draw 3 winners (remove from set)
    winners = []
    for i in range(3):
        winner = r.spop(lottery_key)
        if winner:
            winners.append(winner)
    
    print(f"Winners: {winners}")
    print(f"Remaining participants: {r.scard(lottery_key)}")
    
    # Verify no duplicate winners
    print(f"Unique winners: {len(set(winners))}")
    
    # Clean up
    r.delete(lottery_key)
    print()

def use_case_online_users():
    """Use Case 5: Online Users Tracking"""
    print("=" * 60)
    print("7. USE CASE: ONLINE USERS")
    print("=" * 60)
    
    # Add online users
    r.sadd('users:online', 'user1', 'user2', 'user3')
    r.expire('users:online', 300)  # Auto-expire in 5 minutes
    
    # User comes online
    user_id = 'user4'
    r.sadd('users:online', user_id)
    print(f"User {user_id} is now online")
    
    # Check if user is online
    is_online = r.sismember('users:online', 'user1')
    print(f"Is user1 online: {is_online}")
    
    # Get online count
    online_count = r.scard('users:online')
    print(f"Users online: {online_count}")
    
    # User goes offline
    r.srem('users:online', 'user2')
    print(f"User2 went offline. Online now: {r.scard('users:online')}")
    
    # Get all online users
    all_online = r.smembers('users:online')
    print(f"All online users: {all_online}")
    
    # Clean up
    r.delete('users:online')
    print()

def use_case_permissions():
    """Use Case 6: Permission System"""
    print("=" * 60)
    print("8. USE CASE: USER PERMISSIONS")
    print("=" * 60)
    
    # Define role permissions
    r.sadd('role:admin:permissions', 'read', 'write', 'delete', 'manage_users')
    r.sadd('role:editor:permissions', 'read', 'write')
    r.sadd('role:viewer:permissions', 'read')
    
    # Assign roles to users
    r.sadd('user:john:roles', 'admin')
    r.sadd('user:jane:roles', 'editor', 'viewer')
    
    # Check if user has permission
    def has_permission(user_id, permission):
        user_roles = r.smembers(f'user:{user_id}:roles')
        for role in user_roles:
            if r.sismember(f'role:{role}:permissions', permission):
                return True
        return False
    
    print(f"Can john delete: {has_permission('john', 'delete')}")
    print(f"Can jane delete: {has_permission('jane', 'delete')}")
    print(f"Can jane read: {has_permission('jane', 'read')}")
    
    # Get all permissions for a user
    user_permissions = set()
    for role in r.smembers('user:jane:roles'):
        perms = r.smembers(f'role:{role}:permissions')
        user_permissions.update(perms)
    print(f"Jane's permissions: {user_permissions}")
    
    # Clean up
    r.delete('role:admin:permissions', 'role:editor:permissions', 'role:viewer:permissions',
             'user:john:roles', 'user:jane:roles')
    print()

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("REDIS SETS - COMPREHENSIVE EXAMPLES")
    print("=" * 60 + "\n")
    
    try:
        r.ping()
        
        basic_set_operations()
        set_operations()
        use_case_unique_visitors()
        use_case_tagging_system()
        use_case_social_network()
        use_case_lottery_system()
        use_case_online_users()
        use_case_permissions()
        
        print("=" * 60)
        print("✓ All examples completed successfully!")
        print("=" * 60)
        
    except redis.ConnectionError:
        print("\n⚠ Please start Redis server before running this script:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
    except Exception as e:
        print(f"\n✗ Error: {e}")
