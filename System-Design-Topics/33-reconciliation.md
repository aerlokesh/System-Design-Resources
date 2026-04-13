# 🎯 Topic 33: Reconciliation

> **System Design Interview — Deep Dive**
> Daily batch reconciliation, stream vs batch accuracy, billing reconciliation, and how to ensure data correctness in systems with eventual consistency.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Reconciliation Is Needed](#why-reconciliation-is-needed)
3. [Daily Batch Reconciliation](#daily-batch-reconciliation)
4. [Stream vs Batch Numbers](#stream-vs-batch-numbers)
5. [Billing Reconciliation](#billing-reconciliation)
6. [Reconciliation Architecture](#reconciliation-architecture)
7. [Handling Discrepancies](#handling-discrepancies)
8. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
9. [Common Interview Mistakes](#common-interview-mistakes)
10. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Reconciliation** compares data from two or more sources to ensure they agree. It's the safety net that catches drift between real-time streaming systems (approximate) and batch systems (exact).

```
Streaming pipeline: Campaign 123 got ~2,312,000 clicks (approximate, real-time)
Batch recomputation: Campaign 123 got exactly 2,312,847 clicks (exact, nightly)
Discrepancy: 847 clicks (0.037%) — corrected automatically
```

---

## Why Reconciliation Is Needed

| Cause of Drift | How It Happens |
|---|---|
| **Late events** | Event arrives after streaming window closed |
| **Sampling** | Under backpressure, stream sampled at 10% with correction |
| **Exactly-once gaps** | Consumer crash between process and commit → duplicate or loss |
| **Clock skew** | Events timestamped by different clocks → wrong window |
| **Bug in stream logic** | Code error undercounts/overcounts for 5 days until discovered |
| **Network duplicates** | Retry causes duplicate delivery, dedup misses edge case |

---

## Daily Batch Reconciliation

### Process
```
3 AM UTC - Reconciliation Job:

1. Read ALL raw events from S3 for previous day (200 billion events)
2. Recompute exact aggregates: GROUP BY (campaign_id, ad_id, hour)
3. Compare with streaming aggregates in ClickHouse:
   For each dimension:
     batch_count vs stream_count
     If |difference| > 0.1% → flag as discrepancy
4. Correct discrepancies:
   Overwrite ClickHouse row with batch-computed exact value
5. Log corrections for audit

Duration: 2-4 hours (Spark job on spot instances)
Typical result: 99.98% of dimensions match exactly
```

### Interview Script
> *"I'd run a daily reconciliation job at 3 AM UTC. Spark reads all raw click events from S3 for the previous day (all 200 billion of them), recomputes exact aggregates, and compares them to the streaming aggregates in ClickHouse. Any dimension with > 0.1% discrepancy gets corrected. Typically, 99.98% of dimensions match exactly. The 0.02% with corrections are usually due to late events or sampling during backpressure."*

---

## Stream vs Batch Numbers

### Two Different Uses
```
Real-time dashboard (stream):
  Shows: ~2,312,000 clicks (updates every 30 seconds)
  Source: Flink streaming aggregation
  Accuracy: ±0.1% (good enough for display)
  
Invoice (batch):
  Shows: 2,312,847 clicks (computed nightly)
  Source: Spark batch recomputation from raw events
  Accuracy: Exact (required for billing)
```

### Why Separate?
```
Advertisers would complain if:
  Dashboard shows 10,001 clicks
  Invoice charges for 10,003 clicks
  → "Why am I being charged for clicks not shown on my dashboard?"

Solution:
  Dashboard: Stream numbers (fresh, approximate) — clearly labeled "~"
  Invoice:   Batch numbers (exact, one day old) — this is what you're billed for
  
  The one-day delay is inherent to billing anyway (invoices are next-day).
```

### Interview Script
> *"For advertiser billing, we never use the streaming numbers directly. The real-time dashboard shows streaming aggregates (fresh but approximate). The invoice uses batch-reconciled numbers (one day old but exact). This separation is critical — an advertiser seeing 10,001 clicks on the dashboard but being billed for 10,003 would file a support ticket."*

---

## Billing Reconciliation

### Requirements
```
1. Exact counts (no sampling, no approximation)
2. Auditable (full trace from raw event to final count)
3. Reproducible (same input → same output every time)
4. Timely (available by next business day)
5. Correctable (if a bug is found, rerun and correct)
```

### Kafka Replay for Correction
```
Bug discovered in click dedup logic (5 days ago):
  1. Fix the bug in consumer code
  2. Reset Spark job input to 5 days ago
  3. Reprocess 12 billion events from S3
  4. Generate corrected aggregates
  5. Compare with original invoices
  6. Issue credit/debit adjustments for affected advertisers
  
  Kafka's 30-day retention ensures raw events are available for replay.
```

---

## Reconciliation Architecture

```
┌──────────────────────────────────────────────────┐
│                 Raw Events (S3)                    │
│    200 billion events/day, Parquet format          │
└──────────────┬───────────────────────────────────┘
               │
    ┌──────────▼──────────┐     ┌──────────────────┐
    │  Spark Batch Job     │     │  Flink Streaming  │
    │  (exact aggregates)  │     │  (approx counts)  │
    └──────────┬──────────┘     └────────┬─────────┘
               │                          │
    ┌──────────▼──────────┐     ┌────────▼─────────┐
    │  Batch Aggregates    │     │ Stream Aggregates │
    │  (ClickHouse batch)  │     │ (ClickHouse live) │
    └──────────┬──────────┘     └────────┬─────────┘
               │                          │
    ┌──────────▼──────────────────────────▼────────┐
    │           Reconciliation Comparator           │
    │   Compare batch vs stream per dimension       │
    │   If discrepancy > 0.1% → correct stream     │
    └──────────────────────┬───────────────────────┘
                           │
              ┌────────────▼────────────┐
              │  Corrected Aggregates    │
              │  (used for invoicing)    │
              └─────────────────────────┘
```

---

## Handling Discrepancies

### Correction Strategy
```
Automatic (< 0.5% discrepancy):
  Overwrite stream value with batch value silently.
  Log the correction for audit.

Investigation (0.5% - 5% discrepancy):
  Alert engineering team.
  Investigate root cause (late events? sampling? bug?)
  Correct after investigation.

Emergency (> 5% discrepancy):
  Halt billing for affected campaigns.
  Full investigation required.
  Manual correction and advertiser communication.
```

### Audit Trail
```
Every correction logged:
  {
    "campaign_id": 123,
    "date": "2025-03-15",
    "hour": 14,
    "stream_count": 2312000,
    "batch_count": 2312847,
    "discrepancy_pct": 0.037,
    "action": "auto_corrected",
    "corrected_at": "2025-03-16T03:45:00Z"
  }
```

---

## Interview Talking Points & Scripts

### Daily Reconciliation
> *"Nightly Spark job reads all raw events from S3, recomputes exact aggregates, compares with streaming counts. 99.98% match. 0.02% corrected automatically. For billing, always use batch-reconciled numbers."*

### Kafka Replay for Bug Fix
> *"Kafka retains 30 days. If a bug is found, replay from raw events, recompute, and issue corrections. Data is never permanently lost."*

---

## Common Interview Mistakes

### ❌ Using streaming numbers for billing (approximate ≠ billable)
### ❌ Not mentioning reconciliation at all (how do you know stream is correct?)
### ❌ No correction mechanism when discrepancy is found
### ❌ Not retaining raw events for replay

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               RECONCILIATION CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PURPOSE: Ensure stream (approximate) matches batch (exact)  │
│                                                              │
│  PROCESS:                                                    │
│    Nightly: Spark reads raw events from S3                   │
│    Recompute exact aggregates per dimension                  │
│    Compare with streaming aggregates                         │
│    Correct if discrepancy > 0.1%                             │
│                                                              │
│  TWO NUMBERS:                                                │
│    Dashboard: Stream numbers (fresh, ~approximate)           │
│    Invoice:   Batch numbers (next-day, exact)                │
│                                                              │
│  TYPICAL RESULT: 99.98% match, 0.02% corrected              │
│  CAUSES: Late events, sampling, dedup gaps, bugs             │
│  RECOVERY: Kafka replay (30-day retention) for bug fixes     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
