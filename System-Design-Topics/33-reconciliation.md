# 🎯 Topic 33: Reconciliation

> **System Design Interview — Deep Dive**
> A comprehensive guide covering daily batch reconciliation, stream vs batch accuracy, billing reconciliation, discrepancy detection and correction, Kafka replay for bug fixes, audit trails, and how to articulate reconciliation strategies with depth and precision in a system design interview.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Why Reconciliation Is Needed](#why-reconciliation-is-needed)
3. [Daily Batch Reconciliation](#daily-batch-reconciliation)
4. [Stream vs Batch Numbers — Two Different Truths](#stream-vs-batch-numbers--two-different-truths)
5. [Billing Reconciliation](#billing-reconciliation)
6. [Reconciliation Architecture](#reconciliation-architecture)
7. [Discrepancy Detection and Classification](#discrepancy-detection-and-classification)
8. [Handling Discrepancies](#handling-discrepancies)
9. [Kafka Replay for Bug Fixes](#kafka-replay-for-bug-fixes)
10. [Cross-System Reconciliation](#cross-system-reconciliation)
11. [Audit Trail and Compliance](#audit-trail-and-compliance)
12. [Real-World System Examples](#real-world-system-examples)
13. [Deep Dive: Applying Reconciliation to Popular Problems](#deep-dive-applying-reconciliation-to-popular-problems)
14. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
15. [Common Interview Mistakes](#common-interview-mistakes)
16. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Reconciliation** is the process of comparing data from two or more sources to ensure they agree, and correcting any discrepancies. It's the **safety net** that catches drift between real-time streaming systems (fast but approximate) and batch systems (slow but exact).

```
Real-time pipeline (streaming):
  Campaign 123 got ~2,312,000 clicks (approximate, real-time display)
  Source: Flink streaming aggregation
  Updated: Every 30 seconds
  Accuracy: ±0.1% (due to sampling, late events, dedup gaps)

Batch recomputation (nightly):
  Campaign 123 got exactly 2,312,847 clicks (exact, authoritative)
  Source: Spark batch job over raw events in S3
  Updated: Once per day at 3 AM
  Accuracy: 100% (processes ALL events, no sampling)

Discrepancy: 847 clicks (0.037%)
Action: Auto-correct streaming aggregate to match batch value
```

**The fundamental principle**: In any system with eventual consistency, streaming pipelines, or distributed processing, the data **will** drift. Reconciliation is how you detect and correct that drift.

---

## Why Reconciliation Is Needed

### Sources of Data Drift

| Source of Drift | How It Happens | Typical Impact |
|---|---|---|
| **Late events** | Event arrives after streaming window closed | 0.01-0.1% undercount |
| **Sampling under backpressure** | Stream sampled at 10% with correction factor | 1-3% estimation error |
| **Exactly-once gaps** | Consumer crash between process and commit | Duplicate or lost events |
| **Clock skew** | Events timestamped by different clocks → wrong window | Events in wrong time bucket |
| **Dedup failures** | Retry causes duplicate, dedup misses edge case | Overcounting |
| **Bug in stream logic** | Code error undercounts/overcounts for days | Arbitrary error until fixed |
| **Network duplicates** | At-least-once delivery produces duplicates | Overcounting |
| **Schema evolution** | New field not handled by old consumer code | Partial processing |
| **Partition rebalancing** | Kafka consumer rebalance during processing | Brief duplicate/loss window |

### The Drift Problem in Practice

```
Day 1: Stream says 10,000,000 clicks. Batch says 10,000,000. Perfect ✓
Day 2: Stream says 10,047,000. Batch says 10,050,000. Off by 3,000 (0.03%) ⚠️
Day 3: Stream says 10,012,000. Batch says 10,015,000. Off by 3,000 (0.03%) ⚠️
Day 4: A bug was deployed at 2 PM. Stream says 8,500,000. Batch says 10,100,000. Off by 1,600,000 (15.8%) 🔥

Without reconciliation:
  Day 4's bug goes undetected for days or weeks.
  Advertisers are under-charged by 15.8%.
  Potential revenue loss: Millions of dollars.

With reconciliation:
  Day 4's batch job detects 15.8% discrepancy at 3 AM.
  Alert fires at 3:05 AM.
  On-call engineer investigates → finds the bug → rolls back.
  Corrected aggregates pushed to serving layer by 6 AM.
  Advertiser impact: 3 hours of incorrect display, corrected before business hours.
```

---

## Daily Batch Reconciliation

### The Process

```
3 AM UTC — Daily Reconciliation Job:

Step 1: Read ALL raw events from S3 for previous day
  Input: s3://events/clicks/2025-03-15/*.parquet
  Volume: ~200 billion events
  Format: Parquet (columnar, compressed)

Step 2: Recompute EXACT aggregates
  Spark job: GROUP BY (campaign_id, ad_id, hour)
  Output: Exact click count, impression count, spend per dimension
  Duration: 2-4 hours (200 Spark executors on spot instances)

Step 3: Compare with streaming aggregates
  For each dimension (campaign_id, ad_id, hour):
    batch_count = spark_result.clicks
    stream_count = clickhouse_streaming.clicks
    discrepancy = abs(batch_count - stream_count)
    discrepancy_pct = discrepancy / batch_count * 100

Step 4: Classify discrepancies
  < 0.1%: Auto-correct silently (expected drift)
  0.1% - 0.5%: Auto-correct, log warning
  0.5% - 5%: Auto-correct, alert engineering team for investigation
  > 5%: HALT billing, alert senior on-call, require manual approval

Step 5: Apply corrections
  For auto-correctable dimensions:
    UPDATE clickhouse SET clicks = batch_clicks WHERE campaign_id = X AND date = Y
  Log every correction for audit trail

Step 6: Generate reconciliation report
  Total dimensions checked: 2,847,000
  Dimensions matching exactly: 2,841,000 (99.79%)
  Dimensions with < 0.1% discrepancy: 5,500 (0.19%)
  Dimensions with 0.1-5% discrepancy: 480 (0.02%)
  Dimensions with > 5% discrepancy: 20 (0.0007%)
```

### Reconciliation Job Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                RECONCILIATION PIPELINE                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  S3 (Raw Events)                                             │
│  └── 200B events/day, Parquet format, partitioned by date    │
│       │                                                      │
│       ▼                                                      │
│  Spark Batch Job (3 AM UTC)                                  │
│  └── Read all events for previous day                        │
│  └── GROUP BY (campaign, ad, hour)                           │
│  └── Compute exact aggregates                                │
│       │                                                      │
│       ▼                                                      │
│  Reconciliation Comparator                                   │
│  └── Load streaming aggregates from ClickHouse               │
│  └── Compare batch vs stream per dimension                   │
│  └── Classify discrepancies by severity                      │
│       │                                                      │
│       ▼                                                      │
│  Correction Engine                                           │
│  └── Auto-correct < 5% discrepancies                         │
│  └── Halt + alert for > 5% discrepancies                     │
│  └── Write corrected values to ClickHouse serving layer      │
│       │                                                      │
│       ▼                                                      │
│  Audit Log + Reporting                                       │
│  └── Log every correction with before/after values           │
│  └── Generate daily reconciliation report                    │
│  └── Dashboard: Drift trends over time                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Timing and Dependencies

```
Timeline for a single day's reconciliation:

11:59 PM: Day "2025-03-15" ends (UTC)
12:00 AM: Last events still arriving (late events, timezone drift)
2:00 AM:  Late event window closes (2-hour buffer for stragglers)
3:00 AM:  Reconciliation job starts
3:00-5:00 AM: Spark processes 200B events
5:00-5:30 AM: Comparison and correction
5:30 AM:  Reconciliation complete
6:00 AM:  Corrected aggregates available for morning dashboards

Why 3 AM?
  1. Low traffic period (minimal new events arriving)
  2. After late event window (2-hour buffer captures 99.99% of late events)
  3. Before business hours (corrections visible when advertisers log in)
```

---

## Stream vs Batch Numbers — Two Different Truths

### Why Two Numbers?

```
Real-time dashboard (stream):
  Shows: ~2,312,000 clicks (updates every 30 seconds)
  Source: Flink streaming aggregation → Redis → Dashboard
  Accuracy: ±0.1% (good enough for display)
  Freshness: < 30 seconds old
  Label: "~2.3M clicks (real-time, approximate)"

Invoice / Billing (batch):
  Shows: 2,312,847 clicks (computed nightly)
  Source: Spark batch recomputation from ALL raw events
  Accuracy: Exact (required for billing)
  Freshness: T+1 (one day old)
  Label: "2,312,847 clicks (reconciled, authoritative)"
```

### Why They Must Be Separate

```
Problem scenario (if using stream numbers for billing):
  Dashboard shows 10,001 clicks
  Invoice charges for 10,003 clicks (batch found 2 more late events)
  → Advertiser: "Why am I being charged for 2 clicks not on my dashboard?"
  → Support ticket, trust erosion, potential dispute

Problem scenario (if using batch numbers for display):
  Dashboard shows yesterday's number (10,003 clicks)
  User refreshes → still shows 10,003
  → User: "My dashboard hasn't updated in 24 hours!"
  → Useless for real-time monitoring

Solution: Clearly separate the two:
  Dashboard: Stream numbers (fresh but approximate)
    Clearly labeled: "Real-time (approximate)"
    Use: Monitoring, quick checks, trend watching
    
  Invoice: Batch numbers (day-old but exact)
    Clearly labeled: "Reconciled (authoritative)"
    Use: Billing, financial reporting, compliance
    
  The one-day delay is inherent to billing anyway
  (invoices are always for completed periods)
```

### Stream-to-Batch Handoff

```
Dashboard evolution for a single day:

During March 15:
  Dashboard shows: Stream numbers (real-time, approximate)
  Source: Flink → Redis
  "~2.3M clicks today (live)"

After reconciliation (March 16, 5:30 AM):
  Dashboard switches to: Batch numbers (reconciled, exact)
  Source: Spark → ClickHouse (corrected)
  "2,312,847 clicks on March 15 (reconciled)"

The handoff is automatic:
  For "today": Always show stream numbers (live)
  For "yesterday and older": Always show batch numbers (reconciled)
  Transition happens at reconciliation completion time (~5:30 AM)
```

---

## Billing Reconciliation

### Requirements for Billing-Grade Accuracy

```
1. Exact counts (no sampling, no approximation)
   Each click = revenue. $0.50 CPC × 2,312,847 clicks = $1,156,423.50
   Even 0.1% error = $1,156 over/under-charged

2. Auditable (full trace from raw event to final count)
   Regulator asks: "How did you determine this advertiser owes $1,156,423.50?"
   Must trace: Raw click event → dedup → aggregation → invoice line item

3. Reproducible (same input → same output every time)
   Run the job twice with same input → identical output
   Deterministic processing (no random sampling, no non-deterministic ordering)

4. Timely (available by next business day)
   Advertisers expect daily billing reports by 9 AM local time
   Reconciliation must complete before morning reports are generated

5. Correctable (if a bug is found, rerun and correct)
   Raw events retained for 30+ days
   Can reprocess any day's events to generate corrected invoice
   Issue credit/debit adjustments for affected advertisers
```

### The Billing Pipeline

```
┌──────────────────────────────────────────────────────────────┐
│                  BILLING RECONCILIATION FLOW                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Raw Events (S3) — Source of truth                        │
│     All click events stored as immutable Parquet files       │
│     Retained for 90 days minimum (compliance requirement)    │
│                                                              │
│  2. Spark Batch Job — Exact aggregation                      │
│     GROUP BY (campaign_id, date)                             │
│     Apply exact dedup (no sampling)                          │
│     Apply click fraud detection                              │
│     Output: Exact billable clicks per campaign per day       │
│                                                              │
│  3. Comparison — Spot-check against streaming                │
│     If discrepancy > threshold → investigate before billing  │
│     If acceptable → proceed to billing                       │
│                                                              │
│  4. Invoice Generation                                       │
│     campaign_123: 2,312,847 clicks × $0.50 CPC = $1,156,423│
│     Detailed breakdown by ad group, hour, geo available      │
│                                                              │
│  5. Audit Trail                                              │
│     Every invoice line item traceable to raw events          │
│     Correction history preserved (original + corrected)      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Billing Correction Process

```
Scenario: Bug discovered in click dedup logic (deployed 5 days ago)

Day 0 (bug deployed):
  Dedup logic has a race condition → ~2% of clicks double-counted
  Stream and batch both use the buggy logic → both show inflated numbers
  Reconciliation doesn't catch it (both sources agree on the wrong number!)

Day 5 (bug discovered via customer complaint):
  Advertiser: "My click count seems 2% higher than expected"
  Investigation: Find the race condition in dedup code

Correction process:
  1. Fix the bug in consumer code (PR, review, deploy)
  2. Identify affected time range: Day 0 through Day 5 (5 days)
  3. Reprocess affected days:
     For each day D in [Day 0, Day 5]:
       Read raw events from S3 for day D
       Run CORRECTED Spark job (with fixed dedup logic)
       Compare corrected aggregates with original invoiced amounts
       Generate adjustment records
  4. Apply corrections:
     campaign_123, Day 0: Original = 2,312,847, Corrected = 2,266,590
     Difference: -46,257 clicks → credit $23,128.50 to advertiser
  5. Issue credits/debits to all affected advertisers
  6. Send communication: "We identified and corrected a billing discrepancy..."

Why raw events must be retained:
  Without raw events → can't reprocess → can't correct → permanent error
  With 90-day retention → any bug discovered within 90 days is correctable
```

---

## Reconciliation Architecture

### Multi-Level Reconciliation

```
Level 1: Intra-Pipeline Reconciliation (continuous)
  What: Compare counts at pipeline stages
  Where: Kafka producer count vs consumer count
  When: Continuous (every minute)
  Purpose: Detect pipeline issues in near-real-time
  
  Example:
    Kafka topic "clicks": 10,000,000 messages produced
    Consumer group "aggregator": 9,999,980 messages consumed
    Gap: 20 messages (0.0002%) — within tolerance ✓
    If gap > 0.1%: Alert pipeline team

Level 2: Stream vs Batch Reconciliation (daily)
  What: Compare streaming aggregates with batch-computed exact aggregates
  When: Daily at 3 AM UTC
  Purpose: Detect and correct drift between stream and batch
  
  Example:
    Stream: campaign_123 = 2,312,000 clicks
    Batch: campaign_123 = 2,312,847 clicks
    Discrepancy: 0.037% — auto-correct ✓

Level 3: Cross-System Reconciliation (daily/weekly)
  What: Compare data across different systems
  When: Daily for critical systems, weekly for others
  Purpose: Ensure all derived views match the source of truth
  
  Example:
    PostgreSQL (source): 10,000 orders
    Elasticsearch (search index): 9,998 orders indexed
    Gap: 2 orders missing from search → trigger re-index
    
    PostgreSQL (source): User profile updated at 14:30
    Redis (cache): User profile shows stale data from 14:00
    Gap: Cache not invalidated → trigger cache refresh

Level 4: Financial Reconciliation (monthly)
  What: Compare internal billing with payment processor records
  When: Monthly close
  Purpose: Ensure our books match Stripe/bank records
  
  Example:
    Our system: $1,156,423 charged to campaign_123 in March
    Stripe: $1,156,423 settled for campaign_123 in March
    Match ✓
```

---

## Discrepancy Detection and Classification

### Severity Levels

```
LEVEL 1 — Noise (< 0.1% discrepancy):
  Expected due to: Late events, clock skew, rounding
  Action: Auto-correct silently
  Alert: None (logged for audit)
  Frequency: 99.7% of dimensions fall here

LEVEL 2 — Warning (0.1% - 0.5% discrepancy):
  Possible causes: Elevated late events, brief backpressure sampling
  Action: Auto-correct, log warning
  Alert: Slack notification to #data-quality
  Frequency: ~0.2% of dimensions

LEVEL 3 — Investigation (0.5% - 5% discrepancy):
  Possible causes: Consumer bug, partition rebalance, dedup failure
  Action: Auto-correct, alert engineering team
  Alert: PagerDuty (low urgency), assign investigation ticket
  Frequency: ~0.05% of dimensions

LEVEL 4 — Critical (> 5% discrepancy):
  Possible causes: Major bug, data loss, pipeline failure
  Action: HALT billing for affected campaigns
  Alert: PagerDuty (high urgency), senior on-call
  Frequency: ~0.01% of dimensions (rare, but high impact)
```

### Discrepancy Patterns

```
Pattern 1: Uniform undercount across all dimensions
  Symptom: Every dimension is ~0.5% lower in stream than batch
  Cause: Likely late events (events arriving after window close)
  Fix: Increase stream window allowance OR adjust correction factor

Pattern 2: Spike in one specific time bucket
  Symptom: Hour 14:00 has 15% discrepancy, other hours are fine
  Cause: Consumer crash at 14:00, events reprocessed but some lost
  Fix: Replay from Kafka for that hour, re-aggregate

Pattern 3: One dimension consistently off
  Symptom: Campaign 789 always 3% higher in stream
  Cause: Possible click fraud (bot generating duplicate events)
  Fix: Investigate campaign 789 traffic, apply fraud filter

Pattern 4: All dimensions off by exactly the same amount
  Symptom: Every dimension stream = batch × 1.1 (exactly 10% higher)
  Cause: Bug — sampling correction factor applied incorrectly
  Fix: Fix the correction logic, reprocess
```

---

## Handling Discrepancies

### Correction Strategies

```
Strategy 1: Overwrite (most common)
  Stream value: 2,312,000
  Batch value:  2,312,847
  Action: UPDATE serving_table SET clicks = 2,312,847 WHERE ...
  Use when: Batch is authoritative, stream was approximate

Strategy 2: Merge (for late events)
  Stream missed 847 late events
  Action: Add the 847 events to the stream aggregate
  Final value: 2,312,000 + 847 = 2,312,847
  Use when: Stream is not wrong, just incomplete

Strategy 3: Invalidate and recompute
  Stream value is known to be corrupted (bug)
  Action: Delete stream aggregates, recompute from raw events
  Use when: Stream logic was fundamentally wrong (not just drift)

Strategy 4: Hold and investigate
  Discrepancy too large to auto-correct (> 5%)
  Action: Flag for human review, hold billing
  Use when: Potential data integrity issue, fraud, or major bug
```

### Automatic Correction Pipeline

```python
def reconcile(dimension, stream_value, batch_value):
    discrepancy = abs(stream_value - batch_value)
    discrepancy_pct = discrepancy / batch_value * 100
    
    correction = {
        "dimension": dimension,
        "stream_value": stream_value,
        "batch_value": batch_value,
        "discrepancy_pct": discrepancy_pct,
        "timestamp": now()
    }
    
    if discrepancy_pct < 0.1:
        # Level 1: Auto-correct silently
        apply_correction(dimension, batch_value)
        correction["action"] = "auto_corrected"
        correction["severity"] = "noise"
        
    elif discrepancy_pct < 0.5:
        # Level 2: Auto-correct with warning
        apply_correction(dimension, batch_value)
        correction["action"] = "auto_corrected"
        correction["severity"] = "warning"
        notify_slack("#data-quality", correction)
        
    elif discrepancy_pct < 5.0:
        # Level 3: Auto-correct, investigate
        apply_correction(dimension, batch_value)
        correction["action"] = "auto_corrected_pending_investigation"
        correction["severity"] = "investigation"
        create_ticket("Data Quality", correction)
        notify_pagerduty("low", correction)
        
    else:
        # Level 4: Halt billing, escalate
        correction["action"] = "halted_pending_review"
        correction["severity"] = "critical"
        halt_billing(dimension)
        notify_pagerduty("high", correction)
    
    log_audit_trail(correction)
```

---

## Kafka Replay for Bug Fixes

### When Replay Is Needed

```
Scenario: Bug in click dedup logic discovered 5 days after deployment

Without Kafka replay:
  Raw events after processing are gone
  Can't reprocess with corrected logic
  Must manually estimate the impact and adjust

With Kafka replay (30-day retention):
  All raw events still available in Kafka
  Reset consumer offset to 5 days ago
  Reprocess with corrected logic
  Generate corrected aggregates
  Diff against original → issue adjustments

Kafka retention strategy:
  Hot (Kafka): 30 days (fast replay, SSD storage)
  Cold (S3): 90 days (slow replay, cheap storage)
  Archive (Glacier): 7 years (compliance, very slow access)
```

### Replay Process

```
Step 1: Identify the bug and affected time range
  Bug introduced: 2025-03-10 14:00 UTC (deploy timestamp)
  Bug discovered: 2025-03-15 10:00 UTC
  Affected range: 5 days, 20 hours

Step 2: Deploy the fixed code (new consumer group)
  Consumer group: "click-aggregator-reprocess-v2"
  Start offset: 2025-03-10 14:00 UTC
  End offset: 2025-03-15 10:00 UTC (current)

Step 3: Reprocess events
  Spark job reads from Kafka (or S3 if beyond Kafka retention)
  Applies corrected dedup logic
  Generates corrected aggregates per (campaign, ad, hour)

Step 4: Compute adjustments
  For each dimension:
    original_value = clickhouse.query("SELECT clicks FROM aggregates WHERE ...")
    corrected_value = reprocess_result.clicks
    adjustment = corrected_value - original_value

Step 5: Apply corrections
  UPDATE aggregates SET clicks = corrected_value WHERE ...
  INSERT INTO adjustments (campaign_id, date, original, corrected, adjustment)

Step 6: Notify affected parties
  Generate credit/debit memos for affected advertisers
  Send communication explaining the correction
```

### Idempotent Replay

```
Critical: Replay must be idempotent (safe to run multiple times)

Problem: If replay inserts events that are already in the database → double counting!

Solution: Dedup by event_id
  Each raw event has a unique event_id (UUID)
  During replay: INSERT ... ON CONFLICT (event_id) DO NOTHING
  Already-processed events are silently skipped
  Only truly missing/incorrect events are (re)processed

Alternative: Write to a new table, then swap
  Reprocess → write to "aggregates_corrected" table
  Verify: Compare "aggregates_corrected" with "aggregates_original"
  If satisfied: RENAME TABLE aggregates TO aggregates_backup;
                RENAME TABLE aggregates_corrected TO aggregates;
  Atomic swap, easy rollback if correction is wrong
```

---

## Cross-System Reconciliation

### Source of Truth vs Derived Views

```
In a polyglot persistence architecture:

Source of truth: PostgreSQL (orders table)
Derived views:
  - Elasticsearch (search index for order search)
  - Redis (cached order details for API)
  - ClickHouse (aggregated order analytics)
  - DynamoDB (denormalized order for mobile API)

Each derived view can drift from the source of truth:
  CDC missed an update → Elasticsearch is missing an order
  Cache TTL not aligned with update frequency → Redis has stale data
  Flink processing lag → ClickHouse shows yesterday's aggregates
  DynamoDB write failed silently → mobile shows old order status

Cross-system reconciliation:
  Nightly job: For each order in PostgreSQL:
    Check: Does it exist in Elasticsearch? With correct fields?
    Check: Is the Redis cache consistent? (spot-check sample)
    Check: Is the ClickHouse aggregate correct?
    Check: Is the DynamoDB record current?
    
  For each discrepancy: Trigger re-sync (re-index, cache invalidate, re-aggregate)
```

### Implementation

```
Reconciliation for PostgreSQL → Elasticsearch:

Step 1: Count comparison
  pg_count = SELECT COUNT(*) FROM orders WHERE created_at >= '2025-03-15'
  es_count = GET /orders/_count?q=created_at:>=2025-03-15
  
  If pg_count != es_count → some orders missing from ES

Step 2: Sampling comparison (for large datasets)
  Sample 10,000 random order IDs from PostgreSQL
  For each: Check if ES has the order with correct fields
  If > 0.1% are missing or stale → trigger full re-index

Step 3: Full comparison (if sampling finds issues)
  Export all order IDs from PostgreSQL
  Export all order IDs from Elasticsearch
  Diff: Missing in ES, extra in ES, field mismatches
  For missing: Re-index from PostgreSQL
  For extra: Delete from ES (orphaned records)
  For mismatches: Update ES from PostgreSQL (source of truth wins)
```

---

## Audit Trail and Compliance

### What to Log

```
Every reconciliation correction must be logged:

{
  "reconciliation_id": "recon_2025-03-16_001",
  "dimension": {
    "campaign_id": 123,
    "ad_id": 456,
    "date": "2025-03-15",
    "hour": 14
  },
  "stream_value": 2312000,
  "batch_value": 2312847,
  "discrepancy": 847,
  "discrepancy_pct": 0.037,
  "severity": "noise",
  "action": "auto_corrected",
  "correction_applied": {
    "target": "clickhouse_serving",
    "old_value": 2312000,
    "new_value": 2312847
  },
  "corrected_at": "2025-03-16T05:15:00Z",
  "corrected_by": "reconciliation_job_v3.2",
  "raw_event_source": "s3://events/clicks/2025-03-15/",
  "spark_job_id": "spark-recon-2025-03-16-03:00:00"
}
```

### Compliance Requirements

```
SOX (Sarbanes-Oxley) for financial systems:
  - All billing data must be reconciled before invoicing
  - Corrections must be traceable to source events
  - Audit trail must be immutable (append-only log)
  - Reconciliation reports retained for 7 years

GDPR for user data:
  - Reconciliation must not expose PII unnecessarily
  - Aggregated corrections: OK (campaign-level counts)
  - Individual corrections: Must follow data access policies

Ad industry standards (MRC, IAB):
  - Click counting methodology must be documented
  - Discrepancy rates must be within industry benchmarks (< 5%)
  - Third-party auditors can request reconciliation reports
```

---

## Real-World System Examples

### Google Ads — Click Billing Reconciliation

```
Google's ad system processes billions of clicks per day.

Real-time: Streaming pipeline counts clicks for dashboard display
Billing: Nightly batch recomputes exact billable clicks

Additional: Click fraud detection (invalid click filtering)
  - Bot detection (suspicious patterns, known bot IPs)
  - Double-click filtering (accidental rapid clicks)
  - Competitor click detection (rival clicking your ads)
  
  Invalid clicks are subtracted from billable count
  "Your campaign received 100,000 clicks, 3,000 were invalid → 97,000 billable"

Reconciliation catches:
  - Clicks counted by stream but classified as invalid by batch
  - Clicks missed by stream (late events) but captured by batch
  - Clicks double-counted by stream but deduped by batch
```

### Stripe — Payment Reconciliation

```
Stripe reconciles across multiple systems:

Internal ledger: Stripe's database of payment records
Card network: Visa/Mastercard settlement records
Bank: Actual money movement records

Daily reconciliation:
  For each payment:
    Stripe says: $100 charged to card ending 4242
    Visa says: $100 authorized for merchant ID 12345
    Bank says: $100 deposited to merchant account
    
  All three must match. If not:
    Chargeback → money returned → all three adjusted
    Network error → payment retried → reconcile duplicates
    Bank delay → payment pending → reconcile on settlement
    
  Daily settlement: Sum of all transactions must match bank deposit
```

### Netflix — Streaming Analytics Reconciliation

```
Netflix reconciles streaming metrics:

Real-time: Flink processes playback events for real-time dashboards
  "1.2M people watching Stranger Things right now"

Batch: Spark reprocesses all events for accurate reporting
  "Yesterday, 3.47M unique viewers watched Stranger Things"

Content licensing depends on exact viewer counts:
  Netflix pays content creators based on viewing hours
  Must be exact → batch-reconciled numbers used for payments
  Real-time numbers used only for internal monitoring
```

---

## Deep Dive: Applying Reconciliation to Popular Problems

### Ad Click Aggregation

```
Stream: Flink counts clicks in real-time → Redis → Dashboard
Batch: Spark counts ALL clicks nightly → ClickHouse → Invoicing

Reconciliation:
  Compare per (campaign_id, ad_id, date, hour)
  Auto-correct stream values to match batch
  
  Special case: Fraud detection
    Batch applies more sophisticated fraud filters (ML model, behavioral analysis)
    Batch may REDUCE click count (removing fraudulent clicks)
    Stream shows raw count, batch shows filtered count
    Dashboard after reconciliation: Filtered count (lower, more accurate)
```

### E-Commerce Order Processing

```
Source of truth: PostgreSQL orders table
Derived views: ES (search), Redis (cache), analytics (ClickHouse)

Reconciliation points:
  1. Order count: PG vs ES (must match)
  2. Order status: PG vs Redis cache (must match)
  3. Revenue totals: PG SUM(amount) vs ClickHouse SUM(amount) (must match)
  4. Inventory: Order count × items vs inventory changes (must balance)

Nightly job:
  Count orders in PG → compare with ES → re-index missing orders
  Sample 1,000 orders → verify Redis cache → invalidate stale entries
  Sum revenue in PG → compare with ClickHouse → correct if needed
```

### Messaging System

```
Message delivery reconciliation:

Source of truth: Cassandra (all messages)
Real-time: Unread message count in Redis

Drift scenario:
  User reads a message → Redis decremented → Cassandra marked as read
  But: Redis operation fails (network issue) → count is off by 1
  
  User sees "3 unread" badge but only has 2 unread messages.

Reconciliation:
  Periodic job (every 30 minutes per user):
    actual_unread = COUNT(*) FROM messages WHERE user_id = X AND read = false
    cached_unread = GET unread_count:{user_id}
    If different: SET unread_count:{user_id} {actual_unread}
    
  Light reconciliation: Only active users (last login < 24 hours)
  Full reconciliation: All users, weekly
```

---

## Interview Talking Points & Scripts

### Daily Reconciliation

> *"I'd run a daily reconciliation job at 3 AM UTC. Spark reads all raw click events from S3 for the previous day — all 200 billion of them — and recomputes exact aggregates per campaign per hour. It then compares these with the streaming aggregates in ClickHouse. Any dimension with > 0.1% discrepancy gets auto-corrected. Typically, 99.8% of dimensions match exactly, and the 0.2% with corrections are due to late events or sampling during backpressure. For billing, we always use the batch-reconciled numbers — never the streaming approximations."*

### Stream vs Batch Separation

> *"I'd maintain two separate data paths. The real-time dashboard shows streaming aggregates — fresh but approximate, clearly labeled with a '~' prefix. The invoice uses batch-reconciled numbers — one day old but exact. An advertiser seeing '~10,001 clicks' on the dashboard but being billed for '10,003 clicks' would file a support ticket, so separating these with clear labels prevents confusion."*

### Kafka Replay for Corrections

> *"If a bug is discovered in our dedup logic, Kafka's 30-day retention lets us replay and reprocess affected events. We'd deploy the fixed code as a new consumer group, replay from the bug's deploy timestamp, generate corrected aggregates, and issue credit/debit adjustments to affected advertisers. The key is that raw events are immutable and retained — we can always reconstruct the correct state from the source data."*

### Cross-System Reconciliation

> *"With multiple derived views — Elasticsearch for search, Redis for caching, ClickHouse for analytics — data can drift from the PostgreSQL source of truth. I'd run nightly cross-system reconciliation: count comparison first (fast, catches missing records), then sampling comparison (catches field-level staleness), then targeted re-sync for any discrepancies found."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Using streaming numbers for billing
**Why it's wrong**: Streaming aggregates are approximate (sampling, late events, dedup gaps). Billing requires exact counts.
**Better**: Always use batch-reconciled numbers for billing. Streaming for display only.

### ❌ Mistake 2: Not mentioning reconciliation at all
**Why it's wrong**: In any eventually consistent system, data WILL drift. Without reconciliation, you'd never know.
**Better**: Design reconciliation as a first-class component, not an afterthought.

### ❌ Mistake 3: No correction mechanism when discrepancies are found
**Why it's wrong**: Detecting discrepancies is only half the job. You need to correct them automatically.
**Better**: Auto-correct for small discrepancies, investigate for large ones, halt billing for critical ones.

### ❌ Mistake 4: Not retaining raw events for replay
**Why it's wrong**: Without raw events, bugs can't be corrected retroactively. Data loss is permanent.
**Better**: Retain raw events in S3 for 90+ days, Kafka for 30 days. Enable replay for any bug fix.

### ❌ Mistake 5: Reconciling only at one level
**Why it's wrong**: Drift can happen at pipeline level, stream vs batch level, or cross-system level. Checking only one misses the others.
**Better**: Multi-level reconciliation — intra-pipeline (continuous), stream vs batch (daily), cross-system (daily), financial (monthly).

### ❌ Mistake 6: Not logging corrections for audit trail
**Why it's wrong**: Without an audit trail, you can't prove billing accuracy to regulators or advertisers.
**Better**: Log every correction with before/after values, source references, and timestamps. Immutable audit log.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               RECONCILIATION CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PURPOSE: Detect and correct drift between data sources      │
│                                                              │
│  TWO NUMBERS:                                                │
│    Dashboard: Stream numbers (fresh, ~approximate)           │
│    Invoice:   Batch numbers (T+1, exact, authoritative)      │
│                                                              │
│  DAILY BATCH PROCESS (3 AM UTC):                             │
│    Spark reads ALL raw events from S3                        │
│    Recomputes exact aggregates per dimension                 │
│    Compares with streaming aggregates                        │
│    Auto-corrects if discrepancy < 5%                         │
│    Halts + alerts if discrepancy > 5%                        │
│                                                              │
│  DISCREPANCY LEVELS:                                         │
│    < 0.1%:  Auto-correct silently (expected noise)           │
│    0.1-0.5%: Auto-correct + warning (elevated drift)         │
│    0.5-5%: Auto-correct + investigate (possible bug)         │
│    > 5%:   HALT billing + senior alert (critical issue)      │
│                                                              │
│  TYPICAL RESULTS:                                            │
│    99.8% of dimensions match exactly                         │
│    0.2% corrected (usually late events or sampling)          │
│                                                              │
│  MULTI-LEVEL RECONCILIATION:                                 │
│    Level 1: Pipeline (continuous) — producer vs consumer     │
│    Level 2: Stream vs Batch (daily) — approximate vs exact   │
│    Level 3: Cross-System (daily) — source vs derived views   │
│    Level 4: Financial (monthly) — internal vs bank/network   │
│                                                              │
│  RECOVERY: Kafka replay (30-day retention)                   │
│    Bug found → replay with fixed code → generate corrections │
│    Raw events in S3 (90-day) for extended replay             │
│                                                              │
│  AUDIT TRAIL:                                                │
│    Log every correction: before, after, source, timestamp    │
│    Immutable audit log (compliance, debugging)               │
│    Retain for 7 years (SOX compliance)                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Related Topics

- **Topic 22: Delivery Guarantees** — At-least-once delivery and its reconciliation implications
- **Topic 23: Batch vs Stream Processing** — Lambda architecture and reconciliation
- **Topic 29: Pre-Computation vs On-Demand** — Pre-computed values need reconciliation
- **Topic 30: Backpressure & Flow Control** — Sampling under backpressure requires reconciliation
- **Topic 38: Bloom Filters & Probabilistic DS** — Approximate structures need batch correction

---

*This document is part of the 44-topic System Design Interview Deep Dive series, based on the 200+ Comprehensive System Design Interview Talking Points.*
