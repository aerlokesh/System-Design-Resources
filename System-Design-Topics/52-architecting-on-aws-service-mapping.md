# 🏗️ Architecting on AWS — Complete Service Mapping Guide

> **System Design → AWS Implementation**
> When you design a system using generic technologies (Kafka, Redis, PostgreSQL, Nginx, etc.), this guide maps every component to the correct AWS service — with trade-offs, pricing models, and production-ready configurations.

---

## 📋 Table of Contents

1. [Complete Technology → AWS Mapping Table](#1-complete-technology--aws-mapping-table)
2. [Message Queues & Event Streaming](#2-message-queues--event-streaming)
3. [Databases & Storage](#3-databases--storage)
4. [Caching](#4-caching)
5. [Compute](#5-compute)
6. [API & Networking](#6-api--networking)
7. [Search & Analytics](#7-search--analytics)
8. [File & Object Storage](#8-file--object-storage)
9. [CDN & Edge](#9-cdn--edge)
10. [Monitoring & Observability](#10-monitoring--observability)
11. [Security & Auth](#11-security--auth)
12. [Common Architecture Patterns on AWS](#12-common-architecture-patterns-on-aws)
13. [AWS Service Selection Decision Trees](#13-aws-service-selection-decision-trees)
14. [Cost Optimization Tips](#14-cost-optimization-tips)
15. [Interview Cheat Sheet: "How Would You Build This on AWS?"](#15-interview-cheat-sheet)

---

## 1. Complete Technology → AWS Mapping Table

### The Master Reference

| Generic Technology | AWS Service | When to Use | Key Difference |
|-------------------|-------------|-------------|----------------|
| **Kafka** | **Amazon MSK** (Managed Kafka) | Need actual Kafka compatibility | Fully managed Kafka, same APIs |
| **Kafka** (simpler) | **Amazon Kinesis Data Streams** | Event streaming, real-time analytics | Simpler, serverless pricing, no Kafka overhead |
| **RabbitMQ** | **Amazon MQ** (Managed RabbitMQ) | Need AMQP protocol compatibility | Managed RabbitMQ or ActiveMQ |
| **Message Queue** (generic) | **Amazon SQS** | Decouple services, async processing | Serverless, infinite scale, cheapest |
| **Pub/Sub** (generic) | **Amazon SNS** | Fan-out notifications to multiple subscribers | Push to SQS, Lambda, HTTP, email, SMS |
| **Redis** | **Amazon ElastiCache (Redis)** | Caching, sessions, leaderboards, pub/sub | Managed Redis, multi-AZ, auto-failover |
| **Redis** (serverless) | **Amazon ElastiCache Serverless** | Variable traffic, don't want to manage nodes | Pay-per-use, auto-scales |
| **Memcached** | **Amazon ElastiCache (Memcached)** | Simple key-value caching, multi-threaded | Simpler than Redis, no persistence |
| **PostgreSQL** | **Amazon RDS PostgreSQL** | Managed PostgreSQL, single-region | Automated backups, Multi-AZ failover |
| **PostgreSQL** (global) | **Amazon Aurora PostgreSQL** | High performance, global, auto-scaling | 5x faster than RDS, global database |
| **MySQL** | **Amazon RDS MySQL** or **Aurora MySQL** | Same as above for MySQL ecosystem | Aurora recommended for production |
| **MongoDB** | **Amazon DocumentDB** | Document database, MongoDB compatible | MongoDB API compatible, not 100% MongoDB |
| **DynamoDB** | **Amazon DynamoDB** | Key-value / document, unlimited scale | No equivalent elsewhere — AWS native |
| **Cassandra** | **Amazon Keyspaces** | Cassandra-compatible, wide-column | Managed Cassandra, CQL compatible |
| **Neo4j** | **Amazon Neptune** | Graph database, relationships | Gremlin + SPARQL query languages |
| **InfluxDB / TimescaleDB** | **Amazon Timestream** | Time-series data, IoT, metrics | Serverless, auto-tiered storage |
| **Elasticsearch** | **Amazon OpenSearch** | Full-text search, log analytics | Fork of Elasticsearch, Kibana → Dashboards |
| **Solr** | **Amazon CloudSearch** | Simpler search use cases | Simpler than OpenSearch, less flexible |
| **Nginx / HAProxy** | **Application Load Balancer (ALB)** | HTTP/HTTPS load balancing, routing | Layer 7, path-based routing, WebSocket |
| **Nginx** (TCP) | **Network Load Balancer (NLB)** | TCP/UDP load balancing, ultra-low latency | Layer 4, millions of RPS, static IP |
| **Nginx** (reverse proxy) | **API Gateway** | API management, auth, throttling | Fully managed, Cognito integration |
| **Kubernetes** | **Amazon EKS** | Container orchestration, K8s compatible | Managed control plane, you manage nodes |
| **Docker Compose** | **Amazon ECS** | Simpler container orchestration | AWS-native, Fargate (serverless) option |
| **Docker** (serverless) | **AWS Fargate** | Containers without managing servers | No EC2 instances to manage |
| **VMs / Servers** | **Amazon EC2** | Full control, custom OS, GPUs | Most flexible, most operational overhead |
| **Serverless Functions** | **AWS Lambda** | Event-driven, short-lived tasks | 15 min max, pay per invocation |
| **Cron Jobs** | **Amazon EventBridge + Lambda** | Scheduled tasks | Cron expression → triggers Lambda |
| **Celery / Sidekiq** | **SQS + Lambda** or **Step Functions** | Background job processing | SQS for simple, Step Functions for workflows |
| **Apache Airflow** | **Amazon MWAA** | Data pipeline orchestration | Managed Airflow |
| **Apache Spark** | **Amazon EMR** | Big data processing | Managed Spark, Hadoop, Presto |
| **Apache Flink** | **Amazon Kinesis Data Analytics** or **EMR Flink** | Stream processing | Managed Flink |
| **Hadoop / HDFS** | **Amazon S3 + EMR** | Distributed storage + processing | S3 replaces HDFS, EMR for compute |
| **Prometheus + Grafana** | **Amazon CloudWatch** + **Managed Grafana** | Metrics, dashboards | CloudWatch native or Managed Prometheus |
| **ELK Stack** | **Amazon OpenSearch** | Log aggregation, search, dashboards | Managed Elasticsearch + Kibana |
| **Datadog / New Relic** | **CloudWatch + X-Ray** | APM, distributed tracing | Native AWS integration |
| **Jaeger / Zipkin** | **AWS X-Ray** | Distributed tracing | Auto-instruments AWS SDK calls |
| **HashiCorp Vault** | **AWS Secrets Manager** | Secret storage, rotation | Auto-rotates RDS/Redshift passwords |
| **Let's Encrypt** | **AWS Certificate Manager (ACM)** | TLS certificates | Free, auto-renew, integrated with ALB/CF |
| **Auth0 / Okta** | **Amazon Cognito** | User authentication, social login | User pools + identity pools |
| **Terraform** | **AWS CloudFormation** or **AWS CDK** | Infrastructure as Code | CloudFormation = YAML/JSON, CDK = TypeScript/Python |
| **GitHub Actions** | **AWS CodePipeline + CodeBuild** | CI/CD | Or use GitHub Actions with AWS deploy |
| **SendGrid / Mailgun** | **Amazon SES** | Transactional email | $0.10/1000 emails, cheapest option |
| **Twilio** | **Amazon SNS** (SMS) + **Pinpoint** | SMS, push notifications | SNS for basic, Pinpoint for campaigns |
| **Cloudflare** | **CloudFront + WAF + Shield** | CDN, security, DDoS | Native AWS integration |
| **DNS** | **Amazon Route 53** | DNS management, health checks | Latency-based, geolocation, failover routing |

---

## 2. Message Queues & Event Streaming

### Kafka → AWS Options

```
┌──────────────────────────────────────────────────────────────────────┐
│                 KAFKA ON AWS — THREE OPTIONS                         │
├──────────────────┬───────────────────┬───────────────────────────────┤
│ Amazon MSK       │ Kinesis Data      │ SQS + SNS                    │
│ (Managed Kafka)  │ Streams           │ (Serverless Queue)           │
├──────────────────┼───────────────────┼───────────────────────────────┤
│ Real Kafka       │ Kafka-like        │ Not Kafka at all             │
│ Same APIs/tools  │ Simpler model     │ Point-to-point queue         │
│ Consumer groups  │ Shards (=partns)  │ No ordering guarantee*       │
│ Exactly-once     │ At-least-once     │ At-least-once                │
│ Unlimited retent.│ 7 days max (365   │ 14 days max                  │
│                  │ with extended)     │                              │
│ You size brokers │ Serverless option │ Fully serverless             │
│ $0.10/hr/broker  │ $0.015/shard/hr   │ $0.40/M requests             │
│                  │ or on-demand       │                              │
├──────────────────┼───────────────────┼───────────────────────────────┤
│ USE WHEN:        │ USE WHEN:         │ USE WHEN:                    │
│ • Existing Kafka │ • Real-time       │ • Simple decouple            │
│   ecosystem      │   analytics       │ • No ordering needed         │
│ • Kafka Connect  │ • Simpler ops     │ • Don't need replay          │
│ • Kafka Streams  │ • Variable load   │ • Cheapest option            │
│ • Multi-consumer │ • Fan-out to      │ • Dead letter queue          │
│   replay         │   Lambda/S3       │                              │
└──────────────────┴───────────────────┴───────────────────────────────┘

* SQS FIFO queues DO guarantee ordering (within message group ID)
```

### Event-Driven Architecture on AWS

```
# Pattern: Event Bus (like Kafka topics)
# AWS: Amazon EventBridge

# Producer publishes event:
eventbridge.put_events(
    Entries=[{
        'Source': 'order-service',
        'DetailType': 'OrderPlaced',
        'Detail': '{"orderId": "123", "amount": 4999}',
        'EventBusName': 'my-app-bus'
    }]
)

# Rules route events to targets:
# Rule 1: OrderPlaced → SQS (inventory service)
# Rule 2: OrderPlaced → Lambda (send confirmation email)
# Rule 3: OrderPlaced → Kinesis (analytics pipeline)
# Rule 4: OrderPlaced AND amount > 10000 → SNS (fraud alert)

# EventBridge vs SNS vs Kinesis:
# EventBridge: Content-based filtering, schema registry, 30+ targets
# SNS: Simple fan-out, push to SQS/Lambda/HTTP
# Kinesis: High-throughput stream processing, replay capability
```

---

## 3. Databases & Storage

### Database Selection on AWS

```
┌────────────────────────────────────────────────────────────────────────┐
│              AWS DATABASE SELECTION GUIDE                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ Need SQL + ACID + complex queries?                                     │
│ ├── Small/medium workload → RDS PostgreSQL                            │
│ ├── High performance + auto-scale → Aurora PostgreSQL                 │
│ └── Global + multi-region → Aurora Global Database                    │
│                                                                        │
│ Need key-value / document at any scale?                                │
│ ├── Known access patterns → DynamoDB (single-digit ms at any scale)   │
│ └── MongoDB compatible → DocumentDB                                   │
│                                                                        │
│ Need full-text search?                                                 │
│ └── OpenSearch (managed Elasticsearch)                                │
│                                                                        │
│ Need graph traversal (social, recommendations)?                        │
│ └── Neptune (Gremlin / SPARQL)                                        │
│                                                                        │
│ Need time-series (IoT, metrics)?                                      │
│ └── Timestream (serverless, auto-tiered)                              │
│                                                                        │
│ Need wide-column (Cassandra-like)?                                    │
│ └── Keyspaces (managed Cassandra, CQL compatible)                     │
│                                                                        │
│ Need in-memory (microsecond latency)?                                  │
│ └── ElastiCache (Redis or Memcached)                                  │
│     OR DynamoDB DAX (DynamoDB-specific cache)                         │
│                                                                        │
│ Need data warehouse (analytics)?                                       │
│ └── Redshift (columnar, petabyte-scale SQL analytics)                 │
│                                                                        │
│ Need data lake (store everything cheaply)?                             │
│ └── S3 + Athena (query with SQL, pay per query)                       │
│     OR S3 + Glue (ETL) + Redshift Spectrum                           │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### RDS vs Aurora vs DynamoDB

| Feature | RDS PostgreSQL | Aurora PostgreSQL | DynamoDB |
|---------|---------------|-------------------|----------|
| Model | Relational (SQL) | Relational (SQL) | Key-Value / Document |
| Max storage | 64 TB | 128 TB | Unlimited |
| Read replicas | 5 | 15 | Global Tables |
| Auto-scaling | Storage only | Storage + read replicas | Read + Write capacity |
| Multi-region | Read replicas | Global Database | Global Tables |
| Latency | ~5-10ms | ~3-5ms | ~1-5ms |
| Price | Cheapest SQL | ~2x RDS (but faster) | Pay per request |
| Best for | Small-medium, cost-sensitive | Production workloads | Massive scale, known patterns |

---

## 4. Caching

### Redis on AWS

```
# ElastiCache Redis (node-based)
# → You choose instance type + number of nodes
# → Best for: predictable traffic, cost optimization

# ElastiCache Serverless (new)
# → Auto-scales, pay per GB-hr + ECPUs
# → Best for: variable traffic, don't want to manage

# DynamoDB DAX (DynamoDB Accelerator)
# → In-memory cache ONLY for DynamoDB
# → Drop-in replacement: same DynamoDB API, microsecond reads
# → Best for: DynamoDB read-heavy workloads

# CloudFront (edge caching)
# → Cache HTTP responses at 450+ edge locations
# → Best for: static assets, API responses

# Caching Decision:
# API responses (edge)     → CloudFront
# Application data cache   → ElastiCache Redis
# DynamoDB reads            → DAX
# Session store             → ElastiCache Redis or DynamoDB
# Real-time leaderboard    → ElastiCache Redis (Sorted Sets)
```

---

## 5. Compute

### Compute Selection Guide

```
┌────────────────────────────────────────────────────────────────────────┐
│                   AWS COMPUTE DECISION TREE                            │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ Event-driven, short tasks (< 15 min)?                                  │
│ └── AWS Lambda                                                        │
│     • API handlers, file processing, scheduled tasks                  │
│     • Pay per invocation ($0.20/M + duration)                        │
│     • Cold starts: 100-500ms (Java worse, Python better)              │
│                                                                        │
│ Containers, don't want to manage servers?                              │
│ └── ECS on Fargate                                                    │
│     • Long-running services, background workers                       │
│     • Pay per vCPU/memory/second                                      │
│     • No EC2 management                                               │
│                                                                        │
│ Containers, need Kubernetes?                                           │
│ └── EKS (on Fargate or EC2)                                          │
│     • Team already knows K8s, multi-cloud portability                 │
│     • $0.10/hr for control plane + compute                           │
│                                                                        │
│ Need full OS control, GPUs, specific instance types?                   │
│ └── EC2                                                               │
│     • ML training, gaming, legacy apps                                │
│     • Spot instances for 60-90% savings                               │
│                                                                        │
│ Batch processing jobs?                                                 │
│ └── AWS Batch                                                         │
│     • Automatically provisions optimal compute                        │
│     • Uses Spot instances for cost savings                            │
│                                                                        │
│ Workflow orchestration?                                                 │
│ └── Step Functions                                                    │
│     • State machine: sequence Lambda, ECS, human approval             │
│     • Built-in retry, error handling, parallel execution              │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Lambda vs Fargate vs EC2

| Feature | Lambda | Fargate | EC2 |
|---------|--------|---------|-----|
| Max duration | 15 minutes | Unlimited | Unlimited |
| Scaling | Instant (0 → 1000s) | Minutes (task startup) | Minutes (instance launch) |
| Min cost | $0 (no invocations = free) | ~$0.04/hr (1 task) | ~$0.01/hr (t3.nano) |
| Cold start | Yes (100-500ms) | No (always running) | No |
| Memory | 128MB - 10GB | 0.5 - 120 GB | Instance-dependent |
| Networking | VPC optional | VPC required | VPC required |
| Best for | API handlers, events | Web services, workers | GPUs, legacy, full control |

---

## 6. API & Networking

```
# Load Balancer Selection:
# HTTP APIs         → API Gateway (managed, auth, throttle)
# Web apps          → ALB (Application Load Balancer)
# TCP/UDP           → NLB (Network Load Balancer)  
# Legacy/all ports  → CLB (Classic — avoid for new projects)

# API Gateway vs ALB:
| Feature           | API Gateway          | ALB                  |
|-------------------|----------------------|----------------------|
| Auth integration  | Cognito, Lambda auth | None (do it yourself)|
| Rate limiting     | Built-in             | None                 |
| Request validation| JSON Schema          | None                 |
| WebSocket         | ✅                   | ✅                   |
| Cost at high vol  | Expensive ($3.50/M)  | Cheap ($0.008/LCU-hr)|
| Use case          | Public APIs, mobile  | Internal, microservices|

# DNS:
# Route 53 routing policies:
# Simple        → Single endpoint
# Weighted      → A/B testing (90% v1, 10% v2)
# Latency-based → Route to lowest-latency region
# Failover      → Primary/secondary with health checks
# Geolocation   → EU users → EU servers, US users → US servers
# Multi-value   → Return multiple healthy IPs (basic LB)
```

---

## 7. Search & Analytics

```
# Elasticsearch → Amazon OpenSearch Service
# • Managed clusters (or serverless option)
# • Kibana → OpenSearch Dashboards
# • Use for: Full-text search, log analytics, APM

# Data Lake Analytics:
# Athena → Query S3 data with SQL (pay per query scanned)
# → No infrastructure. Point at S3, write SQL.
# → Supports: CSV, JSON, Parquet, ORC, Avro

# Redshift → Data warehouse
# → Columnar storage, petabyte-scale
# → Complex analytics, BI dashboards
# → Redshift Spectrum: query S3 data from Redshift

# Glue → ETL (Extract, Transform, Load)
# → Serverless Spark
# → Crawlers auto-discover schema in S3
# → Glue Data Catalog = central metadata store
```

---

## 8. File & Object Storage

```
# S3 → Object storage (files, images, videos, backups, data lake)
#   Standard:           Frequent access ($0.023/GB/month)
#   Intelligent-Tiering: Auto-moves between tiers
#   Standard-IA:         Infrequent access ($0.0125/GB/month)
#   Glacier Instant:     Rare access, ms retrieval ($0.004/GB/month)
#   Glacier Flexible:    Archive, minutes-hours retrieval ($0.0036/GB)
#   Glacier Deep:        Long-term archive, 12hr retrieval ($0.00099/GB)

# EFS → Shared file system (NFS) for EC2/ECS/Lambda
#   → Multiple instances read/write same files concurrently
#   → Use for: CMS uploads, shared config, ML training data

# EBS → Block storage (hard drive for EC2)
#   → Single EC2 instance attachment
#   → Use for: Databases, OS volumes, high-IOPS applications

# FSx → Managed file systems
#   → FSx for Lustre: HPC, ML training (S3-backed)
#   → FSx for Windows: Windows file shares
#   → FSx for NetApp ONTAP: Hybrid cloud
```

---

## 9. CDN & Edge

```
# CloudFront → CDN (static + dynamic content)
# Lambda@Edge → Run code at edge (auth, redirects, image resize)
# CloudFront Functions → Lightweight edge compute (URL rewrites, headers)
# Global Accelerator → Anycast IPs, route to nearest healthy endpoint
#   → Use when: TCP/UDP (not just HTTP), need static IPs, gaming

# CloudFront vs Global Accelerator:
# CloudFront: HTTP/HTTPS, caching, edge compute
# Global Accelerator: TCP/UDP, no caching, static IPs, health-check failover
```

---

## 10. Monitoring & Observability

```
# Prometheus → Amazon Managed Prometheus (AMP)
# Grafana → Amazon Managed Grafana (AMG)
# CloudWatch → Native metrics, logs, dashboards, alarms
# X-Ray → Distributed tracing
# CloudTrail → API audit log (who did what)

# Logging pipeline:
# App → CloudWatch Logs → Subscription Filter → Kinesis Firehose → S3 / OpenSearch
# OR: App → Fluent Bit (sidecar) → CloudWatch Logs
# OR: App → Fluent Bit → OpenSearch (for complex log analytics)
```

---

## 11. Security & Auth

```
# Auth0/Okta      → Amazon Cognito (User Pools + Identity Pools)
# Vault            → Secrets Manager (auto-rotation) + Parameter Store (config)
# Let's Encrypt    → ACM (free, auto-renew)
# NGINX WAF        → AWS WAF (managed rules for OWASP Top 10)
# Cloudflare DDoS  → AWS Shield (Standard = free, Advanced = $3K/mo)
# VPN              → AWS Site-to-Site VPN or Client VPN
# Service mesh     → AWS App Mesh (Envoy-based) or ECS Service Connect
# Firewall         → Security Groups (stateful) + NACLs (stateless)
# Private access   → VPC Endpoints / PrivateLink
```

---

## 12. Common Architecture Patterns on AWS

### Pattern 1: Serverless Web App

```
Route 53 → CloudFront → S3 (React SPA)
                      → API Gateway → Lambda → DynamoDB
                                    → Cognito (auth)
# Cost at 100K users/month: ~$50-200
```

### Pattern 2: Microservices

```
Route 53 → ALB → ECS Fargate (Service A)
              → ECS Fargate (Service B)
              → ECS Fargate (Service C)
SQS between services for async communication
Aurora PostgreSQL for shared database (or DynamoDB per service)
ElastiCache Redis for shared caching
# Cost at moderate traffic: ~$500-2000/month
```

### Pattern 3: Event-Driven Architecture

```
API Gateway → Lambda → EventBridge (event bus)
                        ├── SQS → Lambda (order processing)
                        ├── Lambda (send notification via SES/SNS)
                        ├── Kinesis Firehose → S3 (analytics)
                        └── Step Functions (complex workflow)
DynamoDB for state, S3 for files
```

### Pattern 4: Real-Time Data Pipeline

```
IoT Devices / App → Kinesis Data Streams → Lambda (transform)
                                          → Kinesis Firehose → S3 (data lake)
                                          → Lambda → DynamoDB (hot data)
                                          → OpenSearch (search/dashboards)
Athena: Query S3 data lake with SQL
QuickSight: BI dashboards
```

### Pattern 5: Multi-Region Active-Active

```
Route 53 (latency-based routing)
├── us-east-1: CloudFront → ALB → ECS → Aurora Global (primary)
└── eu-west-1: CloudFront → ALB → ECS → Aurora Global (replica)

DynamoDB Global Tables (automatic multi-region replication)
S3 Cross-Region Replication
ElastiCache Global Datastore (Redis multi-region)
```

---

## 13. AWS Service Selection Decision Trees

### "I need a database"

```
SQL needed?
├── YES → Need global / auto-scaling reads?
│         ├── YES → Aurora (PostgreSQL or MySQL)
│         └── NO → RDS (cheaper, simpler)
└── NO → Need sub-10ms at unlimited scale?
         ├── YES → DynamoDB
         └── NO → Need full-text search?
                  ├── YES → OpenSearch
                  └── NO → Need document model (MongoDB-like)?
                           ├── YES → DocumentDB
                           └── NO → Need graph?
                                    ├── YES → Neptune
                                    └── NO → DynamoDB (probably)
```

### "I need a queue"

```
Need Kafka compatibility?
├── YES → Amazon MSK
└── NO → Need event replay / stream processing?
         ├── YES → Kinesis Data Streams
         └── NO → Need simple async decoupling?
                  ├── YES → SQS (+ SNS for fan-out)
                  └── NO → Need event routing with rules?
                           └── YES → EventBridge
```

### "I need compute"

```
Short-lived, event-triggered (< 15 min)?
├── YES → Lambda
└── NO → Need containers?
         ├── YES → Need Kubernetes?
         │         ├── YES → EKS
         │         └── NO → ECS on Fargate
         └── NO → Need full OS control / GPU?
                  └── YES → EC2
```

---

## 14. Cost Optimization Tips

```
# 1. Lambda: Free tier = 1M invocations/month. Perfect for small apps.
# 2. DynamoDB: On-demand = pay per request. Provisioned = cheaper at steady load.
# 3. EC2 Spot: 60-90% savings for fault-tolerant workloads.
# 4. S3: Use lifecycle policies → move old data to Glacier automatically.
# 5. Reserved Instances: 30-60% savings for 1-3 year commits (RDS, EC2, ElastiCache).
# 6. Savings Plans: Flexible discounts for Lambda + Fargate + EC2.
# 7. NAT Gateway: $0.045/hr + $0.045/GB. Use VPC endpoints for S3/DynamoDB (free!).
# 8. CloudWatch: $0.30/metric/month. Use EMF to reduce PutMetricData API calls.
# 9. API Gateway: $3.50/M requests. ALB = $0.008/LCU-hr (cheaper at high volume).
# 10. Aurora Serverless v2: Scales to 0 ACUs. Perfect for dev/staging.
```

---

## 15. Interview Cheat Sheet

### "How Would You Build [System X] on AWS?"

```
┌────────────────────────────────────────────────────────────────────────┐
│         AWS ARCHITECTURE INTERVIEW CHEAT SHEET                         │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│ "URL Shortener"                                                        │
│ → API Gateway → Lambda → DynamoDB (short_code → original_url)         │
│ → CloudFront for caching redirect responses                           │
│                                                                        │
│ "Chat Application (WhatsApp-like)"                                     │
│ → API Gateway WebSocket → Lambda → DynamoDB (messages)                │
│ → SQS for offline message queue                                       │
│ → ElastiCache Redis for presence (online/offline)                     │
│                                                                        │
│ "Instagram / Social Media"                                             │
│ → CloudFront → S3 (images/videos)                                     │
│ → ALB → ECS (API services) → Aurora PostgreSQL (social graph)         │
│ → ElastiCache Redis (feed cache, counters)                            │
│ → SQS + Lambda (async: resize images, send notifications)             │
│                                                                        │
│ "Netflix / Video Streaming"                                            │
│ → CloudFront (video segment delivery)                                  │
│ → S3 (video storage) + MediaConvert (transcoding)                     │
│ → DynamoDB (user profiles, viewing history)                           │
│ → ElastiCache Redis (session, continue watching)                      │
│ → Kinesis (viewing analytics pipeline)                                │
│                                                                        │
│ "Twitter / News Feed"                                                  │
│ → ALB → ECS (API) → Aurora (tweets, users)                           │
│ → ElastiCache Redis (timeline cache, trending)                        │
│ → Kinesis → Lambda (fan-out new tweets to followers' caches)          │
│ → OpenSearch (tweet search)                                           │
│ → CloudFront (media delivery)                                         │
│                                                                        │
│ "E-Commerce (Amazon-like)"                                             │
│ → CloudFront → ALB → ECS (product, cart, order services)              │
│ → Aurora PostgreSQL (products, orders — ACID needed)                  │
│ → DynamoDB (shopping cart — high write throughput)                     │
│ → ElastiCache Redis (product cache, session)                          │
│ → SQS (order processing queue)                                        │
│ → Step Functions (order workflow: pay → inventory → ship → notify)    │
│ → OpenSearch (product search)                                         │
│ → SES (order confirmation emails)                                     │
│                                                                        │
│ "Uber / Ride-Sharing"                                                  │
│ → API Gateway → Lambda (ride request)                                  │
│ → Kinesis (driver location ingestion)                                  │
│ → ElastiCache Redis + GEOADD (nearby driver matching)                 │
│ → DynamoDB (trip data, driver profiles)                               │
│ → SNS (push notifications)                                            │
│ → API Gateway WebSocket (real-time trip tracking to rider)            │
│                                                                        │
│ "Metrics / Monitoring System"                                          │
│ → Kinesis Data Streams (metric ingestion)                             │
│ → Lambda (aggregation, anomaly detection)                             │
│ → Timestream (time-series storage)                                    │
│ → Managed Grafana (dashboards)                                        │
│ → SNS → Lambda (alerting pipeline)                                    │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Quick Interview Answer Template

> *"On AWS, I'd replace [generic technology] with [AWS service] because [reason]. For example, instead of self-managed Kafka, I'd use Amazon MSK if we need Kafka compatibility for existing consumers, or Kinesis Data Streams if we just need event streaming with simpler operations. For the database, since we need [ACID/scale/search], I'd choose [Aurora/DynamoDB/OpenSearch]. For caching, ElastiCache Redis handles our session store and leaderboards. The whole thing sits behind CloudFront for edge caching and WAF for security, with CloudWatch + X-Ray for observability."*

---

> **Related Topics**: [AWS Security & Encryption →](./27b-aws-security-and-encryption-real-world.md) | [CDN Real-World →](./19b-cdn-real-world-applications.md) | [Observability Real-World →](./45b-observability-monitoring-real-world-applications.md) | [DynamoDB Real-World →](./49c-dynamodb-real-world-applications.md) | [PostgreSQL Real-World →](./50b-postgresql-real-world-applications.md)
