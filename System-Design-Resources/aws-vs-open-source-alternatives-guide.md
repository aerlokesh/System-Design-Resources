# AWS Services vs Open Source Alternatives - System Design Guide

## Overview
This guide provides a comprehensive comparison of AWS services with their open source and other cloud alternatives, including when to use them in system design and practical examples.

---

## Service Comparison Table

**Total Services Covered: 36 AWS Services**

| AWS Service | Open Source / Other Alternatives | When to Use in System Design | Examples (3-4) |
|------------|----------------------------------|------------------------------|----------------|
| **EC2 (Elastic Compute Cloud)** | • Docker + Kubernetes<br>• GCP Compute Engine<br>• Azure Virtual Machines<br>• DigitalOcean Droplets<br>• Bare metal servers | Use when you need full control over computing resources, custom OS configurations, or long-running stateful applications | 1. **Microservices hosting**: Deploy multiple containerized services<br>2. **Database servers**: Host MongoDB, PostgreSQL with specific configurations<br>3. **Batch processing**: ETL jobs requiring specific OS dependencies<br>4. **Game servers**: Multiplayer gaming servers with custom networking |
| **Lambda (Serverless Functions)** | • OpenFaaS<br>• Apache OpenWhisk<br>• Knative<br>• GCP Cloud Functions<br>• Azure Functions<br>• Cloudflare Workers | Use for event-driven architectures, short-lived tasks, unpredictable/sporadic workloads, and microservices without managing servers | 1. **Image processing**: Resize/compress images on S3 upload<br>2. **API backends**: REST API for mobile apps with variable traffic<br>3. **Scheduled tasks**: Cron jobs for data cleanup, report generation<br>4. **Webhook handlers**: Process GitHub webhooks, payment notifications |
| **S3 (Simple Storage Service)** | • MinIO<br>• Ceph<br>• OpenStack Swift<br>• GCP Cloud Storage<br>• Azure Blob Storage<br>• Backblaze B2 | Use for storing unstructured data, static assets, backups, data lakes, and content that needs high durability and availability | 1. **Media storage**: User-uploaded images/videos for social platforms<br>2. **Static website hosting**: React/Angular app hosting<br>3. **Data lake**: Raw logs, analytics data for processing<br>4. **Backup storage**: Database backups, application snapshots |
| **RDS (Relational Database Service)** | • PostgreSQL (self-managed)<br>• MySQL (self-managed)<br>• MariaDB<br>• GCP Cloud SQL<br>• Azure Database<br>• PlanetScale | Use for structured data with ACID requirements, complex queries, transactions, and relational data models | 1. **E-commerce**: Product catalog, orders, inventory management<br>2. **User management**: Authentication, profiles, permissions<br>3. **Financial systems**: Transactions, account balances, audit logs<br>4. **SaaS applications**: Multi-tenant data with complex relationships |
| **DynamoDB (NoSQL Database)** | • MongoDB<br>• Cassandra<br>• ScyllaDB<br>• CouchDB<br>• GCP Firestore<br>• Azure Cosmos DB | Use for high-throughput key-value access, flexible schemas, single-digit millisecond latency, and massive scale requirements | 1. **Session management**: User sessions for web applications<br>2. **Gaming leaderboards**: High-write workloads with fast reads<br>3. **IoT data**: Sensor data collection and retrieval<br>4. **Shopping carts**: Real-time cart updates with high concurrency |
| **ElastiCache (Redis/Memcached)** | • Redis (self-managed)<br>• Memcached (self-managed)<br>• Hazelcast<br>• Apache Ignite<br>• Dragonfly | Use for caching frequently accessed data, session storage, real-time analytics, pub/sub messaging, and reducing database load | 1. **API response caching**: Cache expensive database queries<br>2. **Session store**: Distributed session management<br>3. **Rate limiting**: Token bucket counters for API throttling<br>4. **Real-time leaderboards**: Sorted sets for gaming rankings |
| **SQS (Simple Queue Service)** | • RabbitMQ<br>• Apache Kafka<br>• Redis Streams<br>• ActiveMQ<br>• NATS<br>• GCP Pub/Sub | Use for decoupling services, asynchronous processing, load leveling, and building reliable distributed systems | 1. **Order processing**: Decouple order placement from fulfillment<br>2. **Email sending**: Queue emails for async delivery<br>3. **Video transcoding**: Queue video processing jobs<br>4. **Notification system**: Queue push notifications for delivery |
| **SNS (Simple Notification Service)** | • Apache Kafka<br>• RabbitMQ<br>• NATS<br>• Redis Pub/Sub<br>• GCP Pub/Sub | Use for pub/sub messaging patterns, fan-out scenarios, push notifications, and broadcasting events to multiple subscribers | 1. **Order notifications**: Notify inventory, shipping, analytics systems<br>2. **Mobile push**: Send notifications to iOS/Android apps<br>3. **Alert system**: Broadcast system alerts to multiple endpoints<br>4. **Event broadcasting**: Publish domain events to microservices |
| **Kinesis (Streaming Data)** | • Apache Kafka<br>• Apache Pulsar<br>• Apache Flink<br>• GCP Dataflow<br>• RedPanda | Use for real-time data streaming, log aggregation, clickstream analysis, and time-series data processing | 1. **Log aggregation**: Collect logs from distributed services<br>2. **Real-time analytics**: Process clickstream data for dashboards<br>3. **IoT data pipeline**: Ingest sensor data for real-time processing<br>4. **Change data capture**: Stream database changes to data warehouse |
| **ELB/ALB (Load Balancer)** | • NGINX<br>• HAProxy<br>• Traefik<br>• Envoy<br>• GCP Load Balancer<br>• Azure Load Balancer | Use for distributing traffic across multiple instances, improving availability, SSL termination, and routing based on content | 1. **Web application**: Distribute HTTP/HTTPS traffic across web servers<br>2. **Microservices routing**: Path-based routing to different services<br>3. **Blue-green deployment**: Route traffic between versions<br>4. **Geographic routing**: Route users to nearest data center |
| **CloudFront (CDN)** | • Cloudflare<br>• Fastly<br>• Akamai<br>• Varnish Cache<br>• GCP Cloud CDN<br>• Azure CDN | Use for serving static content globally, reducing latency, DDoS protection, and offloading origin servers | 1. **Static website**: Serve React app globally with low latency<br>2. **Video streaming**: Distribute video content worldwide<br>3. **API acceleration**: Cache API responses at edge locations<br>4. **Software downloads**: Distribute large files efficiently |
| **ECS/EKS (Container Orchestration)** | • Kubernetes<br>• Docker Swarm<br>• Nomad<br>• OpenShift<br>• GCP GKE<br>• Azure AKS | Use for deploying containerized applications, auto-scaling, service discovery, and managing microservices at scale | 1. **Microservices platform**: Deploy and manage 50+ microservices<br>2. **CI/CD pipelines**: Run build and test containers<br>3. **Batch processing**: Kubernetes jobs for data processing<br>4. **Multi-tenant SaaS**: Isolate customer workloads in containers |
| **API Gateway** | • Kong<br>• Tyk<br>• KrakenD<br>• Apache APISIX<br>• GCP API Gateway<br>• Azure API Management | Use for managing API endpoints, authentication, rate limiting, request/response transformation, and monitoring APIs | 1. **REST API management**: Single entry point for microservices<br>2. **Mobile backend**: Authenticate and route mobile app requests<br>3. **API monetization**: Rate limit based on subscription tiers<br>4. **Legacy system integration**: Transform requests for old backends |
| **CloudWatch (Monitoring)** | • Prometheus + Grafana<br>• ELK Stack (Elasticsearch, Logstash, Kibana)<br>• Datadog<br>• New Relic<br>• GCP Stackdriver | Use for monitoring application metrics, logs, setting alarms, and visualizing system health | 1. **Application monitoring**: Track API latency, error rates<br>2. **Infrastructure monitoring**: CPU, memory, disk usage alerts<br>3. **Log analysis**: Search and analyze application logs<br>4. **Business metrics**: Track DAU, revenue, conversion rates |
| **Route 53 (DNS Service)** | • BIND<br>• PowerDNS<br>• CoreDNS<br>• Cloudflare DNS<br>• GCP Cloud DNS<br>• Azure DNS | Use for domain registration, DNS routing, health checks, and traffic management across regions | 1. **Multi-region failover**: Route to healthy region automatically<br>2. **Geo-routing**: Direct users to nearest data center<br>3. **Weighted routing**: A/B testing with traffic splitting<br>4. **Subdomain management**: Service discovery for microservices |
| **ElasticSearch Service** | • Elasticsearch (self-managed)<br>• OpenSearch<br>• Solr<br>• Typesense<br>• Meilisearch | Use for full-text search, log analytics, real-time search applications, and complex queries on large datasets | 1. **E-commerce search**: Product search with filters, facets<br>2. **Content management**: Search across documents, articles<br>3. **Log analytics**: Search and analyze application logs<br>4. **Autocomplete**: Type-ahead search suggestions |
| **EventBridge (Event Bus)** | • Apache Kafka<br>• RabbitMQ<br>• Redis Streams<br>• NATS<br>• GCP Eventarc | Use for event-driven architectures, integrating SaaS applications, scheduled events, and building loosely coupled systems | 1. **Microservices communication**: Event-driven service integration<br>2. **SaaS integration**: Connect Salesforce, Zendesk events<br>3. **Workflow orchestration**: Trigger workflows based on events<br>4. **Scheduled tasks**: Cron-like scheduling for AWS services |
| **Step Functions (Workflow Orchestration)** | • Apache Airflow<br>• Temporal<br>• Camunda<br>• Prefect<br>• GCP Workflows | Use for coordinating distributed applications, long-running workflows, error handling, and state management | 1. **Order fulfillment**: Orchestrate payment, inventory, shipping<br>2. **Data pipeline**: Coordinate ETL jobs with dependencies<br>3. **User onboarding**: Multi-step signup with email verification<br>4. **Video processing**: Coordinate transcode, thumbnail, upload |
| **SES (Simple Email Service)** | • Postfix<br>• SendGrid<br>• Mailgun<br>• Postmark<br>• SMTP servers | Use for sending transactional emails, marketing emails, and receiving emails programmatically | 1. **Transactional emails**: Order confirmations, password resets<br>2. **Marketing campaigns**: Newsletter distribution<br>3. **Email notifications**: System alerts to administrators<br>4. **Email verification**: Send and verify user email addresses |
| **Secrets Manager** | • HashiCorp Vault<br>• Kubernetes Secrets<br>• Azure Key Vault<br>• GCP Secret Manager<br>• Doppler | Use for storing and managing sensitive data like API keys, passwords, certificates with rotation support | 1. **Database credentials**: Rotate DB passwords automatically<br>2. **API keys**: Store third-party service credentials<br>3. **SSL certificates**: Manage and rotate TLS certificates<br>4. **OAuth tokens**: Store and refresh access tokens |
| **Aurora (MySQL/PostgreSQL)** | • PostgreSQL (self-managed)<br>• MySQL (self-managed)<br>• CockroachDB<br>• GCP Cloud Spanner<br>• YugabyteDB | Use when you need RDS features with better performance, automatic failover, and read replicas at scale | 1. **High-traffic applications**: Handle millions of transactions<br>2. **Global applications**: Multi-region read replicas<br>3. **Analytics workloads**: Separate read replicas for reports<br>4. **SaaS platforms**: Auto-scaling database for variable load |
| **Redshift (Data Warehouse)** | • PostgreSQL<br>• Apache Druid<br>• ClickHouse<br>• GCP BigQuery<br>• Snowflake<br>• Apache Hive | Use for OLAP queries, business intelligence, large-scale analytics, and historical data analysis | 1. **Business intelligence**: Power Tableau/Looker dashboards<br>2. **Data analytics**: Analyze years of transaction history<br>3. **ETL destination**: Load transformed data for reporting<br>4. **Customer analytics**: Aggregate user behavior across time |
| **Glue (ETL Service)** | • Apache Spark<br>• Apache NiFi<br>• Talend<br>• Airbyte<br>• GCP Dataflow | Use for data integration, ETL jobs, data catalog, and transforming data between different formats | 1. **Data lake ETL**: Transform raw data to analytics-ready format<br>2. **Database migration**: Move data between databases<br>3. **Data cataloging**: Discover and organize data sources<br>4. **Real-time ETL**: Stream processing and transformation |
| **CloudFormation (IaC)** | • Terraform<br>• Pulumi<br>• Ansible<br>• Chef<br>• Puppet<br>• GCP Deployment Manager | Use for infrastructure as code, repeatable deployments, version control of infrastructure, and multi-environment management | 1. **Environment provisioning**: Create dev/staging/prod environments<br>2. **Disaster recovery**: Recreate infrastructure from templates<br>3. **Multi-region deployment**: Deploy same stack to multiple regions<br>4. **Team collaboration**: Version control infrastructure changes |
| **Cognito (User Management)** | • Auth0<br>• Keycloak<br>• Okta<br>• Firebase Auth<br>• Azure AD B2C<br>• FusionAuth | Use for user authentication, authorization, user pools, social login, and token management | 1. **Mobile app authentication**: OAuth2 flows for iOS/Android<br>2. **Web application login**: Email/password + social login<br>3. **API authorization**: JWT tokens for microservices<br>4. **Multi-tenant SaaS**: Isolate user data per organization |
| **Athena (Serverless Query)** | • Presto<br>• Apache Drill<br>• Dremio<br>• GCP BigQuery<br>• Trino | Use for ad-hoc querying of S3 data, log analysis, and running SQL on data lakes without infrastructure | 1. **Log analysis**: Query CloudWatch logs in S3<br>2. **Data exploration**: Ad-hoc analysis of data lake<br>3. **Cost optimization**: Analyze AWS billing data<br>4. **A/B test analysis**: Query experiment results from S3 |
| **MSK (Managed Streaming for Kafka)** | • Apache Kafka (self-managed)<br>• Apache Pulsar<br>• RedPanda<br>• Confluent Platform<br>• GCP Pub/Sub | Use for real-time streaming, event sourcing, log aggregation, and building event-driven architectures with Kafka ecosystem | 1. **Event streaming**: Stream order events across microservices<br>2. **Log aggregation**: Centralize logs from distributed systems<br>3. **Change data capture**: Stream database changes in real-time<br>4. **Real-time analytics**: Feed data to stream processing engines |
| **EMR (Elastic MapReduce)** | • Apache Hadoop<br>• Apache Spark<br>• Apache Flink<br>• Databricks<br>• GCP Dataproc | Use for big data processing, batch analytics, ETL at scale, machine learning pipelines, and processing petabytes of data | 1. **Batch analytics**: Process terabytes of log data daily<br>2. **ETL pipelines**: Transform raw data for data warehouse<br>3. **Machine learning**: Train models on large datasets<br>4. **Genomics processing**: Analyze DNA sequencing data |
| **Neptune (Graph Database)** | • Neo4j<br>• ArangoDB<br>• JanusGraph<br>• OrientDB<br>• TigerGraph | Use for highly connected data, social networks, recommendation engines, fraud detection, and knowledge graphs | 1. **Social network**: Friends-of-friends queries<br>2. **Recommendation engine**: Product/content recommendations<br>3. **Fraud detection**: Detect fraud patterns in transactions<br>4. **Knowledge graphs**: Build entity relationship graphs |
| **DocumentDB (MongoDB-compatible)** | • MongoDB (self-managed)<br>• CouchDB<br>• Couchbase<br>• GCP Firestore<br>• Azure Cosmos DB | Use for document-based data models, flexible schemas, JSON storage, and when migrating from MongoDB | 1. **Content management**: Store articles, blogs with varying structure<br>2. **Product catalog**: E-commerce with diverse product attributes<br>3. **User profiles**: Store user data with flexible schemas<br>4. **IoT data**: Store sensor readings with varying formats |
| **Timestream (Time-Series Database)** | • InfluxDB<br>• TimescaleDB<br>• Prometheus<br>• QuestDB<br>• OpenTSDB | Use for IoT data, DevOps metrics, application monitoring, and any time-stamped data with high ingestion rates | 1. **IoT monitoring**: Store millions of sensor readings/second<br>2. **Application metrics**: Store and query performance metrics<br>3. **Financial data**: Stock prices, trading data over time<br>4. **Fleet monitoring**: Track vehicle telemetry data |
| **EFS (Elastic File System)** | • NFS servers<br>• GlusterFS<br>• CephFS<br>• GCP Filestore<br>• Azure Files | Use for shared file storage across multiple EC2 instances, containerized applications, and lift-and-shift migrations | 1. **Content management**: Shared storage for web servers<br>2. **Data science**: Shared datasets for ML training<br>3. **Container storage**: Persistent volumes for Kubernetes<br>4. **Development**: Shared codebase for dev environments |
| **Batch (Batch Computing)** | • Apache Airflow<br>• Kubernetes Jobs<br>• Slurm<br>• GCP Batch<br>• Azure Batch | Use for batch processing jobs, scheduled tasks, data processing pipelines, and high-performance computing | 1. **Video transcoding**: Process videos in parallel<br>2. **Financial reports**: Generate end-of-day reports<br>3. **Image processing**: Batch resize/compress images<br>4. **Simulation jobs**: Run scientific simulations at scale |
| **SageMaker (Machine Learning)** | • TensorFlow<br>• PyTorch<br>• Kubeflow<br>• MLflow<br>• GCP Vertex AI<br>• Azure ML | Use for building, training, and deploying ML models, experiment tracking, and managing ML lifecycle | 1. **Recommendation system**: Train and deploy product recommendations<br>2. **Fraud detection**: Build ML models for fraud detection<br>3. **Image recognition**: Train computer vision models<br>4. **NLP applications**: Sentiment analysis, chatbots |
| **AppSync (GraphQL API)** | • Apollo Server<br>• Hasura<br>• Prisma<br>• Postgraphile<br>• GCP Cloud Endpoints | Use for building GraphQL APIs, real-time data sync, offline support, and mobile/web backends | 1. **Mobile backend**: Unified API for iOS/Android apps<br>2. **Real-time chat**: Subscribe to messages with GraphQL subscriptions<br>3. **Dashboard**: Aggregate data from multiple sources<br>4. **Offline-first apps**: Sync data when back online |
| **DMS (Database Migration Service)** | • Debezium<br>• Flyway<br>• Liquibase<br>• GCP Database Migration<br>• Azure DMS | Use for migrating databases with minimal downtime, ongoing replication, and database consolidation | 1. **Cloud migration**: Migrate on-premise DB to AWS<br>2. **Database upgrade**: Migrate MySQL 5.7 to 8.0<br>3. **Continuous replication**: Keep on-premise and cloud in sync<br>4. **Data consolidation**: Merge multiple DBs into one |
| **ElastiCache for Valkey** | • Valkey (self-managed)<br>• Redis (self-managed)<br>• KeyDB<br>• Dragonfly | Use for high-performance caching with Redis-compatible API, session storage, and real-time applications | 1. **API caching**: Cache API responses with Redis protocol<br>2. **Session management**: Store user sessions<br>3. **Leaderboards**: Real-time gaming rankings<br>4. **Pub/Sub messaging**: Real-time notifications |

