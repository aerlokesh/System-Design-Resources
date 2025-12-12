"""
Redis Sorted Sets (ZSets) - Ordered collections with scores
Each member has a unique score for ordering

Use Cases:
- Leaderboards and rankings
- Priority queues
- Time series data
- Auto-complete
- Rate limiting with sliding window
"""

import redis
import time

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

def basic_sorted_set_operations():
    """Basic sorted set operations"""
    print("=" * 60)
    print("1. BASIC SORTED SET OPERATIONS")
    print("=" * 60)
    
    # ZADD - Add members with scores
    r.zadd('scores', {'alice': 100, 'bob': 85, 'charlie': 92})
    r.zadd('scores', {'dave': 78, 'eve': 95})
    
    # ZCARD - Get cardinality
    count = r.zcard('scores')
    print(f"Total members: {count}")
    
    # ZCOUNT - Count members in score range
    mid_range = r.zcount('scores', 80, 95)
    print(f"Scores between 80-95: {mid_range}")
    
    # ZSCORE - Get member's score
    alice_score = r.zscore('scores', 'alice')
    print(f"Alice's score: {alice_score}")
    
    # ZRANK - Get rank (0-based, lowest score = 0)
    alice_rank = r.zrank('scores', 'alice')
    print(f"Alice's rank (ascending): {alice_rank}")
    
    # ZREVRANK - Get reverse rank (highest score = 0)
    alice_rev_rank = r.zrevrank('scores', 'alice')
    print(f"Alice's rank (descending): {alice_rev_rank}")
    
    # ZINCRBY - Increment score
    new_score = r.zincrby('scores', 10, 'bob')
    print(f"Bob's new score after +10: {new_score}")
    
    # Clean up
    r.delete('scores')
    print()

def range_operations():
    """Range query operations"""
    print("=" * 60)
    print("2. RANGE OPERATIONS")
    print("=" * 60)
    
    # Add data
    r.zadd('leaderboard', {
        'player1': 1000,
        'player2': 1500,
        'player3': 800,
        'player4': 2000,
        'player5': 1200
    })
    
    # ZRANGE - Get members by rank (ascending)
    bottom_3 = r.zrange('leaderboard', 0, 2, withscores=True)
    print(f"Bottom 3: {bottom_3}")
    
    # ZREVRANGE - Get members by rank (descending)
    top_3 = r.zrevrange('leaderboard', 0, 2, withscores=True)
    print(f"Top 3: {top_3}")
    
    # ZRANGEBYSCORE - Get members by score range
    mid_tier = r.zrangebyscore('leaderboard', 1000, 1500, withscores=True)
    print(f"Score 1000-1500: {mid_tier}")
    
    # ZREVRANGEBYSCORE - Reverse range by score
    high_scores = r.zrevrangebyscore('leaderboard', 2000, 1200, withscores=True)
    print(f"High scores (2000-1200): {high_scores}")
    
    # ZRANGEBYLEX - Lexicographical range (when all scores are same)
    r.zadd('words', {word: 0 for word in ['apple', 'banana', 'cherry', 'date']})
    lex_range = r.zrangebylex('words', '[b', '[d')
    print(f"Words b-d: {lex_range}")
    
    # Clean up
    r.delete('leaderboard', 'words')
    print()

def removal_operations():
    """Removal operations"""
    print("=" * 60)
    print("3. REMOVAL OPERATIONS")
    print("=" * 60)
    
    # Setup
    r.zadd('players', {f'player{i}': i*10 for i in range(1, 11)})
    print(f"Initial: {r.zcard('players')} players")
    
    # ZREM - Remove specific members
    r.zrem('players', 'player5', 'player6')
    print(f"After ZREM: {r.zcard('players')} players")
    
    # ZREMRANGEBYRANK - Remove by rank range
    r.zremrangebyrank('players', 0, 1)  # Remove 2 lowest
    print(f"After removing lowest 2: {r.zcard('players')} players")
    
    # ZREMRANGEBYSCORE - Remove by score range
    r.zremrangebyscore('players', 70, 80)
    print(f"After removing scores 70-80: {r.zcard('players')} players")
    
    # ZPOPMAX - Pop member with highest score
    highest = r.zpopmax('players')
    print(f"Popped highest: {highest}")
    
    # ZPOPMIN - Pop member with lowest score
    lowest = r.zpopmin('players')
    print(f"Popped lowest: {lowest}")
    
    # Clean up
    r.delete('players')
    print()

