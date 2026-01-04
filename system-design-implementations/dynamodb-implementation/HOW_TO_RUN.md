# How to Run DynamoDB Examples

## Prerequisites

You can run these examples in two ways:
1. **DynamoDB Local** (Recommended for learning) - Free, runs on your machine
2. **AWS DynamoDB** (Production) - Requires AWS account

---

## Option 1: DynamoDB Local (Recommended)

### Step 1: Install DynamoDB Local

**Using Docker (Easiest):**
```bash
# Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Keep this terminal running
```

**Using Java (Alternative):**
```bash
# Download DynamoDB Local
cd ~
mkdir dynamodb-local
cd dynamodb-local
wget https://s3-us-west-2.amazonaws.com/dynamodb-local/dynamodb_local_latest.tar.gz
tar -xzf dynamodb_local_latest.tar.gz

# Run DynamoDB Local
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb

# Keep this terminal running
```

### Step 2: Install Dependencies
```bash
cd system-design-implementations/dynamodb-implementation
pip install -r requirements.txt
```

### Step 3: Run Examples
```bash
# Run any example file
python3 01-basics/01_key_value_operations.py
python3 01-basics/02_query_operations.py
python3 01-basics/03_scan_operations.py
python3 01-basics/04_indexes.py
python3 01-basics/05_transactions.py

# Run advanced patterns
python3 02-patterns/01_single_table_design.py
python3 02-patterns/02_time_series_data.py
python3 02-patterns/03_hierarchical_data.py
python3 02-patterns/04_batch_operations.py
```

---

## Option 2: AWS DynamoDB (Production)

### Step 1: Configure AWS Credentials
```bash
# Install AWS CLI if not already installed
# macOS:
brew install awscli

# Linux:
sudo apt-get install awscli

# Configure with your credentials
aws configure
# Enter:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (e.g., us-east-1)
# - Default output format (json)
```

### Step 2: Install Dependencies
```bash
cd system-design-implementations/dynamodb-implementation
pip install -r requirements.txt
```

### Step 3: Update Examples to Use AWS
Each example file defaults to DynamoDB Local. To use AWS DynamoDB:

**Open any example file and modify:**
```python
# Change this line:
dynamodb = boto3.resource(
    'dynamodb',
    endpoint_url='http://localhost:8000'  # Remove this line
)

# To this:
dynamodb = boto3.resource(
    'dynamodb',
    region_name='us-east-1'  # Your AWS region
)
```

### Step 4: Run Examples
```bash
python3 01-basics/01_key_value_operations.py
# ... etc
```

**⚠️ Cost Warning:** AWS DynamoDB charges for:
- Storage ($0.25 per GB-month)
- Read/Write requests (On-Demand mode)
- Use DynamoDB Local for learning to avoid charges

---

## What Each Example Demonstrates

### 01-basics/ - Core Operations

**01_key_value_operations.py**
- PutItem: Create items
- GetItem: Retrieve items
- UpdateItem: Modify items
- DeleteItem: Remove items
- Use cases: User profiles, sessions, caching

**02_query_operations.py**
- Query with partition key
- Query with sort key conditions (>, <, between)
- Query with filters
- Use cases: User history, time-based data, activity logs

**03_scan_operations.py**
- Full table scans
- Filtered scans
- Pagination
- Performance comparison: Scan vs Query
- Use cases: Analytics, backups, migrations

**04_indexes.py**
- Global Secondary Indexes (GSI)
- Local Secondary Indexes (LSI)
- Multiple access patterns
- Use cases: Search, filtering, sorting

**05_transactions.py**
- ACID transactions
- Conditional writes
- Atomic operations
- Use cases: Financial transactions, inventory management

### 02-patterns/ - Advanced Design Patterns

**01_single_table_design.py**
- Multi-entity modeling
- One table for entire app
- Composite keys
- Use cases: SaaS apps, multi-tenant systems

**02_time_series_data.py**
- IoT sensor data
- Metrics collection
- Time bucketing
- TTL for auto-deletion
- Use cases: IoT, monitoring, analytics

**03_hierarchical_data.py**
- Nested relationships
- Parent-child queries
- Adjacency lists
- Use cases: Org charts, file systems, categories

**04_batch_operations.py**
- BatchGetItem (read multiple)
- BatchWriteItem (write multiple)
- Performance optimization
- Use cases: Bulk operations, ETL, migrations

---

## Verifying DynamoDB Local is Running

```bash
# Test connection
aws dynamodb list-tables --endpoint-url http://localhost:8000

# Expected output: {"TableNames": []}

# If error: "Could not connect to the endpoint URL"
# Solution: Start DynamoDB Local (see Step 1)
```

---

## Exploring Tables with AWS CLI

