# AWS Services for System Design - Interview Guide

## 🎯 Purpose
A focused guide on **popular AWS services** for system design interviews. Only includes commonly used services - no niche services like IoT, BI, Ground Station, etc.

---

## 📊 Table of Contents
1. [Compute Services](#compute-services)
2. [Storage Services](#storage-services)
3. [Database Services](#database-services)
4. [Caching Services](#caching-services)
5. [Networking & Content Delivery](#networking--content-delivery)
6. [Message Queues & Streaming](#message-queues--streaming)
7. [Application Integration](#application-integration)
8. [Security & Identity](#security--identity)
9. [Monitoring & Logging](#monitoring--logging)
10. [Developer Tools](#developer-tools)
11. [Common Architecture Patterns](#common-architecture-patterns)
12. [Service Selection Decision Trees](#service-selection-decision-trees)

---

## 🖥️ Compute Services

### EC2 (Elastic Compute Cloud)
**When to Use:**
- Need full control over OS and configuration
- Custom software that requires specific OS versions
- Long-running applications
- Batch processing jobs
- Lift-and-shift migrations

**When NOT to Use:**
- Simple stateless applications → Use Lambda or Fargate
- Auto-scaling web apps → Use Elastic Beanstalk or App Runner
- Short-lived tasks → Use Lambda

**Key Features:**
- Instance types: General purpose, Compute optimized, Memory optimized
- Auto Scaling Groups for elasticity
- Spot Instances for cost savings (up to 90% off)
- Reserved Instances for predictable workloads

**Example Use Cases:**
```
✅ Machine learning training with specific GPU requirements
✅ Legacy applications requiring specific OS configurations
✅ High-performance computing clusters
✅ Game servers requiring persistent state
```

---

### Lambda
**When to Use:**
- Event-driven processing
- Short-duration tasks (< 15 minutes)
- Unpredictable or sporadic traffic
- Microservices with simple logic
- Serverless backends

**When NOT to Use:**
- Long-running processes (> 15 min) → Use ECS/Fargate or EC2
- Requires persistent connections → Use EC2 or Fargate
- Heavy computational tasks → Use EC2 with proper instance types
- Need for specific OS customization → Use EC2

**Key Features:**
- Pay per invocation (100ms granularity)
- Auto-scales automatically
- 15-minute max execution time
- Supports multiple languages (Python, Node.js, Java, Go, etc.)

**Example Use Cases:**
```
✅ API backends (with API Gateway)
✅ Image/video processing on S3 upload
✅ Real-time file processing
✅ Scheduled tasks (cron jobs)
✅ Stream processing (with Kinesis)
```

---

### ECS (Elastic Container Service) / Fargate
**When to Use:**
- Containerized applications
- Microservices architecture
- Need orchestration without managing Kubernetes
- Want serverless containers (Fargate)

**When NOT to Use:**
- Simple functions → Use Lambda
- Need Kubernetes ecosystem → Use EKS
- Simple web apps → Use Elastic Beanstalk

**Key Features:**
- **ECS:** Container orchestration (like Docker Swarm)
- **Fargate:** Serverless compute for containers (no EC2 management)
- Task definitions for container configuration
- Service auto-scaling

**ECS vs Fargate:**
```
ECS on EC2:                      Fargate:
- Manage EC2 instances           - Serverless, no instances
- More control                   - Less operational overhead
- Cheaper at scale               - Pay per task (more expensive)
- Can use Spot/Reserved          - Simpler pricing
```

**Example Use Cases:**
```
✅ Microservices with complex dependencies
✅ Batch processing jobs
✅ Long-running applications in containers
✅ Migration from on-premises Docker deployments
```

---

### EKS (Elastic Kubernetes Service)
**When to Use:**
- Already using Kubernetes
- Need Kubernetes-specific features
- Complex microservices with service mesh
- Multi-cloud or hybrid deployments

**When NOT to Use:**
- Simple containerized apps → Use ECS/Fargate
- Serverless functions → Use Lambda
- Don't need Kubernetes complexity → Use ECS

**Key Features:**
- Managed Kubernetes control plane
- Compatible with standard Kubernetes tools
- Integration with AWS services
- Supports Fargate for serverless Kubernetes

**Example Use Cases:**
```
✅ Complex microservices requiring service mesh (Istio)
✅ Teams already experienced with Kubernetes
✅ Multi-cloud applications
✅ Need Kubernetes-native features (Operators, CRDs)
```

---

### Elastic Beanstalk
**When to Use:**
- Quick deployment of web applications
- Standard tech stacks (Node.js, Python, Java, .NET, etc.)
- Don't want to manage infrastructure
- Platform-as-a-Service experience

**When NOT to Use:**
- Need fine-grained control → Use EC2 or ECS
- Serverless architecture → Use Lambda + API Gateway
- Custom infrastructure requirements → Use EC2

**Example Use Cases:**
```
✅ Web applications (Django, Rails, Express.js)
✅ REST APIs
✅ Rapid prototyping
✅ Simple CRUD applications
```

---

## 💾 Storage Services

### S3 (Simple Storage Service)
**When to Use:**
- Object storage (images, videos, documents)
- Static website hosting
- Data lakes
- Backup and archive
- Big data analytics source

**When NOT to Use:**
- Frequently changing small files → Use EFS or EBS
- Database storage → Use RDS, DynamoDB, etc.
- File system for applications → Use EFS

**Key Features:**
- 99.999999999% (11 nines) durability
- Storage classes for cost optimization (Standard, IA, Glacier)
- Versioning and lifecycle policies
- Event notifications (to Lambda, SQS, SNS)
- Cross-region replication

**Storage Classes:**
```
S3 Standard:          Frequently accessed data
S3 Intelligent-Tier:  Auto-moves between tiers
S3 IA:               Infrequently accessed (cheaper)
S3 Glacier:          Archive (minutes to hours retrieval)
S3 Glacier Deep:     Long-term archive (12+ hours retrieval)
```

**Example Use Cases:**
```
✅ User-generated content (photos, videos)
✅ Static website hosting
✅ Data lake for analytics
✅ Backup and disaster recovery
✅ Log storage
✅ Media streaming source
```

---

### EBS (Elastic Block Store)
**When to Use:**
- Persistent block storage for EC2
- Database storage (on EC2)
- File systems requiring high IOPS
- Boot volumes for EC2

**When NOT to Use:**
- Object storage → Use S3
- Shared file storage → Use EFS
- Ephemeral storage → Use Instance Store

**Key Features:**
- Attached to single EC2 instance
- Snapshot backups to S3
- Multiple volume types (SSD, HDD)
- Encryption at rest

**Volume Types:**
```
gp3/gp2 (SSD):    General purpose, balanced
io2/io1 (SSD):    High IOPS, databases
st1 (HDD):        Throughput-optimized, big data
sc1 (HDD):        Cold storage, infrequent access
```

**Example Use Cases:**
```
✅ Database storage on EC2 (MySQL, PostgreSQL)
✅ Boot volumes
✅ Application data requiring persistence
✅ High-performance applications needing IOPS
```

---

### EFS (Elastic File System)
**When to Use:**
- Shared file storage across multiple EC2 instances
- NFS-compatible file system
- Content management systems
- Web serving

**When NOT to Use:**
- Object storage → Use S3
- Single-instance storage → Use EBS (cheaper)
- Windows file shares → Use FSx for Windows

**Key Features:**
- Scales automatically (no pre-provisioning)
- Accessible from multiple AZs
- NFS v4 protocol
- Two performance modes: General Purpose, Max I/O

**Example Use Cases:**
```
✅ WordPress (multiple web servers)
✅ Shared application data
✅ Content repositories
✅ Development environments
```

---

## 🗄️ Database Services

### RDS (Relational Database Service)
**When to Use:**
- Relational data with ACID requirements
- Standard SQL databases (MySQL, PostgreSQL, etc.)
- Transactional workloads
- Existing applications using SQL databases

**When NOT to Use:**
- NoSQL requirements → Use DynamoDB
- Massive scale (100+ TB) → Use Aurora
- Simple key-value → Use DynamoDB
- Graph data → Use Neptune

**Supported Engines:**
```
✅ MySQL
✅ PostgreSQL  
✅ MariaDB
✅ Oracle
✅ SQL Server
```

**Key Features:**
- Automated backups and snapshots
- Multi-AZ for high availability
- Read Replicas for scaling reads
- Automatic failover
- Point-in-time recovery

**Example Use Cases:**
```
✅ E-commerce transactions
✅ User accounts and profiles
✅ Financial records
✅ CRM systems
✅ Order management
```

---

### Aurora
**When to Use:**
- Need MySQL/PostgreSQL compatibility
- High performance required
- Large databases (> 64 TB)
- Global applications (Aurora Global Database)

**When NOT to Use:**
- Small workloads → Use RDS (cheaper)
- NoSQL → Use DynamoDB
- Non-MySQL/PostgreSQL → Use appropriate RDS engine

**Key Features:**
- 5x faster than MySQL, 3x faster than PostgreSQL
- Up to 15 read replicas
- Storage auto-scales to 128 TB
- Global Database (< 1 second cross-region replication)
- Serverless option (Aurora Serverless v2)

**Aurora vs RDS:**
```
Aurora:                          RDS:
- Higher performance             - Standard performance
- More expensive                 - Cheaper
- Auto-scaling storage           - Fixed storage
- Up to 15 read replicas         - Up to 5 read replicas
- Better for large scale         - Better for small/medium
```

**Example Use Cases:**
```
✅ SaaS applications with global users
✅ High-traffic e-commerce
✅ Gaming leaderboards
✅ Financial trading platforms
```

---

### DynamoDB
**When to Use:**
- NoSQL requirements
- Massive scale (unlimited)
- Serverless applications
- Single-digit millisecond latency
- Flexible schema
- High write throughput

**When NOT to Use:**
- Complex queries and joins → Use RDS
- ACID across multiple tables → Use RDS
- Need for SQL → Use RDS or Aurora
- Ad-hoc analytics → Use Redshift or Athena

**Key Features:**
- Fully managed NoSQL
- Auto-scaling capacity
- Single-digit ms latency
- Global Tables (multi-region)
- DynamoDB Streams (change data capture)
- Two pricing modes: On-Demand, Provisioned

**Example Use Cases:**
```
✅ Session storage
✅ Gaming user profiles and sessions
✅ Mobile app backends
✅ Shopping carts
✅ IoT data storage
✅ Real-time bidding
```

**DynamoDB Best Practices:**
```
✅ Use on-demand for unpredictable traffic
✅ Use provisioned for steady, predictable traffic
✅ Implement single-table design for related data
✅ Use DynamoDB Streams for event-driven architectures
✅ Use Global Tables for multi-region applications
```

---

### ElastiCache (Redis / Memcached)
**When to Use:**
- Caching database queries
- Session storage
- Real-time analytics
- Leaderboards (Redis sorted sets)
- Pub/Sub messaging (Redis)

**When NOT to Use:**
- Persistent primary database → Use RDS or DynamoDB
- Object storage → Use S3
- Complex queries → Use RDS

**Redis vs Memcached:**
```
Redis:                           Memcached:
- Rich data structures           - Simple key-value
- Persistence options            - No persistence
- Replication                    - No replication
- Pub/Sub messaging              - No pub/sub
- More features                  - Simpler, faster for basic use
- Single-threaded                - Multi-threaded
```

**Example Use Cases:**
```
Redis:
✅ Session store with persistence
✅ Leaderboards (sorted sets)
✅ Real-time analytics
✅ Rate limiting
✅ Pub/Sub messaging

Memcached:
✅ Simple database query caching
✅ Session store (without persistence)
✅ HTML fragment caching
```

---

## 🌐 Networking & Content Delivery

### CloudFront (CDN)
**When to Use:**
- Static content delivery (images, CSS, JS)
- Video streaming
- API acceleration
- DDoS protection
- Global user base

**When NOT to Use:**
- Dynamic content only → Use direct origin
- Single region users → May not need CDN

**Key Features:**
- 400+ edge locations worldwide
- Integration with S3, EC2, ALB
- SSL/TLS termination
- Lambda@Edge for edge computing
- Shield Standard (DDoS protection) included

**Example Use Cases:**
```
✅ Website assets (images, CSS, JS)
✅ Video streaming (HLS, DASH)
✅ Software distribution
✅ API caching
✅ Static website hosting with S3
```

---

### VPC (Virtual Private Cloud)
**When to Use:**
- Always! Default networking foundation
- Isolate resources
- Private subnets for databases
- Hybrid cloud with VPN/Direct Connect

**Key Components:**
```
Subnets:         Public (internet access) vs Private (no internet)
Route Tables:    Control traffic routing
Internet Gateway: Public subnet internet access
NAT Gateway:     Private subnet outbound internet
Security Groups: Instance-level firewall (stateful)
NACLs:          Subnet-level firewall (stateless)
```

**Example Architecture:**
```
Public Subnet:   ALB, Bastion hosts, NAT Gateway
Private Subnet:  Application servers, databases
Database Subnet: RDS, ElastiCache (isolated)
```

---

### ALB (Application Load Balancer)
**When to Use:**
- HTTP/HTTPS traffic
- Microservices routing
- Path-based routing (/api, /images)
- Host-based routing (api.example.com)
- WebSocket support

**When NOT to Use:**
- TCP/UDP traffic → Use NLB
- Extreme performance (millions RPS) → Use NLB
- Static IP required → Use NLB

**Key Features:**
- Layer 7 (HTTP/HTTPS)
- Path and host-based routing
- SSL/TLS termination
- Sticky sessions
- WebSocket support
- Health checks

**Example Use Cases:**
```
✅ Web applications
✅ Microservices with different routes
✅ Container-based applications
✅ API Gateway alternative
```

---

### NLB (Network Load Balancer)
**When to Use:**
- Extreme performance (millions RPS)
- TCP/UDP traffic
- Static IP required
- Preserve source IP
- Low latency required

**When NOT to Use:**
- HTTP routing features needed → Use ALB
- Cost-sensitive (ALB cheaper) → Use ALB

**Key Features:**
- Layer 4 (TCP/UDP)
- Ultra-low latency
- Static IP addresses
- Preserves client IP
- Handles millions of requests/second

**Example Use Cases:**
```
✅ Gaming servers
✅ IoT applications
✅ Financial trading platforms
✅ VoIP applications
✅ When static IP is required
```

---

### Route 53
**When to Use:**
- Domain name registration
- DNS hosting
- Traffic routing (latency, geolocation, weighted)
- Health checks and failover

**Key Features:**
- Highly available DNS (100% uptime SLA)
- Multiple routing policies
- Health checks
- Integration with other AWS services

**Routing Policies:**
```
Simple:         Single resource
Weighted:       Traffic distribution (A/B testing)
Latency:        Route to lowest latency region
Geolocation:    Route based on user location
Failover:       Active-passive setup
Multi-value:    Return multiple values
```

**Example Use Cases:**
```
✅ Blue-green deployments (weighted routing)
✅ Disaster recovery (failover routing)
✅ Global applications (latency routing)
✅ Regional compliance (geolocation)
```

---

## 📨 Message Queues & Streaming

### SQS (Simple Queue Service)
**When to Use:**
- Decouple microservices
- Async processing
- Buffer between components
- Job queues
- Handle traffic spikes

**When NOT to Use:**
- Need message ordering → Use SQS FIFO or Kinesis
- Real-time streaming → Use Kinesis
- Pub/sub pattern → Use SNS
- Message replay required → Use Kinesis

**Queue Types:**
```
Standard Queue:
- At-least-once delivery
- Best-effort ordering
- Unlimited throughput
- Cheaper

FIFO Queue:
- Exactly-once processing
- Strict ordering
- 300 TPS limit (3000 with batching)
- More expensive
```

**Key Features:**
- Unlimited messages
- Retention: 1 minute to 14 days
- Dead Letter Queues (DLQ)
- Visibility timeout
- Long polling

**Example Use Cases:**
```
✅ Background job processing
✅ Email sending queue
✅ Image processing pipeline
✅ Order processing
✅ Microservices decoupling
```

---

### SNS (Simple Notification Service)
**When to Use:**
- Pub/sub messaging
- Fan-out pattern
- Push notifications (mobile, email, SMS)
- Application-to-application messaging

**When NOT to Use:**
- Need queuing → Use SQS
- Message persistence → Use SQS or Kinesis
- Streaming data → Use Kinesis

**Key Features:**
- Pub/sub model (one-to-many)
- Multiple subscribers per topic
- Push-based delivery
- Message filtering
- FIFO topics available

**SNS + SQS Fan-out Pattern:**
```
Producer → SNS Topic → [SQS Queue 1, SQS Queue 2, SQS Queue 3]
                       [Lambda, Email, HTTP endpoint]
```

**Example Use Cases:**
```
✅ Sending notifications (email, SMS, push)
✅ Fan-out to multiple SQS queues
✅ Application alerts
✅ Triggering multiple Lambda functions
✅ Mobile push notifications
```

---

### Kinesis Data Streams
**When to Use:**
- Real-time data streaming
- Log aggregation
- Clickstream data
- Need to replay messages
- Multiple consumers processing same data

**When NOT to Use:**
- Simple queuing → Use SQS
- Low throughput → Use SQS (cheaper)
- Don't need replay → Use SQS

**Key Features:**
- Real-time processing (< 200ms)
- Data retention: 24 hours to 365 days
- Multiple consumers can read same stream
- Message replay capability
- Ordered within shard

**Kinesis vs SQS:**
```
Kinesis:                         SQS:
- Real-time streaming            - Queuing
- Multiple consumers             - Single consumer per message
- Message replay                 - No replay (deleted after read)
- Ordered per shard              - No guaranteed order (standard)
- Pay per shard-hour             - Pay per request
- Complex setup                  - Simple setup
```

**Example Use Cases:**
```
✅ Real-time analytics dashboards
✅ Log aggregation from multiple sources
✅ Clickstream analysis
✅ IoT data ingestion
✅ Video stream processing
```

---

## 🔗 Application Integration

### API Gateway
**When to Use:**
- REST APIs
- WebSocket APIs
- API management (throttling, auth, caching)
- Lambda backends
- Microservices API facade

**When NOT to Use:**
- Simple HTTP routing → Use ALB
- Internal microservices → Use App Mesh or service discovery

**Key Features:**
- Request/response transformation
- API throttling and quotas
- API keys and usage plans
- Caching
- Request validation
- CORS support

**API Types:**
```
REST API:        Full-featured, most common
HTTP API:        Simpler, cheaper, lower latency
WebSocket API:   Real-time bidirectional communication
```

**Example Use Cases:**
```
✅ Serverless REST APIs (with Lambda)
✅ Mobile app backends
✅ Third-party API access
✅ Microservices API gateway
✅ WebSocket applications (chat, gaming)
```

---

### Step Functions
**When to Use:**
- Orchestrate multiple Lambda functions
- Long-running workflows
- Complex business logic with branches
- Need visual workflow

**When NOT to Use:**
- Simple single-step process → Use Lambda directly
- High-frequency invocations (>2000/sec) → Use SQS + Lambda

**Key Features:**
- Visual workflow designer
- Error handling and retry logic
- State persistence
- Two types: Standard (long-running) and Express (high-volume, short)

**Workflow Types:**
```
Standard Workflow:
- Up to 1 year duration
- Exactly-once execution
- Full execution history
- More expensive

Express Workflow:
- Up to 5 minutes duration
- At-least-once execution
- CloudWatch Logs only
- Cheaper, higher throughput
```

**Example Use Cases:**
```
✅ Order processing workflow
✅ ETL pipelines
✅ Video processing pipeline
✅ Approval workflows
✅ Data validation and transformation
```

---

### EventBridge
**When to Use:**
- Event-driven architectures
- SaaS integration
- Scheduled events (cron jobs)
- Cross-account event routing

**When NOT to Use:**
- Simple pub/sub → Use SNS
- High-throughput streaming → Use Kinesis

**Key Features:**
- Schema registry
- Event filtering
- Transform events
- Multiple targets per rule
- Integration with 90+ AWS services and SaaS

**EventBridge vs SNS:**
```
EventBridge:                     SNS:
- Event bus model                - Topic model
- Advanced filtering             - Basic filtering
- Schema registry                - No schema
- SaaS integrations              - No SaaS integrations
- Scheduled events               - No scheduling
```

**Example Use Cases:**
```
✅ Microservices event routing
✅ SaaS integration (Zendesk, Shopify, etc.)
✅ Scheduled tasks (cron)
✅ Multi-account event routing
✅ Event replay for testing
```

---

## 🔐 Security & Identity

### IAM (Identity and Access Management)
**When to Use:**
- Always! Foundation for AWS security
- User and role management
- Service-to-service authentication
- Fine-grained access control

**Key Concepts:**
```
Users:       Individuals/applications (long-term credentials)
Groups:      Collection of users
Roles:       Temporary credentials (EC2, Lambda, cross-account)
Policies:    JSON documents defining permissions
```

**Best Practices:**
```
✅ Use roles for EC2/Lambda, not access keys
✅ Follow principle of least privilege
✅ Enable MFA for users
✅ Use IAM roles for cross-account access
✅ Rotate credentials regularly
✅ Use policy conditions for fine-grained control
```

**Example Use Cases:**
```
✅ EC2 instance accessing S3
✅ Lambda function accessing DynamoDB
✅ Cross-account resource access
✅ User access to AWS Console
✅ CI/CD pipeline accessing AWS
```

---

### Cognito
**When to Use:**
- User authentication for web/mobile apps
- Social identity providers (Google, Facebook)
- User pools and identity pools
- OAuth 2.0 / OpenID Connect

**When NOT to Use:**
- Internal employee authentication → Use IAM or SSO
- Simple API keys → Use API Gateway API keys
- Machine-to-machine auth → Use IAM roles

**Components:**
```
User Pools:      User directory, authentication
Identity Pools:  AWS credentials for users
```

**Example Use Cases:**
```
✅ Mobile app user login
✅ Web application authentication
✅ Social login (Google, Facebook, Amazon)
✅ Multi-factor authentication
✅ User profile storage
```

---

### Secrets Manager
**When to Use:**
- Database credentials
- API keys
- Encryption keys
- Automatic rotation required

**When NOT to Use:**
- Configuration values → Use Systems Manager Parameter Store
- Application code → Store in Git
- Public information → No need for secrets

**Secrets Manager vs Parameter Store:**
```
Secrets Manager:                 Systems Manager Parameter Store:
- Automatic rotation             - No rotation (manual only)
- More expensive                 - Free tier available
- Purpose-built for secrets      - For config + secrets
- Better audit logging           - Basic logging
```

**Example Use Cases:**
```
✅ RDS database passwords
✅ API keys for third-party services
✅ OAuth tokens
✅ Encryption keys
```

---

## 📊 Monitoring & Logging

### CloudWatch
**When to Use:**
- Always! Essential for monitoring
- Metrics collection
- Log aggregation
- Alarms and notifications
- Dashboards

**Key Components:**
```
Metrics:    Performance data (CPU, memory, custom)
Logs:       Application and system logs
Alarms:     Notifications based on thresholds
Dashboards: Visual monitoring
Events:     Scheduled tasks (use EventBridge now)
```

**Key Features:**
- Built-in metrics for AWS services
- Custom metrics
- Log insights for querying
- Metric alarms for notifications
- Automatic dashboards

**Example Use Cases:**
```
✅ EC2 CPU/memory monitoring
✅ Application log aggregation
✅ API Gateway latency tracking
✅ Lambda error rates
✅ DynamoDB throttling detection
✅ Auto-scaling triggers
```

---

### X-Ray
**When to Use:**
- Distributed tracing
- Microservices debugging
- Performance bottleneck identification
- Service map visualization

**When NOT to Use:**
- Simple single-service apps → CloudWatch logs enough
- Cost-sensitive for high traffic → X-Ray can be expensive

**Key Features:**
- Service map
- Trace analysis
- Request tracking across services
- Annotations and metadata
- Integration with Lambda, ECS, API Gateway

**Example Use Cases:**
```
✅ Debugging latency in microservices
✅ Tracing user requests across Lambda functions
✅ Finding performance bottlenecks
✅ Error rate analysis by service
```

---

## 🛠️ Developer Tools

### CodePipeline
**When to Use:**
- CI/CD pipelines
- Automated deployments
- Multi-stage deployments (dev → staging → prod)

**Key Features:**
- Source: GitHub, CodeCommit, S3
- Build: CodeBuild, Jenkins
- Deploy: CodeDeploy, ECS, Lambda, CloudFormation
- Manual approval gates

**Example Use Cases:**
```
✅ Web application CI/CD
✅ Lambda function deployments
✅ Infrastructure as Code (CloudFormation)
✅ Container deployments to ECS
```

---

### CodeBuild
**When to Use:**
- Compile source code
- Run tests
- Build Docker images
- Package artifacts

**Key Features:**
- Pay per minute
- Pre-configured environments
- Custom Docker images
- Integration with CodePipeline

**Example Use Cases:**
```
✅ Build Java/Node.js applications
✅ Run unit tests
✅ Build and push Docker images to ECR
✅ Generate deployment artifacts
```

---

### CodeDeploy
**When to Use:**
- Automated application deployments
- Blue/green deployments
- Canary deployments
- Rollback capabilities

**Deployment Targets:**
```
✅ EC2 instances
✅ On-premises servers
✅ Lambda functions
✅ ECS services
```

---

## 🏗️ Common Architecture Patterns

### 1. Three-Tier Web Application
```
User
  ↓
CloudFront (CDN)
  ↓
ALB (Load Balancer)
  ↓
EC2 / ECS / Lambda (Application Layer)
  ↓
RDS / DynamoDB (Database Layer)
  ↓
S3 (Static Assets)
```

**Services Used:**
- **Web Tier:** CloudFront + ALB + EC2/ECS
- **App Tier:** Application servers
- **Data Tier:** RDS/Aurora/DynamoDB
- **Storage:** S3 for static files

---

### 2. Serverless Web Application
```
User
  ↓
CloudFront
  ↓
S3 (Static Website)
  ↓
API Gateway
  ↓
Lambda
  ↓
DynamoDB / RDS
```

**Services Used:**
- **Frontend:** S3 + CloudFront
- **API:** API Gateway + Lambda
- **Database:** DynamoDB
- **Auth:** Cognito

---

### 3. Event-Driven Architecture
```
Event Source (S3, DynamoDB, etc.)
  ↓
EventBridge / SNS
  ↓
[Lambda 1, Lambda 2, SQS → Lambda 3]
  ↓
Various Destinations
```

**Services Used:**
- **Event Bus:** EventBridge or SNS
- **Processing:** Lambda functions
- **Queuing:** SQS for buffering
- **Storage:** S3, DynamoDB

---

### 4. Microservices Architecture
```
User
  ↓
API Gateway / ALB
  ↓
[Service 1 (ECS), Service 2 (ECS), Service 3 (Lambda)]
  ↓
[RDS, DynamoDB, ElastiCache]
  ↓
SQS / SNS (Inter-service communication)
```

**Services Used:**
- **API Layer:** API Gateway or ALB
- **Compute:** ECS/EKS/Lambda
- **Databases:** RDS, DynamoDB (polyglot persistence)
- **Messaging:** SQS, SNS, EventBridge
- **Caching:** ElastiCache

---

### 5. Data Processing Pipeline
```
Data Source
  ↓
Kinesis Data Streams
  ↓
Lambda / Kinesis Data Analytics
  ↓
S3 / DynamoDB / Redshift
  ↓
Athena / QuickSight (Analytics)
```

**Services Used:**
- **Ingestion:** Kinesis Data Streams
- **Processing:** Lambda or Kinesis Analytics
- **Storage:** S3 (data lake)
- **Analytics:** Athena

---

## 🎯 Service Selection Decision Trees

### Compute: Which Service?
```
Need serverless?
├─ Yes → Event-driven? 
│         ├─ Yes → Lambda
│         └─ No → Fargate
└─ No → Containers?
          ├─ Yes → Need Kubernetes?
          │         ├─ Yes → EKS
          │         └─ No → ECS on EC2
          └─ No → Need full control?
                    ├─ Yes → EC2
                    └─ No → Elastic Beanstalk
```

### Database: Which Service?
```
What type of data?
├─ Relational (SQL) →
│   ├─ MySQL/PostgreSQL?
│   │   ├─ Small scale → RDS
│   │   └─ Large scale/global → Aurora
│   └─ Other (Oracle, SQL Server) → RDS
│
├─ NoSQL →
│   ├─ Document/Key-Value → DynamoDB
│   ├─ In-memory cache → ElastiCache
│   └─ Graph → Neptune (not covered here)
│
└─ Time-series → Timestream (not covered here)
```

### Storage: Which Service?
```
What type of storage?
├─ Object storage (files, media) → S3
├─ Block storage (for EC2) → EBS
├─ Shared file system → EFS
└─ Backup/Archive → S3 Glacier
```

### Messaging: Which Service?
```
What's your pattern?
├─ Simple queue (FIFO or Standard) → SQS
├─ Pub/Sub (fan-out) → SNS
├─ Real-time streaming → Kinesis
└─ Event-driven → EventBridge
```

### Load Balancer: Which Service?
```
What layer?
├─ Layer 7 (HTTP/HTTPS) →
│   ├─ Need advanced routing → ALB
│   └─ Simple routing → ALB
│
└─ Layer 4 (TCP/UDP) →
    ├─ Need static IP → NLB
    ├─ Extreme performance → NLB
    └─ Preserve source IP → NLB
```

---

## 📝 Quick Comparison Tables

### Compute Services Comparison
| Service | Type | Management | Cost | Use When |
|---------|------|------------|------|----------|
| **Lambda** | Serverless | Fully Managed | Pay per invocation | Event-driven, short tasks |
| **Fargate** | Serverless Containers | Fully Managed | Pay per task | Containerized, no server mgmt |
| **ECS** | Container Orchestration | Managed service | EC2 pricing + minimal | Container orchestration |
| **EKS** | Kubernetes | Managed control plane | EC2 + EKS cost | Need Kubernetes |
| **EC2** | Virtual Machines | You manage | Hourly/monthly | Full control needed |
| **Elastic Beanstalk** | PaaS | Managed platform | EC2 pricing | Quick deployments |

---

### Database Services Comparison
| Service | Type | Scale | Latency | Use When |
|---------|------|-------|---------|----------|
| **RDS** | SQL | TB scale | ~10ms | Traditional RDBMS |
| **Aurora** | SQL | 128 TB | ~5ms | High performance SQL |
| **DynamoDB** | NoSQL | Unlimited | <10ms | Massive scale, flexible schema |
| **ElastiCache Redis** | In-Memory | GB to TB | <1ms | Caching, real-time |
| **ElastiCache Memcached** | In-Memory | GB to TB | <1ms | Simple caching |

---

### Storage Services Comparison
| Service | Type | Access Pattern | Cost | Use When |
|---------|------|----------------|------|----------|
| **S3 Standard** | Object | Any | $$$ | Frequent access |
| **S3 IA** | Object | Infrequent | $$ | Monthly access |
| **S3 Glacier** | Object | Archive | $ | Yearly access |
| **EBS** | Block | Single EC2 | $$$ | EC2 persistent storage |
| **EFS** | File | Multi-EC2 | $$$$ | Shared file system |

---

### Messaging Services Comparison
| Service | Pattern | Order | Retention | Use When |
|---------|---------|-------|-----------|----------|
| **SQS Standard** | Queue | Best-effort | 14 days | Simple async processing |
| **SQS FIFO** | Queue | Strict | 14 days | Ordered processing |
| **SNS** | Pub/Sub | N/A | No retention | Fan-out notifications |
| **Kinesis** | Streaming | Per shard | 365 days | Real-time streaming |
| **EventBridge** | Event Bus | N/A | No retention | Event-driven architecture |

---

## 🎓 Interview Preparation Tips

### Common Questions & AWS Services to Mention

**Q: "Design a URL shortener"**
```
✅ API Gateway + Lambda for API
✅ DynamoDB for URL mappings
✅ CloudFront for global access
✅ Route 53 for custom domain
✅ ElastiCache for hot URLs
```

**Q: "Design Instagram/Photo sharing app"**
```
✅ S3 for photo storage
✅ CloudFront for image delivery
✅ DynamoDB for metadata
✅ Lambda for image processing
✅ ElastiCache for feed caching
✅ SQS for async operations
```

**Q: "Design a notification system"**
```
✅ SNS for push notifications
✅ SQS for reliable delivery
✅ Lambda for processing
✅ DynamoDB for user preferences
✅ EventBridge for event routing
```

**Q: "Design Twitter/Social media feed"**
```
✅ DynamoDB for tweets/posts
✅ ElastiCache for timeline caching
✅ S3 for media
✅ CloudFront for media delivery
✅ Kinesis for real-time streams
✅ Lambda for feed generation
```

**Q: "Design Uber/Ride sharing"**
```
✅ DynamoDB for ride data
✅ ElastiCache for driver locations
✅ API Gateway + Lambda for API
✅ Kinesis for real-time tracking
✅ SNS for notifications
✅ S3 for trip history
```

**Q: "Design Netflix/Video streaming"**
```
✅ S3 for video storage
✅ CloudFront for video delivery
✅ ElastiCache for metadata
✅ DynamoDB for user data
✅ Lambda for encoding (with Step Functions)
✅ Kinesis for analytics
```

---

## 💡 Key Principles for AWS Service Selection

### 1. **Start Serverless When Possible**
```
✅ Lambda for compute
✅ DynamoDB for database
✅ API Gateway for APIs
✅ S3 for storage

Benefits:
- No server management
- Auto-scaling built-in
- Pay only for what you use
- Faster time to market
```

### 2. **Add Caching Layers**
```
✅ CloudFront for static content
✅ ElastiCache for database queries
✅ API Gateway caching for APIs
✅ DynamoDB DAX for DynamoDB

Benefits:
- Reduced latency
- Lower costs (fewer DB calls)
- Better scalability
- Improved user experience
```

### 3. **Design for High Availability**
```
✅ Multi-AZ deployments (RDS, ElastiCache)
✅ Auto Scaling Groups for EC2
✅ ALB/NLB with health checks
✅ Route 53 failover routing
✅ S3 cross-region replication

Benefits:
- 99.99% uptime
- Automatic failover
- Disaster recovery
- Regional redundancy
```

### 4. **Decouple Components**
```
✅ SQS between services
✅ SNS for fan-out
✅ EventBridge for events
✅ Lambda for async processing

Benefits:
- Independent scaling
- Better fault isolation
- Easier to maintain
- More resilient
```

### 5. **Use Managed Services**
```
✅ RDS instead of EC2 + MySQL
✅ DynamoDB instead of Cassandra on EC2
✅ ElastiCache instead of Redis on EC2
✅ ECS/EKS instead of self-managed containers

Benefits:
- Less operational overhead
- Built-in backups
- Automatic updates
- Better security
- Focus on business logic
```

---

## 🚀 Cost Optimization Tips

### 1. **Right-size Resources**
```
✅ Start small, scale up based on metrics
✅ Use Auto Scaling
✅ Review CloudWatch metrics
✅ Use AWS Cost Explorer
```

### 2. **Use Appropriate Storage Classes**
```
S3 Standard → Frequent access
S3 IA → Monthly access  
S3 Glacier → Archive
Use S3 Intelligent-Tiering for automatic optimization
```

### 3. **Leverage Reserved Capacity**
```
✅ RDS Reserved Instances (up to 72% savings)
✅ EC2 Reserved Instances (up to 72% savings)
✅ DynamoDB Reserved Capacity (up to 76% savings)
✅ ElastiCache Reserved Nodes (up to 76% savings)
```

### 4. **Use Spot Instances**
```
✅ Batch processing jobs
✅ Big data workloads
✅ CI/CD build servers
✅ Stateless web servers (with ASG)

Savings: Up to 90% off On-Demand price
```

### 5. **Implement Lifecycle Policies**
```
✅ S3 lifecycle to move to cheaper storage
✅ Delete old CloudWatch logs
✅ Clean up unused EBS snapshots
✅ Remove unused Elastic IPs
```

---

## 📚 Additional Resources

### AWS Documentation
- **Compute:** Lambda, EC2, ECS, EKS, Fargate
- **Storage:** S3, EBS, EFS
- **Database:** RDS, Aurora, DynamoDB, ElastiCache
- **Networking:** VPC, CloudFront, Route 53, ALB, NLB
- **Integration:** SQS, SNS, Kinesis, EventBridge, Step Functions

### Architecture Patterns
- **Serverless:** Lambda + API Gateway + DynamoDB
- **Microservices:** ECS/EKS + ALB + RDS/DynamoDB
- **Event-Driven:** EventBridge + Lambda + SQS
- **Data Processing:** Kinesis + Lambda + S3
- **Web Apps:** CloudFront + ALB + EC2 + RDS

### Best Practices
- **Security:** IAM roles, encryption at rest/transit, VPC isolation
- **Reliability:** Multi-AZ, Auto Scaling, health checks, backups
- **Performance:** Caching, CDN, read replicas, optimal instance types
- **Cost:** Right-sizing, reserved capacity, Spot instances, auto-scaling
- **Operations:** CloudWatch monitoring, X-Ray tracing, automated deployments

---

## 🎯 Final Interview Tips

### 1. **Always Mention Trade-offs**
```
"I'd use DynamoDB for the key-value store because:
✅ It scales automatically (vs RDS manual scaling)
✅ Single-digit ms latency (vs RDS ~10ms)
✅ Fully managed (vs EC2 + Cassandra)
❌ But it's more expensive at low scale
❌ And limited query capabilities vs SQL"
```

### 2. **Justify Your Choices**
```
Bad:  "I'd use Lambda"
Good: "I'd use Lambda because the workload is event-driven 
       and sporadic, so serverless makes sense for cost 
       and automatic scaling"
```

### 3. **Start Simple, Then Scale**
```
"For MVP, I'd use:
- API Gateway + Lambda + DynamoDB

As we scale, I'd add:
- CloudFront for caching
- ElastiCache for hot data
- SQS for async processing
- Multiple regions with Route 53"
```

### 4. **Show AWS Knowledge**
```
✅ Mention specific services by name
✅ Know service limits (Lambda 15min, etc.)
✅ Understand pricing models
✅ Discuss Multi-AZ, regions, edge locations
✅ Reference real AWS features (DAX, Global Tables, etc.)
```

### 5. **Common Mistakes to Avoid**
```
❌ Over-engineering (using EKS for simple CRUD app)
❌ Under-engineering (single EC2 for production)
❌ Ignoring costs
❌ Not mentioning monitoring/logging
❌ Forgetting security (IAM, encryption)
❌ Single point of failure
❌ Not discussing trade-offs
```

---

## 📊 Service Popularity (What to Focus On)

### Must Know (90% of interviews)
```
⭐⭐⭐ EC2, Lambda, S3, RDS, DynamoDB
⭐⭐⭐ VPC, ALB, CloudFront, Route 53
⭐⭐⭐ SQS, SNS, API Gateway
⭐⭐⭐ IAM, CloudWatch
```

### Should Know (Frequent)
```
⭐⭐ ECS/Fargate, ElastiCache
⭐⭐ Kinesis, Step Functions, EventBridge
⭐⭐ Aurora, EBS, EFS
⭐⭐ NLB, Cognito
⭐⭐ CodePipeline, X-Ray
```

### Nice to Know (Occasional)
```
⭐ EKS, Elastic Beanstalk
⭐ CodeBuild, CodeDeploy
⭐ Secrets Manager
```

---

## 🎓 Study Plan

### Week 1: Core Services
- **Day 1-2:** EC2, Lambda, S3
- **Day 3-4:** RDS, DynamoDB, ElastiCache
- **Day 5-6:** VPC, ALB/NLB, Route 53, CloudFront
- **Day 7:** Review + practice problems

### Week 2: Integration & Advanced
- **Day 1-2:** SQS, SNS, Kinesis
- **Day 3-4:** API Gateway, Step Functions, EventBridge
- **Day 5-6:** IAM, Cognito, Secrets Manager
- **Day 7:** CloudWatch, X-Ray, review

### Week 3: Architecture Patterns
- **Day 1-2:** Serverless architectures
- **Day 3-4:** Microservices architectures
- **Day 5-6:** Event-driven architectures
- **Day 7:** Practice system design problems

### Week 4: Practice
- **Day 1-7:** Mock interviews, system design problems, review weak areas

---

## ✅ Checklist Before Interview

```
□ Know when to use each compute service (EC2, Lambda, ECS, EKS)
□ Understand database options (RDS, Aurora, DynamoDB)
□ Can explain caching strategies (CloudFront, ElastiCache)
□ Know messaging patterns (SQS, SNS, Kinesis)
□ Understand load balancing (ALB vs NLB)
□ Can design for high availability (Multi-AZ, Auto Scaling)
□ Know security basics (IAM, VPC, encryption)
□ Can discuss cost optimization
□ Understand monitoring (CloudWatch, X-Ray)
□ Know common architecture patterns
□ Can explain trade-offs between services
□ Practiced 10+ system design problems with AWS
```

---

**Good luck with your interviews! 🚀**

---

## 🏗️ Detailed Real-World Architecture Examples

### Example 1: URL Shortener (Like bit.ly)

#### Requirements
- Create short URLs from long URLs
- Redirect users when accessing short URL
- 100M URLs created per month
- 10:1 read/write ratio
- Low latency for redirects (<100ms)
- Custom short codes optional
- Analytics (click tracking)

#### Capacity Estimation
```
Write: 100M URLs/month = ~40 URLs/sec
Read: 400 redirects/sec (10:1 ratio)
Storage: 100M URLs × 500 bytes = 50 GB/month
For 5 years: 50GB × 12 × 5 = 3TB
```

#### Step-by-Step Architecture

**Step 1: API Layer**
```
Service: API Gateway + Lambda
Why: Serverless, auto-scales, pay per request
Endpoints:
- POST /shorten → Create short URL
- GET /{shortCode} → Redirect to original URL
- GET /analytics/{shortCode} → Get click stats
```

**Step 2: Short Code Generation**
```
Lambda Function Logic:
1. Receive long URL
2. Generate short code (base62 encoding of counter or hash)
3. Check if code exists in DynamoDB
4. If collision, regenerate
5. Store mapping in DynamoDB
6. Return short URL

Alternative: Use DynamoDB auto-increment counter
- Guaranteed unique
- Convert to base62 (0-9, a-z, A-Z) = 62^7 = 3.5 trillion URLs
```

**Step 3: Database Design**
```
Service: DynamoDB

Table: URLs
- PK: shortCode (String) - for O(1) lookups
- longURL (String)
- createdAt (Number) - timestamp
- expiresAt (Number) - TTL for cleanup
- userId (String) - for custom URLs
- clickCount (Number) - for analytics

GSI: longURL-index (for checking if URL already shortened)

Why DynamoDB:
✅ Single-digit ms latency
✅ Scales automatically
✅ TTL for automatic cleanup
✅ Atomic counters for clicks
```

**Step 4: Caching Layer**
```
Service: ElastiCache (Redis)

Cache hot URLs (most accessed):
Key: shortCode
Value: longURL
TTL: 24 hours

Why Cache:
✅ Reduces DynamoDB reads by 80%
✅ Sub-millisecond latency
✅ Sorted sets for trending URLs

Cache Strategy:
1. Check Redis first
2. If miss, read from DynamoDB
3. Write to Redis with TTL
4. Async update click counter in DynamoDB
```

**Step 5: CDN & Static Content**
```
Service: CloudFront + S3

Host static landing page on S3
Deliver via CloudFront for global access
Benefits:
✅ Low latency worldwide
✅ Reduce origin load
✅ HTTPS termination
```

**Step 6: Analytics Pipeline**
```
Flow:
1. Each redirect triggers event
2. API Gateway → Kinesis Data Stream
3. Lambda processes events
4. Aggregate data in DynamoDB
5. S3 for long-term storage

Real-time analytics:
- Redis sorted sets for trending URLs
- DynamoDB for detailed analytics
```

#### Complete Data Flow

**Create Short URL:**
```
User → CloudFront → API Gateway → Lambda
       ↓
    DynamoDB (check if exists)
       ↓
    Generate short code
       ↓
    DynamoDB (save mapping)
       ↓
    Return short URL to user
```

**Access Short URL:**
```
User requests https://short.ly/abc123
       ↓
CloudFront (geographic edge)
       ↓
API Gateway → Lambda
       ↓
Check Redis Cache
  ├─ Hit → Return longURL (redirect)
  └─ Miss → DynamoDB → Update Redis → Redirect
       ↓
Async: Increment click counter
       ↓
Kinesis → Lambda → Analytics
```

#### Scaling Considerations

**For 1M requests/sec:**
```
1. Add read replicas (DynamoDB auto-scales)
2. Increase Redis cluster size
3. Add CloudFront → reduces origin load
4. Use Lambda reserved concurrency
5. DynamoDB DAX for caching layer
```

#### Cost Optimization
```
1. Use DynamoDB on-demand for unpredictable traffic
2. Redis: Reserved nodes for steady traffic
3. CloudFront: Reduces API Gateway calls (saves $)
4. S3 lifecycle: Move old analytics to Glacier
5. Lambda: Optimize memory (faster = cheaper per request)

Estimated monthly cost (100M URLs):
- DynamoDB: $25 (on-demand)
- Lambda: $20 (1M executions free tier)
- API Gateway: $35 (1M requests = $3.50)
- ElastiCache: $50 (t3.micro)
- CloudFront: $10
Total: ~$140/month
```

#### Monitoring
```
CloudWatch Alarms:
✅ API Gateway 5xx errors > 1%
✅ Lambda errors > 0.1%
✅ DynamoDB throttling > 0
✅ Redis CPU > 75%
✅ Cache hit rate < 80%

X-Ray:
✅ Trace slow requests
✅ Identify bottlenecks
```

---

### Example 2: Twitter/Social Media Feed

#### Requirements
- Post tweets (280 chars)
- Follow/unfollow users
- View home timeline (tweets from followed users)
- 300M users, 50M daily active
- 100M tweets/day
- Timeline should load <500ms
- Real-time updates

#### Capacity Estimation
```
Tweets: 100M/day = 1,157 tweets/sec
Timeline reads: 50M users × 10 reads/day = 500M reads/day = 5,787 reads/sec
Storage: 100M tweets × 500 bytes = 50GB/day
1 year: 18TB
```

#### Architecture Components

**Step 1: API Layer**
```
Service: ALB + ECS (Fargate)

Why not Lambda:
- Complex business logic
- WebSocket connections for real-time
- Persistent connections needed

Container: Node.js/Go API server
Auto-scaling: Based on CPU/memory
```

**Step 2: Tweet Storage**
```
Service: DynamoDB

Table: Tweets
PK: tweetId (UUID)
SK: timestamp
Attributes: userId, content, mediaUrls, likeCount, retweetCount

GSI: userId-timestamp-index
- For user's timeline

Why DynamoDB:
✅ Unlimited scale
✅ Fast writes (1,157/sec is easy)
✅ DynamoDB Streams for fanout
```

**Step 3: Timeline Generation (Fanout on Write)**
```
Strategy: Pre-compute timelines

Flow:
1. User posts tweet
2. DynamoDB Streams triggers Lambda
3. Lambda reads user's followers from cache
4. Writes tweet to each follower's timeline cache

Service: ElastiCache (Redis)

Timeline Structure:
Key: timeline:{userId}
Type: Sorted Set (score = timestamp)
Value: tweetId
Limit: 1000 most recent

Benefits:
✅ Timeline reads are O(1)
✅ No join queries needed
✅ Super fast (<10ms)
```

**Step 4: Follow Graph**
```
Service: DynamoDB + ElastiCache

Table: Follows
PK: followerId
SK: followeeId
Timestamp: when followed

Cache follow lists in Redis:
Key: followers:{userId}
Type: Set
Members: userIds

Key: following:{userId}
Type: Set
Members: userIds
```

**Step 5: Media Storage**
```
Service: S3 + CloudFront

Tweet with images/videos:
1. Client uploads to S3 (presigned URL)
2. Lambda triggers on upload
3. Creates thumbnails (Lambda + FFmpeg layer)
4. Stores in S3
5. Delivers via CloudFront

Why:
✅ S3: Infinite storage, 11 9's durability
✅ CloudFront: Fast global delivery
✅ Lazy loading: Only load images when visible
```

**Step 6: Real-time Updates**
```
Service: API Gateway (WebSocket) + Lambda

WebSocket connections for:
- New tweets from followed users
- Likes, retweets notifications
- Direct messages

Flow:
1. Client connects via WebSocket
2. Connection stored in DynamoDB
3. New tweet → Lambda → Push via WebSocket
```

#### Complete Data Flow

**Post Tweet:**
```
User → ALB → ECS API
       ↓
Save to DynamoDB (Tweets table)
       ↓
DynamoDB Stream → Lambda (fanout worker)
       ↓
Read followers from Redis
       ↓
For each follower:
  - Add tweetId to their timeline (Redis sorted set)
  - If online → Push via WebSocket
       ↓
Update counts in DynamoDB
```

**View Timeline:**
```
User → ALB → ECS API
       ↓
Read from Redis: timeline:{userId}
       ↓
Get top 20 tweetIds
       ↓
Batch get tweet details from DynamoDB
       ↓
Get user info from cache
       ↓
Get media URLs from S3/CloudFront
       ↓
Return compiled timeline
```

**Follow User:**
```
User A follows User B
       ↓
DynamoDB: Add to Follows table
       ↓
Update Redis sets:
  - followers:{B} += A
  - following:{A} += B
       ↓
Lazy: Next timeline refresh pulls B's tweets
```

#### Optimizations

**For Celebrity Users (1M+ followers):**
```
Problem: Fanout takes too long

Solution: Hybrid approach
- Regular users (<10K followers): Fanout on write
- Celebrities: Fanout on read

Implementation:
1. Check follower count
2. If < threshold → fanout on write
3. If >= threshold → don't fanout
4. Timeline generation:
   - Pull from cache (fanout users)
   - Query DynamoDB for celebrities
   - Merge and sort
```

**For Timeline Generation:**
```
Caching strategy:
1. L1: Redis (hot timelines, 1M users)
2. L2: DynamoDB (warm timelines)
3. L3: Regenerate on-demand (cold)

Eviction policy:
- Active users: Keep in Redis
- Inactive users: Expire after 7 days
```

#### Scaling to 500M Users

```
Compute:
- ECS: 100+ containers with auto-scaling
- Lambda: Reserve concurrency for fanout workers

Database:
- DynamoDB: Enable auto-scaling, use on-demand
- Redis: Use cluster mode (10-20 nodes)

Storage:
- S3: Automatically scales
- CloudFront: 400+ global edge locations

Network:
- ALB: Handles millions of connections
- WebSocket: Use API Gateway for serverless WebSockets
```

#### Cost Estimation (50M DAU)
```
Compute:
- ECS Fargate: $2,000/month (50 containers)
- Lambda: $500/month (fanout processing)

Database:
- DynamoDB: $1,000/month
- ElastiCache: $3,000/month (20 nodes)

Storage:
- S3: $500/month (media storage)
- CloudFront: $1,000/month

Total: ~$8,000/month
Per active user: $0.16/month
```

#### Monitoring & Alerts
```
Key Metrics:
✅ Timeline load time (p99 < 500ms)
✅ Tweet post latency (p99 < 100ms)
✅ Fanout job completion time
✅ Cache hit ratio (> 95%)
✅ WebSocket connection count
✅ DynamoDB throttling (= 0)

Dashboard:
- Real-time tweet rate
- Active WebSocket connections
- Cache performance
- Error rates by endpoint
```

---

### Example 3: Instagram/Photo Sharing App

#### Requirements
- Upload photos (max 10MB)
- View feed (photos from followed users)
- Like, comment on photos
- Search users
- 1B users, 500M daily active users
- 100M photos uploaded daily
- Feed should load <1 second

#### Capacity Estimation
```
Storage:
- 100M photos/day × 5MB average = 500TB/day
- 1 year = 182PB (with thumbnails)

Bandwidth:
- Upload: 100M photos/day × 5MB = 500TB/day = 5.8GB/sec
- View: 10:1 ratio = 58GB/sec

Database:
- 100M photos × 1KB metadata = 100GB/day
```

#### Architecture Components

**Step 1: Photo Upload Pipeline**
```
Flow:
1. Client requests upload URL
   API → Lambda → Generate S3 presigned URL
   
2. Client uploads directly to S3
   → S3 bucket (photos-original)
   
3. S3 triggers Lambda on upload
   → Lambda: Create thumbnails (3 sizes)
   → Save to S3 (photos-processed)
   → Save metadata to DynamoDB
   → Invalidate CloudFront cache
   → Trigger fanout to followers

Services:
- S3: Original + processed photos
- Lambda: Image processing (Sharp library)
- DynamoDB: Photo metadata
- SQS: Queue for fanout jobs
```

**Step 2: Database Design**
```
DynamoDB Tables:

1. Photos
PK: photoId (UUID)
Attributes: userId, caption, location, timestamp,
           likeCount, commentCount, s3Key
GSI: userId-timestamp-index

2. Feed
PK: userId
SK: photoId-timestamp
Attributes: photoId (denormalized for speed)
TTL: 90 days (cleanup old entries)

3. Likes
PK: photoId
SK: userId-timestamp
GSI: userId-index (for user's liked photos)

4. Comments
PK: photoId
SK: commentId-timestamp
Attributes: userId, text, timestamp

5. Follows
PK: followerId
SK: followingId
GSI: followingId-index (get followers)
```

**Step 3: Feed Generation**
```
Hybrid Approach:

For Feed Reads:
1. Check Redis cache
   Key: feed:{userId}
   Value: Sorted set of photoIds (score = timestamp)
   
2. If cache miss or partial:
   - Query DynamoDB Feed table
   - Populate cache
   
3. Batch get photo details from DynamoDB
4. Get user info from cache
5. Get URLs from CloudFront

For New Photo Posts:
1. Photo uploaded to S3
2. Metadata saved to DynamoDB
3. SQS queue created for fanout
4. Lambda workers (parallel):
   - Read followers from cache
   - For users with <50K followers: Fanout on write
     → Add photoId to each follower's Feed cache
   - For influencers: Fanout on read
     → Mark photo as "needs pulling" in cache
```

**Step 4: Image Delivery**
```
Service: S3 + CloudFront + Lambda@Edge

Image Processing On-Demand:
1. Request: https://cdn.instagram.com/p1234/300x300.jpg
2. CloudFront checks cache
3. If miss → Lambda@Edge
4. Lambda@Edge resizes from S3 original
5. Saves to S3 processed bucket
6. CloudFront caches (24 hours)

Optimization:
- Responsive images (multiple sizes)
- WebP format for supported browsers
- Lazy loading (only load visible images)

Benefits:
✅ Only generate sizes actually requested
✅ CloudFront: 400+ edge locations
✅ < 100ms delivery worldwide
```

**Step 5: Search & Discovery**
```
Service: OpenSearch (ElasticSearch)

Indexed Data:
- Users (username, name, bio)
- Hashtags
- Locations
- Photo captions

Real-time indexing:
DynamoDB Stream → Lambda → OpenSearch

Search Types:
1. User search: Autocomplete (as you type)
2. Hashtag search: Popular posts
3. Location search: Photos from location
4. Explore: ML-based recommendations (S3 + SageMaker)
```

**Step 6: Real-time Features**
```
Service: API Gateway WebSocket + Lambda

Real-time Updates:
- New likes notification
- New comments
- New follower
- Direct messages

Architecture:
1. Client connects via WebSocket
2. Connection stored in DynamoDB (connectionId → userId)
3. Event occurs (like, comment, etc.)
4. Lambda looks up recipient's connectionId
5. Pushes notification via WebSocket

Fallback: If offline, store in DynamoDB notifications table
```

**Step 7: Analytics & ML**
```
Services: Kinesis + S3 + Athena + SageMaker

Data Pipeline:
1. User interactions → Kinesis Data Firehose
2. Batch to S3 (Parquet format)
3. Athena for ad-hoc analysis
4. SageMaker for ML models:
   - Recommendation engine
   - Content moderation
   - Hashtag suggestions
   - Similar photos

Model Deployment:
- SageMaker endpoint for real-time predictions
- Batch predictions via EMR for feed generation
```

#### Complete Flows

**Upload Photo Flow:**
```
1. User clicks upload
2. App → API Gateway → Lambda (get presigned URL)
3. User uploads directly to S3
4. S3 event → Lambda
5. Lambda:
   - Create thumbnails (300x300, 600x600, 1080x1080)
   - Save to S3 processed bucket
   - Extract EXIF data
   - Content moderation check (Rekognition)
   - Save metadata to DynamoDB
6. DynamoDB Stream → Lambda
7. Fanout to followers:
   - SQS queue per region
   - Lambda workers add to follower feeds
8. Update user stats
9. WebSocket push to online followers
```

**View Feed Flow:**
```
1. User opens app
2. API request → ALB → ECS
3. Check Redis: feed:{userId}
4. If cache hit:
   - Get photoIds from sorted set
5. If cache miss:
   - Query DynamoDB Feed table
   - Populate Redis
6. For influencer photos (on-demand):
   - Query recent photos from influencers
   - Merge with feed
7. Batch get photo metadata from DynamoDB
8. Get user info from ElastiCache
9. Generate CloudFront URLs
10. Return feed to client
11. Client lazy-loads images as user scrolls
```

#### Scaling Strategy

**For 1B Users:**
```
Horizontal Scaling:
- ECS: 1,000+ containers across regions
- Lambda: Concurrent executions in thousands
- DynamoDB: Use on-demand scaling
- S3: Infinite scale (automatic)
- CloudFront: Global CDN

Regional Distribution:
- Multi-region active-active
- DynamoDB Global Tables
- S3 Cross-Region Replication
- Route 53 latency-based routing

Caching:
- CloudFront: Static assets, images
- ElastiCache: User data, follower lists
- DynamoDB DAX: Hot photo metadata
- Redis: Feed cache (1M most active users)
```

#### Cost Optimization

```
Storage:
- S3 Intelligent-Tiering for photos
- Move photos older than 1 year to Glacier
- Delete unviewed photos after 2 years

Compute:
- Use Spot instances for batch processing
- Lambda: Optimize memory allocation
- ECS: Mix of on-demand and Spot

Database:
- DynamoDB: On-demand for unpredictable traffic
- Reserved capacity for baseline traffic
- Archive old data to S3

CDN:
- CloudFront: Caching reduces origin requests
- Lazy loading: Don't load invisible images

Estimated Cost (500M DAU):
- Compute: $50,000/month
- Storage: $100,000/month (photos)
- Database: $30,000/month
- CDN: $80,000/month
- Total: ~$260,000/month
- Per user: $0.52/month
```

#### Monitoring

```
Critical Metrics:
✅ Photo upload success rate (> 99.9%)
✅ Feed load time p99 (< 1 second)
✅ Image delivery p99 (< 200ms)
✅ Cache hit ratio (> 95%)
✅ Lambda errors (< 0.1%)
✅ S3 4xx/5xx errors
✅ DynamoDB throttling

Alarms:
- Upload failures spike
- Feed timeout increase
- CDN cache hit ratio drop
- Lambda concurrent execution limit
- High S3 costs (monitor storage growth)

Dashboard:
- Real-time upload rate
- Active users by region
- Popular photos (trending)
- Storage growth trend
- Cost breakdown by service
```

---

*Last Updated: November 2025*
*Created for System Design Interview Preparation*