def use_case_leaderboard():
    """Use Case 1: Gaming Leaderboard"""
    print("=" * 60)
    print("4. USE CASE: GAMING LEADERBOARD")
    print("=" * 60)
    
    leaderboard = 'game:leaderboard'
    
    # Players finish games with scores
    game_results = {
        'player_alice': 2500,
        'player_bob': 1800,
        'player_charlie': 3200,
        'player_dave': 2100,
        'player_eve': 2900
    }
    
    r.zadd(leaderboard, game_results)
    print("Leaderboard created")
    
    # Player scores more points
    r.zincrby(leaderboard, 500, 'player_bob')
    print("Bob scored 500 more points")
    
    # Get top 3
    top_players = r.zrevrange(leaderboard, 0, 2, withscores=True)
    print("\n🏆 Top 3 Players:")
    for rank, (player, score) in enumerate(top_players, 1):
        print(f"  #{rank}: {player} - {int(score)} points")
    
    # Get player's rank
    player_rank = r.zrevrank(leaderboard, 'player_bob')
    player_score = r.zscore(leaderboard, 'player_bob')
    print(f"\nBob's rank: #{player_rank + 1} with {int(player_score)} points")
    
    # Get players around Bob
    bob_rank = r.zrevrank(leaderboard, 'player_bob')
    start = max(0, bob_rank - 1)
    end = bob_rank + 1
    around_bob = r.zrevrange(leaderboard, start, end, withscores=True)
    print(f"Players around Bob: {around_bob}")
    
    # Clean up
    r.delete(leaderboard)
    print()

def use_case_priority_queue():
    """Use Case 2: Priority Queue"""
    print("=" * 60)
    print("5. USE CASE: PRIORITY QUEUE")
    print("=" * 60)
    
    queue = 'tasks:priority'
    
    # Add tasks with priority (lower score = higher priority)
    tasks = {
        'send_welcome_email': 3,
        'process_payment': 1,      # Highest priority
        'update_profile': 5,
        'generate_report': 2,
        'backup_database': 4
    }
    
    r.zadd(queue, tasks)
    print(f"Added {r.zcard(queue)} tasks to priority queue")
    
    # Process tasks by priority
    print("\nProcessing tasks by priority:")
    for i in range(3):
        # Get and remove highest priority task
        task = r.zpopmin(queue)
        if task:
            task_name, priority = task[0]
            print(f"  Processing: {task_name} (priority: {int(priority)})")
            time.sleep(0.1)
    
    remaining = r.zcard(queue)
    print(f"\nRemaining tasks: {remaining}")
    
    # Clean up
    r.delete(queue)
    print()

def use_case_time_series():
    """Use Case 3: Time Series Data"""
    print("=" * 60)
    print("6. USE CASE: TIME SERIES DATA")
    print("=" * 60)
    
    sensor_key = 'sensor:temperature'
    
    # Record temperature readings (timestamp as score)
    current_time = time.time()
    for i in range(10):
        timestamp = current_time + i
        temperature = 20 + (i * 0.5)  # Simulated temp increase
        r.zadd(sensor_key, {f'reading_{i}': timestamp})
        r.hset(f'sensor:data:{int(timestamp)}', 'temperature', temperature)
    
    print("Recorded 10 temperature readings")
    
    # Get readings from last 5 seconds
    five_sec_ago = current_time + 5
    recent_readings = r.zrangebyscore(sensor_key, five_sec_ago, '+inf')
    print(f"Readings in last 5 seconds: {len(recent_readings)}")
    
    # Get oldest 3 readings
    oldest = r.zrange(sensor_key, 0, 2, withscores=True)
    print(f"Oldest readings: {oldest}")
    
    # Remove old readings (older than 6 seconds)
    cutoff = current_time + 4
    removed = r.zremrangebyscore(sensor_key, '-inf', cutoff)
    print(f"Removed {removed} old readings")
    
    # Clean up
    r.delete(sensor_key)
    for i in range(10):
        r.delete(f'sensor:data:{int(current_time + i)}')
    print()

def use_case_rate_limiting_sliding_window():
    """Use Case 4: Rate Limiting (Sliding Window)"""
    print("=" * 60)
    print("7. USE CASE: RATE LIMITING (Sliding Window)")
    print("=" * 60)
    
    def check_rate_limit(user_id, max_requests=5, window=60):
        """Check if user is within rate limit"""
        key = f'rate_limit:{user_id}'
        current_time = time.time()
        
        # Add current request with timestamp as score
        request_id = f'req_{current_time}'
        r.zadd(key, {request_id: current_time})
        
        # Remove requests older than window
        cutoff_time = current_time - window
        r.zremrangebyscore(key, '-inf', cutoff_time)
        
        # Count requests in window
        request_count = r.zcard(key)
        
        # Set expiration
        r.expire(key, window)
        
        return request_count <= max_requests, request_count
    
    user_id = 'user_789'
    
    # Simulate 7 requests
    print("Simulating API requests:")
    for i in range(7):
        allowed, count = check_rate_limit(user_id)
        status = "✓ Allowed" if allowed else "✗ Blocked"
        print(f"Request {i+1}: {status} ({count}/5)")
        time.sleep(0.1)
    
    # Clean up
    r.delete(f'rate_limit:{user_id}')
    print()

