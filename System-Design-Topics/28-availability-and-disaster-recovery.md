# 🎯 Topic 28: Availability & Disaster Recovery

> **System Design Interview — Deep Dive**
> 99.99% availability design, multi-region failover, graceful degradation, circuit breakers, retry with exponential backoff + jitter, and game day testing.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Availability Tiers (The Nines)](#availability-tiers-the-nines)
3. [Designing for 99.99% Availability](#designing-for-9999-availability)
4. [Graceful Degradation](#graceful-degradation)
5. [Circuit Breaker Pattern](#circuit-breaker-pattern)
6. [Retry with Exponential Backoff + Jitter](#retry-with-exponential-backoff--jitter)
7. [Multi-Region Active-Active](#multi-region-active-active)
8. [Failover Strategies](#failover-strategies)
9. [Health Checks & Automated Recovery](#health-checks--automated-recovery)
10. [Game Days & Chaos Engineering](#game-days--chaos-engineering)
11. [Blast Radius Limitation](#blast-radius-limitation)
12. [Interview Talking Points & Scripts](#interview-talking-points--scripts)
13. [Common Interview Mistakes](#common-interview-mistakes)
14. [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

**Availability** = uptime / (uptime + downtime). The goal is minimizing unplanned downtime through redundancy, automated failover, and graceful degradation.

---

## Availability Tiers (The Nines)

| Availability | Downtime/Year | Downtime/Month | Use Case |
|---|---|---|---|
| 99% (two 9s) | 3.65 days | 7.3 hours | Internal tools |
| 99.9% (three 9s) | 8.77 hours | 43.8 minutes | Standard web apps |
| **99.99% (four 9s)** | **52.6 minutes** | **4.38 minutes** | **Production services** |
| 99.999% (five 9s) | 5.26 minutes | 26.3 seconds | Financial, critical infra |

### Interview Script
> *"I'd design for 99.99% availability — that's 52.6 minutes of downtime per year. This requires: active-active deployment in at least 2 regions, automated health-check-driven failover (no human in the loop), no single point of failure, and graceful degradation for non-critical services."*

---

## Designing for 99.99% Availability

### Requirements
```
1. Active-active in 2+ regions (region failure → other region serves all traffic)
2. Automated failover (DNS switches in < 30 seconds, no human required)
3. No single point of failure (every component has redundancy)
4. Graceful degradation (non-critical services down → reduced but functional)
5. Database replication (RF=3 minimum, cross-region for DR)
6. Health checks (automatic detection and removal of unhealthy nodes)
7. Load balancing (distribute traffic, route away from failures)
```

### Architecture
```
Region US-East:                    Region EU-West:
┌──────────────────┐              ┌──────────────────┐
│ LB → App (3+)   │              │ LB → App (3+)   │
│ DB Master + 2 Rep│    ←sync→   │ DB Master + 2 Rep│
│ Redis (3-node)   │              │ Redis (3-node)   │
│ Kafka (3-broker) │              │ Kafka (3-broker) │
└──────────────────┘              └──────────────────┘
         ↑                                 ↑
         └────── DNS (Route 53) ───────────┘
              (latency-based + failover)
```

---

## Graceful Degradation

### The Principle
When a non-critical component fails, **degrade the experience, don't show an error page**.

```
Component Down:         Degraded Behavior:
Recommendation service → Show chronological feed (instead of ranked)
Search index stale     → Show results with "may not include latest posts" banner
Avatar service down    → Show default avatar placeholder
Analytics pipeline     → Dashboard shows "data may be delayed"
Notification service   → Notifications delayed, core app unaffected
ML ranking model       → Fall back to rule-based ranking
```

### Interview Script
> *"Graceful degradation is more valuable than raw uptime. If our recommendation ML model goes down, we don't show an error — we fall back to a chronological feed. If the search index is stale, users see results with a banner. Every component has a defined degradation behavior, and we test these failure modes monthly in game days."*

---

## Circuit Breaker Pattern

### How It Works
```
States:
  CLOSED:    Normal operation. Requests pass through.
  OPEN:      Dependency is down. Requests immediately return fallback.
  HALF-OPEN: Testing if dependency recovered. One test request.

Transitions:
  CLOSED → OPEN:      After 5 consecutive failures
  OPEN → HALF-OPEN:   After 30-second timeout
  HALF-OPEN → CLOSED: If test request succeeds
  HALF-OPEN → OPEN:   If test request fails (another 30-second timeout)
```

### Implementation
```python
class CircuitBreaker:
    def __init__(self, failure_threshold=5, recovery_timeout=30):
        self.state = "CLOSED"
        self.failure_count = 0
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.last_failure_time = None
    
    def call(self, func, fallback):
        if self.state == "OPEN":
            if time.now() - self.last_failure_time > self.recovery_timeout:
                self.state = "HALF_OPEN"
            else:
                return fallback()  # Don't even try
        
        try:
            result = func()
            if self.state == "HALF_OPEN":
                self.state = "CLOSED"
                self.failure_count = 0
            return result
        except Exception:
            self.failure_count += 1
            self.last_failure_time = time.now()
            if self.failure_count >= self.failure_threshold:
                self.state = "OPEN"
            return fallback()
```

### Interview Script
> *"The circuit breaker pattern is backpressure between synchronous services. If the Recommendation Service starts timing out, the circuit trips after 5 consecutive failures. For the next 30 seconds, the Feed Service doesn't call Recommendation at all — it immediately returns a chronological feed. After 30 seconds, one test request goes through. If it succeeds, the circuit closes. This prevents a slow dependency from dragging down the entire system."*

---

## Retry with Exponential Backoff + Jitter

### The Formula
```
delay = min(base_delay × 2^attempt + random(0, base_delay), max_delay)

Attempt 1: min(1 × 2^0 + rand(0,1), 60) = ~1-2 seconds
Attempt 2: min(1 × 2^1 + rand(0,1), 60) = ~2-3 seconds
Attempt 3: min(1 × 2^2 + rand(0,1), 60) = ~4-5 seconds
Attempt 4: min(1 × 2^3 + rand(0,1), 60) = ~8-9 seconds
Attempt 5: min(1 × 2^4 + rand(0,1), 60) = ~16-17 seconds
```

### Why Jitter Is Essential
```
Without jitter: 1000 failed requests all retry at exactly 1s, 2s, 4s, 8s
  → Synchronized retry storms that overwhelm the recovering service

With jitter: 1000 failed requests retry at random times
  → Spread over 0-2s, 0-4s, 0-8s → service can recover gradually
```

### Interview Script
> *"I'd implement retry with exponential backoff and jitter for all external service calls. Without jitter, all clients retry at exactly the same time, creating synchronized retry storms. The jitter randomizes retry timing. Without backoff, clients retry immediately and continuously, turning a brief outage into a sustained DDoS. Together, backoff + jitter is the difference between 'service recovers in 10 seconds' and 'cascading failure for 10 minutes'."*

---

## Multi-Region Active-Active

### Architecture
```
3 regions: us-east-1, eu-west-1, ap-southeast-1

Each region has:
  - Full stack (API, DB, cache, queues)
  - Local reads (15ms) 
  - Local writes (async replication to other regions, 200-500ms)

DNS routing:
  User in Paris  → eu-west-1 (15ms)
  User in Tokyo  → ap-southeast-1 (15ms)
  User in NYC    → us-east-1 (15ms)

Region failure:
  eu-west-1 down → DNS failover → EU users route to us-east-1
  Failover time: < 30 seconds (DNS TTL)
  Data loss window: 0-500ms of async replication lag
```

---

## Failover Strategies

### Active-Passive (Warm Standby)
```
Primary: Active, serving traffic
Standby: Warm, receiving replicated data, not serving traffic

On primary failure:
  1. Health check detects failure
  2. Promote standby to active
  3. DNS switch to standby
  Time: 30-60 seconds

Cost: Standby resources are underutilized (but ready)
```

### Active-Active
```
Both regions: Serving traffic simultaneously
On region failure: Other region absorbs all traffic

Time: < 30 seconds (DNS-based)
Cost: Higher (both regions at full capacity)
Benefit: No "warm-up" delay, instant failover
```

---

## Health Checks & Automated Recovery

### Multi-Level Health Checks
```
Level 1: TCP health check (is the port open?)
  Frequency: Every 5 seconds
  Detects: Process crash, network issue

Level 2: HTTP health check (GET /health → 200 OK)
  Frequency: Every 10 seconds
  Detects: Application errors, dependency issues

Level 3: Deep health check (GET /health/deep → checks DB, Redis, Kafka)
  Frequency: Every 30 seconds
  Detects: Dependency failures, data corruption
```

### Automated Recovery Actions
```
Unhealthy instance detected (3 consecutive failures):
  1. Remove from load balancer (stop sending traffic)
  2. Attempt restart
  3. If restart fails → terminate and launch new instance
  4. New instance passes health check → add to load balancer

Total recovery time: 30-90 seconds (automated, no human)
```

---

## Game Days & Chaos Engineering

### What Are Game Days?
```
Scheduled exercises where you intentionally cause failures:
  - Kill a database instance (does failover work?)
  - Block network between services (does circuit breaker activate?)
  - Spike CPU to 100% on app servers (does auto-scaling trigger?)
  - Simulate region failure (does DNS failover work?)

Goal: Verify that failure modes work as designed BEFORE a real incident.
Frequency: Monthly
```

### Chaos Engineering Tools
```
- Chaos Monkey (Netflix): Randomly kills instances in production
- Gremlin: Controlled failure injection
- AWS Fault Injection Simulator: AWS-native chaos testing
- Litmus Chaos: Kubernetes-native chaos
```

### Interview Script
> *"Every component has a defined degradation behavior, and we test these failure modes monthly in game days so they actually work when we need them."*

---

## Blast Radius Limitation

### Concept
Every failure should affect the **smallest possible scope**.

```
Single server failure:  Affects 1/N of traffic → LB routes to healthy servers
Single AZ failure:      Affects ~33% → other AZs absorb traffic
Single region failure:  Affects ~33% → other regions absorb traffic  
Single service failure: Affects one feature → degraded, not down
Database failure:       Affects one shard → other shards unaffected

Design principle: Every boundary (server, AZ, region, service) 
is a failure boundary with a defined degradation strategy.
```

### Interview Script
> *"The blast radius of this failure is intentionally limited. If the Recommendation Service crashes, the Timeline Service returns a chronological feed instead. If the Search Service has stale indexes, users see slightly outdated results. Every service boundary is a failure boundary — and each boundary has a defined degradation strategy. The core product works even if 3 out of 5 dependent services are down."*

---

## Interview Talking Points & Scripts

### 99.99% Design
> *"Active-active in 2+ regions, automated failover in < 30 seconds, no SPOF, graceful degradation for every non-critical component."*

### Circuit Breaker
> *"If Recommendation times out 5 times, the circuit opens — Feed returns chronological order for 30 seconds. After 30s, one test request. If it succeeds, circuit closes."*

### Retry + Backoff + Jitter
> *"Exponential backoff spreads retries over time. Jitter randomizes to prevent synchronized retry storms. Without both → brief outage becomes cascading failure."*

---

## Common Interview Mistakes

### ❌ Not mentioning graceful degradation (just "99.99% uptime" without the how)
### ❌ No circuit breaker for synchronous dependencies
### ❌ Retry without backoff or jitter (causes retry storms)
### ❌ Single region deployment for high-availability requirements
### ❌ Not mentioning game days / chaos testing

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│       AVAILABILITY & DISASTER RECOVERY CHEAT SHEET           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  99.99% = 52 min downtime/year                               │
│  Requires: multi-region, auto-failover, no SPOF              │
│                                                              │
│  GRACEFUL DEGRADATION: Degrade feature, not entire app       │
│    Recommendation down → chronological feed                  │
│    Search stale → "results may not include latest" banner    │
│                                                              │
│  CIRCUIT BREAKER: 5 failures → open → 30s timeout            │
│    → half-open → test → closed or re-open                    │
│    Prevents slow dependency from cascading                   │
│                                                              │
│  RETRY: Exponential backoff + jitter                         │
│    delay = min(base × 2^attempt + random, max)               │
│    Prevents synchronized retry storms                        │
│                                                              │
│  MULTI-REGION: Active-active, DNS failover < 30s             │
│  HEALTH CHECKS: TCP (5s), HTTP (10s), Deep (30s)             │
│  CHAOS TESTING: Monthly game days, verify failure modes      │
│  BLAST RADIUS: Every boundary is a failure boundary          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

*This document is part of the 44-topic System Design Interview Deep Dive series.*
