"""
Redis Lists - Ordered collections of strings
Lists are linked lists, not arrays (O(1) for head/tail operations)

Use Cases:
- Task queues
- Message queues
- Activity feeds
- Recent items (last N items)
- Undo/redo operations
"""

import redis
import time
import json

# Connect to Redis
r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)

def basic_list_operations():
    """Basic list operations"""
    print("=" * 60)
    print("1. BASIC LIST OPERATIONS")
    print("=" * 60)
    
    # LPUSH - Push to left (head)
    r.lpush('tasks', 'task3', 'task2', 'task1')
    print("Pushed tasks to left: task1, task2, task3")
    
    # RPUSH - Push to right (tail)
    r.rpush('tasks', 'task4', 'task5')
    print("Pushed tasks to right: task4, task5")
    
    # LRANGE - Get range (0 to -1 means all)
    all_tasks = r.lrange('tasks', 0, -1)
    print(f"All tasks: {all_tasks}")
    
    # LLEN - Get list length
    length = r.llen('tasks')
    print(f"Task count: {length}")
    
    # LINDEX - Get element at index
    first_task = r.lindex('tasks', 0)
    print(f"First task: {first_task}")
    
    # LSET - Set element at index
    r.lset('tasks', 0, 'task1_updated')
    print(f"Updated first task: {r.lindex('tasks', 0)}")
    
    # Clean up
    r.delete('tasks')
    print()

def pop_operations():
    """Pop operations - removing elements"""
    print("=" * 60)
    print("2. POP OPERATIONS")
    print("=" * 60)
    
    # Setup list
    r.rpush('queue', 'job1', 'job2', 'job3', 'job4')
    print(f"Queue: {r.lrange('queue', 0, -1)}")
    
    # LPOP - Remove and return first element
    first = r.lpop('queue')
    print(f"Popped from left: {first}")
    print(f"Queue after LPOP: {r.lrange('queue', 0, -1)}")
    
    # RPOP - Remove and return last element
    last = r.rpop('queue')
    print(f"Popped from right: {last}")
    print(f"Queue after RPOP: {r.lrange('queue', 0, -1)}")
    
    # BLPOP - Blocking pop (waits if list is empty)
    # Useful for task queues
    r.rpush('blocking_queue', 'task1')
    result = r.blpop('blocking_queue', timeout=2)  # Wait max 2 seconds
    print(f"Blocking pop result: {result}")
    
    # RPOPLPUSH - Atomically pop from one list and push to another
    r.rpush('todo', 'task_a', 'task_b')
    r.rpoplpush('todo', 'in_progress')
    print(f"TODO: {r.lrange('todo', 0, -1)}")
    print(f"IN PROGRESS: {r.lrange('in_progress', 0, -1)}")
    
    # Clean up
    r.delete('queue', 'blocking_queue', 'todo', 'in_progress')
    print()

def list_manipulation():
    """List manipulation operations"""
    print("=" * 60)
    print("3. LIST MANIPULATION")
    print("=" * 60)
    
    # LINSERT - Insert before or after
    r.rpush('fruits', 'apple', 'cherry', 'date')
    r.linsert('fruits', 'before', 'cherry', 'banana')
    print(f"After insert before: {r.lrange('fruits', 0, -1)}")
    
    # LREM - Remove elements
    r.rpush('numbers', '1', '2', '3', '2', '4', '2')
    r.lrem('numbers', 2, '2')  # Remove first 2 occurrences of '2'
    print(f"After removing '2': {r.lrange('numbers', 0, -1)}")
    
    # LTRIM - Keep only specified range
    r.rpush('logs', 'log1', 'log2', 'log3', 'log4', 'log5')
    r.ltrim('logs', 0, 2)  # Keep only first 3 elements
    print(f"After trim (keep first 3): {r.lrange('logs', 0, -1)}")
    
    # Clean up
    r.delete('fruits', 'numbers', 'logs')
    print()

def use_case_task_queue():
    """Use Case 1: Task Queue (Producer-Consumer)"""
    print("=" * 60)
    print("4. USE CASE: TASK QUEUE")
    print("=" * 60)
    
    # Producer adds tasks
    tasks = ['send_email', 'process_image', 'generate_report']
    for task in tasks:
        r.rpush('task_queue', json.dumps({
            'task': task,
            'timestamp': time.time()
        }))
    print(f"Producer: Added {len(tasks)} tasks to queue")
    
    # Consumer processes tasks
    processed = 0
    while r.llen('task_queue') > 0:
        # BLPOP with timeout for graceful shutdown
        result = r.blpop('task_queue', timeout=1)
        if result:
            queue_name, task_data = result
            task = json.loads(task_data)
            print(f"Consumer: Processing {task['task']}")
            time.sleep(0.1)  # Simulate work
            processed += 1
    
    print(f"Consumer: Processed {processed} tasks")
    print()

