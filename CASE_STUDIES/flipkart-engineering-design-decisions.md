# Flipkart Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Flipkart's engineering blog, conference talks, and tech articles. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Flipkart Tech Blog (tech.flipkart.com), Rootconf/JSFoo talks, @Scale talks, tech articles
>
> **Context**: Flipkart is India's largest e-commerce platform (now Walmart-owned). Their biggest challenge: **Big Billion Days (BBD)** — India's largest sale event where traffic spikes 100× and hundreds of millions of dollars in GMV happen in hours. Also: serving 400M+ registered users across India's diverse infrastructure.

---

## Table of Contents

1. [Big Billion Days — Scaling for India's Largest Sale](#1-bbd-scaling)
2. [Astra — Custom Search Platform](#2-astra-search)
3. [Sherlock — Fraud Detection at Scale](#3-sherlock-fraud)
4. [Flipkart Lite — Progressive Web App for Tier 2-4](#4-flipkart-lite)
5. [MySQL to Custom Data Platform — Evolving Storage](#5-data-platform)
6. [Flipkart Ads — Real-Time Ad Serving](#6-ads-platform)
7. [Supply Chain — Ekart Logistics Platform](#7-ekart-logistics)
8. [Microservices on Kubernetes](#8-microservices)
9. [Recommendation Engine — Personalization](#9-recommendation-engine)
10. [Payment System — PhonePe Integration & Wallet](#10-payment-system)

---

## 1. Big Billion Days — Scaling for India's Largest Sale

**Source**: "Engineering Big Billion Days" + multiple Flipkart tech talks

### The Problem
Big Billion Days (BBD) is India's largest e-commerce sale — hundreds of millions of dollars in a few days:
- **Normal day**: X million orders/day
- **BBD peak**: 100× traffic spike in the first hour of sale (midnight flash sale)
- **Flash deals**: Specific products at deep discounts for 15-minute windows → 1000× spike for that SKU
- **Concurrent users**: Tens of millions simultaneously at midnight

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Pre-provisioning** | Scale to 10× expected peak 48h before sale | Reactive auto-scaling | BBD starts at midnight; can't wait for scaling during the spike |
| **Queue-based architecture** | All orders go through queues (Kafka + custom) | Synchronous processing | Queue absorbs the midnight flash; workers process at sustainable rate |
| **Inventory locking** | Redis atomic decrement for flash deals | MySQL SELECT FOR UPDATE | Flash deals: millions of users competing for 100 units; Redis is O(1) atomic |
| **Feature throttling** | Disable non-essential features: product reviews, Q&A, compare, recommendations | All features on | Protect checkout flow; other features can wait |
| **Sale token** | Virtual queue + token: users get a "sale token" to enter | Let everyone in at once | Token-based admission controls load; users wait in queue for their turn |
| **CDN** | Pre-cache all product pages, images, deal pages on CDN | Dynamic generation | CDN serves 90%+ of page views; origin handles only checkout + cart |

### The "Sale Token" Architecture
```
Midnight — BBD starts:
  1. 50 million users hit Flipkart simultaneously
  2. API Gateway assigns each user a "sale token" (position in virtual queue)
  3. Users see: "Your sale will start in ~5 minutes" (based on token position)
  4. Tokens released in batches (1 million every 30 seconds)
  5. Token holders can browse deals and add to cart
  6. Non-token holders see a waiting room page (served from CDN — zero origin load)

Result:
  - 50 million users → only 1 million active at any time
  - Origin servers handle 1M concurrent (manageable) vs 50M (impossible)
  - Users perceive fairness (FIFO queue)
```

### Tradeoff
- ✅ Controlled admission prevents system crash at midnight
- ✅ CDN serves waiting room page (90%+ of requests never hit origin)
- ✅ Queue-based order processing absorbs the burst
- ❌ Users must wait (frustrating — but better than errors)
- ❌ Sale token system is complex to build and test
- ❌ Pre-provisioning 10× is expensive (wasted for non-sale days)

### Interview Use
> "For the biggest e-commerce sale events (like Flipkart's BBD — 100× traffic at midnight), I'd use a virtual queue with sale tokens: control admission rate, serve a CDN-cached waiting room for users not yet admitted, and process orders through Kafka queues. This converts a 50-million-user spike into a manageable 1-million-at-a-time flow. Redis atomic decrements for flash deal inventory (millions competing for 100 units)."

---

## 2. Astra — Custom Search Platform

**Source**: "Building Flipkart's Search" + tech blog

### Problem
Flipkart search handles hundreds of millions of queries/day across 150M+ products. Must handle: multi-language (Hindi, Tamil, English), typos, synonyms ("mobile" = "phone" = "smartphone"), category-aware results, and personalized ranking.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Engine** | Custom search platform (Astra) built on Lucene/Solr | Pure Elasticsearch | Custom optimizations for Indian e-commerce: multi-language, transliteration, catalog-aware ranking |
| **Ranking** | ML model: relevance + personalization + conversion prediction | BM25 text matching | ML considers 100+ signals: user history, product quality, seller rating, delivery speed |
| **Query understanding** | NLP pipeline: tokenize → spell correct → synonym expand → intent classify | Simple keyword match | "buy redmi note 10 under 15000" → intent: purchase, brand: Redmi, model: Note 10, price filter: < ₹15000 |
| **Multi-language** | Hindi transliteration + regional language support | English only | 60%+ of Flipkart users prefer Hindi or regional languages |
| **Autocomplete** | Trie-based prefix matching + trending queries + personalized suggestions | Full search per keystroke | Instant results while typing; trending queries surface popular searches |
| **Indexing** | Near real-time (Kafka → indexer within seconds) | Batch (hourly) | Price changes, stock changes must be reflected immediately |

### Tradeoff
- ✅ Custom NLP handles Indian languages, transliteration, and shopping intent
- ✅ ML ranking optimizes for conversion (not just text relevance)
- ✅ Near real-time indexing for price and stock changes
- ❌ Custom platform is expensive to maintain vs managed Elasticsearch
- ❌ ML ranking is a black box — harder to debug "why did this product rank #1?"
- ❌ Multi-language tokenization and stemming are complex per language

### Interview Use
> "For e-commerce search in India, I'd build NLP query understanding: parse 'redmi under 15000' into structured intent (brand: Redmi, price: < ₹15000). Multi-language search with Hindi transliteration (users type Hindi in English characters). ML ranking optimizes for conversion, not just text match. Flipkart's Astra handles hundreds of millions of queries/day across 150M+ products."

---

## 3. Sherlock — Fraud Detection

**Source**: "Fighting Fraud at Flipkart" + tech blog

### Problem
Flash deals attract bots that buy all inventory in milliseconds, preventing genuine customers from purchasing. Fraudsters create fake accounts, abuse referral programs, and exploit promotional offers.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Detection** | Real-time ML (< 100ms per request) + rule engine | Rules only | ML detects patterns rules miss; rules handle known fraud instantly |
| **Signals** | Device fingerprint, IP, purchase velocity, account age, browsing pattern, payment method | IP only | Multi-signal catches sophisticated fraud that single-signal misses |
| **Bot detection** | CAPTCHA for suspicious patterns + device fingerprinting + behavioral analysis | CAPTCHA for all | Don't CAPTCHA legitimate users; only trigger for suspicious behavior |
| **Flash deal protection** | Rate limit per device + per account + velocity check | No protection | Without protection, bots buy all 100 units in 0.1 seconds |
| **Account abuse** | ML model detects fake accounts: device shared across accounts, bulk creation, no browsing before purchase | Trust all accounts | Fake accounts used for referral abuse, promo exploitation |
| **Response** | Block (high risk) → Challenge (medium risk) → Allow (low risk) | Block all suspicious | Graduated response minimizes false positive impact |

### Tradeoff
- ✅ Real-time detection blocks fraud before transactions complete
- ✅ Multi-signal approach catches sophisticated fraud
- ✅ Graduated response minimizes blocking legitimate users
- ❌ False positives frustrate real customers during BBD (when everyone looks suspicious — rapid clicking, unusual patterns)
- ❌ ML model needs continuous retraining as fraudsters evolve
- ❌ Bot detection and CAPTCHA add friction to user experience

### Interview Use
> "For flash deal protection (100 units, millions of users), I'd use multi-layered fraud detection: device fingerprinting + behavioral analysis (bots click faster than humans) + account velocity checks. Rate limit per device, not just per account (bots create multiple accounts on one device). Flipkart's Sherlock runs ML + rules in < 100ms per request to block bots during Big Billion Days."

---

## 4. Flipkart Lite — Progressive Web App

**Source**: "Building Flipkart Lite" + Google I/O talks

### Problem
Flipkart was one of the first companies to build a Progressive Web App (PWA). Users in Tier 2-4 India have: slow 2G/3G connections, low-end devices with limited storage, and expensive data plans. Native app (100MB) is too large to install.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Platform** | PWA (Progressive Web App) | Native-only | PWA: < 1MB download (vs 100MB native app), works on any browser, installable |
| **Offline** | Service Worker caches key pages for offline browsing | Online-only | Users on intermittent connections can browse catalog offline |
| **Performance** | App Shell model: cache the UI shell, load content dynamically | Full page loads | UI appears in < 1 second (cached shell); content loads progressively |
| **Push notifications** | Web Push API (works without native app installed) | Notifications only in native app | Engage users who don't want to install the native app |
| **Data savings** | Lazy loading images + WebP + text-first rendering | Load everything | Show product titles and prices first; images load as user scrolls |

### Results (from Google case study)
- **3× more time spent** on site
- **40% higher re-engagement** rate
- **70% increase** in conversions from Add-to-Homescreen users
- **3× less data usage** compared to native app

### Tradeoff
- ✅ < 1MB vs 100MB — accessible to storage-constrained devices
- ✅ Works on any browser — no Play Store installation needed
- ✅ Offline browsing via Service Worker
- ❌ PWA capabilities are limited vs native (no access to some device APIs)
- ❌ iOS Safari has limited PWA support (getting better)
- ❌ Push notification UX is weaker than native

### Interview Use
> "For Tier 2-4 India users on 2G/3G with limited storage, a PWA (< 1MB vs 100MB native app) dramatically improves accessibility. Flipkart Lite was a pioneering PWA — App Shell model for < 1 second load, Service Worker for offline browsing, lazy image loading. Result: 3× more time on site, 70% more conversions from homescreen users. The lesson: meet users where they are — not everyone can install a 100MB app."

---

## 5. Data Platform — Storage Evolution

**Source**: Flipkart tech blog on database architecture

### Problem
Flipkart's data needs evolved: MySQL for transactional data, Couchbase for catalog (high-read), HBase for analytics, and custom solutions for different access patterns.

### Design Decisions

| Data | Database | Why |
|------|----------|-----|
| **Orders, payments** | MySQL (sharded by user_id) | ACID transactions, strong consistency for financial data |
| **Product catalog** | Couchbase (document store) + Elasticsearch (search) | High-read throughput for browsing; ES for full-text search |
| **Session, cache** | Redis (ElastiCache equivalent) | Sub-millisecond, rich data structures |
| **Analytics, events** | HBase + Hive (Hadoop ecosystem) | Write-heavy event logging, batch analytics |
| **Recommendations** | Custom in-memory store + Redis | Pre-computed ML features served at < 10ms |
| **Inventory** | Redis (hot) + MySQL (source of truth) | Redis for atomic flash deal decrements; MySQL for accuracy |

### Tradeoff
- ✅ Right database for each access pattern
- ✅ MySQL ACID for financial data; Redis for hot inventory
- ❌ Multiple databases to operate
- ❌ Data consistency across systems is eventual

### Interview Use
> "Flipkart uses polyglot persistence: MySQL for orders (ACID), Couchbase for catalog (high-read), Redis for inventory during flash deals (atomic decrement), HBase for analytics events. The key: match the database to the access pattern. Orders need ACID; catalog needs read throughput; flash deal inventory needs atomic operations."

---

## 6. Ads Platform — Real-Time Serving

**Source**: Flipkart tech blog on advertising

### Problem
Flipkart's ads platform must serve relevant product ads within the search/browse flow (< 100ms budget). Advertisers bid on keywords; must balance revenue (ad bids) with user experience (relevant ads).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Ranking** | eCPM = bid × predicted_CTR × relevance_score | Highest bid wins | Relevance prevents showing irrelevant high-bid ads; better UX = more clicks = more revenue |
| **Latency** | < 100ms for ad selection (within search response budget) | Async ad loading | Ads must appear with search results, not after |
| **Targeting** | Query-based (search keywords) + user profile + context | Keywords only | User profile + context improve targeting (returning user vs new, mobile vs desktop) |
| **Budget pacing** | Spend daily budget evenly (not all in the morning) | First-come-first-served | Even distribution throughout the day serves more advertisers |

### Interview Use
> "For e-commerce ads in search results, I'd rank by eCPM = bid × predicted_CTR × relevance — same as Google Ads. This balances revenue with relevance. Budget pacing ensures advertisers' daily budgets are spread evenly. Total ad selection must complete in < 100ms to fit within the search response budget."

---

## 7. Ekart — Logistics Platform

**Source**: Flipkart tech blog on logistics

### Problem
Flipkart delivers to 19,000+ pincodes across India through its logistics arm Ekart. Must handle the BBD spike (10× delivery volume in a week), optimize for cost (low average order value — ₹500), and deliver to remote areas.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Routing** | ML-based delivery routing (optimize for cost + speed) | Manual route planning | Millions of deliveries/day → manual planning impossible |
| **Warehousing** | Regional fulfillment centers + last-mile hubs | Centralized warehouse | Regional FCs reduce delivery time and shipping cost |
| **Delivery prediction** | ML-based ETA: factor distance, traffic, weather, delivery partner performance | Fixed SLA (3-5 days) | Accurate ETAs improve customer experience; "Delivered by Tuesday" vs "3-5 business days" |
| **BBD preparation** | Pre-position popular products in regional warehouses weeks before BBD | Ship from central warehouse during sale | BBD volume is 10× — central warehouse would be a bottleneck |
| **Last-mile** | Fleet of delivery partners (gig economy + full-time) + India Post for remote areas | Own fleet only | Gig economy scales for BBD; India Post reaches pincodes Ekart doesn't |
| **Cash on Delivery** | Dedicated COD handling (cash reconciliation, counterfeit detection) | Online-only | 40-50% of Flipkart orders are COD; can't ignore |

### Tradeoff
- ✅ ML routing optimizes millions of daily deliveries
- ✅ Regional warehousing reduces delivery time and cost
- ✅ Pre-positioning for BBD handles 10× volume
- ❌ COD handling is expensive (cash management, reconciliation, counterfeit risk)
- ❌ Last-mile delivery to remote India is expensive relative to order value
- ❌ BBD pre-positioning ties up capital in inventory

### Interview Use
> "For e-commerce logistics at scale, I'd use regional fulfillment centers + ML-based routing. Pre-position popular products in regional warehouses before major sales (BBD). Use gig delivery partners for elastic capacity during sales + India Post for remote areas. Flipkart's Ekart delivers to 19,000+ pincodes — the routing optimization alone saves millions in logistics costs."

---

## 8. Microservices on Kubernetes

**Source**: Flipkart tech blog on platform evolution

### Problem
Flipkart evolved from a Java monolith to hundreds of microservices. During BBD, different services have wildly different scaling needs (search scales 50×, order processing scales 20×, catalog stays relatively flat).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Orchestration** | Kubernetes (custom on-premise + cloud) | Pure cloud (EKS) | Flipkart runs hybrid: on-premise for cost control + cloud for BBD burst capacity |
| **Hybrid cloud** | On-premise baseline + cloud burst for BBD | All cloud | On-premise for steady-state (cheaper at Flipkart's scale); cloud for BBD spike (elastic) |
| **Service communication** | gRPC (internal) + REST (external) | All REST | gRPC for performance-critical internal calls; REST for merchant/seller APIs |
| **Circuit breaker** | Hystrix-equivalent with automatic fallbacks | No protection | During BBD, any service can be overwhelmed; circuit breakers prevent cascading failures |
| **Configuration** | Dynamic config (hot-reload without restart) | Static config files | During BBD, need to adjust thresholds, toggle features, change rate limits in real-time |

### The Hybrid Cloud Strategy for BBD
```
Normal days:
  - 100% of traffic served from on-premise data centers
  - Cloud instances scaled to zero (no cost)

BBD preparation (T-48 hours):
  - Spin up cloud instances (10× on-premise capacity)
  - Run smoke tests on cloud infrastructure
  - Warm caches, DNS, connections

BBD (5 days):
  - On-premise: handles baseline traffic
  - Cloud: handles burst traffic (overflow from on-premise)
  - Auto-scale in cloud based on real-time load

Post-BBD:
  - Scale cloud to zero within 24 hours
  - Cost: pay for cloud only during BBD (~1 week/year)
```

### Tradeoff
- ✅ Hybrid cloud: on-premise for cost control + cloud for BBD elasticity
- ✅ Pay for cloud burst only ~1 week/year (significant cost savings vs all-cloud)
- ✅ Dynamic config enables real-time BBD tuning without deployments
- ❌ Hybrid infrastructure is complex (two environments to manage)
- ❌ On-premise requires capacity planning and hardware management
- ❌ Cloud burst needs pre-testing (can't discover issues during BBD)

### Interview Use
> "For systems with predictable annual spikes (like e-commerce sales), hybrid cloud makes sense: on-premise for cost-efficient baseline, cloud burst for the spike. Flipkart runs on-premise 95% of the year but scales into cloud for Big Billion Days — paying for cloud burst only ~1 week/year. This saves millions vs running 10× capacity on cloud year-round."

---

## 9. Recommendation Engine

**Source**: Flipkart tech blog on personalization

### Problem
Personalized recommendations drive 30%+ of Flipkart's revenue. Must recommend from 150M+ products to 400M+ users, handling diverse preferences across India's varied demographics.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Algorithm** | Collaborative filtering + content-based + deep learning ensemble | Single model | Ensemble outperforms any single approach |
| **Architecture** | Offline batch (daily) + near-line (streaming for recent activity) + online (real-time context) | Offline only | Recent activity (just browsed phones) should influence immediately, not next day |
| **Candidate generation** | ANN (Approximate Nearest Neighbor) from embedding space | Exact search | 150M products; ANN finds similar in milliseconds |
| **Serving** | Pre-computed top-N per user (cached) + real-time re-ranking | Compute on demand | Pre-computed for fast serving; re-rank with real-time context (time, device, session) |
| **Cold start** | Popularity-based + demographic matching for new users | No recommendations | New users still see recommendations based on similar users' behavior |

### Tradeoff
- ✅ 30%+ revenue from recommendations
- ✅ Three-layer (offline + near-line + online) balances freshness with cost
- ✅ ANN makes 150M-product search feasible in real-time
- ❌ ML pipeline is expensive (training, serving, feature engineering)
- ❌ Filter bubble: users see more of what they already browse
- ❌ Cold-start for new users and new products

### Interview Use
> "For e-commerce recommendations (150M products, 400M users), I'd use a three-layer architecture: offline models (daily Spark), near-line updates (Kafka streaming for recent activity), online re-ranking (real-time context). ANN in embedding space finds candidates in milliseconds. Pre-compute top-N per user, re-rank with session context at serving time. Flipkart drives 30%+ revenue from personalized recommendations."

---

## 10. Payment System — PhonePe & Wallet

**Source**: Flipkart tech blog on payments

### Problem
Flipkart processes millions of transactions daily, supporting 10+ payment methods: UPI (via PhonePe), cards, netbanking, EMI, wallet, COD. During BBD, payment volume spikes 20× — payment gateway must not be the bottleneck.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Gateway** | Multi-gateway routing (switch between gateways based on success rate) | Single gateway | If one gateway's success rate drops, automatically route to backup |
| **UPI** | PhonePe (Flipkart's own UPI app) | Third-party UPI | Own UPI app gives control, lower fees, better integration |
| **COD** | Support by default (40-50% of orders) | Online-only | COD is essential for Tier 2-4 where digital payment adoption is lower |
| **EMI** | No-cost EMI for high-value items (Flipkart subsidizes interest) | No EMI | EMI increases average order value; makes expensive electronics accessible |
| **Idempotency** | Per-order idempotency key | No protection | BBD network issues cause retries; must not double-charge |
| **BBD preparation** | Pre-warm payment gateway connections; increase timeout limits | Normal configuration | BBD payment volume 20× → gateways need advance notice to scale |

### Smart Gateway Routing
```
Payment request arrives:
  1. Check payment method (UPI/card/netbanking)
  2. For each method, check real-time success rate per gateway:
     - Gateway A: 95% success rate for HDFC cards
     - Gateway B: 88% success rate for HDFC cards
  3. Route to Gateway A (higher success rate)
  4. If Gateway A fails: immediately retry on Gateway B
  5. Track success rates per gateway × payment method × bank (updated every minute)

Result: Higher overall payment success rate by always routing to the best-performing gateway
```

### Tradeoff
- ✅ Smart routing maximizes payment success rate (route to best gateway)
- ✅ Automatic failover: if one gateway degrades, traffic shifts instantly
- ✅ PhonePe integration gives control over UPI experience
- ❌ Multi-gateway adds complexity (different APIs, different reconciliation)
- ❌ COD has higher return rates and cash management costs
- ❌ No-cost EMI is subsidized by Flipkart (reduces margin)

### Interview Use
> "For payment reliability during high-traffic events, I'd use smart gateway routing: track real-time success rates per gateway × payment method × bank, and route each payment to the highest-performing gateway. If Gateway A's success rate drops from 95% to 80%, automatically shift traffic to Gateway B. Flipkart's smart routing maximizes payment success rate by always choosing the best-performing path."

---

## 🎯 Quick Reference: Flipkart's Key Decisions

### BBD (Big Billion Days)
| Challenge | Solution |
|-----------|---------|
| 100× traffic at midnight | Sale token (virtual queue) + CDN waiting room |
| Flash deal inventory | Redis atomic decrement + rate limiting per device |
| Order burst | Kafka queue absorption; sync ACK + async processing |
| Bot protection | Multi-signal fraud detection (Sherlock) < 100ms |
| Infrastructure | Hybrid cloud: on-premise baseline + cloud burst |
| Features | Graceful degradation: disable non-essential during peak |

### Data
| Data Type | Database | Why |
|-----------|----------|-----|
| Orders, payments | MySQL (sharded) | ACID, financial accuracy |
| Product catalog | Couchbase + Elasticsearch | High-read + full-text search |
| Inventory (BBD) | Redis (hot) + MySQL (truth) | Atomic flash deal operations |
| Analytics | HBase + Hive | Write-heavy events, batch analytics |
| Recommendations | Pre-computed in Redis/custom store | < 10ms serving |

### India-Specific
| Challenge | Solution |
|-----------|---------|
| Tier 2-4 access | PWA (Flipkart Lite): < 1MB, offline, fast |
| Multi-language search | Hindi transliteration, NLP query understanding |
| COD (40-50% of orders) | Dedicated COD handling, cash reconciliation |
| Remote delivery | Ekart + India Post, ML routing, 19,000+ pincodes |
| Payment diversity | Smart multi-gateway routing by success rate |

---

## 🗣️ How to Use Flipkart Examples in Interviews

### Example Sentences
- "For India's largest sale events, I'd use a sale token system — virtual queue with controlled admission. Flipkart converts 50M simultaneous users into 1M at a time via token-based admission. Waiting room served from CDN."
- "Smart payment gateway routing: track success rates per gateway × bank × method in real-time, route to the best. If a gateway degrades, traffic shifts automatically."
- "For Tier 2-4 India, a PWA (< 1MB) dramatically outperforms native apps (100MB). Flipkart Lite showed 3× engagement increase and 70% more conversions."
- "Hybrid cloud for annual spikes: on-premise baseline + cloud burst for BBD. Pay for cloud only ~1 week/year."
- "Flash deal inventory: Redis atomic DECR for millions competing for 100 units. Rate limit per device (not just per account) to stop bots."
- "For e-commerce search in India, NLP query understanding turns 'redmi under 15000' into structured filters (brand + price range)."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 10 design decisions focused on e-commerce, BBD scaling, and India-specific challenges  
**Status**: Complete & Interview-Ready ✅