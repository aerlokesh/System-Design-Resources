# Technology to AWS Mapping Guide for System Design Interviews

## 🎯 Purpose
This guide helps AWS engineers understand popular system design technologies by drawing direct parallels to AWS services you already know. Perfect for interview preparation!

---

## 📊 Table of Contents
1. [Databases](#databases)
2. [Caching Solutions](#caching-solutions)
3. [Message Queues & Event Streaming](#message-queues--event-streaming)
4. [Data Warehousing & Analytics](#data-warehousing--analytics)
5. [Search & Indexing](#search--indexing)
6. [Distributed Computing](#distributed-computing)
7. [Object Storage & File Systems](#object-storage--file-systems)
8. [Monitoring & Observability](#monitoring--observability)
9. [Serverless & Functions](#serverless--functions)
10. [API Gateways](#api-gateways)
11. [Workflow Orchestration](#workflow-orchestration)
12. [Data Integration & ETL](#data-integration--etl)
13. [Container Registries](#container-registries)
14. [Configuration Management](#configuration-management)
15. [DNS Services](#dns-services)
16. [Machine Learning](#machine-learning)
17. [IoT Platforms](#iot-platforms)
18. [Media Processing](#media-processing)
19. [BI & Analytics Visualization](#bi--analytics-visualization)
20. [Additional AWS Services](#additional-aws-services)
21. [Quick Reference Table](#quick-reference-table)

---

## 🗄️ Databases

### PostgreSQL
**What it is:** Open-source relational database (RDBMS) with ACID compliance, advanced SQL features, and excellent data integrity.

**AWS Equivalent:** **Amazon RDS PostgreSQL** or **Amazon Aurora PostgreSQL**

**Key Parallels:**
- **ACID Transactions:** Just like RDS, guarantees data consistency
- **SQL Engine:** Standard SQL with advanced features (JSON support, full-text search)
- **Replication:** Similar to RDS Multi-AZ and Read Replicas
- **Use Cases:** Transactional workloads, complex queries, data integrity requirements

**When to mention in interviews:**
- "For ACID-compliant relational data, I'd use PostgreSQL (similar to RDS)"
- "PostgreSQL offers advanced indexing like B-trees and GIN indexes, comparable to Aurora's capabilities"

**Key Features:**
- JSONB support for semi-structured data
- Advanced indexing (B-tree, Hash, GiST, GIN)
- MVCC (Multi-Version Concurrency Control) - like Aurora's version control
- Strong consistency guarantees

---

### MySQL
**What it is:** Popular open-source RDBMS, known for speed and reliability.

**AWS Equivalent:** **Amazon RDS MySQL** or **Amazon Aurora MySQL**

**Key Parallels:**
- **High Performance:** Similar optimization as Aurora MySQL
- **Replication:** Master-slave replication like RDS Read Replicas
- **Storage Engines:** InnoDB (default) similar to Aurora's storage architecture
- **Use Cases:** Web applications, e-commerce, content management

**When to mention in interviews:**
- "MySQL works well for read-heavy workloads, similar to Aurora MySQL with its 15 read replicas"
- "For web-scale applications, MySQL (like RDS) provides reliable ACID transactions"

**Key Features:**
- InnoDB storage engine for ACID compliance
- Query cache (similar to RDS query caching)
- Partitioning for large tables
- Strong community support

---

### Cassandra
**What it is:** Distributed NoSQL database designed for high availability, massive scale, and no single point of failure.

**AWS Equivalent:** **Amazon Keyspaces (for Apache Cassandra)** or **DynamoDB**

**Key Parallels:**
- **Distributed Architecture:** Like DynamoDB, data is partitioned across nodes
- **High Availability:** Multi-region, multi-datacenter - similar to DynamoDB Global Tables
- **Eventual Consistency:** Tunable consistency levels (similar to DynamoDB's eventual consistency option)
- **Write-Optimized:** Like DynamoDB, excellent for write-heavy workloads
- **No Single Point of Failure:** Peer-to-peer architecture (DynamoDB achieves this differently)

**When to mention in interviews:**
- "For massive scale writes with high availability, I'd use Cassandra (similar to DynamoDB's architecture)"
- "Cassandra's ring architecture provides similar fault tolerance to DynamoDB's multi-AZ deployment"

**Key Features:**
- **CQL (Cassandra Query Language):** SQL-like syntax
- **Tunable Consistency:** Choose between consistency and availability (CAP theorem)
- **Linear Scalability:** Add nodes without downtime (like DynamoDB auto-scaling)
- **Time-Series Data:** Excellent for IoT, metrics (similar to Amazon Timestream)
- **Wide Column Store:** Think of it as DynamoDB with more flexible schema

**Use Cases:**
- Time-series data (IoT sensor data, metrics)
- High-volume writes (logging, analytics events)
- Geographically distributed applications
- When you need 99.999% availability

**Architecture Comparison:**
```
Cassandra:                    DynamoDB:
- Ring topology               - Managed partitions
- Tunable consistency         - Eventual/Strong consistency
- Manual node management      - Fully managed
- Self-managed backups        - Point-in-time recovery built-in
```

---

### MongoDB
**What it is:** Document-oriented NoSQL database using JSON-like documents.

**AWS Equivalent:** **Amazon DocumentDB** or **DynamoDB (for flexible schema)**

**Key Parallels:**
- **Document Store:** JSON documents, similar to DocumentDB
- **Flexible Schema:** Like DynamoDB, no fixed schema required
- **Indexing:** Rich indexing capabilities (like DocumentDB)
- **Aggregation Pipeline:** Complex queries (similar to DocumentDB aggregation)

**When to mention in interviews:**
- "MongoDB's document model works like DocumentDB, great for flexible schemas"
- "For rapid prototyping with evolving schemas, MongoDB (like DynamoDB) provides flexibility"

**Key Features:**
- **BSON Format:** Binary JSON for efficient storage
- **Replication:** Replica sets (like RDS Multi-AZ)
- **Sharding:** Horizontal scaling (like DynamoDB partitioning)
- **Aggregation Framework:** Powerful data processing pipeline
- **Change Streams:** Real-time data changes (similar to DynamoDB Streams)

**Use Cases:**
- Content management systems
- Real-time analytics
- Mobile/web applications with evolving schema
- Catalogs with varied attributes

---

### Redis (Database Mode)
**What it is:** In-memory data structure store, also used as a database with persistence.

**AWS Equivalent:** **Amazon ElastiCache for Redis** or **Amazon MemoryDB for Redis**

**Key Parallels:**
- **In-Memory:** Ultra-fast like ElastiCache
- **Persistence Options:** Similar to MemoryDB's durability
- **Replication:** Master-replica setup like ElastiCache
- **Data Structures:** Rich data types beyond key-value

**When to mention in interviews:**
- "Redis as a database provides durability, similar to MemoryDB for Redis"
- "For sub-millisecond latency with persistence, Redis (like MemoryDB) is ideal"

---

## ⚡ Caching Solutions

### Redis (Cache Mode)
**What it is:** In-memory key-value store, the most popular caching solution.

**AWS Equivalent:** **Amazon ElastiCache for Redis**

**Key Parallels:**
- **In-Memory Storage:** Microsecond latency like ElastiCache
- **Data Structures:** Strings, hashes, lists, sets, sorted sets
- **TTL Support:** Automatic expiration like ElastiCache
- **Pub/Sub:** Messaging capabilities (like SNS for caching layers)
- **Cluster Mode:** Sharding for scalability (like ElastiCache Redis Cluster)

**When to mention in interviews:**
- "Redis cache reduces database load, just like ElastiCache in front of RDS"
- "For session storage, Redis (ElastiCache) provides fast access with replication"

**Key Features:**
- **Sub-millisecond latency:** Faster than ElastiCache (single-digit microseconds)
- **Rich Data Types:**
  - Strings (simple key-value)
  - Hashes (objects with fields)
  - Lists (ordered collections)
  - Sets (unique elements)
  - Sorted Sets (scored elements - leaderboards)
  - Bitmaps, HyperLogLogs, Streams
- **Atomic Operations:** INCR, DECR for counters (like DynamoDB atomic counters)
- **Lua Scripting:** Complex operations (similar to DynamoDB transactions)

**Common Use Cases:**
- **Session Store:** User sessions with TTL
- **Leaderboards:** Sorted sets for rankings
- **Rate Limiting:** INCR with EXPIRE (like API Gateway throttling)
- **Real-time Analytics:** Counting, trending data
- **Cache-Aside Pattern:** Similar to ElastiCache implementation

**Cache Strategies:**
```
1. Cache-Aside (Lazy Loading):
   - Like: CloudFront + S3
   - Read: Check Redis → If miss, read DB → Store in Redis

2. Write-Through:
   - Like: ElastiCache write-through
   - Write: Write to DB → Write to Redis

3. Write-Behind:
   - Write to Redis → Async write to DB
```

---

### Memcached
**What it is:** Simple, high-performance distributed memory caching system.

**AWS Equivalent:** **Amazon ElastiCache for Memcached**

**Key Parallels:**
- **Simple Key-Value:** Simpler than Redis, like basic ElastiCache
- **Multi-threaded:** Better CPU utilization than Redis
- **Distributed:** Sharding across nodes (like ElastiCache cluster)
- **LRU Eviction:** Automatic memory management

**When to mention in interviews:**
- "Memcached is simpler than Redis, good for basic caching like ElastiCache Memcached"
- "For horizontally scaled caching without persistence, Memcached works like ElastiCache"

**Redis vs Memcached:**
```
Redis (ElastiCache Redis):        Memcached (ElastiCache Memcached):
- Rich data structures            - Simple key-value only
- Persistence options             - No persistence
- Replication support             - No replication
- Single-threaded                 - Multi-threaded
- More features                   - Simpler, faster for basic use
```

**When to use Memcached:**
- Simple caching needs
- Need multi-threading
- Don't need persistence or data structures
- Pure cache (not database)

---

## 📨 Message Queues & Event Streaming

### Apache Kafka
**What it is:** Distributed event streaming platform for high-throughput, fault-tolerant message processing.

**AWS Equivalent:** **Amazon MSK (Managed Streaming for Kafka)** or **Amazon Kinesis Data Streams**

**Key Parallels:**
- **Event Streaming:** Like Kinesis Data Streams for real-time data
- **Publish-Subscribe:** Similar to SNS/SQS but with replay capability
- **Partitioning:** Like Kinesis shards for parallel processing
- **Consumer Groups:** Multiple consumers (like SQS with multiple workers)
- **Retention:** Messages retained for replay (Kinesis retains for 24h-365 days)

**When to mention in interviews:**
- "Kafka provides event streaming similar to Kinesis, with message replay capability"
- "For real-time analytics pipeline, Kafka (like MSK) connects producers and consumers"

**Key Features:**
- **High Throughput:** Millions of messages/second
- **Durability:** Replicated across brokers (like Kinesis multi-AZ)
- **Topics & Partitions:** Organize data streams (similar to Kinesis shards)
- **Consumer Groups:** Parallel processing with load balancing
- **Message Replay:** Read historical data (unlike SQS which deletes after consumption)
- **Exactly-Once Semantics:** Guaranteed message delivery

**Kafka vs Kinesis:**
```
Kafka (MSK):                     Kinesis Data Streams:
- Topics with partitions         - Streams with shards
- Unlimited retention            - 24h to 365 days retention
- Horizontal scaling             - Auto-scaling with shards
- Self-managed (or MSK)          - Fully managed
- Pull-based consumers           - Pull-based (GetRecords)
- Kafka Connect ecosystem        - Kinesis Data Firehose
```

**Use Cases:**
- **Real-time Analytics:** Like Kinesis + Lambda
- **Log Aggregation:** Centralized logging (similar to CloudWatch Logs)
- **Event Sourcing:** Store all state changes
- **Stream Processing:** With Kafka Streams (like Kinesis Analytics)
- **Microservices Communication:** Event-driven architecture

**Architecture Pattern:**
```
Producers → Kafka Topics → Consumer Groups → Processing
   ↓
(Similar to)
   ↓
Services → Kinesis Streams → Lambda Functions → Storage
```

---

### RabbitMQ
**What it is:** Message broker implementing AMQP (Advanced Message Queuing Protocol).

**AWS Equivalent:** **Amazon MQ** or **Amazon SQS**

**Key Parallels:**
- **Message Queue:** Like SQS for async processing
- **Routing:** Exchanges and queues (like SNS → SQS fanout)
- **Acknowledgments:** Message delivery guarantees (like SQS visibility timeout)
- **Dead Letter Queues:** Failed message handling (same as SQS DLQ)

**When to mention in interviews:**
- "RabbitMQ works like SQS but with more routing flexibility via exchanges"
- "For complex routing patterns, RabbitMQ (Amazon MQ) offers exchanges similar to SNS topics"

**Key Features:**
- **Exchanges:** Route messages to queues
  - **Direct:** Route by key (like SQS with message attributes)
  - **Fanout:** Broadcast to all queues (like SNS)
  - **Topic:** Pattern matching (like SNS filter policies)
  - **Headers:** Route by headers
- **Message Priority:** Urgent messages first
- **TTL (Time-to-Live):** Message expiration
- **Clustering:** High availability (like SQS multi-AZ)

**RabbitMQ vs SQS:**
```
RabbitMQ:                        SQS:
- Complex routing (exchanges)    - Simple queue
- Push-based (to consumers)      - Pull-based (polling)
- Lower latency                  - Higher latency but scalable
- Self-managed (or Amazon MQ)    - Fully managed
- AMQP protocol                  - AWS SDK/HTTP
```

**Use Cases:**
- Task queues for background jobs
- Microservices communication
- Workflow orchestration
- Request/reply patterns

---

### Apache Pulsar
**What it is:** Distributed pub-sub messaging system with streaming capabilities.

**AWS Equivalent:** **Amazon Kinesis** or **Amazon SNS + SQS**

**Key Parallels:**
- **Pub-Sub + Queuing:** Combines SNS and SQS features
- **Multi-tenancy:** Like AWS accounts with resource isolation
- **Geo-replication:** Similar to DynamoDB Global Tables
- **Tiered Storage:** Hot/cold storage (like S3 Intelligent-Tiering)

**When to mention in interviews:**
- "Pulsar combines pub-sub and queuing, similar to SNS + SQS but unified"

---

## 🏢 Data Warehousing & Analytics

### Snowflake
**What it is:** Cloud-based data warehouse platform for analytics and data sharing.

**AWS Equivalent:** **Amazon Redshift**

**Key Parallels:**
- **Columnar Storage:** Like Redshift for fast analytics
- **Separation of Compute & Storage:** Like Redshift RA3 nodes
- **Auto-Scaling:** Compute scales independently (like Redshift concurrency scaling)
- **Data Sharing:** Share data between accounts (like Redshift data sharing)
- **Time Travel:** Query historical data (like Redshift snapshots)

**When to mention in interviews:**
- "Snowflake's architecture is similar to Redshift with compute-storage separation"
- "For data warehousing, Snowflake (like Redshift) uses columnar storage for fast queries"

**Key Features:**
- **Multi-Cluster Architecture:** Separate compute for different workloads
- **Zero-Copy Cloning:** Instant data copies (like Redshift snapshots)
- **Semi-Structured Data:** Native JSON, Avro, Parquet support
- **Automatic Clustering:** Self-optimizing (like Redshift auto-vacuum)
- **Secure Data Sharing:** Share live data across organizations

**Snowflake vs Redshift:**
```
Snowflake:                       Redshift:
- Compute scales independently   - RA3: storage scales independently
- Pay per second                 - Hourly billing
- No manual tuning needed        - Some tuning required
- Multi-cloud (AWS, Azure, GCP)  - AWS only
- Automatic scaling              - Concurrency scaling
```

**Use Cases:**
- Business intelligence and analytics
- Data lake analytics
- Data science workloads
- Real-time dashboards
- Cross-organization data sharing

---

### Google BigQuery
**What it is:** Serverless, highly scalable data warehouse for analytics.

**AWS Equivalent:** **Amazon Redshift Serverless** or **Amazon Athena**

**Key Parallels:**
- **Serverless:** Like Athena, no infrastructure to manage
- **Pay per Query:** Similar to Athena's pay-per-scan model
- **Columnar Storage:** Like Redshift for analytics
- **Standard SQL:** Similar query syntax
- **Petabyte Scale:** Handle massive datasets like Redshift

**When to mention in interviews:**
- "BigQuery's serverless model is similar to Athena for SQL analytics"
- "For ad-hoc queries on large datasets, BigQuery (like Athena) charges per data scanned"

**Key Features:**
- **Standard SQL:** ANSI SQL compatible
- **Real-time Analytics:** Stream data ingestion (like Kinesis → Redshift)
- **Machine Learning:** Built-in ML functions (like SageMaker integration)
- **Geospatial Analysis:** GIS functions
- **BI Engine:** In-memory caching for dashboards

**BigQuery vs Athena:**
```
BigQuery:                        Athena:
- Serverless                     - Serverless
- Pay per data processed         - Pay per data scanned
- Storage included               - Uses S3 for storage
- Caching included               - No caching (use results bucket)
- Streaming inserts              - Batch loads or Kinesis Firehose
```

---

### Apache Spark
**What it is:** Unified analytics engine for large-scale data processing.

**AWS Equivalent:** **Amazon EMR (Elastic MapReduce)** or **AWS Glue**

**Key Parallels:**
- **Distributed Processing:** Like EMR for big data
- **In-Memory Computing:** Faster than disk-based (EMR supports Spark)
- **Batch & Streaming:** Both modes (like Glue for ETL)
- **SQL Support:** Spark SQL (like Athena)
- **Machine Learning:** MLlib (like SageMaker)

**When to mention in interviews:**
- "Spark runs on EMR for distributed data processing"
- "For ETL pipelines, Spark (via Glue or EMR) processes large datasets in parallel"

**Key Features:**
- **RDD (Resilient Distributed Datasets):** Fault-tolerant data structures
- **DataFrames & Datasets:** High-level APIs (like Athena tables)
- **Lazy Evaluation:** Optimizes execution plan
- **Catalyst Optimizer:** Query optimization
- **Spark Streaming:** Real-time processing (like Kinesis Analytics)

**Use Cases:**
- ETL pipelines (like Glue jobs)
- Real-time stream processing
- Machine learning at scale
- Graph processing
- Log analysis

---

### Presto/Trino
**What it is:** Distributed SQL query engine for big data analytics.

**AWS Equivalent:** **Amazon Athena** (runs on Presto)

**Key Parallels:**
- **Query Federation:** Query multiple data sources (like Athena Federated Query)
- **Standard SQL:** ANSI SQL support
- **Scalable:** Distributed query execution
- **No ETL Required:** Query data in place (like Athena on S3)

**When to mention in interviews:**
- "Athena is built on Presto for SQL queries across S3"
- "Presto enables querying multiple data sources, similar to Athena's federated queries"

---

## 🔍 Search & Indexing

### Elasticsearch
**What it is:** Distributed search and analytics engine based on Apache Lucene.

**AWS Equivalent:** **Amazon OpenSearch Service** (formerly Elasticsearch Service)

**Key Parallels:**
- **Full-Text Search:** Like OpenSearch for search functionality
- **Log Analytics:** Similar to CloudWatch Logs Insights
- **Real-time Indexing:** Like OpenSearch domains
- **RESTful API:** HTTP-based queries
- **Distributed:** Sharding and replication (like OpenSearch multi-AZ)

**When to mention in interviews:**
- "Elasticsearch provides full-text search, similar to OpenSearch Service"
- "For log analytics, Elasticsearch (like OpenSearch) indexes logs for fast search"

**Key Features:**
- **Inverted Index:** Fast text search (like OpenSearch)
- **Aggregations:** Analytics queries (similar to CloudWatch metrics)
- **Near Real-time Search:** Sub-second indexing
- **Scalability:** Add nodes horizontally
- **Kibana:** Visualization (like OpenSearch Dashboards)
- **ELK Stack:** Elasticsearch + Logstash + Kibana (like CloudWatch + OpenSearch)

**Common Use Cases:**
- **Application Search:** Product search, content search
- **Log Analytics:** Centralized logging (like CloudWatch)
- **Metrics & Monitoring:** Time-series data (like CloudWatch Metrics)
- **Security Analytics:** SIEM use cases
- **Business Analytics:** Real-time dashboards

**Elasticsearch vs OpenSearch:**
```
Elasticsearch:                   OpenSearch:
- Open source (with licensing)   - Open source (Apache 2.0)
- Elastic company                - AWS and community maintained
- Kibana for visualization       - OpenSearch Dashboards
- X-Pack features                - Security plugin built-in
```

**Architecture Pattern:**
```
Application Logs → Logstash → Elasticsearch → Kibana
       ↓
(Similar to)
       ↓
Application → CloudWatch Logs → OpenSearch → Dashboards
```

---

### Apache Solr
**What it is:** Open-source search platform built on Apache Lucene.

**AWS Equivalent:** **Amazon CloudSearch** or **Amazon OpenSearch**

**Key Parallels:**
- **Full-Text Search:** Like CloudSearch for search functionality
- **Faceted Search:** Filter results (like CloudSearch facets)
- **Distributed:** SolrCloud for scaling
- **RESTful API:** HTTP queries

**When to mention in interviews:**
- "Solr provides search capabilities similar to CloudSearch"
- "For e-commerce search with facets, Solr (like CloudSearch) works well"

---

### Apache Lucene
**What it is:** Core search library used by Elasticsearch and Solr.

**AWS Equivalent:** **The underlying technology of OpenSearch and CloudSearch**

**When to mention in interviews:**
- "Lucene is the core indexing library, similar to what powers OpenSearch"

---

## 🖥️ Distributed Computing

### Apache Hadoop
**What it is:** Framework for distributed storage and processing of big data.

**AWS Equivalent:** **Amazon EMR** or **AWS Lake Formation**

**Key Parallels:**
- **HDFS:** Distributed storage (like S3 for EMR)
- **MapReduce:** Parallel processing (like EMR with MapReduce)
- **YARN:** Resource management (like EMR cluster management)
- **Hive:** SQL on Hadoop (like Athena on S3)

**When to mention in interviews:**
- "Hadoop's HDFS stores data distributed, similar to how EMR uses S3"
- "For batch processing, Hadoop MapReduce runs on EMR"

**Key Components:**
- **HDFS:** Distributed file system (replaced by S3 in AWS)
- **MapReduce:** Batch processing framework
- **YARN:** Cluster resource manager
- **Hive:** SQL interface (like Athena)
- **HBase:** NoSQL database (like DynamoDB)
- **Pig:** Data flow scripting (like Glue ETL)

---

### Apache Flink
**What it is:** Stream processing framework for stateful computations.

**AWS Equivalent:** **Amazon Kinesis Data Analytics** or **AWS Glue Streaming**

**Key Parallels:**
- **Stream Processing:** Like Kinesis Analytics for real-time
- **Event Time Processing:** Time-based windows (like Kinesis Analytics)
- **Exactly-Once Semantics:** Reliable processing
- **State Management:** Stateful operations

**When to mention in interviews:**
- "Flink processes streams similar to Kinesis Data Analytics"
- "For real-time aggregations, Flink (like Kinesis Analytics) provides windowing functions"

---

### Apache Storm
**What it is:** Real-time computation system for stream processing.

**AWS Equivalent:** **Amazon Kinesis Data Analytics**

**Key Parallels:**
- **Real-time Processing:** Like Kinesis for streaming data
- **Topology:** Processing graph (like Kinesis Analytics application)
- **Spouts & Bolts:** Data sources and processors (like Kinesis streams and Lambda)

---

## 🗃️ Object Storage

### MinIO
**What it is:** High-performance, S3-compatible object storage.

**AWS Equivalent:** **Amazon S3**

**Key Parallels:**
- **S3 API Compatible:** Same API as S3
- **Object Storage:** Store files and blobs
- **Distributed:** Scale across nodes (like S3's distributed nature)
- **Erasure Coding:** Data protection (like S3 redundancy)

**When to mention in interviews:**
- "MinIO provides S3-compatible storage for on-premises or hybrid cloud"
- "MinIO uses the same API as S3, making it portable"

---

## 📊 Monitoring & Observability

### Prometheus
**What it is:** Open-source monitoring and alerting toolkit.

**AWS Equivalent:** **Amazon CloudWatch** or **Amazon Managed Prometheus**

**Key Parallels:**
- **Metrics Collection:** Like CloudWatch Metrics
- **Time-Series Database:** Store metrics over time
- **PromQL:** Query language (like CloudWatch Insights)
- **Alerting:** Alert on thresholds (like CloudWatch Alarms)
- **Service Discovery:** Auto-discover targets (like CloudWatch automatic metrics)

**When to mention in interviews:**
- "Prometheus collects metrics similar to CloudWatch agent"
- "For Kubernetes monitoring, Prometheus (Amazon Managed Prometheus) scrapes pod metrics"

**Key Features:**
- **Pull-Based:** Scrapes metrics from targets
- **Multi-dimensional Data:** Labels for filtering (like CloudWatch dimensions)
- **PromQL:** Powerful query language
- **Grafana Integration:** Visualization (like CloudWatch Dashboards)
- **Alertmanager:** Route alerts (like SNS)

---

### Grafana
**What it is:** Open-source analytics and monitoring platform.

**AWS Equivalent:** **Amazon CloudWatch Dashboards** or **Amazon Managed Grafana**

**Key Parallels:**
- **Visualization:** Create dashboards like CloudWatch
- **Multiple Data Sources:** Connect to various sources (like CloudWatch cross-account)
- **Alerting:** Alert rules (like CloudWatch Alarms)
- **Templating:** Reusable dashboards

**When to mention in interviews:**
- "Grafana visualizes metrics from multiple sources, similar to CloudWatch unified dashboards"

---

### Jaeger / Zipkin
**What it is:** Distributed tracing systems for monitoring microservices.

**AWS Equivalent:** **AWS X-Ray**

**Key Parallels:**
- **Distributed Tracing:** Track requests across services (like X-Ray)
- **Service Map:** Visualize dependencies (like X-Ray service map)
- **Performance Analysis:** Identify bottlenecks (like X-Ray analytics)
- **Root Cause Analysis:** Debug issues across services

**When to mention in interviews:**
- "Jaeger traces requests across microservices, similar to X-Ray"
- "For latency analysis, Jaeger/Zipkin (like X-Ray) shows the request path"

---

## 🎛️ Service Mesh & Networking

### Istio
**What it is:** Service mesh for microservices communication, security, and observability.

**AWS Equivalent:** **AWS App Mesh**

**Key Parallels:**
- **Service Mesh:** Manage service-to-service communication
- **Traffic Management:** Routing, load balancing (like App Mesh virtual routers)
- **Security:** mTLS encryption (like App Mesh TLS)
- **Observability:** Metrics and tracing (like App Mesh with X-Ray)

**When to mention in interviews:**
- "Istio manages microservices traffic similar to App Mesh"

---

### Envoy Proxy
**What it is:** High-performance proxy for cloud-native applications.

**AWS Equivalent:** **AWS App Mesh data plane** or **Application Load Balancer**

**Key Parallels:**
- **Layer 7 Proxy:** Application-level routing (like ALB)
- **Service Discovery:** Dynamic endpoints (like ECS service discovery)
- **Load Balancing:** Distribute traffic (like ALB/NLB)
- **Observability:** Rich metrics and tracing

---

## 🔐 Authentication & Authorization

### Keycloak
**What it is:** Open-source identity and access management solution.

**AWS Equivalent:** **Amazon Cognito**

**Key Parallels:**
- **User Management:** User pools (like Cognito User Pools)
- **OAuth 2.0 / OIDC:** Standard protocols
- **SSO:** Single sign-on (like Cognito with identity providers)
- **MFA:** Multi-factor authentication

**When to mention in interviews:**
- "Keycloak manages authentication similar to Cognito User Pools"

---

### HashiCorp Vault
**What it is:** Secrets management and data protection.

**AWS Equivalent:** **AWS Secrets Manager** or **AWS Systems Manager Parameter Store**

**Key Parallels:**
- **Secrets Storage:** Encrypt and store secrets
- **Dynamic Secrets:** Generate on-demand (like RDS IAM auth)
- **Encryption as a Service:** Encrypt data (like KMS)
- **Access Control:** Fine-grained policies (like IAM)

**When to mention in interviews:**
- "Vault manages secrets similar to AWS Secrets Manager"

---

## 🚀 Container Orchestration

### Kubernetes
**What it is:** Container orchestration platform for automating deployment, scaling, and management.

**AWS Equivalent:** **Amazon EKS (Elastic Kubernetes Service)** or **Amazon ECS**

**Key Parallels:**
- **Container Orchestration:** Like ECS for Docker containers
- **Auto-Scaling:** HPA (like ECS auto-scaling)
- **Service Discovery:** Internal DNS (like ECS service discovery)
- **Load Balancing:** Ingress controllers (like ALB Ingress Controller)
- **Self-Healing:** Restart failed containers (like ECS task replacement)

**When to mention in interviews:**
- "Kubernetes orchestrates containers, similar to ECS but more flexible"
- "K8s provides declarative configuration, like ECS task definitions"

**Key Concepts:**
- **Pods:** Smallest deployable units (like ECS tasks)
- **Services:** Stable network endpoint (like ECS services)
- **Deployments:** Manage pod lifecycle (like ECS services with rolling updates)
- **ConfigMaps & Secrets:** Configuration (like SSM Parameter Store)
- **Persistent Volumes:** Storage (like EBS volumes)

---

### Docker Swarm
**What it is:** Native Docker clustering and orchestration.

**AWS Equivalent:** **Amazon ECS**

**Key Parallels:**
- **Container Orchestration:** Simpler than K8s, like ECS
- **Swarm Services:** Long-running tasks (like ECS services)
- **Overlay Networks:** Service communication (like ECS task networking)

---

## 📦 CI/CD & DevOps

### Jenkins
**What it is:** Open-source automation server for CI/CD.

**AWS Equivalent:** **AWS CodePipeline** + **AWS CodeBuild**

**Key Parallels:**
- **Pipeline as Code:** Jenkinsfile (like CodePipeline CloudFormation)
- **Build Automation:** Compile and test (like CodeBuild)
- **Plugin Ecosystem:** Extend functionality (like CodePipeline actions)
- **Triggers:** SCM webhooks (like CodePipeline source stage)

**When to mention in interviews:**
- "Jenkins automates CI/CD similar to CodePipeline + CodeBuild"
- "For build pipelines, Jenkins (like CodeBuild) compiles, tests, and packages code"

---

### GitLab CI
**What it is:** Built-in CI/CD platform integrated with GitLab.

**AWS Equivalent:** **AWS CodePipeline** + **AWS CodeBuild** + **AWS CodeCommit**

**Key Parallels:**
- **Integrated Git:** Source control + CI/CD (like CodeCommit + CodePipeline)
- **Pipeline as Code:** .gitlab-ci.yml (like buildspec.yml)
- **Container Registry:** Store Docker images (like Amazon ECR)
- **Runners:** Execute jobs (like CodeBuild agents)

**When to mention in interviews:**
- "GitLab CI integrates version control and CI/CD, similar to CodeCommit + CodePipeline"

---

### GitHub Actions
**What it is:** CI/CD platform integrated with GitHub.

**AWS Equivalent:** **AWS CodePipeline** + **AWS CodeBuild**

**Key Parallels:**
- **Workflows:** YAML-based pipelines (like CodePipeline)
- **Actions:** Reusable steps (like CodeBuild commands)
- **Triggers:** Events-based (like CodePipeline triggers)
- **Secrets Management:** Encrypted variables (like Systems Manager Parameter Store)

**When to mention in interviews:**
- "GitHub Actions automates workflows similar to CodePipeline"

---

### Terraform
**What it is:** Infrastructure as Code (IaC) tool for provisioning resources.

**AWS Equivalent:** **AWS CloudFormation** or **AWS CDK**

**Key Parallels:**
- **Declarative:** Define desired state (like CloudFormation templates)
- **State Management:** Track resources (like CloudFormation stacks)
- **Multi-Cloud:** Works across providers (CloudFormation is AWS-only)
- **Modules:** Reusable components (like CloudFormation nested stacks)

**When to mention in interviews:**
- "Terraform provisions infrastructure similar to CloudFormation but multi-cloud"
- "For IaC, Terraform (like CDK) defines resources as code"

---

### Ansible
**What it is:** Configuration management and automation tool.

**AWS Equivalent:** **AWS Systems Manager** or **AWS OpsWorks**

**Key Parallels:**
- **Configuration Management:** Configure servers (like Systems Manager)
- **Agentless:** SSH-based (like Systems Manager Run Command)
- **Playbooks:** Define tasks (like Systems Manager Documents)
- **Idempotent:** Safe to run multiple times

**When to mention in interviews:**
- "Ansible manages server configuration similar to Systems Manager"

---

## 🌐 CDN & Edge Computing

### Cloudflare
**What it is:** CDN, DDoS protection, and edge computing platform.

**AWS Equivalent:** **Amazon CloudFront** + **AWS WAF** + **AWS Shield**

**Key Parallels:**
- **CDN:** Content delivery (like CloudFront)
- **DDoS Protection:** Similar to AWS Shield
- **WAF:** Web application firewall (like AWS WAF)
- **Edge Functions:** Workers (like Lambda@Edge)
- **DNS:** Managed DNS (like Route 53)

**When to mention in interviews:**
- "Cloudflare provides CDN similar to CloudFront with integrated security"

---

### Akamai
**What it is:** Leading CDN and edge platform.

**AWS Equivalent:** **Amazon CloudFront**

**Key Parallels:**
- **Global Network:** Edge locations worldwide (like CloudFront)
- **Content Caching:** Reduce origin load
- **Dynamic Content:** Accelerate API calls
- **Security:** DDoS protection

---

## 📡 Real-time Communication

### WebSockets
**What it is:** Protocol for full-duplex communication over TCP.

**AWS Equivalent:** **Amazon API Gateway WebSocket APIs** or **AWS IoT Core**

**Key Parallels:**
- **Bi-directional:** Real-time two-way communication
- **Persistent Connection:** Unlike HTTP request/response
- **Use Cases:** Chat, gaming, live updates (like IoT Core for devices)

**When to mention in interviews:**
- "WebSockets enable real-time features, similar to API Gateway WebSocket APIs"

---

### gRPC
**What it is:** High-performance RPC framework using HTTP/2.

**AWS Equivalent:** **AWS App Mesh** or **Application Load Balancer (HTTP/2)**

**Key Parallels:**
- **HTTP/2:** Multiplexing, streaming (like ALB HTTP/2)
- **Protocol Buffers:** Efficient serialization (similar to JSON in API Gateway)
- **Bidirectional Streaming:** Real-time communication
- **Service Mesh:** Works with App Mesh

**When to mention in interviews:**
- "gRPC provides efficient microservices communication, supported by App Mesh"

---

## 🧮 Coordination & Consensus

### Apache ZooKeeper
**What it is:** Centralized service for distributed coordination.

**AWS Equivalent:** **AWS Systems Manager Parameter Store** or **Amazon DynamoDB (for locking)**

**Key Parallels:**
- **Configuration Management:** Store configs (like Parameter Store)
- **Leader Election:** Coordinate distributed systems
- **Distributed Locking:** Synchronization (like DynamoDB conditional writes)
- **Service Discovery:** Track services (like AWS Cloud Map)

**When to mention in interviews:**
- "ZooKeeper coordinates distributed systems, similar to Parameter Store for configs"
- "For distributed locking, ZooKeeper is like DynamoDB's conditional writes"

---

### etcd
**What it is:** Distributed key-value store for Kubernetes configuration.

**AWS Equivalent:** **AWS Systems Manager Parameter Store** or **Amazon DynamoDB**

**Key Parallels:**
- **Key-Value Store:** Configuration data (like Parameter Store)
- **Consistency:** Strong consistency (like DynamoDB strong consistency)
- **Watch API:** Monitor changes (like DynamoDB Streams)
- **Kubernetes:** Built-in (like EKS control plane)

**When to mention in interviews:**
- "etcd stores Kubernetes state, similar to Parameter Store for ECS/EKS configs"

---

### Consul
**What it is:** Service mesh and service discovery platform.

**AWS Equivalent:** **AWS Cloud Map** + **AWS App Mesh**

**Key Parallels:**
- **Service Discovery:** Find services (like Cloud Map)
- **Health Checking:** Monitor service health (like ELB health checks)
- **Key-Value Store:** Configuration (like Parameter Store)
- **Service Mesh:** Traffic management (like App Mesh)

**When to mention in interviews:**
- "Consul discovers services similar to Cloud Map"

---

## 🔄 Load Balancers

### NGINX
**What it is:** Web server, reverse proxy, and load balancer.

**AWS Equivalent:** **Application Load Balancer (ALB)** or **Network Load Balancer (NLB)**

**Key Parallels:**
- **Reverse Proxy:** Route requests (like ALB)
- **Load Balancing:** Distribute traffic (like ALB/NLB)
- **SSL Termination:** Handle HTTPS (like ALB)
- **Layer 7:** HTTP routing (like ALB path-based routing)

**When to mention in interviews:**
- "NGINX load balances traffic similar to ALB at Layer 7"
- "For reverse proxy, NGINX (like ALB) handles routing and SSL"

---

### HAProxy
**What it is:** High-performance TCP/HTTP load balancer.

**AWS Equivalent:** **Network Load Balancer (NLB)** or **Application Load Balancer (ALB)**

**Key Parallels:**
- **Load Balancing:** Layer 4 and Layer 7 (like NLB and ALB)
- **Health Checks:** Monitor backends (like ELB health checks)
- **SSL Termination:** HTTPS handling
- **High Performance:** Low latency (like NLB)

**When to mention in interviews:**
- "HAProxy provides load balancing like NLB for Layer 4 or ALB for Layer 7"

---

## 🗂️ Graph Databases

### Neo4j
**What it is:** Graph database for connected data.

**AWS Equivalent:** **Amazon Neptune**

**Key Parallels:**
- **Graph Model:** Nodes and relationships (like Neptune)
- **Cypher Query Language:** Graph queries (Neptune uses Gremlin/SPARQL)
- **ACID Compliance:** Transactions
- **Use Cases:** Social networks, recommendations, fraud detection

**When to mention in interviews:**
- "Neo4j handles graph data similar to Neptune"
- "For relationship-heavy data like social networks, Neo4j (like Neptune) is ideal"

---

### ArangoDB
**What it is:** Multi-model database (document, graph, key-value).

**AWS Equivalent:** **Amazon Neptune** + **DocumentDB**

**Key Parallels:**
- **Multi-Model:** Multiple data models in one database
- **Graph Queries:** Like Neptune
- **Document Storage:** Like DocumentDB
- **Flexible:** Choose model per collection

---

## 📈 Time-Series Databases

### InfluxDB
**What it is:** Time-series database optimized for metrics and events.

**AWS Equivalent:** **Amazon Timestream**

**Key Parallels:**
- **Time-Series:** Optimized for time-stamped data (like Timestream)
- **Retention Policies:** Automatic data expiration (like Timestream tiers)
- **High Write Throughput:** Metrics ingestion (like Timestream)
- **Aggregations:** Time-based queries

**When to mention in interviews:**
- "InfluxDB stores time-series metrics similar to Timestream"
- "For IoT sensor data, InfluxDB (like Timestream) handles high-frequency writes"

---

### TimescaleDB
**What it is:** PostgreSQL extension for time-series data.

**AWS Equivalent:** **Amazon Timestream** or **Amazon RDS PostgreSQL**

**Key Parallels:**
- **SQL Interface:** Standard SQL (like Timestream SQL)
- **Time-Series Optimized:** Compression and indexing
- **PostgreSQL Compatible:** Use existing tools

**When to mention in interviews:**
- "TimescaleDB extends PostgreSQL for time-series, similar to Timestream"

---

## ⚡ Serverless & Functions

### OpenFaaS
**What it is:** Serverless functions framework for containers.

**AWS Equivalent:** **AWS Lambda**

**Key Parallels:**
- **Function as a Service:** Run functions without managing servers (like Lambda)
- **Auto-Scaling:** Scale based on demand (like Lambda concurrency)
- **Event-Driven:** Trigger from events (like Lambda triggers)
- **Container-Based:** Package functions in containers (like Lambda container images)

**When to mention in interviews:**
- "OpenFaaS provides serverless functions similar to Lambda but on Kubernetes"
- "For container-based functions, OpenFaaS (like Lambda) auto-scales based on load"

---

### Knative
**What it is:** Kubernetes-based platform for serverless workloads.

**AWS Equivalent:** **AWS Lambda** or **AWS Fargate**

**Key Parallels:**
- **Serverless Containers:** Run containers without infrastructure (like Fargate)
- **Auto-Scaling:** Scale to zero (like Lambda)
- **Event-Driven:** Eventing system (like EventBridge)
- **Build & Deploy:** Built-in CI/CD (like CodePipeline)

**When to mention in interviews:**
- "Knative enables serverless on Kubernetes, similar to Lambda on EKS"

---

### Apache OpenWhisk
**What it is:** Open-source serverless platform.

**AWS Equivalent:** **AWS Lambda**

**Key Parallels:**
- **FaaS:** Function execution (like Lambda)
- **Triggers:** Event-based invocation (like Lambda triggers)
- **Sequences:** Chain functions (like Step Functions)
- **Multi-Language:** Support various runtimes

---

## 🚪 API Gateways

### Kong
**What it is:** Cloud-native API gateway and service mesh.

**AWS Equivalent:** **Amazon API Gateway**

**Key Parallels:**
- **API Management:** Route, authenticate, rate limit (like API Gateway)
- **Plugins:** Extend functionality (like API Gateway integrations)
- **Authentication:** OAuth, JWT, API keys (like API Gateway authorizers)
- **Rate Limiting:** Throttle requests (like API Gateway throttling)
- **Load Balancing:** Distribute traffic (like API Gateway)

**When to mention in interviews:**
- "Kong manages APIs similar to API Gateway with plugin ecosystem"
- "For API authentication, Kong (like API Gateway) supports OAuth and JWT"

---

### Tyk
**What it is:** Open-source API gateway.

**AWS Equivalent:** **Amazon API Gateway**

**Key Parallels:**
- **API Management:** Similar to API Gateway
- **Analytics:** Track API usage (like API Gateway metrics)
- **Rate Limiting:** Control traffic
- **GraphQL:** Support GraphQL APIs (like AppSync)

---

### Ambassador / Emissary-Ingress
**What it is:** Kubernetes-native API gateway.

**AWS Equivalent:** **Amazon API Gateway** + **ALB Ingress Controller**

**Key Parallels:**
- **Kubernetes Integration:** Native K8s (like API Gateway with EKS)
- **Layer 7 Routing:** HTTP routing (like API Gateway)
- **Authentication:** Integrate with auth services
- **Canary Deployments:** Traffic splitting (like API Gateway stages)

---

## 🔄 Workflow Orchestration

### Apache Airflow
**What it is:** Platform for programmatically authoring, scheduling, and monitoring workflows.

**AWS Equivalent:** **AWS Step Functions** or **Amazon MWAA (Managed Workflows for Apache Airflow)**

**Key Parallels:**
- **DAG-Based:** Define workflows as directed acyclic graphs (like Step Functions state machines)
- **Scheduling:** Cron-based scheduling (like EventBridge Scheduler)
- **Retry Logic:** Automatic retries (like Step Functions retry policies)
- **Monitoring:** Track execution (like Step Functions console)
- **Operators:** Pre-built tasks (like Step Functions integrations)

**When to mention in interviews:**
- "Airflow orchestrates workflows similar to Step Functions using DAGs"
- "For ETL pipelines, Airflow (like MWAA) schedules and monitors tasks"

---

### Prefect
**What it is:** Modern workflow orchestration framework.

**AWS Equivalent:** **AWS Step Functions**

**Key Parallels:**
- **Python-Based:** Define workflows in Python (like Step Functions with SDK)
- **Dynamic Workflows:** Runtime-determined flows
- **Error Handling:** Built-in retry logic
- **Scheduling:** Time-based triggers

**When to mention in interviews:**
- "Prefect provides workflow orchestration like Step Functions with Python API"

---

### Temporal
**What it is:** Durable execution platform for workflows.

**AWS Equivalent:** **AWS Step Functions**

**Key Parallels:**
- **Durable Execution:** Survive failures (like Step Functions)
- **Long-Running Workflows:** Days/months (like Step Functions)
- **Retry & Compensation:** Built-in error handling
- **Event-Driven:** React to events

---

### Argo Workflows
**What it is:** Kubernetes-native workflow engine.

**AWS Equivalent:** **AWS Step Functions** on **EKS**

**Key Parallels:**
- **Container-Native:** Workflows as containers (like Step Functions with ECS tasks)
- **DAG Support:** Directed acyclic graphs
- **CI/CD Integration:** Build pipelines (like CodePipeline)

---

## 🔗 Data Integration & ETL

### Apache NiFi
**What it is:** Data integration and ETL platform.

**AWS Equivalent:** **AWS Glue** or **AWS Data Pipeline**

**Key Parallels:**
- **Visual Designer:** Drag-and-drop ETL (like Glue Studio)
- **Data Routing:** Flow-based programming (like Glue workflows)
- **Processors:** Pre-built transformations (like Glue transforms)
- **Data Provenance:** Track data lineage (like Glue Data Catalog)

**When to mention in interviews:**
- "NiFi provides visual ETL similar to Glue Studio"
- "For data pipelines, NiFi (like Glue) connects sources and destinations"

---

### Talend
**What it is:** Data integration platform.

**AWS Equivalent:** **AWS Glue**

**Key Parallels:**
- **ETL/ELT:** Transform data (like Glue jobs)
- **Visual Design:** GUI for pipelines
- **Multiple Sources:** Connect various databases (like Glue connections)
- **Data Quality:** Validate data (like Glue DataBrew)

---

### dbt (Data Build Tool)
**What it is:** Transformation tool for analytics.

**AWS Equivalent:** **AWS Glue** (transformation logic) or **Redshift Stored Procedures**

**Key Parallels:**
- **SQL-Based:** Transform with SQL (like Glue with Spark SQL)
- **Version Control:** Git-based (like Glue with Git)
- **Testing:** Data quality tests
- **Documentation:** Auto-generate docs

**When to mention in interviews:**
- "dbt transforms data in warehouses, similar to Glue transformations"

---

## 📦 Container Registries

### Harbor
**What it is:** Open-source container registry.

**AWS Equivalent:** **Amazon ECR (Elastic Container Registry)**

**Key Parallels:**
- **Container Images:** Store Docker images (like ECR)
- **Security Scanning:** Vulnerability scanning (like ECR image scanning)
- **Access Control:** RBAC (like ECR IAM policies)
- **Replication:** Multi-region (like ECR replication)
- **Helm Charts:** Store Helm packages

**When to mention in interviews:**
- "Harbor stores container images similar to ECR with security scanning"

---

### JFrog Artifactory
**What it is:** Universal artifact repository.

**AWS Equivalent:** **Amazon ECR** + **CodeArtifact**

**Key Parallels:**
- **Multi-Format:** Docker, Maven, npm, etc. (like ECR + CodeArtifact)
- **Access Control:** Fine-grained permissions
- **Artifact Promotion:** Move between environments
- **High Availability:** Replication (like ECR)

---

### Nexus Repository
**What it is:** Repository manager.

**AWS Equivalent:** **AWS CodeArtifact**

**Key Parallels:**
- **Package Management:** Maven, npm, PyPI (like CodeArtifact)
- **Proxy Repository:** Cache external packages
- **Access Control:** User management

---

## ⚙️ Configuration Management

### Chef
**What it is:** Infrastructure automation and configuration management.

**AWS Equivalent:** **AWS OpsWorks** or **Systems Manager**

**Key Parallels:**
- **Infrastructure as Code:** Define configurations (like OpsWorks Chef)
- **Idempotent:** Safe to run multiple times (like Systems Manager State Manager)
- **Cookbooks:** Reusable configurations (like Systems Manager documents)
- **Agent-Based:** Install agents on servers

**When to mention in interviews:**
- "Chef automates configuration similar to OpsWorks Chef"

---

### Puppet
**What it is:** Configuration management tool.

**AWS Equivalent:** **AWS OpsWorks** or **Systems Manager**

**Key Parallels:**
- **Declarative:** Define desired state (like Systems Manager State Manager)
- **Modules:** Reusable components
- **Agent-Based:** Puppet agent on nodes
- **Reporting:** Compliance reports (like Systems Manager Compliance)

---

### SaltStack
**What it is:** Automation and configuration management.

**AWS Equivalent:** **AWS Systems Manager**

**Key Parallels:**
- **Remote Execution:** Run commands (like Systems Manager Run Command)
- **State Management:** Configure servers (like State Manager)
- **Event-Driven:** React to changes (like EventBridge)

---

## 🌐 DNS Services

### CoreDNS
**What it is:** DNS server for service discovery.

**AWS Equivalent:** **Amazon Route 53** + **AWS Cloud Map**

**Key Parallels:**
- **Service Discovery:** DNS-based (like Cloud Map)
- **Kubernetes Integration:** Built into K8s (like EKS)
- **Plugins:** Extend functionality
- **Caching:** DNS caching (like Route 53 resolver)

**When to mention in interviews:**
- "CoreDNS provides service discovery similar to Cloud Map for Kubernetes"

---

### BIND (Berkeley Internet Name Domain)
**What it is:** Widely-used DNS server.

**AWS Equivalent:** **Amazon Route 53**

**Key Parallels:**
- **Authoritative DNS:** Host zones (like Route 53 hosted zones)
- **Recursive Resolver:** DNS resolution (like Route 53 Resolver)
- **DNSSEC:** Security extensions (like Route 53 DNSSEC)

---

## 🗄️ Distributed File Systems

### GlusterFS
**What it is:** Scalable network filesystem.

**AWS Equivalent:** **Amazon EFS (Elastic File System)**

**Key Parallels:**
- **Distributed:** Scale across nodes (like EFS)
- **POSIX-Compliant:** Standard file operations (like EFS)
- **Replication:** Data redundancy (like EFS Multi-AZ)
- **NFS/SMB:** Standard protocols (like EFS with NFS)

**When to mention in interviews:**
- "GlusterFS provides distributed file storage similar to EFS"

---

### Ceph
**What it is:** Unified storage platform (object, block, file).

**AWS Equivalent:** **S3** + **EBS** + **EFS**

**Key Parallels:**
- **Object Storage:** S3-compatible (like S3)
- **Block Storage:** RBD for VMs (like EBS)
- **File Storage:** CephFS (like EFS)
- **Self-Healing:** Automatic recovery
- **Scalable:** Petabyte-scale (like AWS storage)

**When to mention in interviews:**
- "Ceph provides unified storage like S3 + EBS + EFS in one system"

---

### HDFS (Hadoop Distributed File System)
**What it is:** Distributed file system for Hadoop.

**AWS Equivalent:** **Amazon S3** (with EMR) or **Amazon FSx for Lustre**

**Key Parallels:**
- **Big Data:** Store large datasets (like S3 for EMR)
- **Distributed:** Data across nodes
- **Fault Tolerance:** Replication (like S3 durability)
- **Hadoop Integration:** Native support (like S3 with EMR)

---

## 🤖 Machine Learning & AI

### Kubeflow
**What it is:** ML toolkit for Kubernetes.

**AWS Equivalent:** **Amazon SageMaker**

**Key Parallels:**
- **ML Workflows:** Train and deploy models (like SageMaker)
- **Jupyter Notebooks:** Interactive development (like SageMaker Notebooks)
- **Model Serving:** Deploy models (like SageMaker Endpoints)
- **Pipelines:** ML workflows (like SageMaker Pipelines)
- **Distributed Training:** Multi-node training (like SageMaker)

**When to mention in interviews:**
- "Kubeflow provides ML workflows on Kubernetes, similar to SageMaker"

---

### MLflow
**What it is:** Open-source platform for ML lifecycle.

**AWS Equivalent:** **Amazon SageMaker**

**Key Parallels:**
- **Experiment Tracking:** Log metrics (like SageMaker Experiments)
- **Model Registry:** Store models (like SageMaker Model Registry)
- **Model Deployment:** Serve models (like SageMaker Endpoints)
- **Model Versioning:** Track versions

**When to mention in interviews:**
- "MLflow tracks ML experiments similar to SageMaker Experiments"

---

### TensorFlow Serving
**What it is:** Serving system for ML models.

**AWS Equivalent:** **Amazon SageMaker Endpoints**

**Key Parallels:**
- **Model Serving:** Deploy TensorFlow models (like SageMaker)
- **REST/gRPC APIs:** Model inference
- **Multi-Model:** Serve multiple models
- **Versioning:** Model versions (like SageMaker variants)

---

### Ray
**What it is:** Distributed computing framework for ML and AI.

**AWS Equivalent:** **Amazon SageMaker** (distributed training) + **AWS Batch**

**Key Parallels:**
- **Distributed ML:** Scale training (like SageMaker distributed)
- **Parallel Processing:** Distributed tasks (like Batch)
- **Auto-Scaling:** Dynamic resources
- **Python-Native:** Easy integration

---

## 📡 IoT Platforms

### Eclipse Mosquitto
**What it is:** Open-source MQTT broker.

**AWS Equivalent:** **AWS IoT Core**

**Key Parallels:**
- **MQTT Protocol:** Message broker (like IoT Core MQTT)
- **Pub/Sub:** Messaging pattern (like IoT Core topics)
- **Lightweight:** For IoT devices (like IoT Core)
- **SSL/TLS:** Secure communication (like IoT Core)

**When to mention in interviews:**
- "Mosquitto provides MQTT messaging similar to AWS IoT Core"

---

### ThingsBoard
**What it is:** Open-source IoT platform.

**AWS Equivalent:** **AWS IoT Core** + **AWS IoT Analytics**

**Key Parallels:**
- **Device Management:** Manage IoT devices (like IoT Core)
- **Data Collection:** Telemetry ingestion (like IoT Core)
- **Rule Engine:** Process data (like IoT Core Rules)
- **Dashboards:** Visualize data (like IoT Analytics)
- **Alerts:** Notifications (like IoT Events)

**When to mention in interviews:**
- "ThingsBoard manages IoT devices similar to AWS IoT Core with built-in dashboards"

---

### EdgeX Foundry
**What it is:** Vendor-neutral edge computing framework.

**AWS Equivalent:** **AWS IoT Greengrass**

**Key Parallels:**
- **Edge Computing:** Process data at edge (like Greengrass)
- **Device Connectivity:** Connect devices (like Greengrass)
- **Local Processing:** Compute locally (like Greengrass Lambda)
- **Cloud Integration:** Sync with cloud (like Greengrass to IoT Core)

---

## 🎬 Media Processing

### FFmpeg
**What it is:** Multimedia framework for video/audio processing.

**AWS Equivalent:** **AWS Elemental MediaConvert** or **Elastic Transcoder**

**Key Parallels:**
- **Video Transcoding:** Convert formats (like MediaConvert)
- **Encoding:** Compress videos (like MediaConvert)
- **Streaming:** HLS, DASH (like MediaConvert outputs)
- **Filters:** Video effects (like MediaConvert presets)

**When to mention in interviews:**
- "FFmpeg transcodes video similar to MediaConvert but self-managed"

---

### Jitsi
**What it is:** Open-source video conferencing platform.

**AWS Equivalent:** **Amazon Chime SDK**

**Key Parallels:**
- **Video Conferencing:** WebRTC-based (like Chime SDK)
- **Screen Sharing:** Share screens (like Chime)
- **Recording:** Record meetings (like Chime)
- **Self-Hosted:** On-premises option

---

### OBS Studio
**What it is:** Open-source streaming software.

**AWS Equivalent:** **AWS Elemental MediaLive**

**Key Parallels:**
- **Live Streaming:** Broadcast video (like MediaLive)
- **Scene Composition:** Mix sources (like MediaLive)
- **RTMP Output:** Stream to platforms (like MediaLive outputs)

---

## 📊 BI & Analytics Visualization

### Apache Superset
**What it is:** Modern data exploration and visualization platform.

**AWS Equivalent:** **Amazon QuickSight**

**Key Parallels:**
- **Dashboards:** Create visualizations (like QuickSight)
- **SQL Lab:** Query databases (like QuickSight datasets)
- **Multiple Sources:** Connect databases (like QuickSight data sources)
- **Sharing:** Collaborate on dashboards (like QuickSight sharing)

**When to mention in interviews:**
- "Superset provides BI dashboards similar to QuickSight"

---

### Metabase
**What it is:** Open-source business intelligence tool.

**AWS Equivalent:** **Amazon QuickSight**

**Key Parallels:**
- **Self-Service BI:** User-friendly (like QuickSight Q)
- **Dashboards:** Visualizations (like QuickSight)
- **Questions:** Natural language (like QuickSight Q)
- **Alerts:** Data alerts (like QuickSight alerts)

---

### Redash
**What it is:** Data visualization and dashboards.

**AWS Equivalent:** **Amazon QuickSight**

**Key Parallels:**
- **Query Editor:** Write SQL (like QuickSight)
- **Visualizations:** Charts and graphs
- **Scheduled Queries:** Automated reports (like QuickSight schedules)
- **Alerts:** Threshold-based alerts

---

## 🔐 Identity & Access Management

### FreeIPA
**What it is:** Identity management system.

**AWS Equivalent:** **AWS IAM** + **AWS Directory Service**

**Key Parallels:**
- **User Management:** Centralized identity (like IAM)
- **LDAP:** Directory services (like AWS Directory Service)
- **Kerberos:** Authentication
- **Policy Management:** Access control (like IAM policies)

---

### Okta (Commercial but important)
**What it is:** Identity and access management platform.

**AWS Equivalent:** **AWS IAM Identity Center** (formerly AWS SSO) + **Amazon Cognito**

**Key Parallels:**
- **SSO:** Single sign-on (like IAM Identity Center)
- **MFA:** Multi-factor auth (like IAM MFA)
- **User Pools:** User management (like Cognito)
- **Federation:** SAML, OIDC (like IAM Identity Center)

---

## 🔄 Additional AWS Services & Open-Source Equivalents

### AWS EventBridge
**Open-Source Equivalent:** **Apache Camel** or **RabbitMQ + Dead Letter Exchanges**

**Key Parallels:**
- **Event Bus:** Route events
- **Rules:** Filter and transform
- **Integrations:** Connect services
- **Schema Registry:** Event schemas

---

### AWS AppSync
**Open-Source Equivalent:** **Hasura** or **Apollo GraphQL Server**

**Key Parallels:**
- **GraphQL:** API layer
- **Real-time:** Subscriptions
- **Data Sources:** Connect databases
- **Resolvers:** Query logic

---

### AWS Batch
**Open-Source Equivalent:** **Slurm** or **HTCondor**

**Key Parallels:**
- **Job Scheduling:** Batch processing
- **Resource Management:** Compute resources
- **Job Queues:** Priority-based
- **Spot Integration:** Cost optimization

---

### AWS Transfer Family
**Open-Source Equivalent:** **vsftpd** or **ProFTPD**

**Key Parallels:**
- **SFTP/FTP:** File transfer protocols
- **S3 Integration:** Store files
- **Authentication:** User management
- **Compliance:** Audit logs

---

### AWS DataSync
**Open-Source Equivalent:** **rsync** or **Rclone**

**Key Parallels:**
- **Data Transfer:** Move data
- **Incremental:** Only changed files
- **Scheduling:** Automated transfers
- **Verification:** Data integrity

---

### AWS App Runner
**Open-Source Equivalent:** **Cloud Foundry** or **Heroku Buildpacks**

**Key Parallels:**
- **PaaS:** Platform as a service
- **Auto-Deploy:** Git push deploys
- **Auto-Scaling:** Based on traffic
- **Built-in LB:** Load balancing

---

### AWS Amplify
**Open-Source Equivalent:** **Firebase** (Google) or **Supabase**

**Key Parallels:**
- **Backend as a Service:** Full-stack platform
- **Authentication:** User management
- **API:** GraphQL/REST
- **Hosting:** Static site hosting
- **Storage:** File storage

---

### AWS Ground Station
**Open-Source Equivalent:** **GNU Radio** + **SatNOGS**

**Key Parallels:**
- **Satellite Communication:** Downlink data
- **Signal Processing:** RF processing
- **Data Collection:** Space data

---

### AWS RoboMaker
**Open-Source Equivalent:** **ROS (Robot Operating System)**

**Key Parallels:**
- **Robotics Development:** Robot software
- **Simulation:** Test robots
- **Fleet Management:** Manage robots
- **Cloud Integration:** Connect to cloud

---

## 🎯 Quick Reference Table

| Technology | Category | AWS Equivalent | Key Use Case |
|------------|----------|----------------|--------------|
| **PostgreSQL** | RDBMS | RDS PostgreSQL, Aurora | Relational data with ACID |
| **MySQL** | RDBMS | RDS MySQL, Aurora MySQL | Web applications |
| **Cassandra** | NoSQL | Keyspaces, DynamoDB | High-volume writes, time-series |
| **MongoDB** | NoSQL | DocumentDB, DynamoDB | Flexible schema, documents |
| **Redis** | Cache/DB | ElastiCache, MemoryDB | Caching, session store |
| **Memcached** | Cache | ElastiCache Memcached | Simple caching |
| **Kafka** | Streaming | MSK, Kinesis | Event streaming, log aggregation |
| **RabbitMQ** | Queue | Amazon MQ, SQS | Task queues, async processing |
| **Snowflake** | Data Warehouse | Redshift | Analytics, BI |
| **BigQuery** | Data Warehouse | Athena, Redshift Serverless | Ad-hoc queries |
| **Spark** | Big Data | EMR, Glue | ETL, data processing |
| **Elasticsearch** | Search | OpenSearch | Full-text search, logs |
| **Solr** | Search | CloudSearch, OpenSearch | E-commerce search |
| **Hadoop** | Big Data | EMR | Distributed processing |
| **Flink** | Stream Processing | Kinesis Analytics | Real-time streams |
| **MinIO** | Object Storage | S3 | S3-compatible storage |
| **Prometheus** | Monitoring | CloudWatch, Managed Prometheus | Metrics collection |
| **Grafana** | Visualization | CloudWatch Dashboards, Managed Grafana | Metric visualization |
| **Jaeger/Zipkin** | Tracing | X-Ray | Distributed tracing |
| **Kubernetes** | Orchestration | EKS, ECS | Container management |
| **Jenkins** | CI/CD | CodePipeline, CodeBuild | Build automation |
| **Terraform** | IaC | CloudFormation, CDK | Infrastructure provisioning |
| **NGINX** | Load Balancer | ALB, NLB | Reverse proxy, routing |
| **Neo4j** | Graph DB | Neptune | Connected data |
| **InfluxDB** | Time-Series | Timestream | IoT, metrics |
| **ZooKeeper** | Coordination | Parameter Store, DynamoDB | Distributed coordination |
| **Consul** | Service Discovery | Cloud Map, App Mesh | Service registry |
| **Keycloak** | Auth | Cognito | User management |
| **Vault** | Secrets | Secrets Manager | Secret storage |

---

## 💡 Interview Tips

### 1. **Lead with AWS, Then Generalize**
```
✅ "I'd use DynamoDB for this, which is similar to Cassandra's distributed architecture..."
❌ "I don't know Cassandra well..."
```

### 2. **Use the "Like" Framework**
```
"Kafka is like Kinesis but with unlimited retention and message replay..."
"Redis provides caching like ElastiCache with rich data structures..."
```

### 3. **Focus on Trade-offs**
```
"For this use case, I'd compare:
- Cassandra (DynamoDB): Better for writes, eventual consistency
- PostgreSQL (RDS): Better for ACID, complex queries
- MongoDB (DocumentDB): Better for flexible schema"
```

### 4. **Architecture Patterns**
```
"This reminds me of the Lambda architecture:
- Batch Layer: Hadoop (EMR) for historical data
- Speed Layer: Kafka (Kinesis) for real-time
- Serving Layer: Cassandra (DynamoDB) for queries"
```

### 5. **Know the CAP Theorem**
```
- Cassandra: AP (Availability + Partition Tolerance)
- PostgreSQL: CA (Consistency + Availability)
- DynamoDB: Tunable (CP or AP)
```

### 6. **Discuss Scalability**
```
"For horizontal scaling:
- Cassandra/DynamoDB: Add nodes seamlessly
- PostgreSQL/RDS: Read replicas for reads
- Redis/ElastiCache: Cluster mode for sharding"
```

### 7. **Common Architectures**

**Microservices Stack:**
```
- Containers: Kubernetes (EKS) or Docker (ECS)
- Service Mesh: Istio (App Mesh)
- API Gateway: NGINX (ALB) or Kong (API Gateway)
- Service Discovery: Consul (Cloud Map)
- Tracing: Jaeger (X-Ray)
- Metrics: Prometheus (CloudWatch)
```

**Data Pipeline:**
```
- Ingestion: Kafka (Kinesis)
- Processing: Spark (EMR/Glue)
- Storage: HDFS/S3 (S3)
- Warehouse: Snowflake (Redshift)
- Analytics: Presto (Athena)
```

**Real-time Analytics:**
```
- Stream: Kafka (Kinesis)
- Processing: Flink (Kinesis Analytics)
- Database: Cassandra (DynamoDB)
- Cache: Redis (ElastiCache)
- Search: Elasticsearch (OpenSearch)
```

---

## 🎓 Study Strategy

### Phase 1: Core Technologies (Week 1-2)
1. **Databases:** PostgreSQL, Cassandra, MongoDB, Redis
2. **Message Queues:** Kafka, RabbitMQ
3. **Caching:** Redis, Memcached

### Phase 2: Big Data & Analytics (Week 3)
1. **Data Warehousing:** Snowflake, BigQuery
2. **Processing:** Spark, Hadoop, Flink
3. **Search:** Elasticsearch

### Phase 3: Infrastructure (Week 4)
1. **Orchestration:** Kubernetes
2. **Monitoring:** Prometheus, Grafana
3. **CI/CD:** Jenkins, Terraform

### Practice Questions:
```
1. "Design a real-time analytics dashboard"
   → Kafka (Kinesis) → Flink (Kinesis Analytics) → Redis (ElastiCache) → WebSockets

2. "Design a logging system for microservices"
   → Services → Kafka (Kinesis) → Elasticsearch (OpenSearch) → Kibana (Dashboards)

3. "Design a recommendation engine"
   → User data (PostgreSQL/RDS) → Spark (EMR) → Neo4j (Neptune) → Redis (ElastiCache)

4. "Design a time-series metrics system"
   → Prometheus (CloudWatch) → InfluxDB (Timestream) → Grafana (Dashboards)
```

---

## 🔗 Additional Resources

- **PostgreSQL:** Think RDS with advanced features
- **Cassandra:** Think DynamoDB with flexible consistency
- **Kafka:** Think Kinesis with message replay
- **Redis:** Think ElastiCache with data structures
- **Elasticsearch:** Think OpenSearch for search/logs
- **Snowflake:** Think Redshift with better scaling
- **Spark:** Think EMR for big data processing
- **Kubernetes:** Think EKS/ECS for containers
- **Prometheus:** Think CloudWatch for metrics

---

## 📝 Final Notes

**Remember:**
1. Every technology has an AWS parallel you already know
2. Focus on **use cases and trade-offs**, not just features
3. Use your AWS knowledge as a foundation, then explain differences
4. Interviewers value understanding **why** you'd choose a technology
5. Real-world experience > theoretical knowledge

**Confidence Builders:**
- "This is similar to [AWS Service] that I've used..."
- "The architecture reminds me of how we'd build it with [AWS Services]..."
- "I'd approach this like [AWS Pattern], but with [Technology] for [specific reason]..."

Good luck with your interviews! 🚀

---

*Last Updated: November 2025*
*Created for AWS Engineers preparing for System Design Interviews*