---

## Decision Framework

### When to Choose AWS Services:
- **Quick time to market**: Managed services reduce operational overhead
- **Integrated ecosystem**: Services work seamlessly together
- **Scalability requirements**: Auto-scaling and pay-as-you-go pricing
- **Limited DevOps resources**: Don't want to manage infrastructure
- **Compliance requirements**: AWS compliance certifications needed
- **Global reach**: Need presence in multiple regions quickly

### When to Choose Open Source Alternatives:
- **Cost sensitivity**: Avoid vendor lock-in and reduce costs
- **On-premise requirements**: Deploy in your own data center
- **Specific customization**: Need features not available in managed services
- **Multi-cloud strategy**: Avoid dependency on single cloud provider
- **Learning/experimentation**: Learn without cloud costs
- **Regulatory constraints**: Data must stay on specific infrastructure

### When to Choose Other Cloud Providers:
- **Multi-cloud strategy**: Leverage best-of-breed services
- **Geographic coverage**: Better regional presence (e.g., GCP in Asia)
- **Cost optimization**: Compare pricing across providers
- **Existing contracts**: Leverage enterprise agreements
- **Specialized services**: Unique offerings (e.g., BigQuery, Azure AD)

---

## System Design Selection Guide

### High-Throughput Systems
**Choose**: Kinesis/Kafka, DynamoDB/Cassandra, ElastiCache/Redis
- Example: Real-time analytics platform processing millions of events/second

