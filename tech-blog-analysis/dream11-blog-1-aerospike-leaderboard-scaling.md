# Dream11 Blog Analysis #1: Scaling to 700k TPS with Aerospike

**Blog Title**: Scaling to 700k TPS: A Story of High Volume Writes with Aerospike for Dream11's Leaderboard

**Author**: Pratyush Bansal (Lead Software Engineer @Dream11)

**Publication Date**: October 15, 2025

**Source**: [Level Up Coding on Medium](https://levelup.gitconnected.com/scaling-to-700k-tps-a-story-of-high-volume-writes-with-aerospike-for-dream11s-leaderboard-0a226bdf1adf)

---

## Executive Summary

This blog post details Dream11's migration from Apache Cassandra to Aerospike for their real-time leaderboard system. The migration resulted in achieving 700,000 transactions per second (TPS) with just 21 nodes, down from 150 active nodes in Cassandra. The article provides deep insights into handling massive scale: processing 56 million player entries every 10 seconds while serving millions of concurrent users.

---

## Problem Statement

### Scale Requirements
Dream11's leaderboard system faces extraordinary challenges:

- **Massive Volume**: Processing **56 million player entries every 10 seconds**
- **Real-time Updates**: Leaderboards must reflect changes within seconds
- **High Concurrency**: Serving millions of users simultaneously checking rankings
- **Ephemeral Nature**: Data lifecycle spans from match start to completion
- **Iterative Processing**: Supporting **over 100 iterations** of leaderboard calculations
- **Multi-AZ Resilience**: Ensuring availability across availability zones

### Traffic Patterns
- **60 million requests per minute (RPM)** for read operations
- **650,000 TPS** for reads
- **700,000 TPS** for writes during peak
- Millions of concurrent users

---

## Previous Architecture: Apache Cassandra

### Infrastructure Scale
- **150 active nodes** for production
- **220 passive nodes** for disaster recovery scenarios
- **370 total nodes** to manage

### Limitations Encountered

#### 1. Operational Complexity
- Managing 370 nodes became a full-time operational burden
- **Patching and upgrades were complex and time-consuming**
- High operational overhead for routine maintenance

#### 2. Slow Cluster Switching
- Switching from active to passive cluster took **15-20 minutes**
- Unacceptable for a real-time system requiring high availability
- Limited disaster recovery capabilities

#### 3. Poor Scaling Performance
- Adding a new node required **up to 1 hour** for data rebalancing
- Data streaming during node additions was slow
- Difficult to respond to unexpected traffic spikes
- Linear scalability promise didn't hold at their scale

#### 4. Slower Writes - Critical Bottleneck
- High-latency database writes directly impacted Spark calculations
- Pressure on the **10-second refresh SLA** for leaderboard updates
- Write performance was the primary constraint for their use case

---

## Solution: Aerospike Database

### What is Aerospike?

Aerospike is a NoSQL database optimized for:
- **Blistering speed** through in-memory operations
- Highly efficient **key-value store** architecture
- **Low-latency reads and writes** at massive scale
- Flexible storage options (RAM + SSD)

### Why Aerospike?

The decision was based on multiple factors:
1. Superior write performance
2. Simpler operational model
3. Better resource efficiency
4. Built-in cross-AZ replication
5. Flexible dual-namespace architecture

---

## Technical Deep Dive

### 1. Dual Namespace Architecture

Aerospike's **dual namespace architecture** allows independent configuration for different data workloads:

#### Namespace 1: `live_leaderboard` (In-Memory)
```
- Storage: RAM only (maximum speed)
- Replication Factor: 3x (high availability)
- TTL: 1 hour (data refreshed frequently)
- Use Case: High-frequency writes via Spark every 10 seconds
- Access Pattern: Extremely high write + high read
```

**Purpose**: Stores actively updating leaderboard data during live matches

#### Namespace 2: `complete_leaderboard` (Persistent)
```
- Storage: SSD (cost-effective persistence)
- Replication Factor: 3
- TTL: 7 days
- Use Case: Historical data and final standings
- Access Pattern: Low writes, moderate reads
```

**Purpose**: Archives final leaderboard standings for historical analysis

### 2. Hybrid Storage Model

#### Active Match Flow
1. During matches, all leaderboard data lives in **RAM** (`live_leaderboard` namespace)
2. Lightning-fast reads and writes for real-time updates
3. Spark jobs update the entire dataset every 10 seconds
4. Data is automatically evicted after 1 hour TTL

#### Match Completion Flow
1. Final dataset written to `archived_leaderboard` namespace
2. Persisted to **SSD** for long-term storage
3. Automatic cleanup via TTL (7 days)
4. Optimized disk usage without manual intervention

### 3. Cross-AZ Replication and Gossip Protocol

#### Gossip Protocol Benefits
- Each node maintains a **current map of the entire cluster**
- Tracks location of data partitions across nodes
- Enables **self-healing** during node failures
- Automatic detection of outages
- Automatic rebalancing and re-replication

#### Multi-AZ Resilience
- Handles node failures without manual intervention
- Survives entire AZ outages
- Maintains configured **replication factor (RF = 3)**
- No data loss during failures
- Quick recovery times

### 4. Consistency Model: AP vs CP

#### CAP Theorem Considerations
- Aerospike offers two modes: **AP** (Availability + Partition tolerance) and **SC** (Strong Consistency)
- Dream11 chose **AP mode**

#### Why AP Mode?
- **Prioritizes availability** over strict consistency
- Perfect for ephemeral data refreshed every 10 seconds
- Users tolerate eventual consistency for leaderboard data
- System remains available during network partitions
- Better performance characteristics for their use case

---

## Performance Achievements

### Write Performance

#### Peak Throughput: 700,000 TPS
- Achieved on **21-node cluster** (r5n.16xlarge AWS instances)
- Replication Factor: 3 (AP mode)
- Dataset: 15GB (**56 million records** across **2 million contests**)
- Full dataset refresh: **Every 10 seconds**

#### Write Time: 3 Seconds
- Total time to write 15GB dataset: **3 seconds**
- Critical for meeting **10-second refresh SLA**
- 7 seconds remaining for Spark calculations and other operations

#### CPU Utilization: <30%
- Despite 700k TPS, CPU usage stayed below 30%
- **Significant headroom** for traffic spikes
- Resource-efficient architecture
- Allows co-location of other workloads

### Read Performance

#### Traffic Volume
- **60 million requests per minute (RPM)**
- **650,000 TPS** for reads
- Millions of concurrent users

#### Latency Metrics
- **P99 latency: ~5ms**
- Consistently fast user experience
- Low latency from in-memory access
- Single-hop routing optimization

### The Technology Stack Behind Performance

#### Apache Spark Integration
- **Aerospike Connect for Spark** library used
- Spark calculates rankings for 56 million entries
- Distributed processing across multiple nodes and cores
- Parallel batch writes instead of sequential writes
- Optimized data ingestion

#### Partition Architecture
- **Shared-nothing architecture** for high throughput
- **4096 static partitions** per namespace
- Data distributed evenly across all nodes

#### Routing Mechanism (Single-Hop)
1. **Hash calculation**: Client library hashes the record key
2. **Partition ID mapping**: Hash determines partition ID (0-4095)
3. **Direct routing**: Client consults cached partition map
4. **Master node**: Write sent directly to master node for that partition

**Benefits**:
- Eliminates central coordinator bottleneck
- No multiple network hops
- Direct path to correct node every time
- Linear performance scaling with additional nodes
- No hot spots or bottlenecks

---

## Chaos Testing Results

### Test Scenarios
- Deliberately terminated Aerospike nodes across different AZs
- Simulated real-world failure scenarios

### Cluster Response
- **Automatic takeover**: Remaining nodes took over failed node partitions
- **High availability maintained**: No service interruption
- **Recovery time**: Seconds (not minutes)
- **Self-healing verified**: Full capacity restored automatically
- **No data loss**: All writes preserved

---

## Infrastructure Details

### Cluster Configuration
- **Node Count**: 21 nodes
- **Instance Type**: r5n.16xlarge (AWS)
- **Replication Factor**: 3
- **Consistency Mode**: AP (Availability + Partition tolerance)

### Dataset Characteristics
- **Size**: 15GB per refresh
- **Records**: 56 million player entries
- **Contests**: 2 million unique contests
- **Refresh Rate**: Every 10 seconds

### Resource Efficiency
- **CPU**: <30% utilization at peak
- **Memory**: Efficiently used for hot data
- **Network**: Single-hop routing minimizes latency
- **Storage**: Hybrid (RAM + SSD) based on access patterns

---

## Key Learnings and Insights

### 1. Right Tool for the Right Job
- Cassandra is great for many use cases, but not all
- Write-heavy workloads need specialized solutions
- Operational simplicity is as important as performance

### 2. Namespace Architecture Benefits
- Separating hot and cold data optimizes costs and performance
- TTL-based lifecycle management reduces operational overhead
- Hybrid storage (RAM + SSD) balances speed and cost

### 3. Importance of Testing
- Load testing revealed true system capabilities
- Chaos testing validated resilience assumptions
- Real-world scenarios uncover edge cases

### 4. Migration Strategy
- Extensive evaluation before migration
- Performance testing at scale before production
- Clear understanding of consistency requirements

### 5. Resource Efficiency Matters
- 21 nodes vs 150 nodes = massive operational savings
- Low CPU utilization = headroom for growth
- Reduced infrastructure costs

---

## System Design Patterns Demonstrated

### 1. CQRS Pattern
- Separate namespaces for different workloads
- Optimized storage and access patterns per use case
- Independent scaling of read and write paths

### 2. Time-Based Data Lifecycle
- TTL for automatic data cleanup
- Reduces manual intervention
- Prevents storage bloat

### 3. Multi-AZ Resilience
- Geographic distribution of data
- Automatic failover
- No single point of failure

### 4. Client-Side Routing
- Smart client knows cluster topology
- Direct routing to master nodes
- Reduces latency and bottlenecks

### 5. Eventual Consistency Trade-off
- AP over CP for leaderboard use case
- Acceptable for data refreshed every 10 seconds
- Better availability and performance

---

## Technologies Used

### Primary Technologies
1. **Aerospike** - NoSQL database (Key-Value store)
2. **Apache Spark** - Distributed data processing for leaderboard calculations
3. **Aerospike Connect for Spark** - Integration library for high-volume writes
4. **AWS** - Cloud infrastructure (r5n.16xlarge instances)

### Architecture Components
- **Gossip Protocol** - Cluster communication and fault tolerance
- **Partition Map** - Data distribution and routing
- **Cross-AZ Replication** - Multi-availability zone resilience
- **Hybrid Storage** - RAM + SSD combination

---

## Comparison: Cassandra vs Aerospike

| Aspect | Cassandra (Before) | Aerospike (After) |
|--------|-------------------|-------------------|
| Active Nodes | 150 | 21 |
| Total Nodes | 370 | 21 |
| Cluster Switch Time | 15-20 minutes | Seconds |
| Node Addition Time | ~1 hour | Minutes |
| Write Throughput | Bottleneck | 700k TPS |
| CPU Utilization | High | <30% |
| Operational Complexity | Very High | Moderate |
| Read P99 Latency | N/A | ~5ms |

---

## Business Impact

### Operational Benefits
- **86% reduction** in node count (370 → 21)
- Simplified operations and maintenance
- Faster disaster recovery
- Reduced infrastructure costs

### Performance Benefits
- Met strict 10-second refresh SLA
- 700k TPS write capacity
- 5ms P99 read latency
- Headroom for future growth

### User Experience
- Real-time leaderboard updates
- Consistent low-latency experience
- High availability during peak events
- Seamless experience for millions of users

---

## Questions for Further Exploration

### Architecture Questions
1. How is the Spark job architecture designed for the 10-second refresh cycle?
2. What is the full end-to-end leaderboard calculation pipeline?
3. How do they handle data consistency during Spark writes?
4. What monitoring and alerting is in place?

### Scaling Questions
1. What happens during major tournaments with 10x traffic?
2. How do they handle gradual user growth?
3. What's the plan for future scale (100M+ entries)?
4. Cost analysis: Cassandra vs Aerospike at this scale?

### Operational Questions
1. How are backups handled for the persistent namespace?
2. What's the disaster recovery runbook?
3. How do they test leaderboard accuracy?
4. What metrics do they track for system health?

---

## Related Technologies to Explore

### Alternative Databases
- **Redis** - In-memory data store
- **ScyllaDB** - C++ rewrite of Cassandra
- **Apache Ignite** - Distributed database and caching
- **Hazelcast** - In-memory data grid

### Complementary Technologies
- **Apache Flink** - Stream processing (alternative to Spark)
- **Apache Kafka** - Event streaming
- **Prometheus** - Monitoring
- **Grafana** - Metrics visualization

---

## Recommended Reading

### Official Documentation
- [Aerospike Documentation](https://aerospike.com/docs/)
- [Aerospike Architecture](https://aerospike.com/products/database/)
- [Aerospike Connect for Spark](https://docs.aerospike.com/connect/processing/spark)

### Related Concepts
- CAP Theorem and consistency models
- Distributed consensus algorithms
- Gossip protocols in distributed systems
- Time-series data management
- Real-time analytics at scale

---

## Conclusion

This blog post showcases an excellent example of:
1. **Making architectural decisions** based on workload characteristics
2. **Choosing the right database** for write-heavy workloads
3. **Achieving operational simplicity** at scale
4. **Rigorous testing** before migration (load testing + chaos testing)
5. **Clear technical communication** of complex systems

Dream11's migration from Cassandra to Aerospike demonstrates that sometimes the best optimization is choosing a different tool rather than trying to force-fit an existing one. The 86% reduction in infrastructure while achieving 700k TPS is a remarkable achievement.

The key takeaway: **Match your technology to your workload characteristics**, not the other way around.

---

## Next Steps

To build on this knowledge:
1. Study Aerospike's architecture in depth
2. Understand Apache Spark's distributed processing model
3. Learn about the full leaderboard calculation pipeline
4. Explore other Dream11 engineering blog posts
5. Study similar high-scale systems (gaming, fintech, real-time analytics)
