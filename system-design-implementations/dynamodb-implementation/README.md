# DynamoDB Implementation

Learn DynamoDB through hands-on examples covering core concepts, access patterns, and real-world use cases.

## What's Included

### 01-basics/ - Core Operations
- **01_key_value_operations.py** - PutItem, GetItem, UpdateItem, DeleteItem
- **02_query_operations.py** - Query with partition key, sort key patterns
- **03_scan_operations.py** - Full table scans, filtered scans
- **04_indexes.py** - Global Secondary Indexes (GSI), Local Secondary Indexes (LSI)
- **05_transactions.py** - ACID transactions, conditional writes

### 02-patterns/ - Advanced Design Patterns
- **01_single_table_design.py** - Multi-entity modeling in one table
- **02_time_series_data.py** - IoT data, metrics, event logs
- **03_hierarchical_data.py** - Nested data, adjacency lists
- **04_batch_operations.py** - BatchGetItem, BatchWriteItem optimization

**Total: 40+ production-ready examples**

## Why DynamoDB?

### Used By Major Companies
- **Amazon**: Powers Amazon.com shopping cart (tens of millions of transactions)
- **Netflix**: User profiles, viewing history, recommendations
- **Lyft**: Real-time ride tracking, driver location
- **Airbnb**: Booking system, availability management
- **Snapchat**: User sessions, stories, messaging
- **Samsung**: IoT device management (millions of devices)

### Key Advantages
- **Predictable Performance**: Single-digit millisecond latency at any scale
- **Fully Managed**: No servers to manage, auto-scaling
- **Highly Available**: 99.99% availability SLA across 3 AZs
- **Infinite Scale**: Handle 10+ trillion requests per day

## Quick Start

See **HOW_TO_RUN.md** for complete instructions.

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Configure AWS credentials
aws configure

# 3. Run examples (uses DynamoDB Local by default)
python3 01-basics/01_key_value_operations.py
```

All files are independent and self-runnable.

## Key Concepts

### Primary Key Structures

**1. Simple Primary Key (Partition Key only)**
```
Partition Key: UserID
Use Case: User profiles, simple key-value lookups
```

**2. Composite Primary Key (Partition Key + Sort Key)**
```
Partition Key: UserID
Sort Key: Timestamp
Use Case: User activity logs, order history, time series
```

### Access Patterns

**Query** - Fast, efficient (uses indexes)
- Filter by partition key (required)
- Optional: Filter by sort key range
- Returns items in sort key order

**Scan** - Slow, expensive (reads entire table)
- Use only for: Analytics, backups, migrations
- Avoid in production applications

**GetItem** - Fastest (direct key lookup)
- Retrieve single item by primary key
- Consistent reads available

### Capacity Modes

**On-Demand** (Recommended for Development)
- Pay per request
- No capacity planning needed
- Auto-scales instantly

**Provisioned** (Cost-effective for Predictable Workloads)
- Specify read/write capacity units
- Auto-scaling available
- 50-75% cheaper than on-demand at scale

## Interview Topics Covered

✅ Single-table design patterns  
✅ Access pattern optimization  
✅ Secondary indexes (GSI/LSI)  
✅ Partition key design  
✅ Hot partition mitigation  
✅ Transactions & consistency  
✅ Time series data modeling  
✅ Cost optimization  
✅ DynamoDB vs other databases  

See **INTERVIEW_GUIDE.md** for detailed explanations and real interview questions.