```bash
# List all tables
aws dynamodb list-tables --endpoint-url http://localhost:8000

# Describe a table
aws dynamodb describe-table \
    --table-name Users \
    --endpoint-url http://localhost:8000

# Scan table (get all items)
aws dynamodb scan \
    --table-name Users \
    --endpoint-url http://localhost:8000

# Query table
aws dynamodb query \
    --table-name Users \
    --key-condition-expression "user_id = :uid" \
    --expression-attribute-values '{":uid":{"S":"user123"}}' \
    --endpoint-url http://localhost:8000

# Delete a table
aws dynamodb delete-table \
    --table-name Users \
    --endpoint-url http://localhost:8000
```

---

## GUI Tools for DynamoDB

### NoSQL Workbench (Recommended)
```bash
# Download from AWS:
https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/workbench.html

# Features:
- Visual table designer
- Query builder
- Data modeling
- Import/Export data
- Works with DynamoDB Local and AWS
```

### VS Code Extension
```bash
# Install "AWS Toolkit" extension
# Features:
- Browse DynamoDB tables
- Run queries
- View items
- Integrated with AWS credentials
```

---

## Troubleshooting

### Problem: "Unable to locate credentials"
**Solution:**
```bash
# For DynamoDB Local (no credentials needed):
# Ensure you're using endpoint_url in code:
dynamodb = boto3.resource(
    'dynamodb',
    endpoint_url='http://localhost:8000',
    region_name='us-east-1',  # Required but not used
    aws_access_key_id='fakeKey',  # DynamoDB Local doesn't validate
    aws_secret_access_key='fakeSecret'
)

# For AWS DynamoDB:
aws configure  # Set up real credentials
```

### Problem: "Could not connect to endpoint URL"
**Solution:**
```bash
# Verify DynamoDB Local is running:
docker ps  # Should see amazon/dynamodb-local

# If not running:
docker run -p 8000:8000 amazon/dynamodb-local
```

### Problem: "ResourceNotFoundException: Cannot do operations on a non-existent table"
**Solution:**
```python
# Each example creates its own tables
# Run the entire script, not individual functions

# Or create table manually:
table = dynamodb.create_table(
    TableName='Users',
    KeySchema=[
        {'AttributeName': 'user_id', 'KeyType': 'HASH'}
    ],
    AttributeDefinitions=[
        {'AttributeName': 'user_id', 'AttributeType': 'S'}
    ],
    BillingMode='PAY_PER_REQUEST'
)
table.wait_until_exists()
```

### Problem: "ValidationException: One or more parameter values were invalid"
**Solution:**
- Check attribute names match table schema
- Verify data types (S=String, N=Number, B=Binary)
- Ensure partition key and sort key are provided

---

## Performance Testing

### Measure Operation Latency
```python
import time

# Measure GetItem latency
start = time.time()
response = table.get_item(Key={'user_id': 'user123'})
latency = (time.time() - start) * 1000  # Convert to ms
print(f"GetItem latency: {latency:.2f}ms")

# Expected:
# DynamoDB Local: 5-20ms
# AWS DynamoDB: 1-10ms (depending on region)
```

### Batch vs Individual Operations
```python
# Run 04_batch_operations.py to see:
# - Individual writes: 100 operations = ~2 seconds
# - Batch writes: 100 items = ~0.3 seconds
# - 6-7x faster with batching!
```

---

## Learning Path

**Beginner:**
1. Start with `01_key_value_operations.py` - Understand basic CRUD
2. Run `02_query_operations.py` - Learn Query vs GetItem
3. Experiment with `03_scan_operations.py` - See why Scans are expensive

**Intermediate:**
4. Study `04_indexes.py` - Master GSI/LSI for multiple access patterns
5. Practice `05_transactions.py` - Understand ACID operations

**Advanced:**
6. Deep dive `01_single_table_design.py` - Learn Netflix/Amazon approach
7. Analyze `02_time_series_data.py` - Optimize for high-volume data
8. Review `INTERVIEW_GUIDE.md` - Prepare for system design interviews

---

## Interview Preparation

After running all examples, practice these:

1. **Design Questions:**
   - "Design Instagram's data model in DynamoDB"
   - "How would you handle a celebrity user with 10M followers?"
   - "Implement a shopping cart with DynamoDB"

2. **Optimization Questions:**
   - "Your DynamoDB bill is $10,000/month. How do you reduce it?"
   - "How do you avoid hot partitions?"
   - "Query vs Scan: When to use each?"

3. **Architecture Questions:**
   - "DynamoDB vs MongoDB vs PostgreSQL - when to use each?"
   - "How does DynamoDB achieve single-digit millisecond latency?"
   - "Explain eventual consistency vs strong consistency"

See **INTERVIEW_GUIDE.md** for detailed answers and examples.

---

That's it! Happy learning! 🚀

**Questions?** Each example file is thoroughly commented with explanations.