### Cost-Optimized Systems
**Choose**: EC2 Spot Instances, Open Source alternatives on bare metal
- Example: Batch processing workloads with flexible scheduling

### Multi-Region Systems
**Choose**: CloudFront/Cloudflare, Route 53, Aurora Global Database
- Example: Global SaaS application with <100ms latency worldwide

### Microservices Architecture
**Choose**: EKS/Kubernetes, API Gateway/Kong, EventBridge/Kafka
- Example: E-commerce platform with 50+ independent services

### Data-Intensive Systems
**Choose**: S3/MinIO, Redshift/ClickHouse, Glue/Spark, EMR/Hadoop
- Example: Big data analytics platform processing petabytes

### Real-Time Streaming Systems
**Choose**: MSK/Kafka, Kinesis, Flink on EMR, Timestream/InfluxDB
- Example: Real-time fraud detection processing millions of transactions/second

### Event-Driven Systems
**Choose**: Lambda/OpenFaaS, SQS/RabbitMQ, SNS/Kafka, EventBridge
- Example: Order processing system with async workflows

### Machine Learning Systems
**Choose**: SageMaker/Kubeflow, EMR/Spark, S3/MinIO for data lakes
- Example: Recommendation engine training on billions of user interactions

### Graph-Based Systems
**Choose**: Neptune/Neo4j, DocumentDB/MongoDB for flexible schemas
- Example: Social network with complex relationship queries

