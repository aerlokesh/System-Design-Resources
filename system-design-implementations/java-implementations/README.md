# Java System Design Implementations

Comprehensive, runnable Java implementations of core system design concepts. Each file is self-contained — no external dependencies, just compile and run.

## Implementations

| # | Implementation | Key Concepts | File |
|---|---------------|--------------|------|
| 1 | **URL Shortener** | Base62 encoding, in-memory store, collision handling | `01_URLShortener.java` |
| 2 | **Rate Limiter** | Token Bucket, Sliding Window, Fixed Window, Leaky Bucket | `02_RateLimiter.java` |
| 3 | **Consistent Hashing** | Hash ring, virtual nodes, minimal redistribution | `03_ConsistentHashing.java` |
| 4 | **LRU Cache** | Doubly-linked list + HashMap, O(1) get/put | `04_LRUCache.java` |
| 5 | **Message Queue** | Producer/Consumer, topics, pub/sub, dead letter queue | `05_MessageQueue.java` |
| 6 | **Load Balancer** | Round Robin, Weighted, Least Connections, IP Hash | `06_LoadBalancer.java` |
| 7 | **Distributed Key-Value Store** | Partitioning, replication, vector clocks, quorum | `07_DistributedKVStore.java` |
| 8 | **Bloom Filter** | Probabilistic data structure, multiple hash functions | `08_BloomFilter.java` |
| 9 | **Circuit Breaker** | Closed/Open/Half-Open states, failure threshold | `09_CircuitBreaker.java` |
| 10 | **Notification System** | Multi-channel (Email/SMS/Push), priority queue, retry | `10_NotificationSystem.java` |
| 11 | **Task Scheduler** | Cron-like scheduling, priority, delayed execution | `11_TaskScheduler.java` |
| 12 | **API Gateway** | Routing, auth, rate limiting, request transformation | `12_APIGateway.java` |

## How to Run

Each file is a standalone Java program. No build tools needed.

```bash
cd java-implementations

# Compile and run any file
javac 01_URLShortener.java && java URLShortener
javac 02_RateLimiter.java && java RateLimiter
javac 03_ConsistentHashing.java && java ConsistentHashing
javac 04_LRUCache.java && java LRUCache
javac 05_MessageQueue.java && java MessageQueue
javac 06_LoadBalancer.java && java LoadBalancer
javac 07_DistributedKVStore.java && java DistributedKVStore
javac 08_BloomFilter.java && java BloomFilter
javac 09_CircuitBreaker.java && java CircuitBreaker
javac 10_NotificationSystem.java && java NotificationSystem
javac 11_TaskScheduler.java && java TaskScheduler
javac 12_APIGateway.java && java APIGateway
```

## Interview Tips

Each implementation maps to common system design interview topics:

- **URL Shortener**: Encoding strategies, read-heavy systems, caching
- **Rate Limiter**: API protection, distributed rate limiting, algorithm trade-offs
- **Consistent Hashing**: Data partitioning, virtual nodes, minimal disruption
- **LRU Cache**: Cache eviction policies, O(1) operations, memory management
- **Message Queue**: Async processing, decoupling, at-least-once delivery
- **Load Balancer**: Traffic distribution, health checks, session affinity
- **KV Store**: CAP theorem, replication, consistency models
- **Bloom Filter**: Space-efficient membership testing, false positive rates
- **Circuit Breaker**: Fault tolerance, cascading failure prevention
- **Notification System**: Multi-channel delivery, priority, retry strategies
- **Task Scheduler**: Delayed execution, cron jobs, priority scheduling
- **API Gateway**: Single entry point, cross-cutting concerns, routing

## Prerequisites

- Java 11+ (uses `var`, text blocks, and modern APIs)
- No external libraries required