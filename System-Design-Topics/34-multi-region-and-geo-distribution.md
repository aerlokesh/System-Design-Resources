# 🎯 Topic 34: Multi-Region & Geo-Distribution

> **System Design Interview — Deep Dive**
> Active-active multi-region deployment, data sovereignty (GDPR), geographic sharding, cross-region replication, and DNS-based routing.

---

## Core Concept

Deploy the full stack in multiple geographic regions to provide low-latency access worldwide and survive regional failures.

## Key Architecture

```
3 regions: us-east-1, eu-west-1, ap-southeast-1
Each: Full stack (API, DB, cache, queues)
Reads: Always local (15ms)
Writes: Local leader, async replication (200-500ms lag)
DNS: Route 53 latency-based routing → nearest region
Failover: Region down → DNS routes to next closest (< 30s)
```

## Data Sovereignty (GDPR)

```
EU users: Data MUST stay in eu-west-1
  → Geographic sharding by user's region
  → user_id prefix encodes region
  → Only PUBLIC data (tweets, profiles) replicated cross-region
  → PRIVATE data (DMs, email, phone) NEVER leaves home region
```

## Cross-Region Patterns

| Pattern | Writes | Latency | Consistency | Use Case |
|---|---|---|---|---|
| **Active-Passive** | One region only | High for remote writes | Strong (within region) | Simple, most apps |
| **Active-Active** | Both regions | Low for all | Eventual (async replication) | Global apps, latency-critical |
| **Follow-the-Sun** | Active region shifts with business hours | Low during business hours | Strong per-region | Enterprise SaaS |

## Interview Script
> *"I'd deploy active-active across 3 regions. Reads are always local. Writes go to the local region and replicate asynchronously. For GDPR, EU users' private data never leaves eu-west-1. Public content is replicated cross-region."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│        MULTI-REGION & GEO-DISTRIBUTION CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│  Active-Active: Both regions accept writes, async replication│
│  DNS Routing: Latency-based → nearest region                 │
│  Failover: < 30 seconds via DNS                              │
│  GDPR: Geographic sharding, private data stays in home region│
│  Replication lag: 200-500ms cross-region (acceptable for most)│
│  Conflict resolution: LWW for simple fields, CRDTs for counters│
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