---

## System Design Interview Insights & Best Practices

### Compute Layer Insights

**EC2 - Avoid When Possible**
- ❌ **Anti-pattern**: Using EC2 for everything (requires manual scaling, patching, monitoring)
- ✅ **Better alternatives**: Lambda for event-driven, ECS/EKS for containers, managed services when available
- **When EC2 is actually needed**: Legacy apps, specific OS requirements, long-running stateful processes, GPU workloads

**Lambda Best Practices**
- ✅ Keep functions stateless and idempotent
- ✅ Use environment variables for configuration
- ✅ Set appropriate timeout and memory limits
- ✅ Warm up functions to avoid cold starts for critical paths
- ❌ **Anti-pattern**: Running long-running tasks (>15 min) - use Step Functions + multiple Lambdas or Fargate

**Container Orchestration**
- ✅ Prefer ECS/EKS over self-managed EC2 fleet
- ✅ Use Fargate for serverless containers (no infrastructure management)
- ✅ Implement health checks and auto-scaling policies

---

### Storage & Database Insights

**S3 Best Practices**
- ✅ **Use presigned URLs for direct uploads** - Avoid routing files through your servers
  - Reduces server load and bandwidth costs
  - Improves upload speed (direct to S3)
  - Better security (temporary, scoped access)
