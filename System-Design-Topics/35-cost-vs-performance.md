# 🎯 Topic 35: Cost vs Performance

> **System Design Interview — Deep Dive**
> Caching as cost lever, tiered storage savings, spot instances for batch, and quantifying the ROI of architectural decisions.

---

## Core Concept

Every architectural decision has a cost-performance tradeoff. The goal is maximizing performance per dollar, not minimizing cost or maximizing performance independently.

## Key Cost Levers

### Caching (30x ROI)
```
3-node Redis cluster: $600/month → absorbs 200K reads/sec
Without cache: 15 PostgreSQL replicas at $1,500 each = $22,500/month
Savings: $21,900/month from $600 investment = 36x ROI
```

### Tiered Storage (33x Savings)
```
All in Redis: $500K/month
Tiered (Redis + SSD + HDD + S3): $15K/month
Savings: 97% by matching storage tier to access frequency
```

### Spot Instances (60-80% Cheaper)
```
Batch processing on spot: $0.03/hour vs $0.10/hour on-demand
Nightly Spark job: 200 instances × 4 hours
  On-demand: $80/run × 365 = $29,200/year
  Spot:      $24/run × 365 = $8,760/year
  Savings:   $20,440/year per job
```

### Pre-Computation (Shifts Cost from Read to Write)
```
Pre-compute dashboards: 3 Redis writes per event (cheap)
vs. On-demand dashboards: Scan millions per query (expensive in compute + latency)
```

## Interview Scripts

> *"Caching is the single best cost-performance lever. Our $600/month Redis cluster saves $18,000/month in database costs — a 30x ROI. And read latency drops from 5ms to 0.5ms."*

> *"Spot instances for batch processing cut compute costs by 60-80%. Our nightly Spark reconciliation job uses spot at $0.03/hour instead of $0.10/hour on-demand. If AWS reclaims instances, Spark's fault tolerance reruns affected tasks."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│             COST vs PERFORMANCE CHEAT SHEET                   │
├──────────────────────────────────────────────────────────────┤
│  CACHING: $600/mo Redis saves $18K/mo in DB costs (30x ROI) │
│  TIERED STORAGE: Match tier to access frequency (33x savings)│
│  SPOT INSTANCES: 60-80% cheaper for batch workloads          │
│  PRE-COMPUTATION: 3 writes/event vs scan-millions per read   │
│  CDN: $14K/mo saves $36K/mo in origin server costs           │
│                                                              │
│  DESIGN FOR 10x, PATH TO 100x:                               │
│    At 1K QPS: Single PostgreSQL (simple, cheap)              │
│    At 10K QPS: Add read replicas                             │
│    At 100K QPS: Add caching                                  │
│    At 1M QPS: Shard + microservices                          │
│    Don't over-engineer for 1M QPS when at 1K QPS.            │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
