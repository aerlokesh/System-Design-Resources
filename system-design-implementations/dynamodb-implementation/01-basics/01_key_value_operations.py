"""
DynamoDB Basic Key-Value Operations
====================================

This module demonstrates fundamental DynamoDB operations:
- PutItem: Create/Update items
- GetItem: Retrieve single item
- UpdateItem: Modify specific attributes
- DeleteItem: Remove items

Real-world use cases:
- User profiles (Facebook, Twitter)
- Session management (Netflix, Amazon)
- Configuration storage (Feature flags)
- Caching layer (Product details)
"""

import boto3
from boto3.dynamodb.conditions import Key
from botocore.exceptions import ClientError
import json
from decimal import Decimal


class DecimalEncoder(json.JSONEncoder):
    """Helper class to convert Decimal to int/float for JSON serialization"""
    def default(self, obj):
        if isinstance(obj, Decimal):
            return int(obj) if obj % 1 == 0 else float(obj)
        return super(DecimalEncoder, self).default(obj)


def setup_dynamodb():
    """
    Initialize DynamoDB connection
    
    Uses DynamoDB Local by default (localhost:8000)
    To use AWS DynamoDB, remove endpoint_url parameter
    """
    return boto3.resource(
        'dynamodb',
        endpoint_url='http://localhost:8000',  # DynamoDB Local
        region_name='us-east-1',
        aws_access_key_id='fakeKey',
        aws_secret_access_key='fakeSecret'
    )


def create_users_table(dynamodb):
    """
    Create Users table with simple primary key
    
    Schema:
    - Partition Key: user_id (String)
    - No Sort Key (simple key-value lookups)
    
    Use Case: User profiles, session data
    """
    try:
        table = dynamodb.create_table(
            TableName='Users',
            KeySchema=[
                {
                    'AttributeName': 'user_id',
                    'KeyType': 'HASH'  # Partition key
                }
            ],
            AttributeDefinitions=[
                {
                    'AttributeName': 'user_id',
                    'AttributeType': 'S'  # S = String
                }
            ],
            BillingMode='PAY_PER_REQUEST'  # On-demand pricing
        )
        
        # Wait for table to be created
        table.wait_until_exists()
        print("✅ Table created: Users")
        return table
        
    except ClientError as e:
        if e.response['Error']['Code'] == 'ResourceInUseException':
            print("ℹ️  Table already exists: Users")
            return dynamodb.Table('Users')
        else:
            raise


# ====================
# 1. PutItem Operation
# ====================

def example_put_item(table):
    """
    PutItem: Create or completely replace an item
    
    Real-world examples:
    - Facebook: Store user profile
    - Netflix: Save user preferences
    - Amazon: Store product details in cache
    
    Interview Tip: PutItem overwrites entire item if it exists
    """
    print("\n" + "="*60)
    print("EXAMPLE 1: PutItem (Create User Profile)")
    print("="*60)
    
    # Example 1: Simple user profile
    user = {
        'user_id': 'user123',
        'name': 'Alice Johnson',
        'email': 'alice@example.com',
        'age': 28,
        'city': 'San Francisco',
        'premium': True,
        'created_at': '2024-01-15T10:00:00Z'
    }
    
    table.put_item(Item=user)
    print(f"✅ Created user: {user['user_id']}")
    print(json.dumps(user, indent=2, cls=DecimalEncoder))
    
    # Example 2: Overwrite user (PutItem replaces entire item)
    updated_user = {
        'user_id': 'user123',
        'name': 'Alice Smith',  # Changed
        'email': 'alice.smith@example.com',  # Changed
        'age': 29,  # Changed
        'city': 'Los Angeles',  # Changed
        'premium': True
        # Note: created_at is lost! PutItem replaces entire item
    }
    
    table.put_item(Item=updated_user)
    print(f"\n✅ Overwrote user: {updated_user['user_id']}")
    print("⚠️  Warning: created_at field was lost (PutItem replaces entire item)")
    print(json.dumps(updated_user, indent=2, cls=DecimalEncoder))


