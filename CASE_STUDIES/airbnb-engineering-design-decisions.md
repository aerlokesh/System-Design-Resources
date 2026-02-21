# Airbnb Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Airbnb's engineering blog, conference talks, and open-source projects. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Airbnb Engineering Blog (medium.com/airbnb-engineering), QCon/Strange Loop talks, Airbnb open-source projects

---

## Table of Contents

1. [Monolith to SOA — "The Big Migration"](#1-monolith-to-soa)
2. [Search Ranking — Personalized Search at Scale](#2-search-ranking)
3. [Pricing & Dynamic Pricing (Smart Pricing)](#3-dynamic-pricing)
4. [Payments — Multi-Currency Global Payments](#4-payments)
5. [Chronon — Feature Store for ML](#5-chronon-feature-store)
6. [Service Discovery — SmartStack](#6-smartstack)
7. [Idempotency Framework — Orpheus](#7-orpheus-idempotency)
8. [Data Platform — Minerva (Metrics Store)](#8-minerva-metrics)
9. [Image Classification & Quality Scoring](#9-image-quality)
10. [Availability Calendar — Distributed Booking System](#10-availability-booking)
11. [Messaging — Host-Guest Communication](#11-messaging)
12. [Observability — Detection & Alerting](#12-observability)

---

## 1. Monolith to SOA — "The Big Migration"

**Blog Post**: "Building Services at Airbnb" + "The Great Migration"

### Problem
Airbnb's Ruby on Rails monolith grew to millions of lines of code. Deployment took hours, any bug could crash everything, and 100+ engineers were stepping on each other's changes. Monolith test suite took 30+ minutes.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Service-Oriented Architecture (SOA) — ~500 services | Keep monolith, pure microservices | SOA: larger services than microservices; fewer network hops; clearer boundaries |
| **Migration strategy** | Strangler fig: extract services one by one from monolith | Big-bang rewrite | Continuous feature delivery during migration; no multi-year rewrite freeze |
| **Communication** | Thrift RPC (internal) + REST (external) | All REST, gRPC | Thrift was the industry standard when Airbnb migrated (pre-gRPC); strong typing |
| **Language** | Java (new services) + Ruby (legacy monolith) + JavaScript (frontend) | Single language | Java for performance-critical services; keep Ruby for existing code |
| **Data ownership** | Each service owns its database (no shared DB) | Shared database | Eliminates coupling; services can evolve independently |
| **API gateway** | Custom API gateway (aggregates multiple service calls for client) | Direct service calls from client | Mobile clients need one call, not 10; gateway aggregates backend services |

### Tradeoff
- ✅ Independent deployment: teams deploy services without coordinating
- ✅ Independent scaling: search scales differently from booking
- ✅ Strangler fig: no feature freeze during migration
- ❌ Multi-year migration (not instant — took ~3 years)
- ❌ Network latency between services (was zero in monolith)
- ❌ Distributed tracing, monitoring complexity increased
- ❌ Two languages (Java + Ruby) during transition

### Interview Use
> "Airbnb migrated from a Ruby monolith to ~500 SOA services using the strangler fig pattern — extracting one service at a time while continuing feature development. Each service owns its database. An API gateway aggregates multiple service calls into a single response for mobile clients. The migration took ~3 years but never required a feature freeze."

---

## 2. Search Ranking — Personalized at Scale

**Blog Post**: "Machine Learning-Powered Search Ranking of Listings" + "Chronon: Airbnb's ML Feature Platform"

### Problem
Airbnb search must rank listings by how likely a guest is to book AND have a good experience. Unlike Google (rank by relevance), Airbnb must consider: price sensitivity, location, amenities, host response rate, guest preferences, listing quality, and trip context (solo vs family, business vs leisure).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Model** | Gradient boosted trees (XGBoost) → later deep learning (neural networks) | Simple scoring rules | ML considers 100+ features; rules can't capture complex interactions |
| **Two-sided marketplace** | Optimize for both guest satisfaction AND host satisfaction | Optimize for guest only | If hosts have bad experiences, they leave → fewer listings → worse for guests |
| **Ranking stages** | Two-stage: cheap candidate retrieval → expensive ML ranking | Single-stage | Can't run neural network on all 7M+ listings; filter to ~1000 candidates, rank top 100 |
| **Features** | Guest features + listing features + context features + interaction features | Listing quality only | Guest-listing interaction features (e.g., "does this guest usually book this type of listing?") are most predictive |
| **Training data** | Bookings (positive) + non-bookings (negative) + cancellations + reviews | Bookings only | Cancellations and bad reviews are negative signals — a booked trip that leads to a bad review is a bad ranking |
| **Online vs offline** | Offline daily training + online feature freshness | Offline only | Daily model captures long-term patterns; online features (current price, availability) need to be real-time |

### Tradeoff
- ✅ ML ranking dramatically improves booking rate and guest satisfaction
- ✅ Two-sided optimization keeps both guests and hosts happy
- ✅ Two-stage pipeline makes neural network ranking feasible at 7M+ listings
- ❌ ML is a black box — harder to explain "why is this listing ranked #1?"
- ❌ Cold-start problem for new listings (no booking history)
- ❌ Model retraining and feature pipeline are expensive to maintain

### Interview Use
> "For a two-sided marketplace search (like Airbnb), I'd optimize for BOTH sides — guest satisfaction AND host quality. Two-stage pipeline: cheap retrieval (filter 7M listings to ~1000 candidates) → expensive ML ranking (neural network on top 100). Training on bookings + cancellations + reviews — a booking that leads to a bad review is a negative signal. Airbnb's ML ranking considers 100+ features including guest-listing interaction signals."

---

## 3. Dynamic Pricing (Smart Pricing)

**Blog Post**: "Learning Market Dynamics for Optimal Pricing"

### Problem
Most Airbnb hosts set static prices, but demand varies hugely by season, day of week, local events, and market conditions. Hosts who price too high get no bookings; hosts who price too low leave money on the table.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Approach** | Suggest optimal price per night (host can accept/override) | Force dynamic pricing | Hosts want control; suggested pricing with override maintains trust |
| **Model** | Demand prediction + supply modeling + elasticity estimation | Fixed rules (weekday = X, weekend = Y) | ML captures complex patterns: local events, seasonality, competitive pricing, historical demand |
| **Features** | Listing attributes, location, season, day-of-week, local events, competitor prices, booking lead time | Time + location only | More features = more accurate pricing; events (concerts, conferences) cause huge demand spikes |
| **Update frequency** | Daily price suggestions (host notified of changes) | Set once, never update | Demand changes daily; stale prices hurt both hosts and guests |
| **Personalization** | Per-listing price (not per-market average) | Market-level pricing | A luxury penthouse and a shared room in the same market have different demand curves |

### Tradeoff
- ✅ Hosts earn 5-15% more with Smart Pricing (data-driven)
- ✅ More bookings (correctly priced listings book faster)
- ✅ Better guest experience (prices match demand, less overpricing)
- ❌ Hosts may distrust AI pricing ("why did it lower my price?")
- ❌ Dynamic pricing is complex (demand forecasting is hard)
- ❌ Local events are difficult to capture comprehensively

### Interview Use
> "For dynamic pricing in a marketplace, I'd predict demand per listing per day using ML — considering seasonality, events, competitor prices, and listing attributes. Suggest price to the host (not force — maintain trust). Airbnb's Smart Pricing increases host earnings 5-15% by matching price to demand. The key: personalized per-listing pricing, not market averages."

---

## 4. Payments — Multi-Currency Global System

**Blog Post**: "Payments at Airbnb" + "Scaling Airbnb's Payment Platform"

### Problem
Airbnb operates in 220+ countries with 70+ currencies. A guest in Japan pays in JPY, Airbnb takes a fee, and the host in France receives EUR. Must handle currency conversion, regulatory compliance per country, and multiple payment methods.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Ledger** | Double-entry bookkeeping with multi-currency support | Simple transaction log | Every money movement has debit + credit; must balance in every currency |
| **Currency** | Store in original currency + convert at payment time (not storage time) | Convert everything to USD | Storing in original currency avoids conversion rate fluctuations; convert at settlement |
| **Payout timing** | Pay host 24 hours after guest check-in (not at booking) | Pay at booking | Protects against cancellations; host gets paid when service is delivered |
| **Payment methods** | 20+ methods (cards, bank transfer, PayPal, Alipay, local methods) | Cards only | Different countries prefer different methods; must support local preferences |
| **Fraud** | ML-based fraud detection per transaction | Rules only | ML detects cross-border fraud patterns; rules handle known patterns |
| **Consistency** | Strong (ACID) for all financial operations | Eventual consistency | Financial data: zero tolerance for inconsistency |

### Money Flow
```
Guest books in JPY:
  1. Guest charged ¥15,000 (booking amount + service fee)
  2. Airbnb ledger: DEBIT guest ¥15,000, CREDIT Airbnb ¥15,000
  3. Guest checks in (T+0)
  4. 24 hours after check-in (T+1):
     a. Convert host payout: ¥13,000 → €85 (at current rate)
     b. Airbnb keeps: ¥2,000 service fee
     c. DEBIT Airbnb €85, CREDIT host €85
  5. Host receives €85 via bank transfer
```

### Tradeoff
- ✅ Multi-currency support enables 220+ countries
- ✅ 24-hour hold protects against cancellations
- ✅ Double-entry ensures every currency balances
- ❌ Currency conversion adds complexity and FX risk
- ❌ 20+ payment methods = 20+ integrations to maintain
- ❌ Regulatory compliance differs per country (tax, money transmission laws)

### Interview Use
> "For a global payment system, I'd store amounts in the original currency (not convert to USD at storage time) and convert at settlement time. Double-entry bookkeeping with multi-currency entries ensures every currency balances. Airbnb pays hosts 24 hours after guest check-in (not at booking) to protect against cancellations. The lesson: in multi-currency systems, delay conversion as long as possible to avoid FX risk."

---

## 5. Chronon — Feature Store for ML

**Blog Post**: "Chronon: Airbnb's ML Feature Platform"

### Problem
Airbnb has 100+ ML models (search ranking, pricing, fraud, image quality). Each model needs features (user engagement history, listing attributes, market signals). Teams were computing the same features independently — wasting resources and creating inconsistencies.

### What They Built
**Chronon** — a unified feature computation and serving platform. Compute features once, serve to all models.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Computation** | Unified platform: define features once, compute for batch + stream | Each team computes independently | Eliminate redundant computation; ensure consistency across models |
| **Temporal correctness** | Point-in-time correct features (no data leakage) | Ad-hoc feature computation | Training features must reflect what was known at prediction time — future data must not leak into training |
| **Serving** | Low-latency key-value store (< 10ms) | Compute at inference time | Pre-computed features served from fast store; can't afford feature computation during ranking |
| **Backfill** | Can backfill historical features for training | Training features from live serving | Need historical features for training data; backfill computes features at every historical point in time |
| **Consistency** | Same feature definition for training and serving | Separate training and serving pipelines | Training-serving skew causes model degradation; unified definition prevents this |

### Tradeoff
- ✅ Features computed once, shared across 100+ models
- ✅ Point-in-time correctness prevents training data leakage
- ✅ Training-serving consistency (same feature definition)
- ❌ Platform overhead (building and maintaining Chronon)
- ❌ Feature computation latency for complex features
- ❌ Schema management across teams

### Interview Use
> "For ML at scale (100+ models), I'd use a centralized feature store — like Airbnb's Chronon. Features are defined once and computed for both batch (training) and stream (serving). Point-in-time correctness prevents data leakage in training. Training-serving consistency is guaranteed because both use the same feature definition. This eliminates redundant computation and prevents the #1 cause of model degradation: training-serving skew."

---

## 6. SmartStack — Service Discovery

**Blog Post**: "SmartStack: Service Discovery in the Cloud"

### Problem
With hundreds of services, each service needs to find and connect to its dependencies. IPs change as services scale up/down and instances are replaced. Need real-time service discovery without a single point of failure.

### What They Built
**SmartStack** — a decentralized service discovery system using two sidecars: **Nerve** (registration) and **Synapse** (discovery).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Sidecar-based (each host runs Nerve + Synapse) | Centralized service registry (Consul, Eureka) | Decentralized; no single point of failure; each host makes independent decisions |
| **Registration** | Nerve: each service instance registers itself in ZooKeeper | Central registration | Self-registration: the instance knows best if it's healthy |
| **Discovery** | Synapse: reads ZooKeeper, configures local HAProxy | DNS-based discovery | HAProxy provides local load balancing; no DNS TTL caching issues |
| **Health check** | Local health checks (not centralized) | Central health checker | Each host checks its own services; no centralized health check bottleneck |
| **Load balancing** | Client-side (HAProxy on each host) | Server-side (ELB) | Client-side: no extra network hop; each client has its own LB |

### Tradeoff
- ✅ Decentralized: no single point of failure for service discovery
- ✅ Local HAProxy: load balancing without extra network hop
- ✅ Self-registration: services manage their own lifecycle
- ❌ ZooKeeper dependency (Airbnb later migrated to other solutions)
- ❌ HAProxy config generation adds complexity
- ❌ Industry has moved to Kubernetes service discovery / Istio / Envoy

### Interview Use
> "For service discovery, Airbnb's SmartStack used a sidecar pattern: each host runs a local proxy (HAProxy) that routes to healthy instances. Registration via ZooKeeper, health checks are local. The lesson: client-side load balancing (sidecar proxy) eliminates the extra network hop of server-side load balancers. Modern equivalent: Envoy sidecar in Istio/Kubernetes."

---

## 7. Orpheus — Idempotency Framework

**Blog Post**: "Avoiding Double Payments in a Distributed Payments System"

### Problem
Payment operations at Airbnb can be retried due to network timeouts, service restarts, and message redelivery. Charging a guest twice or paying a host twice is unacceptable.

### What They Built
**Orpheus** — an idempotency framework that makes any operation safely retriable.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Key** | Deterministic idempotency key derived from operation parameters | Random UUID per request | Deterministic key means the SAME operation always produces the SAME key — retries naturally idempotent |
| **Storage** | MySQL table: (idempotency_key, status, response) | Redis (fast but less durable) | Financial data needs durability; MySQL is the permanent record |
| **Response** | Store complete response; return on retry | Return error on retry | Returning the original response makes retries transparent to the caller |
| **Scope** | Framework-level (all services use Orpheus) | Per-service implementation | Consistent idempotency across all payment services; no team implements it differently |
| **Concurrency** | Database unique constraint on idempotency_key | Application-level lock | DB constraint is the final guard; even concurrent requests don't create duplicates |

### Tradeoff
- ✅ Framework-level: all services get idempotency without implementing it
- ✅ Deterministic keys: same operation → same key (natural idempotency)
- ✅ Transparent: caller doesn't know if response is original or cached retry
- ❌ Every payment operation stores its full response (storage overhead)
- ❌ MySQL lookup on every request (mitigated by index)
- ❌ Framework adds a dependency to every payment service

### Interview Use
> "For payment idempotency, I'd use deterministic idempotency keys — derived from operation parameters, not random UUIDs. This means the same operation naturally produces the same key, making retries inherently safe. Store the complete response in MySQL; return it on retry (transparent to caller). Airbnb's Orpheus framework provides this for all payment services — no team implements idempotency differently."

---

## 8. Minerva — Metrics Store

**Blog Post**: "How Airbnb Standardized Metric Computation with Minerva"

### Problem
Different teams computed the same business metric (e.g., "booking rate") differently — using different filters, time windows, and definitions. A VP would see different "booking rate" numbers from search team vs marketing team.

### What They Built
**Minerva** — a centralized metric definition and computation platform. Each metric is defined once, computed consistently, and served to all consumers.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Single source of truth** | One definition per metric (YAML config) | Each team computes independently | "Booking rate" means exactly the same thing everywhere — no conflicting numbers |
| **Computation** | Centralized Spark/Airflow pipeline | Per-team SQL queries | Consistent computation; changes to a metric definition propagate to all consumers |
| **Serving** | Pre-computed + cached (Druid for real-time, Hive for batch) | Compute on demand | Dashboards load in seconds, not minutes |
| **Certification** | Metrics are reviewed and certified before use | Anyone can create metrics | Certified metrics are trusted; uncertified are experimental |
| **Dimensions** | Standardized dimensions (country, platform, market segment) | Ad-hoc dimensions | Consistent slicing across all metrics; "compare booking rate by country" works the same everywhere |

### Tradeoff
- ✅ Single source of truth: no conflicting metric definitions
- ✅ Pre-computed: dashboards load in seconds
- ✅ Certified metrics build organizational trust in data
- ❌ Centralized ownership can bottleneck metric creation
- ❌ Changing a metric definition affects all consumers (careful migration needed)
- ❌ Building and maintaining the platform is a significant investment

### Interview Use
> "For consistent business metrics, I'd use a centralized metrics store — like Airbnb's Minerva. Each metric (e.g., 'booking rate') is defined once in YAML, computed via a centralized pipeline, and served to all dashboards. No team computes it differently. Certified metrics are reviewed before use. The #1 problem Minerva solves: different teams reporting different numbers for the same metric."

---

## 9. Image Classification & Quality Scoring

**Blog Post**: "Categorizing Listing Photos at Airbnb"

### Problem
Listing photos are the #1 factor in booking decisions. Airbnb needs to: identify which photos show bedrooms vs kitchens vs bathrooms, score photo quality (lighting, composition, resolution), detect inappropriate content, and order photos optimally (best photo first).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Room classification** | CNN (convolutional neural network) trained on labeled photos | Manual tagging by hosts | Hosts often mis-tag; ML is consistent and scalable for millions of listings |
| **Quality scoring** | ML model trained on guest engagement data (which photos get clicks/bookings) | Human quality assessment | Engagement data = implicit quality signal at scale; human assessment doesn't scale |
| **Photo ordering** | ML-ranked: best photo first (highest predicted click-through rate) | Host-chosen order | Hosts often put a logo or text photo first; ML picks the most engaging |
| **Content moderation** | Pre-publish ML screening + human review for borderline | Post-publish only | Block inappropriate content before it's visible to guests |
| **Verified photography** | Professional photographer program (free for hosts) | Accept all phone photos | Professional photos increase bookings 20%+; worth the investment |

### Tradeoff
- ✅ ML room classification consistent at scale (millions of listings)
- ✅ Engagement-based quality scoring reflects actual guest preferences
- ✅ Optimal photo ordering increases booking rate
- ❌ ML classification isn't perfect (some mislabeling)
- ❌ Professional photography program is expensive to operate
- ❌ Photo quality bias: listings with bad photos ranked lower (cold-start problem)

### Interview Use
> "For image classification at scale (millions of listings), I'd use CNNs for room type detection (bedroom vs kitchen vs bathroom) and engagement-based quality scoring (predict click-through rate). Order photos by predicted engagement, not host's chosen order. Airbnb found that ML-optimized photo ordering significantly increases booking rate. For content moderation, pre-publish ML screening catches issues before guests see them."

---

## 10. Availability & Booking System

**Blog Post**: "Building Availability at Airbnb"

### Problem
When a guest books dates Jan 10-15, those dates must become unavailable immediately for all other guests. With millions of concurrent users browsing and booking, must prevent double-booking while keeping availability data fresh.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Consistency** | Strong consistency for booking (ACID transaction) | Eventual consistency | Double-booking a listing is unacceptable — one guest, one listing, one set of dates |
| **Availability storage** | Per-listing calendar (date → status: available/booked/blocked) | Global availability index | Per-listing storage enables efficient single-listing queries; calendar metaphor maps naturally |
| **Booking lock** | Pessimistic lock on listing + date range during checkout | Optimistic (check at commit) | Low tolerance for conflicts in bookings; pessimistic prevents double-booking at the cost of reduced concurrency |
| **Search availability** | Eventually consistent (cache, seconds stale) | Strongly consistent | Search results can show briefly stale availability; booking process checks real availability |
| **Caching** | Redis cache for availability (2-second TTL) | No cache | Millions of search queries check availability; can't hit DB for every query |

### Booking Flow
```
Guest selects dates Jan 10-15 for Listing X:
  1. [SEARCH] Check availability from Redis cache → shows "available" (may be 2s stale)
  2. [CHECKOUT] Guest clicks "Reserve"
  3. [BOOKING SERVICE] Acquire lock on Listing X for Jan 10-15
  4. [DB] Check real availability (MySQL transaction)
     → Available: Mark dates as "booked" (ACID transaction)
     → Not available (someone just booked): Return "dates no longer available"
  5. [PAYMENT] Charge guest
  6. [REDIS] Invalidate availability cache
  7. [KAFKA] booking.confirmed event → host notification, calendar sync, analytics
```

### Tradeoff
- ✅ ACID transaction prevents double-booking (zero tolerance)
- ✅ Redis cache handles millions of availability checks for search
- ✅ Lock during checkout prevents concurrent bookings
- ❌ Search results may show stale availability (2-second cache)
- ❌ Lock reduces concurrency for popular listings
- ❌ Cache invalidation must be immediate after booking

### Interview Use
> "For booking availability (no double-booking allowed), I'd use strong consistency: pessimistic lock on the listing + date range during checkout, ACID transaction to mark dates as booked. Search can use eventually consistent cache (Redis, 2s TTL) — showing a listing as available when it was just booked is OK (caught at checkout). This two-tier approach gives fast search with correct bookings."

---

## 11. Messaging — Host-Guest Communication

### Problem
Hosts and guests need to communicate before, during, and after a trip. Messages must be real-time, support rich content (images, locations, itineraries), and be translatable (host speaks French, guest speaks English).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Delivery** | Long polling → WebSocket for real-time + push notifications for offline | WebSocket only | Long polling as fallback for unreliable connections (guests traveling in areas with poor connectivity) |
| **Translation** | Real-time auto-translation (ML) of messages between languages | No translation | Global marketplace: host in Japan, guest from Germany — must communicate |
| **Storage** | Message threads per booking (partition by booking_id) | Global message store | All messages for one booking on one partition; efficient retrieval |
| **Smart replies** | ML-suggested quick responses ("Check-in is at 3 PM") | No suggestions | Reduces response time; hosts can tap suggested response instead of typing |
| **Structured messages** | Special message types: booking confirmation, review request, itinerary | Plain text only | Structured messages render rich UI (map, calendar, checklist) |

### Interview Use
> "For marketplace messaging (host-guest), I'd add real-time auto-translation — Airbnb's global marketplace requires communication across languages. Smart replies (ML-suggested responses) reduce host response time. Store messages partitioned by booking_id. Use WebSocket for real-time delivery with push notification fallback for offline users."

---

## 12. Observability

**Blog Post**: "Alerting Framework at Airbnb" + various talks

### Problem
With 500+ services, Airbnb needs to detect issues within minutes. A broken search ranking or payment failure directly impacts revenue.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Metrics** | Custom metrics platform + Datadog | Prometheus only | Custom for business metrics (booking rate); Datadog for infrastructure |
| **Alerting** | Anomaly detection (statistical) + static thresholds | Static thresholds only | Anomaly detection catches degradation that doesn't cross a fixed threshold |
| **Business metrics** | Track booking rate, search conversion, payment success per market | Technical metrics only | A 1% drop in booking rate in Paris matters more than CPU at 80% |
| **Distributed tracing** | Jaeger (trace requests across 500+ services) | Log-based debugging | Trace a single booking request through search → pricing → availability → payment → notification |
| **Canary analysis** | Automated canary (compare new version metrics vs baseline) | Manual canary evaluation | Catch regressions before full rollout; critical for search ranking changes |

### Interview Use
> "For a marketplace, business metrics matter more than infrastructure metrics. I'd track booking rate, search conversion, and payment success per market — a 1% drop in booking rate is more important than CPU utilization. Anomaly detection catches gradual degradation that static thresholds miss. Airbnb uses automated canary analysis — comparing new version's business metrics against baseline before full rollout."

---

## 🎯 Quick Reference: Airbnb's Key Decisions

### Architecture
| System | Choice | Why |
|--------|--------|-----|
| Migration | Monolith → ~500 SOA services (strangler fig) | Independent deployment and scaling |
| Service discovery | SmartStack (sidecar HAProxy) | Decentralized, no SPOF |
| Communication | Thrift RPC (internal) + REST (external) | Strong typing for internal; REST for public API |
| API gateway | Custom aggregation layer | One mobile call → fan-out to multiple services |

### ML & Data
| System | Choice | Why |
|--------|--------|-----|
| Search ranking | Two-stage: retrieval → neural network ranking | 7M+ listings; can't rank all with neural net |
| Dynamic pricing | Per-listing ML prediction (suggest, not force) | Hosts earn 5-15% more; maintain trust with override |
| Feature store | Chronon (unified, point-in-time correct) | Eliminate training-serving skew |
| Metrics | Minerva (centralized definition + computation) | Single source of truth for business metrics |
| Image quality | CNN classification + engagement-based scoring | ML consistent at millions of listings |

### Payments & Booking
| System | Choice | Why |
|--------|--------|-----|
| Payment | Multi-currency double-entry; convert at settlement, not storage | Avoid FX risk; delay conversion |
| Idempotency | Orpheus (deterministic keys, framework-level) | Same operation → same key → safe retry |
| Booking | ACID + pessimistic lock; search uses cached availability | Zero double-booking tolerance |
| Payout timing | 24h after check-in | Protect against cancellations |

---

## 🗣️ How to Use Airbnb Examples in Interviews

### Example Sentences
- "For a two-sided marketplace, I'd optimize search ranking for BOTH guest and host satisfaction — like Airbnb. A booking that leads to a bad review is a negative signal."
- "Airbnb's Orpheus uses deterministic idempotency keys — the same operation always produces the same key. This makes retries inherently safe without random UUID tracking."
- "For business metrics consistency, I'd use a centralized metrics store like Airbnb's Minerva — 'booking rate' is defined once and computed the same everywhere."
- "In multi-currency payments, store in original currency and convert at settlement — not at storage. Airbnb pays hosts 24 hours after check-in to protect against cancellations."
- "For ML feature management, Airbnb's Chronon ensures point-in-time correctness — preventing data leakage in training."
- "Airbnb migrated from monolith to SOA using strangler fig over 3 years — extracting one service at a time. No feature freeze during migration."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 12 design decisions across architecture, ML, payments, and marketplace  
**Status**: Complete & Interview-Ready ✅