- ✅ Enable versioning for critical data
- ✅ Use S3 Transfer Acceleration for global uploads
- ✅ Implement lifecycle policies to move data to cheaper storage tiers (Glacier)
- ✅ Use CloudFront for serving static assets (not direct S3 URLs)
- ❌ **Anti-pattern**: Storing frequently accessed data in Glacier
- ❌ **Anti-pattern**: Not using multipart upload for large files (>100MB)

**DynamoDB Best Practices**
- ✅ **Use DAX (DynamoDB Accelerator) for read-heavy workloads** - Microsecond latency caching
  - In-memory cache in front of DynamoDB
  - Reduces read costs and improves performance
  - Write-through cache (automatic invalidation)
- ✅ Design partition keys to avoid hot partitions
- ✅ Use GSI (Global Secondary Index) for alternate query patterns
- ✅ Enable point-in-time recovery for critical tables
- ✅ Use DynamoDB Streams for change data capture
- ❌ **Anti-pattern**: Using DynamoDB for complex queries (use RDS/Aurora instead)
- ❌ **Anti-pattern**: Not planning access patterns before schema design

**RDS/Aurora Best Practices**
- ✅ Use read replicas to scale reads (up to 15 for Aurora)
- ✅ Enable Multi-AZ for high availability
- ✅ Use Aurora Serverless for variable workloads
- ✅ Implement connection pooling (RDS Proxy)
- ❌ **Anti-pattern**: Single DB instance for production (no failover)
- ❌ **Anti-pattern**: Not using read replicas for reporting queries