# ====================
# 2. GetItem Operation
# ====================

def example_get_item(table):
    """
    GetItem: Retrieve item by primary key
    
    Real-world examples:
    - Twitter: Get user profile by user_id
    - Instagram: Fetch post details by post_id
    - LinkedIn: Retrieve connection data
    
    Performance: ~1-3ms latency (O(1) operation)
    Interview Tip: Fastest DynamoDB operation
    """
    print("\n" + "="*60)
    print("EXAMPLE 2: GetItem (Retrieve User)")
    print("="*60)
    
    # Get existing user
    response = table.get_item(
        Key={'user_id': 'user123'}
    )
    
    if 'Item' in response:
        user = response['Item']
        print(f"✅ Found user: {user['user_id']}")
        print(json.dumps(user, indent=2, cls=DecimalEncoder))
    else:
        print("❌ User not found")
    
    # Get non-existent user
    response = table.get_item(
        Key={'user_id': 'user999'}
    )
    
    if 'Item' not in response:
        print(f"\n❌ User 'user999' not found (expected)")


def example_get_item_with_projection(table):
    """
    GetItem with ProjectionExpression
    Retrieve only specific attributes (saves bandwidth & cost)
    
    Use Case: Get user name only for comment display
    """
    print("\n" + "="*60)
    print("EXAMPLE 3: GetItem with Projection (Specific Fields)")
    print("="*60)
    
    # Get only name and email (not entire item)
    response = table.get_item(
        Key={'user_id': 'user123'},
        ProjectionExpression='#n, email',  # Use alias for reserved word
        ExpressionAttributeNames={'#n': 'name'}  # 'name' is reserved
    )
    
    if 'Item' in response:
        user = response['Item']
        print("✅ Retrieved specific fields only:")
        print(json.dumps(user, indent=2, cls=DecimalEncoder))
        print("\n💡 Benefits:")
        print("   - Less data transferred (lower cost)")
        print("   - Faster response (less bytes)")
        print("   - Privacy (don't expose sensitive fields)")


# ========================
# 3. UpdateItem Operation
# ========================

def example_update_item(table):
    """
    UpdateItem: Modify specific attributes without replacing entire item
    
    Real-world examples:
    - Twitter: Increment tweet like count
    - Amazon: Update cart quantity
    - LinkedIn: Add new skill to profile
    
    Interview Tip: Use UpdateItem (not PutItem) to modify fields
    """
    print("\n" + "="*60)
    print("EXAMPLE 4: UpdateItem (Modify Specific Fields)")
    print("="*60)
    
    # Create a user first
    table.put_item(Item={
        'user_id': 'user456',
        'name': 'Bob Smith',
        'email': 'bob@example.com',
        'login_count': 0,
        'premium': False
    })
    print("Created user456")
    
    # Update 1: Set new value
    table.update_item(
        Key={'user_id': 'user456'},
        UpdateExpression='SET city = :city',
        ExpressionAttributeValues={':city': 'New York'}
    )
    print("\n✅ Added city field")
    
    # Update 2: Increment counter (atomic operation)
    table.update_item(
        Key={'user_id': 'user456'},
        UpdateExpression='SET login_count = login_count + :inc',
        ExpressionAttributeValues={':inc': 1}
    )
    print("✅ Incremented login_count (atomic)")
    
    # Update 3: Multiple operations
    response = table.update_item(
        Key={'user_id': 'user456'},
        UpdateExpression='SET premium = :premium, last_login = :time ADD login_count :inc',
        ExpressionAttributeValues={
            ':premium': True,
            ':time': '2024-01-16T15:30:00Z',
            ':inc': 1
        },
        ReturnValues='ALL_NEW'  # Return updated item
    )
    
    print("\n✅ Multiple updates in one operation:")
    print(json.dumps(response['Attributes'], indent=2, cls=DecimalEncoder))


