# 🎯 Topic 44: Data Migration

> **System Design Interview — Deep Dive**
> A comprehensive guide covering zero-downtime data migration strategies, dual-write patterns, shadow reads, CDC-based migration, backfill processes, data validation, rollback strategies, and how to articulate migration decisions in system design interviews.

---

## Table of Contents

- [🎯 Topic 44: Data Migration](#-topic-44-data-migration)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Migration Strategies Overview](#migration-strategies-overview)
  - [Big Bang Migration (Offline)](#big-bang-migration-offline)
  - [Dual-Write Pattern (Online, Zero Downtime)](#dual-write-pattern-online-zero-downtime)
    - [The Four Phases](#the-four-phases)
    - [Dual-Write Challenges](#dual-write-challenges)
  - [CDC-Based Migration (Change Data Capture)](#cdc-based-migration-change-data-capture)
    - [The Preferred Pattern](#the-preferred-pattern)
    - [CDC Migration Timeline](#cdc-migration-timeline)
  - [Backfill Process](#backfill-process)
  - [Shadow Reads for Validation](#shadow-reads-for-validation)
  - [Data Validation and Reconciliation](#data-validation-and-reconciliation)
  - [Rollback Strategy](#rollback-strategy)
  - [Schema Migration vs Data Migration](#schema-migration-vs-data-migration)
  - [Real-World Migration Examples](#real-world-migration-examples)
    - [Amazon — DynamoDB Migrations](#amazon--dynamodb-migrations)
    - [Stripe — PostgreSQL Migrations](#stripe--postgresql-migrations)
    - [GitHub — MySQL to Vitess](#github--mysql-to-vitess)
  - [Interview Talking Points \& Scripts](#interview-talking-points--scripts)
    - [Migration Strategy](#migration-strategy)
    - [Why Not Dual-Write?](#why-not-dual-write)
  - [Common Interview Mistakes](#common-interview-mistakes)
    - [❌ Mistake 1: Big bang migration for production systems](#-mistake-1-big-bang-migration-for-production-systems)
    - [❌ Mistake 2: No validation phase](#-mistake-2-no-validation-phase)
    - [❌ Mistake 3: No rollback plan](#-mistake-3-no-rollback-plan)
    - [❌ Mistake 4: Forgetting the backfill](#-mistake-4-forgetting-the-backfill)
    - [❌ Mistake 5: Cutting over all traffic at once](#-mistake-5-cutting-over-all-traffic-at-once)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Data migration** is moving data from one storage system to another (PostgreSQL → DynamoDB, MySQL → Aurora, monolith DB → microservice DBs) while keeping the system running. The challenge is doing this with **zero downtime**, **zero data loss**, and a **safe rollback path**.

```
The fundamental problem:
  Old system: 500M rows in PostgreSQL, serving 50K QPS
  New system: DynamoDB (better scaling, lower latency)
  
  Can't just: 
    1. Stop the old system ❌ (downtime = lost revenue)
    2. Copy all data ❌ (takes hours, data changes during copy)
    3. Start new system ❌ (might have bugs, no rollback)
  
  Must:
    1. Keep old system running ✅
    2. Copy data incrementally ✅
    3. Validate new system matches old ✅
    4. Switch traffic gradually ✅
    5. Have instant rollback ✅
```

---

## Migration Strategies Overview

| Strategy | Downtime | Complexity | Risk | Best For |
|---|---|---|---|---|
| **Big Bang** | Hours | Low | High (no rollback) | Small datasets, maintenance windows OK |
| **Dual-Write** | Zero | High | Medium (consistency risk) | Active-active migration |
| **CDC-Based** | Zero | Medium | Low (proven pattern) | Large datasets, most migrations |
| **Blue-Green** | Seconds | Medium | Low (instant rollback) | Complete system replacement |

---

## Big Bang Migration (Offline)

```
The simplest but riskiest approach:

1. Announce maintenance window (e.g., Saturday 2-6 AM)
2. Stop writes to old system
3. Export all data from old system (pg_dump, SELECT INTO OUTFILE)
4. Transform data for new schema
5. Import into new system (COPY, BatchWriteItem)
6. Switch application to new system
7. Verify → go live

Pros: Simple, no dual-write complexity
Cons: Downtime required, no gradual rollout, rollback = repeat migration

When acceptable:
  Internal tools (maintenance window is OK)
  Small datasets (< 10 GB, migration takes minutes)
  New system with no existing users (greenfield)
```

---

## Dual-Write Pattern (Online, Zero Downtime)

### The Four Phases

```
Phase 1: DUAL-WRITE (writes go to both systems)
  Application writes to OLD system (primary) AND NEW system (secondary)
  Reads: Only from OLD system
  Duration: Until backfill complete + validation passes
  
  write(old_db, data)   // Primary (source of truth)
  try:
      write(new_db, data)   // Secondary (best effort)
  except:
      log_error()           // Don't fail the request if new system has issues
      enqueue_retry(data)   // Retry asynchronously

Phase 2: BACKFILL (copy historical data)
  Background job copies all existing data from OLD → NEW
  While dual-write continues for new data
  After backfill: NEW system has ALL data (historical + ongoing)

Phase 3: SHADOW READS (validate new system)
  Read from BOTH systems, compare results
  Return OLD system's response to user (safe)
  Log discrepancies for investigation
  
  result_old = read(old_db, query)
  result_new = read(new_db, query)
  if result_old != result_new:
      log_discrepancy(query, result_old, result_new)
  return result_old  // Always return old system's answer

Phase 4: CUTOVER (switch to new system)
  Reads: Switch to NEW system (gradually, 1% → 10% → 50% → 100%)
  Writes: Switch primary to NEW system
  OLD system: Keep running for rollback (read-only)
  After N days with no issues: Decommission OLD system
```

### Dual-Write Challenges

```
Challenge 1: Ordering
  Write A arrives at old_db at T=1, new_db at T=3
  Write B arrives at old_db at T=2, new_db at T=2.5
  → new_db sees B before A → different final state!
  
  Solution: Use event ordering (Kafka with partition key = entity_id)
  Or: Use CDC instead of dual-write (avoids ordering issues entirely)

Challenge 2: Partial failures
  Write succeeds on old_db, fails on new_db
  → Data inconsistency between systems
  
  Solution: Async reconciliation job compares both systems
  → Fix discrepancies by re-syncing from old_db (source of truth)

Challenge 3: Performance
  Every write now takes 2x as long (writing to two systems)
  
  Solution: Write to new_db asynchronously (Kafka → consumer → new_db)
  Trades consistency for performance (eventual consistency during migration)
```

---

## CDC-Based Migration (Change Data Capture)

### The Preferred Pattern

```
CDC avoids dual-write's ordering and consistency problems:

Old DB (PostgreSQL) → Debezium (reads WAL) → Kafka → Consumer → New DB (DynamoDB)

Steps:
  1. Deploy Debezium to capture changes from PostgreSQL WAL
  2. All INSERT/UPDATE/DELETE streamed to Kafka topic "db-changes"
  3. Consumer reads from Kafka → transforms → writes to DynamoDB
  4. Backfill: Snapshot old DB → load into DynamoDB (one-time)
  5. CDC catches up changes that happened during backfill
  6. Validate: Shadow reads compare old vs new
  7. Cutover: Switch reads to new DB, then writes

Advantages over dual-write:
  ✅ No application code changes for write path
  ✅ Ordering preserved (WAL is ordered)
  ✅ No partial failure risk (CDC is downstream of committed writes)
  ✅ Works for ANY downstream system (Elasticsearch, Redis, DynamoDB)
  
Disadvantages:
  ❌ Requires Debezium/Kafka infrastructure
  ❌ Slight lag (100ms-1s) between old and new DB
  ❌ Schema transformation logic needed in consumer
```

### CDC Migration Timeline

```
Day 1: Deploy Debezium + Kafka + consumer
  CDC starts streaming changes from PostgreSQL → DynamoDB
  Only NEW changes flowing (no historical data yet)

Day 2-5: Backfill historical data
  Spark job reads full PostgreSQL snapshot → writes to DynamoDB
  ~500M rows × 1KB = 500 GB → at 50K writes/sec = ~3 hours

Day 5-6: CDC catches up
  Events that happened during backfill are applied
  Eventually new DB is fully in sync with old DB

Day 7-14: Shadow reads + validation
  Compare responses: 99.99% match → investigate 0.01% discrepancies
  Fix bugs in transformation logic if needed

Day 15: Cutover reads (gradual)
  1% of reads → new DB (monitor latency, errors)
  10% → 50% → 100% over 3 days

Day 18: Cutover writes
  Application writes directly to new DB
  CDC can be reversed: New DB → Kafka → Old DB (for rollback)

Day 30: Decommission old DB
  Keep old DB read-only for 30 more days (safety net)
  Then decommission
```

---

## Backfill Process

```
Copy ALL historical data from old to new system:

Approach 1: Full table scan + batch write
  SELECT * FROM users LIMIT 10000 OFFSET {cursor}
  For each batch: Transform → BatchWriteItem to DynamoDB
  
  Problem: OFFSET is slow for large tables (O(N) per query)
  Better: Use cursor-based pagination
    SELECT * FROM users WHERE id > {last_id} ORDER BY id LIMIT 10000

Approach 2: pg_dump → S3 → Spark → DynamoDB
  pg_dump → upload to S3 as Parquet
  Spark reads Parquet → transforms → writes to DynamoDB
  Faster for very large datasets (parallelized across Spark cluster)

Approach 3: DMS (AWS Database Migration Service)
  Managed service: PostgreSQL → DynamoDB
  Handles full load + ongoing CDC
  Less control but much less operational overhead

Rate limiting during backfill:
  Don't overwhelm new system → rate limit writes
  DynamoDB: Provision enough WCU or use on-demand
  If backfill saturates new DB → production reads slow down
  
  Typical: Backfill at 50% of provisioned capacity
  Leave 50% for production traffic
```

---

## Shadow Reads for Validation

```
Read from both systems, compare results, return old system's answer:

async def get_user(user_id):
    # Primary read (returned to caller)
    result_old = await old_db.get(user_id)
    
    # Shadow read (for validation only, not returned)
    try:
        result_new = await new_db.get(user_id)
        compare_and_log(user_id, result_old, result_new)
    except Exception as e:
        log_shadow_error(user_id, e)
    
    return result_old  # Always return old system's answer

Compare and log:
  - Missing in new: Item exists in old but not new → backfill gap
  - Extra in new: Item in new but not old → stale data or bug
  - Field mismatch: Same item, different field values → transformation bug
  
  Dashboard: Track match rate over time
    Day 1: 95% match (backfill still running)
    Day 5: 99.9% match (some transformation bugs)
    Day 7: 99.99% match (bugs fixed)
    Day 10: 100% match → ready for cutover

Shadow read adds latency:
  If new_db is slow → shadow read times out → don't block main request
  Timeout: 100ms for shadow reads (fire-and-forget if slow)
```

---

## Data Validation and Reconciliation

```
Three levels of validation:

Level 1: Count comparison (fast)
  old_count = SELECT COUNT(*) FROM users
  new_count = DynamoDB Scan count
  If different → backfill missed records

Level 2: Sampling comparison (medium)
  Sample 10,000 random records from old DB
  For each: Compare with new DB record
  Report: Match rate, field-level mismatches

Level 3: Full comparison (slow but thorough)
  Export both systems → Spark job compares every record
  Report: Exact list of discrepancies
  Fix: Re-sync mismatched records from old → new

Continuous reconciliation during migration:
  Every hour: Run Level 1 + Level 2
  Daily: Run Level 3
  Alerts: If match rate drops below 99.9%
```

---

## Rollback Strategy

```
ALWAYS have a rollback plan:

During dual-write phase:
  Rollback = just stop writing to new DB, reads already from old DB
  Easy, instant, no data loss.

After cutover to new DB:
  Rollback = reverse CDC (new DB → Kafka → old DB)
  Or: Keep dual-write in reverse (new DB primary, old DB secondary)
  Switch reads back to old DB → instant rollback
  
  CRITICAL: Keep old DB in sync during cutover period
  Don't decommission old DB until new DB is proven stable (30+ days)

Feature flag for cutover:
  READ_FROM = config.get("read_source", "old")  # "old" or "new"
  WRITE_TO = config.get("write_target", "old")   # "old", "both", "new"
  
  Rollback: Change config → traffic returns to old DB within seconds
  No code deployment needed. Just a config change.
```

---

## Schema Migration vs Data Migration

```
Schema migration: Changing table structure within the same database
  ALTER TABLE users ADD COLUMN phone TEXT;
  ALTER TABLE orders ADD INDEX idx_status (status);
  
  Tools: Flyway, Liquibase, Alembic, Django migrations
  Risk: ALTER TABLE can lock the table → downtime
  Solution: Online schema change (pt-online-schema-change, gh-ost)

Data migration: Moving data between different systems
  PostgreSQL → DynamoDB
  Monolith DB → Microservice DBs
  
  Much more complex: Different schemas, different query patterns,
  different consistency models, different access patterns.

Microservice decomposition migration:
  Monolith DB has: users, orders, products, payments (all in one DB)
  
  Target:
    User service → DynamoDB (user profiles)
    Order service → PostgreSQL (ACID transactions)
    Product service → Elasticsearch (search)
    Payment service → PostgreSQL (financial ACID)
  
  Migration approach: One service at a time
    1. Migrate users first (simplest, read-heavy)
    2. Then products (add Elasticsearch in parallel)
    3. Then orders (most complex, ACID requirements)
    4. Then payments (last, most critical, needs thorough validation)
```

---

## Real-World Migration Examples

### Amazon — DynamoDB Migrations

```
Internal services frequently migrate between DynamoDB table designs:
  Old table: Single PK design
  New table: Composite PK + GSIs for new access patterns
  
  Approach: CDC (DynamoDB Streams → Lambda → new table)
  Backfill: Scan old table → BatchWriteItem to new table
  Cutover: Feature flag switches read/write target
  Duration: Typically 2-4 weeks for large tables
```

### Stripe — PostgreSQL Migrations

```
Stripe migrated billions of financial records between PostgreSQL schemas:
  Approach: Dual-write + shadow reads
  Validation: Every read compared (financial data = zero tolerance)
  Duration: Months of validation before cutover
  Rollback: Maintained both schemas simultaneously for weeks after cutover
```

### GitHub — MySQL to Vitess

```
GitHub migrated from MySQL to Vitess (sharded MySQL):
  Approach: Ghost (online schema migration tool) + CDC
  Traffic: Billion+ queries/day during migration
  Key challenge: Zero-downtime for millions of developers
  Duration: ~2 years for complete migration
```

---

## Interview Talking Points & Scripts

### Migration Strategy

> *"I'd use CDC-based migration with Debezium. Debezium reads the PostgreSQL WAL and streams every INSERT/UPDATE/DELETE to a Kafka topic. A consumer transforms and writes to DynamoDB. First, I'd backfill all historical data via a Spark job. Then CDC catches up changes that happened during backfill. I'd run shadow reads for 2 weeks — reading from both systems, comparing results, and always returning the old system's answer. Once match rate hits 99.99%, I'd gradually shift reads to the new system using a feature flag: 1% → 10% → 50% → 100%. Old DB stays running for 30 days as a rollback safety net."*

### Why Not Dual-Write?

> *"I'd prefer CDC over dual-write because CDC avoids the ordering and partial failure problems. With dual-write, if the write to the new DB fails, you have inconsistency. With CDC, the Kafka stream is downstream of committed PostgreSQL transactions — ordering is preserved and there are no partial failures. The application code doesn't change at all for the write path."*

---

## Common Interview Mistakes

### ❌ Mistake 1: Big bang migration for production systems
**Better**: CDC or dual-write for zero downtime. Big bang only for small/internal systems.

### ❌ Mistake 2: No validation phase
**Better**: Shadow reads + reconciliation for 2+ weeks before cutover.

### ❌ Mistake 3: No rollback plan
**Better**: Feature flag for instant cutback. Keep old DB in sync during and after cutover.

### ❌ Mistake 4: Forgetting the backfill
**Better**: CDC captures ongoing changes, but historical data needs a separate backfill job.

### ❌ Mistake 5: Cutting over all traffic at once
**Better**: Gradual rollout: 1% → 10% → 50% → 100%. Monitor errors and latency at each step.

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│                DATA MIGRATION CHEAT SHEET                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PREFERRED: CDC-based migration (Debezium → Kafka → New DB)  │
│  ALTERNATIVE: Dual-write (more complex, ordering issues)     │
│  SIMPLE: Big bang (downtime OK, small datasets only)         │
│                                                              │
│  PHASES:                                                     │
│    1. Deploy CDC pipeline (stream changes to new DB)         │
│    2. Backfill historical data (Spark/DMS → new DB)          │
│    3. Catch up (CDC processes changes during backfill)       │
│    4. Shadow reads (compare old vs new, 2+ weeks)            │
│    5. Cutover reads (gradual: 1% → 10% → 50% → 100%)       │
│    6. Cutover writes (switch primary to new DB)              │
│    7. Decommission old DB (after 30+ days of stability)      │
│                                                              │
│  VALIDATION:                                                 │
│    Count comparison (fast sanity check)                      │
│    Sampling comparison (10K random records)                  │
│    Full comparison (Spark diff of both systems)              │
│    Target: 99.99% match before cutover                       │
│                                                              │
│  ROLLBACK: Feature flag to switch back instantly.            │
│    Keep old DB in sync for 30+ days after cutover.           │
│                                                              │
│  DURATION: 2-6 weeks for typical migration                   │
│  RISK: LOW with CDC + shadow reads + gradual cutover         │