def use_case_activity_feed():
    """Use Case 2: Activity Feed (Recent Items)"""
    print("=" * 60)
    print("5. USE CASE: ACTIVITY FEED")
    print("=" * 60)
    
    user_id = 'user_123'
    feed_key = f'feed:{user_id}'
    
    # Add activities (newest first)
    activities = [
        'liked a photo',
        'commented on post',
        'shared an article',
        'updated profile',
        'added friend'
    ]
    
    for activity in activities:
        # Add to left (newest first)
        r.lpush(feed_key, json.dumps({
            'action': activity,
            'timestamp': time.time()
        }))
        # Keep only last 50 activities
        r.ltrim(feed_key, 0, 49)
    
    # Get recent 3 activities
    recent = r.lrange(feed_key, 0, 2)
    print("Recent activities:")
    for item in recent:
        activity = json.loads(item)
        print(f"  - {activity['action']}")
    
    # Clean up
    r.delete(feed_key)
    print()

def use_case_reliable_queue():
    """Use Case 3: Reliable Queue (with backup)"""
    print("=" * 60)
    print("6. USE CASE: RELIABLE QUEUE")
    print("=" * 60)
    
    # Main queue and processing queue
    main_queue = 'orders:pending'
    processing_queue = 'orders:processing'
    
    # Add orders
    r.rpush(main_queue, 'order_1', 'order_2', 'order_3')
    print(f"Orders pending: {r.llen(main_queue)}")
    
    # Move to processing (atomic operation)
    order = r.rpoplpush(main_queue, processing_queue)
    print(f"Processing order: {order}")
    print(f"Pending: {r.llen(main_queue)}, Processing: {r.llen(processing_queue)}")
    
    # Simulate processing
    try:
        # Process order...
        print(f"Order {order} processed successfully")
        # Remove from processing queue
        r.lrem(processing_queue, 1, order)
    except Exception as e:
        print(f"Error processing {order}: {e}")
        # Order remains in processing_queue for retry
    
    print(f"After processing - Pending: {r.llen(main_queue)}, Processing: {r.llen(processing_queue)}")
    
    # Clean up
    r.delete(main_queue, processing_queue)
    print()

def use_case_undo_redo():
    """Use Case 4: Undo/Redo Stack"""
    print("=" * 60)
    print("7. USE CASE: UNDO/REDO STACK")
    print("=" * 60)
    
    history_key = 'editor:history'
    redo_key = 'editor:redo'
    
    # User actions
    actions = ['typed "Hello"', 'typed " World"', 'deleted " World"']
    
    for action in actions:
        r.lpush(history_key, action)
        print(f"Action: {action}")
    
    # Undo last action
    undone = r.lpop(history_key)
    r.lpush(redo_key, undone)
    print(f"\nUndo: {undone}")
    print(f"History: {r.lrange(history_key, 0, -1)}")
    
    # Redo
    redone = r.lpop(redo_key)
    r.lpush(history_key, redone)
    print(f"\nRedo: {redone}")
    print(f"History: {r.lrange(history_key, 0, -1)}")
    
    # Clean up
    r.delete(history_key, redo_key)
    print()

def use_case_rate_limit_sliding_window():
    """Use Case 5: Rate Limiting with Sliding Window"""
    print("=" * 60)
    print("8. USE CASE: RATE LIMITING (Sliding Window)")
    print("=" * 60)
    
    user_id = 'user_456'
    rate_key = f'rate:{user_id}'
    max_requests = 5
    window = 60  # 60 seconds
    
    def check_rate_limit():
        current_time = time.time()
        
        # Add current request timestamp
        r.lpush(rate_key, current_time)
        
        # Remove timestamps older than window
        cutoff = current_time - window
        r.ltrim(rate_key, 0, max_requests - 1)
        
        # Count requests in window
        count = r.llen(rate_key)
        
        if count <= max_requests:
            print(f"Request allowed ({count}/{max_requests})")
            return True
        else:
            print(f"Rate limit exceeded ({count}/{max_requests})")
            return False
    
    # Simulate 7 requests
    for i in range(7):
        check_rate_limit()
        time.sleep(0.1)
    
    # Clean up
    r.delete(rate_key)
    print()

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("REDIS LISTS - COMPREHENSIVE EXAMPLES")
    print("=" * 60 + "\n")
    
    try:
        r.ping()
        
        basic_list_operations()
        pop_operations()
        list_manipulation()
        use_case_task_queue()
        use_case_activity_feed()
        use_case_reliable_queue()
        use_case_undo_redo()
        use_case_rate_limit_sliding_window()
        
        print("=" * 60)
        print("✓ All examples completed successfully!")
        print("=" * 60)
        
    except redis.ConnectionError:
        print("\n⚠ Please start Redis server before running this script:")
        print("   docker run -d --name redis-learning -p 6379:6379 redis:latest")
    except Exception as e:
        print(f"\n✗ Error: {e}")