def example_update_list_and_map(table):
    """
    UpdateItem with Lists and Maps
    
    Use Cases:
    - Add item to cart
    - Add tag to post
    - Update nested configuration
    """
    print("\n" + "="*60)
    print("EXAMPLE 5: UpdateItem with Lists and Maps")
    print("="*60)
    
    # Create user with empty lists
    table.put_item(Item={
        'user_id': 'user789',
        'name': 'Charlie Davis',
        'interests': [],
        'settings': {}
    })
    
    # Add to list
    table.update_item(
        Key={'user_id': 'user789'},
        UpdateExpression='SET interests = list_append(interests, :interest)',
        ExpressionAttributeValues={
            ':interest': ['photography', 'travel']
        }
    )
    print("✅ Added interests to list")
    
    # Update nested map
    response = table.update_item(
        Key={'user_id': 'user789'},
        UpdateExpression='SET settings.notifications = :notif, settings.theme = :theme',
        ExpressionAttributeValues={
            ':notif': True,
            ':theme': 'dark'
        },
        ReturnValues='ALL_NEW'
    )
    
    print("\n✅ Updated nested settings:")
    print(json.dumps(response['Attributes'], indent=2, cls=DecimalEncoder))


# =========================
# 4. DeleteItem Operation
# =========================

def example_delete_item(table):
    """
    DeleteItem: Remove item from table
    
    Real-world examples:
    - Twitter: Delete tweet
    - Instagram: Remove photo
    - LinkedIn: Remove connection
    
    Interview Tip: Can return old values before deletion
    """
    print("\n" + "="*60)
    print("EXAMPLE 6: DeleteItem (Remove Item)")
    print("="*60)
    
    # Delete and return old values
    response = table.delete_item(
        Key={'user_id': 'user789'},
        ReturnValues='ALL_OLD'  # Return item before deletion
    )
    
    if 'Attributes' in response:
        print("✅ Deleted user (returning old values):")
        print(json.dumps(response['Attributes'], indent=2, cls=DecimalEncoder))
    
    # Verify deletion
    response = table.get_item(Key={'user_id': 'user789'})
    if 'Item' not in response:
        print("\n✅ Confirmed: user789 no longer exists")


# ================================
# 5. Conditional Write Operations
# ================================

def example_conditional_put(table):
    """
    Conditional PutItem: Prevent overwrites
    
    Real-world examples:
    - LinkedIn: Prevent duplicate connection requests
    - Instagram: Prevent double likes
    - Booking systems: Prevent double reservations
    
    Interview Tip: Use conditions to prevent race conditions
    """
    print("\n" + "="*60)
    print("EXAMPLE 7: Conditional Operations (Prevent Duplicates)")
    print("="*60)
    
    # Try to create user only if doesn't exist
    try:
        table.put_item(
            Item={
                'user_id': 'user123',
                'name': 'New User',
                'email': 'new@example.com'
            },
            ConditionExpression='attribute_not_exists(user_id)'
        )
        print("✅ User created")
    except ClientError as e:
        if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
            print("❌ User already exists (condition failed)")
            print("💡 This prevents accidental overwrites!")
    
    # Conditional update: Only if premium = False
    try:
        table.update_item(
            Key={'user_id': 'user456'},
            UpdateExpression='SET premium = :premium',
            ConditionExpression='premium = :false',
            ExpressionAttributeValues={
                ':premium': True,
                ':false': False
            }
        )
        print("\n✅ Upgraded user to premium (was free)")
    except ClientError as e:
        if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
            print("❌ User already premium (condition failed)")


# ==============================
# 6. Real-World Use Cases
# ==============================