**ElastiCache Best Practices**
- ✅ Use Redis for complex data structures, pub/sub, persistence
- ✅ Use Memcached for simple caching, multi-threaded performance
- ✅ Implement cache warming strategies
- ✅ Set appropriate TTL (Time To Live) values
- ✅ Use cluster mode for horizontal scaling (Redis)
- ❌ **Anti-pattern**: Using database as primary cache (cache aside pattern is better)

---

### Messaging & Streaming Insights

**SQS Best Practices**
- ✅ Use FIFO queues when order matters (lower throughput)
- ✅ Use standard queues for high throughput (at-least-once delivery)
- ✅ Implement dead letter queues (DLQ) for failed messages
- ✅ Set visibility timeout > function execution time
- ✅ Use long polling to reduce costs and latency
- ❌ **Anti-pattern**: Not handling duplicate messages (idempotency)

**SNS Best Practices**
- ✅ Use fan-out pattern (SNS → multiple SQS queues)
- ✅ Implement message filtering to reduce unnecessary processing
- ✅ Use FIFO topics when order matters across subscribers

**Kinesis/MSK Best Practices**
- ✅ Choose MSK for Kafka ecosystem compatibility
- ✅ Choose Kinesis for AWS-native integration
- ✅ Shard based on partition key to distribute load
- ✅ Use Kinesis Data Firehose for direct S3/Redshift delivery
- ❌ **Anti-pattern**: Using Kinesis for request-response patterns (use SQS/API Gateway)

