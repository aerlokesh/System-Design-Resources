# 🎯 Topic 42: Data Migration

> **System Design Interview — Deep Dive**
> Zero-downtime migration (MySQL → DynamoDB), 4-phase approach (dual-write, backfill, shadow-read, cutover), and common pitfalls.

---

## The 4-Phase Zero-Downtime Migration

```
Phase 1: DUAL-WRITE (1-2 days)
  Application writes to BOTH old (MySQL) and new (DynamoDB) databases.
  Reads still from MySQL (source of truth).
  Goal: New DB starts receiving live data.

Phase 2: BACKFILL (days-weeks)
  Batch job migrates all existing MySQL data to DynamoDB.
  Runs at low priority to avoid impacting production.
  For 1 billion rows: 1-3 weeks depending on throughput.

Phase 3: SHADOW-READ (1-2 weeks)
  For 10% of traffic: Read from BOTH databases, compare results.
  Log discrepancies (don't affect response — serve from MySQL).
  Fix issues: timestamp precision, null vs empty string, encoding.
  Re-run backfill for affected records.

Phase 4: CUTOVER (gradual)
  Feature flag shifts reads: MySQL → DynamoDB
    1% → 10% → 50% → 100%
  Monitor error rates, latency, discrepancies at each step.
  If issues at any step → flip flag back to MySQL (instant rollback).
  After 100% stable for 1 week → stop dual-write → decommission MySQL.

Total timeline: 2-6 weeks for a 1-billion-row table.
```

## The Shadow-Read Phase (Most Important)

```
For 10% of requests:
  result_mysql = mysql.query(key)
  result_dynamo = dynamodb.get(key)
  
  if result_mysql != result_dynamo:
      log_discrepancy(key, result_mysql, result_dynamo)
  
  return result_mysql  # Always serve from old DB during shadow phase

Common discrepancies found:
  - Timestamp precision: MySQL rounds to seconds, DynamoDB stores milliseconds
  - Null handling: MySQL NULL vs DynamoDB missing attribute
  - Character encoding: UTF-8 normalization differences
  - Decimal precision: DECIMAL(10,2) vs DynamoDB Number
```

## Interview Script
> *"For zero-downtime migration from MySQL to DynamoDB, I'd use a 4-phase approach. Phase 1: dual-write to both databases. Phase 2: backfill existing data. Phase 3: shadow-read — compare results from both databases, fix discrepancies. Phase 4: gradual cutover via feature flag (1% → 10% → 50% → 100%). If issues arise at any phase, we roll back instantly by flipping the flag."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│               DATA MIGRATION CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│  Phase 1: Dual-Write (writes to both old + new DB)           │
│  Phase 2: Backfill (migrate existing data, low priority)     │
│  Phase 3: Shadow-Read (compare results, fix discrepancies)   │
│  Phase 4: Cutover (feature flag: 1% → 10% → 50% → 100%)     │
│                                                              │
│  Each phase: Independently reversible                        │
│  Shadow-read: The most important phase (catches bugs)        │
│  Timeline: 2-6 weeks for billion-row tables                  │
│  Rollback: Flip feature flag → instant (< 1 second)          │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