def use_case_user_session(table):
    """
    Use Case: Session Management (Netflix, Amazon)
    
    Requirements:
    - Fast reads (GetItem)
    - Auto-expiration (TTL)
    - Atomic updates
    """
    print("\n" + "="*60)
    print("USE CASE: Session Management")
    print("="*60)
    
    import time
    
    # Create session
    session = {
        'user_id': 'session_user1',
        'session_id': 'sess_abc123',
        'device': 'iPhone 15',
        'ip_address': '192.168.1.100',
        'login_time': int(time.time()),
        'last_activity': int(time.time()),
        'TTL': int(time.time()) + 3600  # Expire in 1 hour
    }
    
    table.put_item(Item=session)
    print("✅ Session created:")
    print(json.dumps(session, indent=2, cls=DecimalEncoder))
    
    # Update last activity (heartbeat)
    table.update_item(
        Key={'user_id': 'session_user1'},
        UpdateExpression='SET last_activity = :time',
        ExpressionAttributeValues={':time': int(time.time())}
    )
    print("\n✅ Session heartbeat updated")
    
    # Validate session
    response = table.get_item(Key={'user_id': 'session_user1'})
    if 'Item' in response:
        session_data = response['Item']
        current_time = int(time.time())
        if session_data['TTL'] > current_time:
            print("\n✅ Session valid")
        else:
            print("\n❌ Session expired")


def use_case_feature_flags(table):
    """
    Use Case: Feature Flags (LaunchDarkly pattern)
    
    Requirements:
    - Fast reads (cached in app)
    - Real-time updates
    - Percentage rollouts
    """
    print("\n" + "="*60)
    print("USE CASE: Feature Flags")
    print("="*60)
    
    # Store feature flags
    flags = [
        {
            'user_id': 'feature_new_ui',
            'enabled': True,
            'rollout_percentage': 50,  # 50% of users
            'description': 'New dashboard UI',
            'environments': ['staging', 'production']
        },
        {
            'user_id': 'feature_beta_search',
            'enabled': False,
            'rollout_percentage': 10,
            'description': 'Experimental search algorithm'
        }
    ]
    
    for flag in flags:
        table.put_item(Item=flag)
    
    print("✅ Feature flags stored:")
    for flag in flags:
        print(f"   - {flag['user_id']}: {flag['enabled']}")
    
    # Check if feature enabled
    response = table.get_item(Key={'user_id': 'feature_new_ui'})
    if 'Item' in response:
        flag = response['Item']
        print(f"\n💡 Feature: {flag['description']}")
        print(f"   Status: {'ENABLED' if flag['enabled'] else 'DISABLED'}")
        print(f"   Rollout: {flag['rollout_percentage']}% of users")


# ==============================
# Main Execution
# ==============================

def main():
    """Run all examples"""
    print("\n" + "🚀 "*30)
    print("DynamoDB Key-Value Operations Examples")
    print("🚀 "*30)
    
    # Setup
    dynamodb = setup_dynamodb()
    table = create_users_table(dynamodb)
    
    # Basic operations
    example_put_item(table)
    example_get_item(table)
    example_get_item_with_projection(table)
    example_update_item(table)
    example_update_list_and_map(table)
    example_delete_item(table)
    
    # Advanced operations
    example_conditional_put(table)
    
    # Real-world use cases
    use_case_user_session(table)
    use_case_feature_flags(table)
    
    # Summary
    print("\n" + "="*60)
    print("SUMMARY: Key Interview Takeaways")
    print("="*60)
    print("""
1. PutItem: Creates OR replaces entire item
   - Use for: Initial creation, full replacement
   - ⚠️  Overwrites all attributes

2. GetItem: Retrieve by primary key (O(1))
   - Fastest operation (~1-3ms)
   - Use ProjectionExpression to fetch specific fields
   
3. UpdateItem: Modify specific attributes
   - Use for: Partial updates, atomic increments
   - More efficient than PutItem for modifications
   
4. DeleteItem: Remove item
   - Can return old values (ReturnValues='ALL_OLD')
   
5. Conditional Operations: Prevent race conditions
   - Use ConditionExpression
   - Prevents overwrites, double-likes, duplicate bookings
    
📚 Performance Characteristics:
   - GetItem: O(1), 1-3ms latency
   - PutItem: O(1), 5-10ms latency
   - UpdateItem: O(1), 5-10ms latency
   - DeleteItem: O(1), 5-10ms latency

💡 Interview Tips:
   - Always use UpdateItem (not PutItem) for modifications
   - Use conditions to prevent race conditions
   - Project only needed attributes (cost optimization)
   - Consider TTL for auto-expiration (sessions, caches)
""")


if __name__ == '__main__':
    main()