---

### API & Networking Insights

**API Gateway Best Practices**
- ✅ Implement rate limiting and throttling per client
- ✅ Use caching to reduce backend load
- ✅ Enable CloudWatch logging for monitoring
- ✅ Use custom domains with SSL certificates
- ✅ Implement request/response validation
- ❌ **Anti-pattern**: Exposing Lambda functions directly (use API Gateway)

**Load Balancer Selection**
- ✅ ALB (Application Load Balancer) - HTTP/HTTPS, path-based routing, WebSocket
- ✅ NLB (Network Load Balancer) - TCP/UDP, static IP, extreme performance
- ✅ Enable access logs for troubleshooting
- ✅ Implement health checks with appropriate intervals

**CloudFront Best Practices**
- ✅ Use CloudFront for all static assets (not direct S3)
- ✅ Implement cache invalidation strategies
- ✅ Use Lambda@Edge for edge computing
- ✅ Enable compression for text-based content
- ✅ Use signed URLs/cookies for private content
- ❌ **Anti-pattern**: Not using CDN for global users

---

### Architecture Patterns

**Avoid Over-Engineering**
- ❌ Don't use microservices for small teams (<5 developers)
- ❌ Don't use Kubernetes for simple applications (use ECS/Fargate)
- ❌ Don't implement caching without measuring (premature optimization)
- ❌ Don't use NoSQL if you have complex relational data

**Embrace Managed Services**
- ✅ Prefer managed services over self-hosted (reduces operational burden)
- ✅ Use serverless when possible (Lambda, DynamoDB, API Gateway)
- ✅ Let AWS handle undifferentiated heavy lifting

**Design for Failure**
- ✅ Implement circuit breakers and retries with exponential backoff
- ✅ Use multiple availability zones (Multi-AZ)
- ✅ Implement health checks and auto-recovery
- ✅ Design idempotent operations

