# System Design Implementations

This repository contains hands-on implementations of various system design concepts. Each implementation is designed to be runnable in the terminal and demonstrates real-world use cases.

## Structure

- **redis-implementation/** - Complete Redis learning with examples (5 files, 40+ examples)
- **dynamodb-implementation/** - DynamoDB with real-world patterns and interview prep
- **java-implementations/** - 12 core system design concepts in Java (zero dependencies)

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

### 3. Java System Design Implementations
**Status:** ✅ Complete (12 implementations)

Self-contained Java implementations of core system design building blocks — no external dependencies, just compile and run:

| # | Implementation | Key Concepts |
|---|---------------|-------------|
| 1 | **URL Shortener** | Base62 encoding, collision handling, deduplication |
| 2 | **Rate Limiter** | Token Bucket, Sliding Window, Fixed Window, Leaky Bucket |
| 3 | **Consistent Hashing** | Hash ring, virtual nodes, minimal redistribution |
| 4 | **LRU Cache** | O(1) get/put, TTL support, LFU variant |
| 5 | **Message Queue** | FIFO, Pub/Sub, DLQ, priority queue, consumer groups |
| 6 | **Load Balancer** | Round Robin, Weighted, Least Connections, IP Hash, Health checks |
| 7 | **Distributed KV Store** | Partitioning, replication, vector clocks, quorum |
| 8 | **Bloom Filter** | Probabilistic membership, counting variant, false positive analysis |
| 9 | **Circuit Breaker** | Closed/Open/Half-Open, sliding window, registry |
| 10 | **Notification System** | Multi-channel, priority, rate limiting, retry, templates |
| 11 | **Task Scheduler** | Delayed execution, priority, DAG dependencies, retry |
| 12 | **API Gateway** | Routing, auth middleware, rate limiting, CORS, logging |

**Quick Start:**
```bash
cd java-implementations
javac 01_URLShortener.java && java URLShortener
```

[📖 View Java Implementations →](java-implementations/)

---

## Prerequisites

- Python 3.8+
- Docker (for Redis and DynamoDB Local)
- AWS CLI (optional, for cloud deployments)

## Quick Start

### Java
```bash
# No setup needed — just Java 11+
cd java-implementations
javac 03_ConsistentHashing.java && java ConsistentHashing
javac 08_BloomFilter.java && java BloomFilter
# ... any of the 12 files
```

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
