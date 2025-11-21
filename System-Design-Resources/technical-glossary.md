# System Design Technical Glossary

A comprehensive reference guide for all technical terms, acronyms, and concepts used throughout the High-Level Design (HLD) documents.

## Table of Contents
- [Networking & Protocols](#networking--protocols)
- [Security & Authentication](#security--authentication)
- [Architecture Patterns](#architecture-patterns)
- [Databases & Storage](#databases--storage)
- [Caching & Performance](#caching--performance)
- [Cloud Services (AWS)](#cloud-services-aws)
- [Messaging & Events](#messaging--events)
- [Monitoring & Observability](#monitoring--observability)
- [System Design Metrics](#system-design-metrics)
- [Consistency & Reliability](#consistency--reliability)
- [Design Patterns](#design-patterns)
- [Data Structures & Algorithms](#data-structures--algorithms)
- [Compliance & Regulations](#compliance--regulations)

---

## Networking & Protocols

### AJAX (Asynchronous JavaScript and XML)
A technique for creating asynchronous web applications. Allows web pages to update content without reloading the entire page by exchanging data with a server in the background.

### API (Application Programming Interface)
A set of rules and protocols that allows different software applications to communicate with each other. Defines methods and data structures for interaction.

### CDN (Content Delivery Network)
A geographically distributed network of servers that cache and deliver content (images, videos, static files) from locations closer to users, reducing latency and improving load times.

**Key Providers**: CloudFront, Akamai, Cloudflare, Fastly

### CIDR (Classless Inter-Domain Routing)
A method for allocating IP addresses and routing. Notation format: `192.168.1.0/24` where `/24` indicates the number of bits in the network prefix.

### CORS (Cross-Origin Resource Sharing)
A security mechanism that allows or restricts web applications running on one domain to access resources from another domain. Controlled via HTTP headers.

### DNS (Domain Name System)
The internet's phone book. Translates human-readable domain names (example.com) to IP addresses (192.168.1.1) that computers use to identify each other.

### gRPC (gRPC Remote Procedure Call)
A high-performance, open-source RPC framework developed by Google. Uses Protocol Buffers (protobuf) for serialization and HTTP/2 for transport.

**Advantages**: 
- Faster than REST (binary protocol)
- Bi-directional streaming
- Built-in load balancing

### HTTP (Hypertext Transfer Protocol)
The foundation protocol of the web. Defines how messages are formatted and transmitted between browsers and servers.

**Common Methods**: GET, POST, PUT, DELETE, PATCH

### HTTP/2
Major revision of HTTP protocol with:
- **Multiplexing**: Multiple requests over single connection
- **Header compression**: Reduces overhead
- **Server push**: Proactive content delivery
- **Binary protocol**: More efficient than text-based HTTP/1.1

### HTTP/3 (QUIC)
Latest HTTP version built on QUIC protocol. Uses UDP instead of TCP for:
- Faster connection establishment
- Better performance on lossy networks
- Improved multiplexing

### HTTPS (HTTP Secure)
HTTP over TLS/SSL encryption. Ensures:
- **Confidentiality**: Encrypted data transmission
- **Integrity**: Data cannot be modified in transit
- **Authentication**: Verifies server identity

**Port**: 443 (default)

### mTLS (Mutual TLS)
Both client and server authenticate each other using certificates. Used for service-to-service communication in microservices.

### NAT (Network Address Translation)
Technique that maps private IP addresses to public IP addresses, allowing multiple devices on a local network to share a single public IP.

### REST (Representational State Transfer)
Architectural style for designing networked applications. Uses HTTP methods and is stateless.

**Principles**:
- Stateless communication
- Client-server separation
- Cacheable responses
- Uniform interface
- Layered system

### SSL (Secure Sockets Layer)
Deprecated predecessor to TLS. Term still commonly used to refer to TLS.

### TCP (Transmission Control Protocol)
Connection-oriented protocol that guarantees ordered, reliable delivery of data. Used when accuracy is critical (HTTP, FTP, email).

**Characteristics**:
- Three-way handshake
- Flow control
- Error checking
- Retransmission

### TLS (Transport Layer Security)
Cryptographic protocol for secure communication over networks. Current versions: TLS 1.2, TLS 1.3

**TLS 1.3 improvements**:
- Faster handshake (1-RTT)
- Removed weak ciphers
- Enhanced privacy

### UDP (User Datagram Protocol)
Connectionless protocol for fast, low-latency data transmission. No guarantees on delivery or order. Used for video streaming, gaming, DNS.

**Characteristics**:
- No handshake
- No congestion control
- Lower overhead than TCP

### VPN (Virtual Private Network)
Creates encrypted tunnel over the internet for secure remote access to private networks.

### WebRTC (Web Real-Time Communication)
Technology enabling peer-to-peer audio, video, and data sharing between browsers without plugins. Used for video conferencing.

### WebSocket
Protocol providing full-duplex communication channels over a single TCP connection. Enables real-time, bi-directional communication.

**Use Cases**: Chat applications, live feeds, gaming, real-time dashboards

---

## Security & Authentication

### ABAC (Attribute-Based Access Control)
Access control method where permissions are granted based on attributes (user role, time of day, location, resource type) rather than identity alone.

### ACL (Access Control List)
List of permissions attached to an object. Specifies which users or system processes can access the object and what operations they can perform.

### Active Directory
Microsoft's directory service for Windows domain networks. Provides authentication and authorization services.

### AES (Advanced Encryption Standard)
Symmetric encryption algorithm widely used for securing data. Key sizes: 128-bit, 192-bit, 256-bit.

**AES-256**: Most secure variant, requires 256-bit key

### API Gateway
Entry point for all client requests. Handles:
- Authentication/Authorization
- Rate limiting
- Request routing
- Load balancing
- API versioning
- Response transformation

### Certificate Authority (CA)
Trusted entity that issues digital certificates to verify identity. Examples: Let's Encrypt, DigiCert, Verisign

### DDoS (Distributed Denial of Service)
Attack that overwhelms a system with traffic from multiple sources, making it unavailable to legitimate users.

**Mitigation**: CDN protection, rate limiting, traffic filtering

### DMZ (Demilitarized Zone)
Physical or logical subnet that separates an internal network from untrusted external networks. Contains public-facing services.

### Encryption
Process of converting plaintext into ciphertext to prevent unauthorized access.

**Types**:
- **Symmetric**: Same key for encryption and decryption (AES)
- **Asymmetric**: Public/private key pair (RSA)

### Firewall
Security system that monitors and controls incoming/outgoing network traffic based on predetermined security rules.

### GDPR (General Data Protection Regulation)
EU regulation on data protection and privacy. Requires:
- User consent for data collection
- Right to deletion ("right to be forgotten")
- Data breach notifications
- Data portability

### Hashing
One-way function that converts input into fixed-size string. Cannot be reversed.

**Common algorithms**: SHA-256, SHA-512, bcrypt (for passwords)

### HIPAA (Health Insurance Portability and Accountability Act)
US regulation protecting sensitive patient health information. Requires:
- Encryption of PHI (Protected Health Information)
- Access controls
- Audit trails

### IAM (Identity and Access Management)
Framework for managing digital identities and controlling access to resources.

**AWS IAM**: Manages access to AWS services and resources

### JWT (JSON Web Token)
Compact, URL-safe token format for securely transmitting information between parties. Contains three parts: header, payload, signature.

**Structure**: `xxxxx.yyyyy.zzzzz`

**Use Cases**: Authentication, information exchange

### LDAP (Lightweight Directory Access Protocol)
Protocol for accessing and maintaining distributed directory information services. Used for user authentication in enterprises.

### MD5 (Message Digest Algorithm 5)
Cryptographic hash function producing 128-bit hash value. **Considered weak** - should not be used for security.

### Multi-Factor Authentication (MFA)
Security method requiring two or more verification factors:
- Something you know (password)
- Something you have (phone, token)
- Something you are (biometric)

### OAuth 2.0
Authorization framework allowing third-party applications limited access to user accounts without exposing passwords.

**Flow**: User → Authorization Server → Access Token → Resource Server

### PCI-DSS (Payment Card Industry Data Security Standard)
Security standard for organizations handling credit card information. Requirements include:
- Encryption of cardholder data
- Secure network architecture
- Regular security testing

### PKI (Public Key Infrastructure)
Framework for managing public key encryption, including digital certificates and certificate authorities.

### RBAC (Role-Based Access Control)
Access control method where permissions are assigned to roles, and users are assigned to roles.

**Example**: Admin role has full access, User role has read-only access

### RSA (Rivest-Shamir-Adleman)
Public-key cryptosystem for secure data transmission. Uses asymmetric encryption with key pairs.

**Key Sizes**: 2048-bit, 4096-bit

### SAML (Security Assertion Markup Language)
XML-based standard for exchanging authentication and authorization data between parties. Used for Single Sign-On (SSO).

### Session
Temporary interactive information exchange between user and system. Session data stored server-side, identified by session ID (stored in cookie).

### SHA (Secure Hash Algorithm)
Family of cryptographic hash functions.

**Variants**:
- **SHA-1**: 160-bit (deprecated)
- **SHA-256**: 256-bit (widely used)
- **SHA-512**: 512-bit (more secure)

### Single Sign-On (SSO)
Authentication scheme allowing users to log in once and access multiple applications without re-authenticating.

### SSL Certificate
Digital certificate that authenticates website identity and enables encrypted connection.

**Types**: DV (Domain Validated), OV (Organization Validated), EV (Extended Validation)

### Token
Piece of data representing authorization or identity. Types include access tokens, refresh tokens, bearer tokens.

**Bearer Token**: Token that grants access to whoever possesses it

### WAF (Web Application Firewall)
Firewall that filters, monitors, and blocks HTTP traffic to/from web applications. Protects against:
- SQL injection
- Cross-site scripting (XSS)
- DDoS attacks

### XSS (Cross-Site Scripting)
Security vulnerability allowing attackers to inject malicious scripts into web pages viewed by other users.

**Types**:
- **Stored XSS**: Malicious script stored on server
- **Reflected XSS**: Script reflected off web server
- **DOM-based XSS**: Vulnerability in client-side code

### Zero Trust
Security model that assumes no trust by default, even inside network perimeter. Requires verification for every access request.

**Principles**: Never trust, always verify

---

## Architecture Patterns

### Actor Model
Concurrency model where "actors" are the fundamental units of computation. Each actor can:
- Send messages to other actors
- Create new actors
- Designate behavior for next message

### Ambassador Pattern
Design pattern where a helper service handles network requests on behalf of a consumer service. Used for service mesh implementations.

### API Gateway Pattern
Single entry point for all clients. Routes requests to appropriate microservices and handles cross-cutting concerns.

### Blue-Green Deployment
Deployment strategy with two identical production environments (Blue and Green). Switch traffic between them for zero-downtime deployments.

### Canary Deployment
Gradual rollout strategy where changes are deployed to small subset of users before full rollout.

**Example**: Deploy to 5% → 25% → 50% → 100%

### Circuit Breaker Pattern
Design pattern that prevents cascading failures by detecting failures and stopping calls to failing service.

**States**: Closed (normal) → Open (failing) → Half-Open (testing recovery)

### Container
Lightweight, standalone package containing application code, runtime, libraries, and dependencies. Isolated from other containers.

**Technology**: Docker

### CQRS (Command Query Responsibility Segregation)
Pattern separating read and write operations into different models.

**Benefits**:
- Optimized data models for each operation
- Independent scaling
- Better performance

### Event-Driven Architecture
Architecture where components communicate through events. Producers emit events, consumers react to them.

### Event Sourcing
Pattern where all changes to application state are stored as sequence of events. Current state derived by replaying events.

**Benefits**: Complete audit trail, time-travel debugging, event replay

### Feature Flag
Technique allowing features to be enabled/disabled without deploying new code. Used for:
- A/B testing
- Gradual rollouts
- Emergency kill switches

### Horizontal Scaling (Scale Out)
Adding more machines/instances to distribute load. Increases capacity by adding servers.

**Example**: 2 servers → 10 servers

### Ingress
Entry point for traffic into cluster/network. Manages external access to services.

### Egress
Exit point for traffic leaving cluster/network.

### Microservices
Architectural style where application is composed of small, independent services that communicate over network.

**Characteristics**:
- Single responsibility
- Independent deployment
- Decentralized data management
- Technology diversity

### Monolith
Traditional architecture where entire application is single, unified unit.

**Pros**: Simpler to develop, deploy, test
**Cons**: Difficult to scale, maintain, deploy

### MVC (Model-View-Controller)
Architectural pattern separating application into three components:
- **Model**: Data and business logic
- **View**: User interface
- **Controller**: Handles user input, updates model/view

### Reactive Programming
Programming paradigm focused on asynchronous data streams and propagation of change.

### Saga Pattern
Pattern for managing distributed transactions across microservices. Sequence of local transactions with compensating transactions for rollback.

### Serverless
Cloud execution model where cloud provider manages server infrastructure. Developer only provides code.

**Examples**: AWS Lambda, Azure Functions, Google Cloud Functions

### Service Mesh
Dedicated infrastructure layer handling service-to-service communication. Provides:
- Load balancing
- Service discovery
- Encryption
- Observability

**Technologies**: Istio, Linkerd, Consul

### Sidecar Pattern
Design pattern where auxiliary component (sidecar) is attached to main application. Provides supporting features without changing main code.

### Vertical Scaling (Scale Up)
Increasing capacity of existing machine (more CPU, RAM, storage).

**Example**: 4GB RAM → 16GB RAM

### Virtual Machine (VM)
Emulation of computer system providing functionality of physical computer. Includes full operating system.

---

## Databases & Storage

### ACID (Atomicity, Consistency, Isolation, Durability)
Set of properties guaranteeing reliable database transactions:
- **Atomicity**: All or nothing
- **Consistency**: Valid state transitions
- **Isolation**: Concurrent transactions don't interfere
- **Durability**: Committed data survives failures

### BASE (Basically Available, Soft state, Eventually consistent)
Alternative to ACID for distributed databases. Prioritizes availability over consistency.

### B-Tree
Self-balancing tree data structure maintaining sorted data for efficient insertion, deletion, and search. Used in database indexes.

**Time Complexity**: O(log n)

### B+Tree
Variant of B-Tree where all records stored in leaf nodes. Better for range queries and sequential access.

### Bitmap Index
Index using bitmaps for efficient query processing on low-cardinality columns.

**Use Case**: Gender (M/F), Status (Active/Inactive)

### Bloom Filter
Space-efficient probabilistic data structure for testing set membership. Can have false positives but no false negatives.

**Use Cases**: Cache filtering, duplicate detection

### Cassandra
Wide-column NoSQL database designed for high availability and scalability. No single point of failure.

**Use Cases**: Time-series data, high write throughput

### Checkpoint
Point in time where database saves state to disk. Used for recovery after crashes.

### Consistent Hashing
Hashing technique that minimizes redistribution when nodes are added/removed. Used for distributed caching and load balancing.

### Count-Min Sketch
Probabilistic data structure for frequency estimation. Uses less memory than exact counting.

### CRUD (Create, Read, Update, Delete)
Four basic operations for persistent storage.

### Cursor-Based Pagination
Pagination using pointer (cursor) to specific record. More efficient than offset-based for large datasets.

### Denormalization
Process of adding redundant data to improve read performance. Trade-off: increased storage, update complexity.

### Dirty Read
Transaction reads uncommitted data from another transaction. Can lead to inconsistent data.

### DynamoDB
AWS NoSQL database service. Key-value and document database with single-digit millisecond latency.

### ElastiCache
AWS managed in-memory caching service. Supports Redis and Memcached.

### Elasticsearch
Distributed search and analytics engine built on Apache Lucene. Used for full-text search, log analytics.

### Faceted Search
Search technique allowing filtering by multiple dimensions (facets).

**Example**: Filter products by brand, price range, rating

### Full-Text Search
Search technique for finding text within documents. Supports relevance ranking and fuzzy matching.

### Fuzzy Search
Search allowing approximate matches. Tolerates typos and spelling variations.

**Algorithm**: Levenshtein distance

### Hash Index
Index using hash function to map keys to locations. Very fast for equality lookups (O(1)).

**Limitation**: Cannot be used for range queries

### HyperLogLog
Probabilistic data structure for estimating cardinality (distinct count) with very little memory.

### Index
Database structure improving query speed. Trade-off: faster reads, slower writes, additional storage.

### Inverted Index
Index mapping content to locations. Core data structure for search engines.

**Example**: Word → [Doc1, Doc3, Doc5]

### Keyset Pagination
Pagination using last seen record's key. More efficient than offset for large datasets.

### Lost Update
Concurrency problem where updates are overwritten by other transactions.

### LSM Tree (Log-Structured Merge Tree)
Data structure optimizing write performance. Writes go to memory then periodically merged to disk.

**Used by**: Cassandra, RocksDB, LevelDB

### Memcached
High-performance, distributed memory caching system. Simple key-value store.

### MongoDB
Document-oriented NoSQL database storing data in JSON-like documents (BSON).

### MVCC (Multi-Version Concurrency Control)
Concurrency control method where database maintains multiple versions of data to allow concurrent access.

### MySQL
Open-source relational database management system. ACID compliant.

### Normalization
Process of organizing data to reduce redundancy and improve integrity.

**Normal Forms**: 1NF, 2NF, 3NF, BCNF

### NoSQL
Non-relational databases designed for specific data models.

**Types**:
- **Document**: MongoDB, Couchbase
- **Key-Value**: Redis, DynamoDB
- **Wide-Column**: Cassandra, HBase
- **Graph**: Neo4j, Neptune

### Offset-Based Pagination
Pagination using OFFSET and LIMIT clauses. Simple but inefficient for large offsets.

### Optimistic Locking
Concurrency control assuming conflicts are rare. Checks for conflicts before committing.

**Implementation**: Version numbers, timestamps

### Partitioning
Dividing database into smaller, manageable pieces (partitions).

**Types**:
- **Horizontal**: Split rows across partitions
- **Vertical**: Split columns across partitions

### Pessimistic Locking
Concurrency control that locks data before reading/writing. Assumes conflicts are common.

### Phantom Read
Transaction reads different data on repeated queries due to inserts/deletes by other transactions.

### PostgreSQL
Advanced open-source relational database. ACID compliant with rich feature set.

### RDS (Relational Database Service)
AWS managed relational database service. Supports MySQL, PostgreSQL, Oracle, SQL Server.

### Read Committed
Isolation level where transaction only sees committed data. Prevents dirty reads.

### Read Uncommitted
Lowest isolation level. Allows dirty reads.

### Redis
In-memory data structure store used as database, cache, and message broker.

**Data Types**: String, List, Set, Sorted Set, Hash, Stream

### Rendezvous Hashing
Consistent hashing variant using scoring function. Better distribution than standard consistent hashing.

### Repeatable Read
Isolation level ensuring same reads within transaction. Prevents dirty and non-repeatable reads.

### Replication
Copying data from one database to another for:
- **High Availability**: Failover to replica
- **Read Scaling**: Distribute read load
- **Disaster Recovery**: Geographic redundancy

**Types**: Master-Slave, Master-Master, Multi-Master

### S3 (Simple Storage Service)
AWS object storage service. Highly durable (99.999999999%) and scalable.

**Storage Classes**: Standard, Intelligent-Tiering, Glacier, Deep Archive

### Serializable
Highest isolation level. Transactions execute as if run serially.

### Sharding
Horizontal partitioning where data distributed across multiple databases based on shard key.

**Example**: Shard by user_id, geographic region

### Snapshot
Point-in-time copy of data. Used for backups and read consistency.

### Snapshot Isolation
Concurrency control where each transaction sees consistent snapshot of database.

### SQL (Structured Query Language)
Standard language for managing relational databases.

### Write Skew
Anomaly where two transactions read overlapping data, make disjoint updates, and commit. Results in inconsistent state.

### Write-Ahead Log (WAL)
Logging technique where changes logged before applied to database. Ensures durability and recovery.

---

## Caching & Performance

### Cache Invalidation
Process of removing or updating stale cache entries.

**Strategies**:
- **TTL (Time to Live)**: Expire after time period
- **Event-based**: Invalidate on data change
- **Lazy**: Invalidate on next access

### Cache-Aside (Lazy Loading)
Caching pattern where application manages cache. On read:
1. Check cache
2. If miss, load from database
3. Update cache

### Caching
Storing frequently accessed data in fast storage layer (memory) to reduce latency and database load.

**Cache Layers**:
- L1: Browser cache
- L2: CDN cache
- L3: Application cache (Redis)

### Connection Pool
Set of reusable database connections maintained for efficiency. Avoids overhead of creating new connections.

### LRU (Least Recently Used)
Cache eviction policy that removes least recently accessed items first.

### Rate Limiter
System component that controls rate of requests to prevent abuse and ensure fair resource usage.

**Algorithms**: Token bucket, Leaky bucket, Fixed window, Sliding window

### Thread Pool
Collection of pre-instantiated threads waiting to execute tasks. Reduces overhead of thread creation.

### TTI (Time to Interactive)
Performance metric measuring time until page becomes fully interactive.

### TTL (Time to Live)
Duration data remains valid in cache before expiration.

### Worker Pool
Collection of worker processes/threads handling background tasks.

---

## Cloud Services (AWS)

### CloudFront
AWS CDN service for content delivery with low latency.

### EC2 (Elastic Compute Cloud)
AWS virtual server service. Provides resizable compute capacity.

**Instance Types**: General Purpose (t3, m5), Compute Optimized (c5), Memory Optimized (r5)

### Lambda
AWS serverless compute service. Runs code in response to events without managing servers.

**Use Cases**: API backends, data processing, real-time file processing

### Subnet
Logical subdivision of IP network. Can be public (internet-accessible) or private (internal only).

### VPC (Virtual Private Cloud)
Isolated virtual network within AWS. Provides complete control over network configuration.

### Route Table
Set of rules (routes) determining where network traffic is directed.

### Security Group
Virtual firewall controlling inbound/outbound traffic to AWS resources.

---

## Messaging & Events

### Backpressure
Flow control mechanism preventing fast producer from overwhelming slow consumer.

### Kafka
Distributed streaming platform for high-throughput, fault-tolerant publish-subscribe messaging.

**Use Cases**: Event sourcing, stream processing, log aggregation

### Message Queue
Asynchronous communication pattern where messages stored in queue until processed.

**Benefits**: Decoupling, load leveling, buffering

### Polling
Technique where client repeatedly checks server for updates at regular intervals.

**Drawback**: Inefficient, increased latency

### Pub/Sub (Publish-Subscribe)
Messaging pattern where publishers send messages to topics, subscribers receive messages from topics.

### RabbitMQ
Message broker implementing Advanced Message Queuing Protocol (AMQP).

### Server-Sent Events (SSE)
Technology enabling server to push updates to client over HTTP connection.

**Use Case**: Real-time notifications, live feeds

### Long Polling
Technique where client makes request and server holds connection open until data available.

### Webhook
HTTP callback triggered by specific events. Server sends HTTP POST to configured URL.

---

## Monitoring & Observability

### Datadog
Cloud monitoring and analytics platform for IT infrastructure, operations, and development.

### ELK Stack (Elasticsearch, Logstash, Kibana)
Suite of tools for centralized logging:
- **Elasticsearch**: Search and analytics
- **Logstash**: Log ingestion and processing
- **Kibana**: Visualization dashboard

### Grafana
Open-source analytics and visualization platform. Creates dashboards from metrics data.

### Jaeger
Distributed tracing system for monitoring and troubleshooting microservices.

### Kibana
Visualization tool for Elasticsearch data. Creates interactive dashboards.

### Prometheus
Open-source monitoring and alerting toolkit. Time-series database for metrics.

### Zipkin
Distributed tracing system helping identify performance bottlenecks in microservices.

---

## System Design Metrics

### Availability
Percentage of time system is operational and accessible.

**Calculation**: (Uptime / Total Time) × 100

**Nines**: 
- 99% = 3.65 days downtime/year
- 99.9% = 8.76 hours downtime/year
- 99.99% = 52.56 minutes downtime/year
- 99.999% = 5.26 minutes downtime/year

### Bandwidth
Maximum rate of data transfer across network path.

**Units**: Mbps (Megabits per second), Gbps (Gigabits per second)

### Disaster Recovery
Policies and procedures for recovering from catastrophic failures.

**Metrics**: RTO (Recovery Time Objective), RPO (Recovery Point Objective)

### Fault Tolerance
System's ability to continue operating despite failures.

### High Availability (HA)
System design ensuring agreed level of operational performance with minimal downtime.

**Techniques**: Redundancy, failover, load balancing, replication

### Idempotency
Property where multiple identical requests have same effect as single request. Critical for retry logic.

**Example**: PUT and DELETE are idempotent, POST is not

### Latency
Time delay between action and response. Measured in milliseconds.

**Types**:
- **Network Latency**: Time for data to travel across network
- **Processing Latency**: Time to process request
- **Database Latency**: Time to query database

### QPS (Queries Per Second)
Number of queries handled by system per second. Measure of throughput.

### RPS (Requests Per Second)
Number of requests handled by system per second.

### Scalability
System's ability to handle increased load by adding resources.

**Types**: Horizontal (add machines), Vertical (add capacity)

### SLA (Service Level Agreement)
Contract defining expected service levels between provider and customer.

**Includes**: Availability, performance, support response times

### SLO (Service Level Objective)
Specific measurable target within SLA.

**Example**: 99.95% uptime, API latency < 200ms (p99)

### Throughput
Amount of work/data processed per unit time.

**Units**: Requests/second, Transactions/second, MB/second

---

## Consistency & Reliability

### CAP Theorem
Theorem stating distributed system can only guarantee 2 of 3 properties:
- **Consistency**: All nodes see same data
- **Availability**: Every request receives response
- **Partition Tolerance**: System continues despite network partitions

### Causal Consistency
Consistency model where causally related operations seen in same order by all processes.

### Consensus
Agreement among distributed processes. Required for leader election, atomic commit.

**Algorithms**: Paxos, Raft

### Consistency
All nodes see same data at same time.

### Distributed Lock
Mechanism ensuring only one process can access shared resource at time across distributed system.

### Eventually Consistent
Consistency model where updates propagate to all replicas eventually, but not immediately.

### Gossip Protocol
Peer-to-peer communication protocol where nodes periodically exchange information. Used for cluster membership, failure detection.

### Head-of-Line Blocking
Performance issue where one slow request blocks subsequent requests in queue.

### Lamport Clock
Logical clock algorithm for ordering events in distributed system.

### Leader Election
Process of designating one process as coordinator in distributed system.

### Merkle Tree
Tree of hashes for efficient verification of large data structures. Used in databases, blockchains.

### Paxos
Consensus algorithm for reaching agreement in distributed system. Complex but proven correct.

### Quorum
Minimum number of nodes that must agree for operation to succeed.

**Formula**: (N/2) + 1 for N nodes

### Raft
Consensus algorithm designed to be easier to understand than Paxos. Used in etcd, Consul.

### Split Brain
Failure scenario where cluster splits into multiple partitions, each believing it's the only active partition.

### Strong Consistency
Consistency model where reads always reflect most recent write.

### Three-Phase Commit (3PC)
Distributed transaction protocol extending two-phase commit to handle coordinator failures.

### Two-Phase Commit (2PC)
Distributed transaction protocol ensuring all participants commit or abort together.

**Phases**: Prepare, Commit

### Vector Clock
Data structure for determining causal ordering of events in distributed system.

---

## Design Patterns

### Bulkhead Pattern
Isolating resources to prevent cascading failures. Separate thread pools for different operations.

### Decorator Pattern
Design pattern adding new functionality to object without modifying structure.

### Observer Pattern
Design pattern where object (subject) maintains list of dependents (observers) and notifies them of state changes.

### Singleton Pattern
Design pattern ensuring class has only one instance with global access point.

### Strategy Pattern
Design pattern enabling algorithm selection at runtime.

---

## Data Structures & Algorithms

### Async (Asynchronous)
Execution model where operations run independently without waiting for previous operations to complete.

### Blocking
Operation that waits for completion before returning control. Blocks thread execution.

### Deadlock
Situation where processes wait for each other indefinitely, unable to proceed.

### Mutex (Mutual Exclusion)
Lock mechanism ensuring only one thread accesses shared resource at time.

### Non-Blocking
Operation that returns immediately, allowing thread to continue execution.

### Priority Inversion
Scheduling problem where high-priority task waits for low-priority task holding required resource.

### Race Condition
Bug where system behavior depends on timing/sequence of uncontrollable events.

### Reader-Writer Lock
Lock allowing multiple readers or single writer, but not both simultaneously.

### Semaphore
Signaling mechanism controlling access to shared resource by multiple processes.

### Starvation
Situation where process cannot gain access to resources and cannot proceed.

### Sync (Synchronous)
Execution model where operations complete before next operation starts. Blocks execution.

---

## Compliance & Regulations

### CAN-SPAM Act
US law regulating commercial email. Requirements:
- Unsubscribe mechanism
- Honest headers and subject lines
- Physical postal address

---

## Common Abbreviations

### API
See: Application Programming Interface

### AWS
Amazon Web Services - Cloud computing platform

### CI/CD
Continuous Integration / Continuous Deployment - Automated software delivery

### CRUD
See: Create, Read, Update, Delete

### CSS
Cascading Style Sheets - Styling language for web pages

### DRY
Don't Repeat Yourself - Software principle avoiding code duplication

### HTML
Hypertext Markup Language - Standard markup language for web pages

### IoT
Internet of Things - Network of physical devices with sensors and connectivity

### KISS
Keep It Simple, Stupid - Software principle favoring simplicity over complexity

### JSON
JavaScript Object Notation - Lightweight data interchange format

### K8s
Kubernetes - Container orchestration platform (K + 8 letters + s)

### ML
Machine Learning - AI that learns from data without explicit programming

### P50, P95, P99 (Percentiles)
Performance metrics:
- **P50**: 50% of requests faster than this value (median)
- **P95**: 95% of requests faster than this value
- **P99**: 99% of requests faster than this value
- **P999**: 99.9% of requests faster than this value

### SOLID
Object-oriented design principles:
- **S**ingle Responsibility
- **O**pen/Closed
- **L**iskov Substitution
- **I**nterface Segregation
- **D**ependency Inversion

### XML
Extensible Markup Language - Markup language for encoding documents

### YAGNI
You Aren't Gonna Need It - Principle to avoid adding functionality until necessary

---

## Additional Important Terms

### A/B Testing
Experimental method comparing two versions to determine which performs better. Used for feature optimization and user experience improvements.

**Process**: Split users into groups → Show different versions → Measure metrics → Choose winner

### API Versioning
Practice of managing changes to APIs while maintaining backward compatibility.

**Strategies**: URI versioning (v1/v2), Header versioning, Query parameter versioning

### Asynchronous Processing
Executing operations independently without blocking. Task initiated then continues in background.

**Benefits**: Better responsiveness, resource utilization, scalability

### Batching
Grouping multiple operations together for efficient processing.

**Benefits**: Reduced network overhead, improved throughput, better resource utilization

### Compression
Reducing data size for storage or transmission.

**Algorithms**: Gzip, Brotli (text), WebP, JPEG (images)

### Cookie
Small piece of data stored by browser, sent with requests to same domain.

**Uses**: Session management, personalization, tracking

### Data Warehouse
Central repository for structured data from multiple sources. Optimized for analytics and reporting.

**Examples**: Amazon Redshift, Google BigQuery, Snowflake

### Debouncing
Limiting function execution frequency. Executes only after specific time has passed since last invocation.

**Use Case**: Search autocomplete, resize events

### Docker
Platform for developing, shipping, and running applications in containers.

### HAProxy
High-performance TCP/HTTP load balancer and proxy server.

### Kubernetes (K8s)
Open-source container orchestration platform for automating deployment, scaling, and management.

### Load Balancer
Distributes incoming traffic across multiple servers to ensure availability and reliability.

**Types**: Layer 4 (TCP/UDP), Layer 7 (HTTP/HTTPS)

**Algorithms**: Round-robin, Least connections, IP hash, Weighted round-robin

### Nginx
High-performance web server and reverse proxy server.

**Uses**: Web server, reverse proxy, load balancer, API gateway

### Pre-signed URL
Temporary URL granting time-limited access to private resource.

**Use Case**: S3 file uploads/downloads without AWS credentials

### Stream Processing
Processing data in real-time as it arrives, rather than batch processing.

**Technologies**: Apache Flink, Apache Storm, Kafka Streams

### Throttling
Limiting rate of operations to prevent system overload or abuse.

**Difference from Rate Limiting**: Throttling slows down, rate limiting blocks

### WebP
Modern image format providing superior compression for web images. 25-35% smaller than JPEG.

---

## Protocol-Specific Terms

### AMQP (Advanced Message Queuing Protocol)
Open standard application layer protocol for message-oriented middleware.

### FTP (File Transfer Protocol)
Standard network protocol for transferring files between client and server.

### SMTP (Simple Mail Transfer Protocol)
Protocol for sending email messages between servers.

### SNI (Server Name Indication)
TLS extension allowing server to present multiple SSL certificates on single IP address.

---

## Database-Specific Terms

### BSON (Binary JSON)
Binary-encoded serialization of JSON-like documents. Used by MongoDB.

### Data Replication
See: [Replication](#replication)

### Eventually Consistent
See: [Eventually Consistent](#eventually-consistent) under Consistency & Reliability

### Graph Database
Database using graph structures (nodes, edges, properties) for semantic queries.

**Examples**: Neo4j, Amazon Neptune

**Use Cases**: Social networks, recommendation engines, fraud detection

### Time-Series Database
Database optimized for time-stamped data.

**Examples**: InfluxDB, TimescaleDB, Prometheus

**Use Cases**: Metrics, IoT data, financial data

### Wide-Column Store
NoSQL database storing data in columns rather than rows.

**Examples**: Cassandra, HBase

---

## System Design Best Practices

### Back-of-Envelope Calculations
Quick estimations for system requirements (storage, bandwidth, QPS).

**Powers of Two**:
- 1 KB = 2^10 bytes = 1,024 bytes
- 1 MB = 2^20 bytes ≈ 1 million bytes
- 1 GB = 2^30 bytes ≈ 1 billion bytes
- 1 TB = 2^40 bytes ≈ 1 trillion bytes

**Latency Numbers**:
- L1 cache: 0.5 ns
- L2 cache: 7 ns
- RAM: 100 ns
- SSD: 150 μs
- HDD: 10 ms
- Network (same datacenter): 500 μs
- Network (cross-region): 150 ms

### Graceful Degradation
System design principle where service continues with reduced functionality during partial failures.

**Example**: Show cached data when database unavailable

### Health Check
Automated test determining if service is functioning properly.

**Types**: Shallow (basic connectivity), Deep (full functionality verification)

### Retry Logic
Mechanism automatically re-attempting failed operations.

**Strategies**: Exponential backoff, jitter, circuit breaker integration

### Stateless vs Stateful
**Stateless**: Each request independent, no session data stored
**Stateful**: Server maintains session information between requests

---

## Quick Reference: Common Values

### Standard Ports
- **HTTP**: 80
- **HTTPS**: 443
- **SSH**: 22
- **FTP**: 21
- **MySQL**: 3306
- **PostgreSQL**: 5432
- **MongoDB**: 27017
- **Redis**: 6379
- **Kafka**: 9092
- **Elasticsearch**: 9200

### HTTP Status Codes
**2xx Success**:
- 200 OK
- 201 Created
- 204 No Content

**3xx Redirection**:
- 301 Moved Permanently
- 302 Found (Temporary Redirect)
- 304 Not Modified

**4xx Client Errors**:
- 400 Bad Request
- 401 Unauthorized
- 403 Forbidden
- 404 Not Found
- 429 Too Many Requests

**5xx Server Errors**:
- 500 Internal Server Error
- 502 Bad Gateway
- 503 Service Unavailable
- 504 Gateway Timeout

---

## Document Information

**Version**: 1.0  
**Last Updated**: January 17, 2025  
**Purpose**: Comprehensive technical glossary for System Design HLD documents  
**Coverage**: 250+ terms across networking, security, architecture, databases, and more

---

## How to Use This Glossary

1. **During HLD Review**: Reference terms you encounter in design documents
2. **Interview Preparation**: Review key concepts and definitions
3. **Quick Reference**: Use Table of Contents for fast navigation
4. **Learning Path**: Start with fundamentals (Networking, Databases) then advance to patterns

---

## Contributing

Found a missing term or error? Suggestions for improvement are welcome. This glossary is a living document and will be updated as new terms emerge in the system design space.

---

**Note**: This glossary covers terms commonly used in High-Level Design documents. For Low-Level Design (LLD) specific terms, design patterns, or language-specific terminology, please refer to additional specialized documentation.