**Cost Optimization**
- ✅ Use S3 Intelligent-Tiering for unpredictable access patterns
- ✅ Use Reserved Instances/Savings Plans for predictable workloads
- ✅ Use Spot Instances for fault-tolerant batch jobs
- ✅ Implement auto-scaling to match demand
- ✅ Use CloudWatch to identify idle resources

**Security Best Practices**
- ✅ Enable encryption at rest and in transit
- ✅ Use IAM roles instead of access keys
- ✅ Implement least privilege access
- ✅ Use VPC and security groups for network isolation
- ✅ Enable CloudTrail for audit logging
- ✅ Use AWS Secrets Manager for credentials (not environment variables)

---

## Common Interview Anti-Patterns to Avoid

1. **Using EC2 as default** - Always consider serverless/managed services first
2. **Not using caching** - Add Redis/CloudFront/DAX to reduce load and latency
3. **Single point of failure** - No Multi-AZ, no read replicas, no failover
4. **Uploading through application servers** - Use presigned URLs for direct S3 uploads
5. **Not implementing rate limiting** - API Gateway throttling, WAF rules
6. **Ignoring security** - No encryption, public S3 buckets, hardcoded credentials
7. **No monitoring/alerting** - CloudWatch alarms for critical metrics
8. **Not considering costs** - Ignoring data transfer costs, storage tiering
9. **Synchronous everything** - Use async processing (SQS, SNS, EventBridge)
10. **Not planning for scale** - No auto-scaling, no read replicas, no CDN

---

## Quick Decision Checklist for Interviews

**Storage Decision Tree:**
```
Structured data + ACID? → RDS/Aurora
Key-value + low latency? → DynamoDB
Files/Objects? → S3
Time-series data? → Timestream
Graph data? → Neptune
Full-text search? → ElasticSearch/OpenSearch
```

**Compute Decision Tree:**
```
Event-driven + short tasks? → Lambda
Containers? → ECS/EKS on Fargate
Long-running stateful? → ECS/EKS on EC2 (last resort)
Batch processing? → AWS Batch or Lambda + SQS
```

**Caching Strategy:**
```
Database results? → ElastiCache (Redis/Memcached)
DynamoDB reads? → DAX
Static assets? → CloudFront
API responses? → API Gateway caching
```

**Messaging Decision Tree:**
```
Simple queue? → SQS
Pub/Sub? → SNS
Streaming + real-time? → Kinesis or MSK
Event-driven workflows? → EventBridge
```

---

## Best Practices

1. **Start Simple**: Use managed services initially, optimize later
2. **Measure First**: Profile before choosing alternatives
3. **Consider Total Cost**: Include operational overhead, not just infrastructure
4. **Plan for Scale**: Choose technologies that grow with you
5. **Avoid Premature Optimization**: Don't over-engineer for scale you don't have
6. **Test Alternatives**: POC different options before committing
7. **Document Decisions**: Record why you chose specific technologies
8. **Review Regularly**: Re-evaluate as requirements and technologies change

---

## Common Patterns

### Pattern 1: Hybrid Approach
- Use AWS for production (reliability, SLA)
- Use Open Source for dev/testing (cost savings)

### Pattern 2: Best of Both Worlds
- Use AWS managed services for critical components
- Use self-managed open source for non-critical workloads

### Pattern 3: Progressive Migration
- Start with AWS managed services
- Gradually migrate to self-managed as team matures

### Pattern 4: Multi-Cloud Strategy
- Use AWS as primary cloud
- Use GCP for specialized services (BigQuery, BigTable)
- Use Azure for Microsoft ecosystem integration

---

## Conclusion

The choice between AWS services and alternatives depends on multiple factors:
- **Team expertise and size**
- **Budget constraints**
- **Time to market**
- **Scale requirements**
- **Operational maturity**
- **Compliance needs**

There's no one-size-fits-all solution. Evaluate each service based on your specific requirements, and don't hesitate to mix and match AWS services with open source alternatives to build the best system for your needs.