def use_case_autocomplete():
    """Use Case 5: Auto-complete with Prefix Search"""
    print("=" * 60)
    print("8. USE CASE: AUTO-COMPLETE")
    print("=" * 60)
    
    autocomplete_key = 'autocomplete:cities'
    
    # Add cities (using lexicographical scoring)
    cities = ['San Francisco', 'San Diego', 'San Jose', 'Seattle', 
              'Sacramento', 'Salt Lake City', 'Santa Barbara']
    
    # Add with score 0 for lexicographical ordering
    r.zadd(autocomplete_key, {city: 0 for city in cities})
    
    # Search for cities starting with "San"
    prefix = 'San'
    # ZRANGEBYLEX with [ for inclusive
    suggestions = r.zrangebylex(autocomplete_key, f'[{prefix}', f'[{prefix}\xff', 0, 5)
    print(f"Cities starting with '{prefix}': {suggestions}")
    
    # Search for cities starting with "Sa"
    prefix = 'Sa'
    suggestions = r.zrangebylex(autocomplete_key, f'[{prefix}', f'[{prefix}\xff')
    print(f"Cities starting with '{prefix}': {suggestions}")
    
    # Clean up
    r.delete(autocomplete_key)
    print()

def use_case_trending_topics():
    """Use Case 6: Trending Topics"""
    print("=" * 60)
    print("9. USE CASE: TRENDING TOPICS")
    print("=" * 60)
    
    trending_key = 'trending:topics'
    
    # Topics with engagement scores
    topics = {
        '#python': 1500,
        '#redis': 800,
        '#javascript': 2200,
        '#docker': 1200,
        '#kubernetes': 1800,
        '#aws': 950
    }
    
    r.zadd(trending_key, topics)
    
    # Topic gets more engagement
    r.zincrby(trending_key, 500, '#redis')
    print("#redis gained 500 more engagements")
    
    # Get top 5 trending
    top_trending = r.zrevrange(trending_key, 0, 4, withscores=True)
    print("\n📈 Top 5 Trending:")
    for rank, (topic, score) in enumerate(top_trending, 1):
        print(f"  #{rank}: {topic} - {int(score)} engagements")
    
    # Get topic rank
    redis_rank = r.zrevrank(trending_key, '#redis')
    print(f"\n#redis is now ranked #{redis_rank + 1}")
    
    # Remove topics below threshold
    threshold = 1000
    removed = r.zremrangebyscore(trending_key, '-inf', threshold)
    print(f"Removed {removed} topics below {threshold} engagements")
    
    # Clean up
    r.delete(trending_key)
    print()

def use_case_scheduled_jobs():
    """Use Case 7: Scheduled Jobs"""
    print("=" * 60)
    print("10. USE CASE: SCHEDULED JOBS")
    print("=" * 60)
    
    jobs_key = 'scheduled:jobs'
    
    # Schedule jobs (timestamp as score)
    current_time = time.time()
    jobs = {
        'send_newsletter': current_time + 10,
        'cleanup_old_data': current_time + 5,
        'backup_database': current_time + 15,
        'process_reports': current_time + 3
    }
    
    r.zadd(jobs_key, jobs)
    print(f"Scheduled {r.zcard(jobs_key)} jobs")
    
    # Get jobs ready to execute (score <= current_time)
    ready_jobs = r.zrangebyscore(jobs_key, '-inf', current_time + 6, withscores=True)
    print(f"\nJobs ready to execute:")
    for job_name, scheduled_time in ready_jobs:
        print(f"  - {job_name} (scheduled for {int(scheduled_time)})")
    
    # Process ready jobs
    for job_name, _ in ready_jobs:
        # Remove from scheduled jobs
        r.zrem(jobs_key, job_name)
        print(f"Executed: {job_name}")
    
    # Remaining jobs
    remaining = r.zcard(jobs_key)
    print(f"\nJobs still scheduled: {remaining}")
    
    # Clean up
    r.delete(jobs_key)
    print()

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("REDIS SORTED SETS - COMPREHENSIVE EXAMPLES")
    print("=" * 60 + "\n")
    
    try:
        r.ping()
        
        basic_sorted_set_operations()
        range_operations()
        removal_operations()
        use_case_leaderboard()
        use_case_priority_queue()
        use_case_time_series()
        use_case_rate_limiting_sliding_window()
        use_case_autocomplete()
        use_case_trending_topics()
        use_case_scheduled_jobs()
        
        print("=" * 60)
        print("✓ All examples completed successfully!")
        print("=" * 60)
        
    except redis.ConnectionError:
        print("\n⚠ Please start Redis server before running this script:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
    except Exception as e:
        print(f"\n✗ Error: {e}")
