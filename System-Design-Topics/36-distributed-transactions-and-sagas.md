# 🎯 Topic 36: Distributed Transactions & Sagas

> **System Design Interview — Deep Dive**
> 2PC problems, Saga pattern (orchestration vs choreography), compensating transactions, and how to maintain data consistency across microservices without ACID.

---

## Core Concept

In microservices, there's no shared database → no cross-service ACID transactions. Booking a ticket requires coordinating across Booking Service, Payment Service, and Notification Service — each with its own DB.

## Why 2PC Fails in Microservices

```
Two-Phase Commit (2PC):
  Phase 1 (Prepare): Coordinator asks all participants "can you commit?"
  Phase 2 (Commit):  Coordinator tells all "commit" or "abort"

Problem: If coordinator crashes between Phase 1 and Phase 2:
  All participants hold locks indefinitely → frozen resources
  In a system with 99.99% uptime → coordinator crashes ~50 min/year
  Each crash potentially freezes thousands of in-flight transactions
```

## Saga Pattern — The Solution

### Each step has a compensating action (undo):

```
Forward flow:
  Step 1: Reserve seat      → success
  Step 2: Charge payment    → success  
  Step 3: Confirm booking   → success
  Step 4: Send confirmation → success

Failure at Step 2:
  Step 2: Charge payment    → FAIL
  Compensate Step 1: Release seat (undo reservation)
  Return error to user

Every step must be:
  - Idempotent (safe to retry)
  - Have a compensating action (reversible)
```

### Orchestration vs Choreography

| Aspect | Orchestration | Choreography |
|---|---|---|
| **Control** | Central orchestrator drives flow | Each service listens for events |
| **Visibility** | One place to see full flow | Must correlate events across services |
| **Debugging** | Easy (look at orchestrator logs) | Hard (trace events across 5 services) |
| **Coupling** | Orchestrator knows all services | Services don't know each other |
| **Best for** | Complex multi-step flows | Simple event-driven reactions |

### Interview Script
> *"I'd use the Saga pattern: reserve seats → charge payment → confirm booking → issue tickets. Each step is a local transaction. If payment fails, we execute compensating actions in reverse: release the reserved seats. I'd use orchestration over choreography for this booking saga. One service (the Booking Orchestrator) drives the entire flow. The alternative — choreography — is more decoupled but nearly impossible to debug."*

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│       DISTRIBUTED TRANSACTIONS & SAGAS CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│  2PC: Theoretically correct, practically fragile. AVOID.     │
│                                                              │
│  SAGA: Sequence of local transactions + compensations        │
│    Forward: reserve → charge → confirm → notify              │
│    Compensate: release → refund → cancel → (no-op)           │
│    Every step: idempotent + has compensating action           │
│                                                              │
│  ORCHESTRATION: Central coordinator drives flow (PREFERRED)  │
│    Easy to debug, one place to see full lifecycle             │
│  CHOREOGRAPHY: Event-driven, each service reacts             │
│    Decoupled but hard to debug                               │
│                                                              │
│  TRANSACTIONAL OUTBOX: Business data + event in same DB txn  │
│    CDC publishes to Kafka atomically                         │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
