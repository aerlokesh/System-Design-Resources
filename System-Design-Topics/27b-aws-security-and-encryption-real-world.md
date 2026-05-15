# 🔒 AWS Security & Encryption Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how AWS security services are used in production. Every IAM policy, KMS key configuration, encryption pattern, and security architecture is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [AWS Security Architecture Overview](#1-aws-security-architecture-overview)
2. [IAM — Identity & Access Management Deep Dive](#2-iam--identity--access-management-deep-dive)
3. [KMS — Key Management Service & Encryption](#3-kms--key-management-service--encryption)
4. [Encryption at Rest — Every AWS Service](#4-encryption-at-rest--every-aws-service)
5. [Encryption in Transit — TLS Everywhere](#5-encryption-in-transit--tls-everywhere)
6. [Secrets Manager & Parameter Store](#6-secrets-manager--parameter-store)
7. [Amazon Cognito — User Authentication](#7-amazon-cognito--user-authentication)
8. [API Gateway — API Security Patterns](#8-api-gateway--api-security-patterns)
9. [VPC Security — Network Isolation](#9-vpc-security--network-isolation)
10. [WAF & Shield — Application & DDoS Protection](#10-waf--shield--application--ddos-protection)
11. [S3 Security — Object Storage Protection](#11-s3-security--object-storage-protection)
12. [Lambda Security — Serverless Patterns](#12-lambda-security--serverless-patterns)
13. [Real-World: E-Commerce Payment Security (Stripe-like)](#13-real-world-e-commerce-payment-security)
14. [Real-World: Healthcare HIPAA Compliance](#14-real-world-healthcare-hipaa-compliance)
15. [Real-World: Multi-Tenant SaaS Security](#15-real-world-multi-tenant-saas-security)
16. [Security Monitoring & Incident Response](#16-security-monitoring--incident-response)
17. [🏆 AWS Security Cheat Sheet](#-aws-security-cheat-sheet)
18. [🎯 Interview Summary](#-interview-summary)

---

## 1. AWS Security Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                   AWS SECURITY — DEFENSE IN DEPTH                      │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  Layer 1: EDGE (Perimeter)                                             │
│  ├── CloudFront + WAF → Rate limiting, bot protection, geo-blocking   │
│  ├── Shield → DDoS protection (L3/L4/L7)                             │
│  └── Route 53 → DNSSEC, DNS firewall                                 │
│                                                                        │
│  Layer 2: NETWORK                                                      │
│  ├── VPC → Network isolation                                          │
│  ├── Security Groups → Instance-level firewall (stateful)             │
│  ├── NACLs → Subnet-level firewall (stateless)                       │
│  ├── PrivateLink → Access services without internet                   │
│  └── VPN / Direct Connect → Secure hybrid connectivity                │
│                                                                        │
│  Layer 3: IDENTITY                                                     │
│  ├── IAM → Roles, policies, least privilege                           │
│  ├── Cognito → User authentication (signup, login, MFA)               │
│  ├── STS → Temporary credentials                                      │
│  └── SSO / Identity Center → Federated access                        │
│                                                                        │
│  Layer 4: APPLICATION                                                  │
│  ├── API Gateway → Authentication, throttling, request validation     │
│  ├── Lambda Authorizer → Custom auth logic                            │
│  └── Resource policies → Cross-account access control                 │
│                                                                        │
│  Layer 5: DATA                                                         │
│  ├── KMS → Encryption key management                                  │
│  ├── Encryption at rest → S3 SSE, DynamoDB, RDS, EBS                 │
│  ├── Encryption in transit → TLS 1.2+, ACM certificates              │
│  ├── Secrets Manager → Rotate DB passwords, API keys                  │
│  └── Macie → Discover & protect sensitive data in S3                  │
│                                                                        │
│  Layer 6: MONITORING                                                   │
│  ├── CloudTrail → API call audit log (who did what, when)             │
│  ├── GuardDuty → Threat detection (ML-based)                         │
│  ├── Security Hub → Centralized security findings                     │
│  ├── Config → Resource compliance tracking                            │
│  └── Detective → Security investigation                               │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. IAM — Identity & Access Management Deep Dive

### IAM Policy Anatomy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowDynamoDBOrdersTable",
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:Query"
      ],
      "Resource": "arn:aws:dynamodb:us-east-1:123456789:table/Orders",
      "Condition": {
        "ForAllValues:StringEquals": {
          "dynamodb:LeadingKeys": ["${aws:PrincipalTag/tenant_id}"]
        }
      }
    }
  ]
}
// This policy:
// 1. Allows ONLY GetItem, PutItem, Query (NOT Scan, DeleteTable)
// 2. ONLY on the Orders table (not other tables)
// 3. ONLY for items where partition key matches the caller's tenant_id tag
// → Tenant isolation enforced at IAM level!
```

### Least Privilege Patterns

```
# ❌ BAD — God-mode policy (common mistake)
{
  "Effect": "Allow",
  "Action": "*",
  "Resource": "*"
}

# ❌ BAD — Too broad
{
  "Effect": "Allow",
  "Action": "s3:*",
  "Resource": "*"
}

# ✅ GOOD — Specific actions, specific resources
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:PutObject"],
  "Resource": "arn:aws:s3:::my-app-uploads/*"
}

# ✅ BEST — With conditions
{
  "Effect": "Allow",
  "Action": ["s3:GetObject"],
  "Resource": "arn:aws:s3:::my-app-uploads/*",
  "Condition": {
    "StringEquals": { "s3:ExistingObjectTag/classification": "public" }
  }
}
```

### IAM Roles for Services (Never Use Access Keys!)

```
# ❌ BAD — Access keys in code or environment variables
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=wJal...
# Keys can leak in git, logs, error messages

# ✅ GOOD — IAM Role attached to service
# Lambda: Execution role assigned in function configuration
# ECS: Task role assigned in task definition
# EC2: Instance profile with IAM role
# → No credentials in code. STS auto-rotates temporary credentials.

# Lambda execution role example:
{
  "Effect": "Allow",
  "Action": [
    "dynamodb:GetItem",
    "dynamodb:PutItem"
  ],
  "Resource": "arn:aws:dynamodb:us-east-1:*:table/Orders"
}
// Lambda assumes this role automatically. Credentials rotate every hour.
```

### Cross-Account Access

```
# Account A (owns S3 bucket) → Account B (Lambda needs access)

# Step 1: Account A creates role with trust policy
{
  "Effect": "Allow",
  "Principal": { "AWS": "arn:aws:iam::ACCOUNT_B:role/LambdaRole" },
  "Action": "sts:AssumeRole"
}

# Step 2: Account B's Lambda assumes Account A's role
sts.assume_role(RoleArn="arn:aws:iam::ACCOUNT_A:role/CrossAccountS3")
# → Gets temporary credentials for Account A's resources
```

---

## 3. KMS — Key Management Service & Encryption

### Envelope Encryption (How KMS Actually Works)

```
┌─────────────────────────────────────────────────────────────┐
│                    ENVELOPE ENCRYPTION                        │
│                                                              │
│  1. Your app calls: KMS GenerateDataKey                      │
│                                                              │
│  2. KMS returns:                                             │
│     ┌─────────────────┐   ┌──────────────────────────┐     │
│     │ Plaintext Data   │   │ Encrypted Data Key        │     │
│     │ Key (DEK)        │   │ (encrypted with CMK)      │     │
│     └────────┬────────┘   └──────────────┬───────────┘     │
│              │                            │                   │
│  3. Your app uses DEK to encrypt data     │                   │
│     AES-256 encrypt(data, DEK) → ciphertext                 │
│                                            │                   │
│  4. Store TOGETHER:                        │                   │
│     ┌─────────────────────────────────────┐                  │
│     │ Encrypted Data + Encrypted DEK      │                  │
│     │ (plaintext DEK DISCARDED!)          │                  │
│     └─────────────────────────────────────┘                  │
│                                                              │
│  5. To decrypt:                                              │
│     a. Send Encrypted DEK → KMS Decrypt → Plaintext DEK     │
│     b. Use Plaintext DEK to decrypt data locally             │
│     c. Discard Plaintext DEK                                 │
│                                                              │
│  WHY? KMS can only encrypt 4KB directly.                     │
│  Envelope encryption encrypts unlimited data sizes.          │
│  Only the DEK (small) goes to KMS. Data stays local.        │
└─────────────────────────────────────────────────────────────┘
```

### KMS Key Types

| Key Type | Managed By | Rotation | Use Case |
|----------|-----------|----------|----------|
| AWS Managed Key (`aws/s3`, `aws/dynamodb`) | AWS | Auto (yearly) | Default encryption, simplest |
| Customer Managed Key (CMK) | You | Configurable | Cross-account, custom policies, audit |
| Customer Managed + Imported Material | You | Manual | Regulatory (you control key material) |
| CloudHSM | You (dedicated hardware) | Manual | FIPS 140-2 Level 3, full control |

### KMS Key Policy (Who Can Use the Key)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowOrderServiceEncrypt",
      "Effect": "Allow",
      "Principal": { "AWS": "arn:aws:iam::123456:role/OrderServiceRole" },
      "Action": [ "kms:Encrypt", "kms:GenerateDataKey" ],
      "Resource": "*"
    },
    {
      "Sid": "AllowOrderServiceDecrypt",
      "Effect": "Allow",
      "Principal": { "AWS": "arn:aws:iam::123456:role/OrderServiceRole" },
      "Action": [ "kms:Decrypt" ],
      "Resource": "*",
      "Condition": {
        "StringEquals": {
          "kms:EncryptionContext:purpose": "order-data"
        }
      }
    }
  ]
}
// Encryption context: Key-value pair logged in CloudTrail
// → Audit trail shows WHO decrypted WHAT data for WHICH purpose
```

---

## 4. Encryption at Rest — Every AWS Service

```
┌────────────────────────────────────────────────────────────────────────┐
│         ENCRYPTION AT REST — AWS SERVICE PATTERNS                      │
├──────────────────┬─────────────────────────────────────────────────────┤
│ SERVICE          │ ENCRYPTION MECHANISM                                │
├──────────────────┼─────────────────────────────────────────────────────┤
│ S3               │ SSE-S3 (default), SSE-KMS (audit), SSE-C (your key)│
│ DynamoDB         │ AWS managed (default) or CMK (cross-account audit)  │
│ RDS / Aurora     │ KMS encryption on EBS volumes. Enable at creation!  │
│ EBS              │ KMS encrypted volumes. Default encryption per region│
│ Lambda           │ Environment vars encrypted with KMS                 │
│ SQS              │ SSE-KMS on queue (encrypts message bodies)          │
│ SNS              │ SSE-KMS on topic                                    │
│ Kinesis          │ SSE-KMS on stream                                   │
│ ElastiCache      │ At-rest encryption + in-transit TLS                 │
│ EFS              │ KMS encryption on file system                       │
│ CloudWatch Logs  │ KMS encryption on log group                         │
│ Secrets Manager  │ KMS encryption (automatic)                          │
└──────────────────┴─────────────────────────────────────────────────────┘

# IMPORTANT: Some services require encryption ENABLED AT CREATION
# RDS: Can't enable encryption on existing unencrypted instance!
# → Must: snapshot → copy snapshot (encrypted) → restore from encrypted snapshot
# EBS: Can't encrypt existing volume → create encrypted snapshot, restore

# Best practice: Enable DEFAULT ENCRYPTION per region
# S3: aws s3api put-bucket-encryption (or account-level default)
# EBS: aws ec2 enable-ebs-encryption-by-default
```

### S3 Encryption Options

```
# SSE-S3 (Server-Side Encryption with S3-managed keys)
# → Simplest. S3 manages everything. AES-256.
# → Default since Jan 2023 (all new objects auto-encrypted)
aws s3api put-object --bucket my-bucket --key data.json --body data.json
# Encrypted automatically!

# SSE-KMS (Server-Side Encryption with KMS)
# → You control the key. Audit via CloudTrail. Cross-account sharing.
aws s3api put-object --bucket my-bucket --key data.json --body data.json \
  --server-side-encryption aws:kms --ssekms-key-id alias/my-key

# SSE-C (Server-Side Encryption with Customer-Provided Key)
# → YOU provide the key with every request. S3 never stores it.
# → Use case: Regulatory requirement to hold all keys yourself
aws s3api put-object --bucket my-bucket --key data.json --body data.json \
  --sse-customer-algorithm AES256 --sse-customer-key <base64-key>

# Client-Side Encryption
# → Encrypt BEFORE uploading. S3 never sees plaintext.
# → Maximum security. Use: AWS Encryption SDK
```

---

## 5. Encryption in Transit — TLS Everywhere

```
# AWS Certificate Manager (ACM) — Free TLS certificates

# CloudFront: ACM cert in us-east-1 (required region for CF)
# ALB: ACM cert in same region as ALB
# API Gateway: Custom domain with ACM cert

# Enforce TLS:
# S3 bucket policy — deny non-HTTPS:
{
  "Effect": "Deny",
  "Principal": "*",
  "Action": "s3:*",
  "Resource": "arn:aws:s3:::my-bucket/*",
  "Condition": { "Bool": { "aws:SecureTransport": "false" } }
}

# ALB: Redirect HTTP → HTTPS (listener rule)
# API Gateway: Minimum TLS 1.2 (security policy)
# ElastiCache: in_transit_encryption_enabled = true
# RDS: rds.force_ssl = 1 (parameter group)

# End-to-end encryption path:
# Client → HTTPS → CloudFront → HTTPS → ALB → HTTPS → App → TLS → RDS
# Every hop encrypted. No plaintext on the wire.
```

---

## 6. Secrets Manager & Parameter Store

### Secrets Manager (For Credentials That Need Rotation)

```python
# Store secret
aws secretsmanager create-secret \
  --name prod/myapp/db-credentials \
  --secret-string '{"username":"admin","password":"super-secret-123"}'

# Retrieve in Lambda/ECS:
import boto3
import json

client = boto3.client('secretsmanager')
response = client.get_secret_value(SecretId='prod/myapp/db-credentials')
credentials = json.loads(response['SecretString'])
db_password = credentials['password']

# AUTOMATIC ROTATION (the killer feature):
# Secrets Manager + Lambda rotation function
# → Every 30 days: generates new password, updates RDS, updates secret
# → Zero downtime. Zero human intervention.
# → Supports: RDS, Redshift, DocumentDB, custom Lambda rotation

# Cost: $0.40/secret/month + $0.05/10K API calls
```

### Parameter Store (For Config Values)

```
# Store config
aws ssm put-parameter \
  --name /prod/myapp/feature-flags \
  --value '{"dark_mode": true, "new_checkout": false}' \
  --type String

# Secure string (KMS-encrypted)
aws ssm put-parameter \
  --name /prod/myapp/api-key \
  --value "sk_live_abc123" \
  --type SecureString \
  --key-id alias/my-key

# Retrieve:
aws ssm get-parameter --name /prod/myapp/api-key --with-decryption

# Cost: Standard tier = FREE (up to 10K params)
# Advanced tier: $0.05/param/month (higher throughput, larger values)
```

### When to Use Which

| Feature | Secrets Manager | Parameter Store |
|---------|----------------|-----------------|
| Auto-rotation | ✅ Built-in | ❌ Manual |
| Cost | $0.40/secret/month | Free (standard) |
| Max size | 64KB | 8KB (standard), 8KB (advanced) |
| Cross-account | ✅ Resource policy | ❌ |
| Use case | DB passwords, API keys | Feature flags, config, endpoints |

---

## 7. Amazon Cognito — User Authentication

```
# User Pool (authentication — who are you?)
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Sign Up    │────→│  Cognito     │────→│  JWT Token   │
│   Login      │     │  User Pool   │     │  (id, access, │
│   MFA        │     │              │     │   refresh)    │
│   OAuth/OIDC │     │  Stores:     │     │              │
│   Social     │     │  - Users     │     │  Verify with │
│   (Google,FB)│     │  - Groups    │     │  API Gateway │
└──────────────┘     │  - MFA config│     │  authorizer  │
                     └──────────────┘     └──────────────┘

# Identity Pool (authorization — what can you access?)
# Maps Cognito users → IAM roles → AWS resource access
# "Authenticated users get S3 read access to their folder"
# "Unauthenticated users get read-only DynamoDB access"
```

### Cognito + API Gateway Flow

```
1. User logs in → Cognito returns JWT (id_token + access_token)
2. Client sends: Authorization: Bearer <access_token>
3. API Gateway → Cognito Authorizer → validates JWT signature + expiry
4. If valid → forward to Lambda/backend with user claims
5. Backend reads claims: userId, email, groups, custom attributes

# API Gateway authorizer config:
# Type: Cognito User Pool
# Token source: Authorization header
# → Zero custom auth code needed!
```

---

## 8. API Gateway — API Security Patterns

```
# Layer 1: Authentication
# Cognito authorizer → JWT validation
# Lambda authorizer → Custom logic (API keys, OAuth, SAML)
# IAM authorizer → AWS Signature V4 (service-to-service)

# Layer 2: Authorization (API key + usage plans)
# Usage plan: 1000 requests/day, 10 requests/second
# Separate plans per customer tier: free (100/day), pro (10K/day), enterprise (unlimited)

# Layer 3: Request validation
# JSON Schema validation on request body → reject malformed requests BEFORE Lambda
# Required headers, query params, path params validated at gateway level

# Layer 4: Throttling
# Account limit: 10,000 RPS (soft limit, can increase)
# Per-method throttle: POST /orders → 100 RPS (protect downstream)
# Per-client throttle via usage plans

# Layer 5: WAF integration
# Attach WAF Web ACL → SQL injection, XSS, rate limiting

# Layer 6: Mutual TLS (mTLS)
# Client presents certificate → API Gateway validates
# Use case: B2B APIs, PCI compliance
```

---

## 9. VPC Security — Network Isolation

```
┌────────────────── VPC (10.0.0.0/16) ──────────────────────┐
│                                                             │
│  ┌──── Public Subnet (10.0.1.0/24) ─────────────────────┐ │
│  │  ALB  │  NAT Gateway  │  Bastion Host                 │ │
│  │  (internet-facing)                                     │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──── Private Subnet (10.0.2.0/24) ─────────────────────┐ │
│  │  ECS Tasks / Lambda / EC2 (app servers)                │ │
│  │  → No direct internet access                           │ │
│  │  → Outbound via NAT Gateway only                       │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──── Data Subnet (10.0.3.0/24) ────────────────────────┐ │
│  │  RDS / ElastiCache / OpenSearch                        │ │
│  │  → No internet access at all                           │ │
│  │  → Only accessible from Private Subnet                 │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  VPC Endpoints (PrivateLink):                               │
│  ├── S3 Gateway Endpoint (free, stays within AWS network)  │
│  ├── DynamoDB Gateway Endpoint (free)                      │
│  ├── KMS Interface Endpoint (keeps encryption traffic private)│
│  ├── Secrets Manager Interface Endpoint                    │
│  └── SQS/SNS/CloudWatch Interface Endpoints                │
│                                                             │
└─────────────────────────────────────────────────────────────┘

# Security Groups (stateful):
# ALB SG: Inbound 443 from 0.0.0.0/0 (internet)
# App SG: Inbound 8080 from ALB SG only
# DB SG:  Inbound 5432 from App SG only
# → Defense in depth: even if ALB compromised, attacker can't reach DB directly
```

### VPC Endpoints — Keep Traffic Private

```
# Without VPC Endpoint:
# Lambda → Internet → S3 (traffic leaves VPC, goes over public internet)

# With VPC Gateway Endpoint (S3, DynamoDB):
# Lambda → VPC Endpoint → S3 (stays within AWS network, FREE)

# With VPC Interface Endpoint (everything else):
# Lambda → ENI → KMS/Secrets Manager (private IP, no internet needed)
# Cost: ~$0.01/hour per endpoint + data transfer

# Why it matters:
# 1. Security: Traffic never leaves AWS network
# 2. Compliance: PCI/HIPAA requires no internet exposure for sensitive data
# 3. Performance: Lower latency (no internet hop)
```

---

## 10. WAF & Shield — Application & DDoS Protection

```
# WAF Rules for Production API:

# 1. AWS Managed Rules (pre-built):
AWSManagedRulesCommonRuleSet       → SQL injection, XSS, path traversal
AWSManagedRulesSQLiRuleSet         → SQL injection (deep inspection)
AWSManagedRulesKnownBadInputsRuleSet → Log4j, SSRF patterns
AWSManagedRulesBotControlRuleSet   → Bot detection
AWSManagedRulesAmazonIpReputationList → Known malicious IPs

# 2. Custom rate limiting:
{
  "Name": "RateLimitPerIP",
  "Priority": 1,
  "Action": { "Block": {} },
  "Statement": {
    "RateBasedStatement": {
      "Limit": 2000,           // 2000 requests per 5 minutes per IP
      "AggregateKeyType": "IP"
    }
  }
}

# 3. Geo-blocking (compliance):
{
  "Name": "BlockSanctionedCountries",
  "Statement": {
    "GeoMatchStatement": {
      "CountryCodes": ["KP", "IR", "CU"]
    }
  },
  "Action": { "Block": {} }
}
```

---

## 11. S3 Security — Object Storage Protection

```
# S3 Security Checklist:

# 1. Block public access (account-level + bucket-level)
aws s3api put-public-access-block --bucket my-bucket \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,\
  BlockPublicPolicy=true,RestrictPublicBuckets=true

# 2. Encryption (default since Jan 2023)
# SSE-S3 minimum. SSE-KMS for audit/compliance.

# 3. Bucket policy — deny unencrypted uploads:
{
  "Effect": "Deny",
  "Principal": "*",
  "Action": "s3:PutObject",
  "Resource": "arn:aws:s3:::my-bucket/*",
  "Condition": {
    "StringNotEquals": { "s3:x-amz-server-side-encryption": "aws:kms" }
  }
}

# 4. Versioning (protect against accidental deletion)
aws s3api put-bucket-versioning --bucket my-bucket --versioning-configuration Status=Enabled

# 5. Object Lock (WORM — write once, read many)
# Compliance mode: NOBODY can delete, not even root (regulatory requirement)

# 6. Access logging → separate logging bucket
# 7. Macie → auto-discover PII/sensitive data in S3
# 8. S3 Access Points → simplified per-application access policies

# 9. Pre-signed URLs for temporary access:
url = s3_client.generate_presigned_url(
    'get_object',
    Params={'Bucket': 'my-bucket', 'Key': 'private/report.pdf'},
    ExpiresIn=3600  # 1 hour
)
# User gets temporary read access to this specific object only
```

---

## 12. Lambda Security — Serverless Patterns

```
# Lambda Security Best Practices:

# 1. Least-privilege execution role
# Each Lambda function → its own IAM role → minimal permissions
# ❌ BAD: One role for all functions with broad access
# ✅ GOOD: OrderFunction role can only access Orders table

# 2. Environment variable encryption
# Sensitive values → encrypt with KMS
# Lambda decrypts at runtime using execution role

# 3. VPC integration (when needed)
# Lambda in VPC → access private RDS, ElastiCache
# Lambda NOT in VPC → simpler, faster cold starts

# 4. Reserved concurrency (blast radius)
# Limit each function → prevents one function from consuming all capacity
# OrderFunction: reserved = 100
# SearchFunction: reserved = 50
# Total account limit: 1000

# 5. Code signing
# Only deploy code signed by your organization
# Prevents: unauthorized code deployment, supply chain attacks

# 6. Lambda Layers
# Share dependencies across functions
# Security team provides "approved crypto library" layer
# All functions use the same vetted dependency

# 7. Function URL auth
# AuthType: AWS_IAM → SigV4 authentication
# AuthType: NONE → public (use only with WAF/CloudFront)
```

---

## 13. Real-World: E-Commerce Payment Security

```
┌────────────────────────────────────────────────────────────┐
│              PAYMENT SECURITY ARCHITECTURE                  │
│                                                            │
│  Browser ──HTTPS──→ CloudFront (WAF) ──→ API Gateway      │
│                                              │             │
│                                    Cognito Authorizer      │
│                                              │             │
│                                    Lambda: ProcessPayment  │
│                                    ├── Secrets Manager     │
│                                    │   (Stripe API key)    │
│                                    ├── KMS encrypt         │
│                                    │   (card token)        │
│                                    └── DynamoDB            │
│                                        (encrypted, CMK)    │
│                                                            │
│  RULES:                                                     │
│  1. Card numbers NEVER stored (tokenized by Stripe)        │
│  2. All data encrypted at rest (KMS CMK)                    │
│  3. All transit encrypted (TLS 1.2+)                       │
│  4. API keys in Secrets Manager (auto-rotated)             │
│  5. CloudTrail logs every KMS decrypt (audit)              │
│  6. WAF blocks SQL injection, rate limits                  │
│  7. VPC endpoints for DynamoDB/KMS (no internet)           │
│  8. Lambda has ONLY permissions it needs                   │
└────────────────────────────────────────────────────────────┘
```

---

## 14. Real-World: Healthcare HIPAA Compliance

```
# HIPAA on AWS requirements:

# 1. BAA (Business Associate Agreement) with AWS ← MUST sign first
# 2. Encryption everywhere:
#    - S3: SSE-KMS (customer-managed key, audit trail)
#    - RDS: KMS encryption + TLS for connections
#    - EBS: KMS encryption
#    - CloudWatch Logs: KMS encryption
#    - ALL data in transit: TLS 1.2+

# 3. Access control:
#    - IAM roles with condition keys
#    - MFA required for console access
#    - No root account usage (SCPs to prevent)

# 4. Audit logging:
#    - CloudTrail: ALL API calls logged to encrypted S3 bucket
#    - CloudTrail log file validation: tamper-proof
#    - S3 access logging: who accessed patient data
#    - VPC Flow Logs: network traffic audit

# 5. Network isolation:
#    - PHI (Protected Health Info) in private subnets ONLY
#    - VPC endpoints for all AWS service access
#    - No public S3 buckets (account-level block)

# 6. Data lifecycle:
#    - S3 Object Lock: Compliance mode (retention period)
#    - Backup: AWS Backup with encryption
#    - Deletion: KMS key deletion = crypto-shredding
```

---

## 15. Real-World: Multi-Tenant SaaS Security

```
# Tenant isolation strategies (on AWS):

# Strategy 1: IAM-based isolation (DynamoDB)
# Each tenant's data has partition key prefix: TENANT#tenant_abc
# IAM policy restricts access to matching partition keys:
{
  "Condition": {
    "ForAllValues:StringLike": {
      "dynamodb:LeadingKeys": ["TENANT#${aws:PrincipalTag/TenantId}#*"]
    }
  }
}

# Strategy 2: Row-Level Security (RDS/Aurora PostgreSQL)
# SET app.current_tenant = 'tenant_abc';
# RLS policy: USING (tenant_id = current_setting('app.current_tenant'))
# → Database enforces isolation automatically

# Strategy 3: Separate KMS keys per tenant
# Tenant A data encrypted with Key A
# Tenant B data encrypted with Key B
# Even if access control fails → can't decrypt wrong tenant's data
# Key policy: Only Tenant A's service role can use Key A

# Strategy 4: Separate AWS accounts per tenant (enterprise)
# AWS Organizations: One account per enterprise customer
# Complete network, IAM, encryption isolation
# Most expensive but strongest isolation guarantee
```

---

## 16. Security Monitoring & Incident Response

```
# CloudTrail — Who did what, when
# Every API call logged: caller, time, source IP, request/response
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=DeleteBucket

# GuardDuty — Threat detection (ML-based)
# Monitors: CloudTrail, VPC Flow Logs, DNS Logs
# Detects: Compromised credentials, crypto-mining, data exfiltration
# Example finding: "EC2 instance i-abc is communicating with known Bitcoin mining pool"

# Security Hub — Centralized findings
# Aggregates: GuardDuty + Inspector + Macie + Config + Firewall Manager
# Compliance checks: CIS Benchmarks, PCI DSS, AWS Foundational Security

# Config — Resource compliance
# Rule: "All S3 buckets must have encryption enabled"
# Rule: "All security groups must not allow 0.0.0.0/0 on port 22"
# Auto-remediation: Lambda triggers on non-compliant resource → fix automatically

# Incident response automation:
# GuardDuty finding → EventBridge → Lambda → 
#   1. Isolate compromised EC2 (modify security group)
#   2. Snapshot EBS volumes (forensics)
#   3. Alert security team (SNS → PagerDuty)
#   4. Create JIRA ticket
```

---

## 🏆 AWS Security Cheat Sheet

```
┌────────────────────────────────────────────────────────────────────────┐
│         AWS SECURITY & ENCRYPTION CHEAT SHEET                          │
├────────────────────────────────────────────────────────────────────────┤
│ IDENTITY:                                                              │
│ • IAM Roles (not keys!) for services                                  │
│ • Least privilege: specific actions + specific resources + conditions  │
│ • Cognito for user auth, STS for temp credentials                     │
│ • MFA on root and all humans, SCPs for guardrails                     │
│                                                                        │
│ ENCRYPTION AT REST:                                                    │
│ • S3: SSE-S3 (default) or SSE-KMS (audit)                            │
│ • DynamoDB: AWS managed (default) or CMK                              │
│ • RDS: KMS (must enable at creation!)                                 │
│ • Lambda env vars: KMS encrypted                                      │
│ • Envelope encryption: KMS → Data Key → encrypt data locally          │
│                                                                        │
│ ENCRYPTION IN TRANSIT:                                                 │
│ • TLS 1.2+ everywhere (ACM for free certs)                            │
│ • S3 bucket policy: deny non-HTTPS                                    │
│ • RDS: force_ssl parameter                                            │
│ • ElastiCache: in-transit encryption enabled                          │
│                                                                        │
│ SECRETS:                                                               │
│ • Secrets Manager: DB passwords, API keys (auto-rotation!)            │
│ • Parameter Store: Config values, feature flags (free)                │
│ • NEVER hardcode credentials in code                                  │
│                                                                        │
│ NETWORK:                                                               │
│ • VPC: Public → Private → Data subnets                                │
│ • Security Groups: Allow only needed ports from needed sources        │
│ • VPC Endpoints: S3/DynamoDB (free), KMS/Secrets Mgr (private)       │
│ • No public access to databases, ever                                 │
│                                                                        │
│ EDGE SECURITY:                                                         │
│ • WAF: SQL injection, XSS, rate limiting, bot protection              │
│ • Shield: DDoS (Standard = free with CloudFront/ALB)                  │
│ • CloudFront: Signed URLs for private content                         │
│                                                                        │
│ MONITORING:                                                            │
│ • CloudTrail: API audit log (enable in ALL regions!)                  │
│ • GuardDuty: Threat detection (enable everywhere, $2/month)           │
│ • Config: Compliance rules + auto-remediation                         │
│ • Security Hub: Centralized findings dashboard                        │
│                                                                        │
│ DATA PROTECTION:                                                       │
│ • S3: Block public access, versioning, Object Lock                   │
│ • Macie: Auto-discover PII in S3                                     │
│ • Backup: AWS Backup with encryption + cross-region                  │
│ • Crypto-shredding: Delete KMS key → all data unreadable             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary

### "How would you secure a production AWS application?"

> *"I'd implement defense in depth across six layers. At the **edge**: CloudFront with WAF for rate limiting, bot protection, and SQL injection prevention, plus Shield for DDoS. **Network**: VPC with public/private/data subnets, security groups allowing only required traffic, VPC endpoints for S3/DynamoDB/KMS to keep traffic off the internet. **Identity**: IAM roles (never access keys) with least-privilege policies, Cognito for user authentication with MFA, STS for temporary credentials. **Application**: API Gateway with Cognito authorizer, request validation, and usage plans for throttling. **Data**: KMS encryption at rest for all services (S3 SSE-KMS, DynamoDB encryption, RDS KMS), TLS 1.2+ in transit everywhere, Secrets Manager for auto-rotating credentials. **Monitoring**: CloudTrail for API audit logs, GuardDuty for threat detection, Config for compliance rules with auto-remediation, and Security Hub for centralized findings."*

---

> **Related Topics**: [Security & Encryption Deep Dive →](./27-security-and-encryption.md) | [CDN Real-World →](./19b-cdn-real-world-applications.md) | [Observability Real-World →](./45b-observability-monitoring-real-world-applications.md)
