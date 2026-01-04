# System Design Implementations

This repository contains hands-on implementations of various system design concepts. Each implementation is designed to be runnable in the terminal and demonstrates real-world use cases.

## Structure

- **redis-implementation/** - Complete Redis learning with examples (5 files, 40+ examples)
- **dynamodb-implementation/** - DynamoDB with real-world patterns and interview prep
- More implementations coming soon...

## Implementations

### 1. Redis Implementation
**Status:** ✅ Complete (40+ examples)

Learn Redis through hands-on examples covering:
- **Strings**: Caching, sessions, rate limiting, distributed locks
- **Lists**: Task queues, activity feeds, message queues
- **Sets**: Unique visitors, tagging, social networks
- **Hashes**: User profiles, shopping carts, feature flags
- **Sorted Sets**: Leaderboards, rankings, time series

**Real Companies:** Twitter, Instagram, Uber, GitHub, Stack Overflow

[📖 View Redis Implementation →](redis-implementation/)

---

### 2. DynamoDB Implementation  
**Status:** ✅ Core examples complete + Comprehensive interview guide

Learn AWS DynamoDB with production patterns:
- **Basic Operations**: PutItem, GetItem, UpdateItem, DeleteItem ✅
- **Query Patterns**: Partition key, sort key, composite keys
- **Indexes**: Global Secondary Indexes (GSI), Local Secondary Indexes (LSI)
- **Advanced**: Transactions, conditional writes, single-table design
- **Optimization**: Time series data, hot partition mitigation, cost reduction

**Real Companies:** Amazon, Netflix, Lyft, Airbnb, Snapchat, Samsung

**Interview Prep:** Includes 50+ interview questions with detailed answers covering:
- Partition key design (most critical topic)
- Single-table design patterns (Netflix/Amazon approach)
- Hot partition problem & solutions
- Transactions & ACID operations
- Cost optimization strategies
- DynamoDB vs MongoDB vs PostgreSQL

[📖 View DynamoDB Implementation →](dynamodb-implementation/)

---

## Prerequisites

- Python 3.8+
- Docker (for Redis and DynamoDB Local)
- AWS CLI (optional, for cloud deployments)

## Quick Start

### Redis
```bash
# Start Redis
docker run -p 6379:6379 redis

# Install and run
cd redis-implementation
pip install -r requirements.txt
python3 01-basics/01_strings.py
```

### DynamoDB
```bash
# Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Install and run
cd dynamodb-implementation
pip install -r requirements.txt
python3 01-basics/01_key_value_operations.py
```

## Interview Preparation

Both implementations include comprehensive **INTERVIEW_GUIDE.md** files with:
- Common system design questions
- Real-world company examples
- Performance metrics to mention
- Trade-offs and decision criteria
- Practice problems with solutions

## AWS Account

Account ID: 339712940768 (available for spinning up resources if needed)
