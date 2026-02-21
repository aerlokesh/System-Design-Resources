# Meesho Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Meesho's engineering blog, conference talks, and tech articles. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Meesho Engineering Blog (tech.meesho.com), AWS re:Invent talks, GopherCon/Rootconf talks, tech articles
>
> **Context**: Meesho is India's largest social commerce platform serving 150M+ monthly transacting users. Their biggest challenge: **Mega Sale events** (like Mega Blockbuster Sale) where traffic spikes 10-20× with tens of millions of orders in 5 days, plus serving Tier 2-4 India where network quality is poor and devices are low-end.

---

## Table of Contents

1. [Mega Sale Scaling — Handling 10-20× Traffic Spikes](#1-mega-sale-scaling)
2. [Microservices Architecture — From Monolith to 200+ Services](#2-microservices)
3. [Product Catalog — Serving 100M+ Products](#3-product-catalog)
4. [Order Management System — Millions of Orders](#4-order-management)
5. [Search & Discovery — Personalized for Tier 2-4 India](#5-search-discovery)
6. [Golang Adoption — Performance-Critical Services](#6-golang-adoption)
7. [Event-Driven Architecture with Kafka](#7-kafka-events)
8. [Caching Strategy — Low-Latency for Poor Networks](#8-caching-strategy)
9. [Logistics & Shipping — Last-Mile in Rural India](#9-logistics)
10. [App Performance — Optimizing for Low-End Devices](#10-app-performance)

---

## 1. Mega Sale Scaling — Handling 10-20× Traffic Spikes

**Source**: "Scaling Meesho for Mega Blockbuster Sale" + AWS re:Invent talks

### The Problem
Meesho's Mega Sales are India's largest e-commerce events by order volume:
- **Normal day**: ~X million orders/day
- **Mega Sale**: 10-20× spike — tens of millions of orders in 5 days
- **Flash deals**: Within the sale, specific time-bound deals spike traffic 50-100× for 15-minute windows
- **User base**: Predominantly Tier 2-4 India — price-sensitive, first-time internet users, low-end devices, unreliable networks

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Scaling strategy** | Pre-provision 3× expected peak + auto-scale | Reactive auto-scaling only | Sale traffic is predictable (marketed weeks in advance); reactive too slow for 20× spike |
| **Load testing** | 3× peak load tests 2 weeks before sale | Test at 1× | 3× gives confidence; real traffic patterns are unpredictable |
| **Feature degradation** | Graceful degradation: disable non-critical features during peak | All features always on | Disable product recommendations, review images, chat support during extreme load to protect core checkout |
| **Database** | Read replicas + aggressive caching for catalog; primary for orders | Single database | Catalog reads (browsing) vastly outnumber order writes; separate scaling |
| **Queue absorption** | SQS/Kafka for order processing (absorbs spike) | Synchronous order processing | Order creation ACK in < 500ms; actual processing async |

### Graceful Degradation Tiers

| Tier | Traffic Level | What's Disabled | Core Function Protected |
|------|-------------|-----------------|------------------------|
| **Normal** | 1× | Nothing | Everything works |
| **Elevated** | 5× | Recommendation engine, review images | Browse + search + checkout |
| **High** | 10× | + Chat support, notifications | Browse + search + checkout |
| **Critical** | 20× | + Search suggestions, wishlist | Browse + checkout |
| **Emergency** | 30×+ | + Product reviews, seller ratings | Checkout only |

### Tradeoff
- ✅ Core checkout flow protected even at 30× traffic
- ✅ Pre-provisioning handles predictable Mega Sale spikes
- ✅ Feature degradation is automatic (traffic-level triggers)
- ❌ Degraded experience during peak (no recommendations, no reviews)
- ❌ Over-provisioning costs during 5-day sale period
- ❌ Complexity in managing multiple degradation tiers

### Interview Use
> "For predictable extreme traffic events (e-commerce sales), I'd implement tiered graceful degradation — automatically disable non-critical features as traffic increases to protect the core checkout flow. Meesho disables recommendations at 5×, chat at 10×, and reviews at 20× while keeping browse + checkout working. Pre-provision 3× expected peak and load test 2 weeks before the event."

---

## 2. Microservices Architecture

**Source**: Meesho engineering blog on architecture evolution

### Problem
Meesho started as a Python/Django monolith. As the platform grew to 150M+ users, the monolith couldn't handle the scale: deployment took hours, a bug in one feature crashed everything, and teams couldn't work independently.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Orchestration** | Kubernetes (EKS) | ECS, bare metal | K8s ecosystem, auto-scaling, health checks, rolling deployments |
| **Decomposition** | Domain-driven: Catalog Service, Order Service, Seller Service, Logistics Service, Payment Service | Feature-based | Clear domain boundaries map to team ownership |
| **Communication** | gRPC (internal) + REST (external) + Kafka (async) | All REST | gRPC for low-latency internal calls (protobuf); REST for public APIs; Kafka for event-driven |
| **Service mesh** | Istio (traffic management, mTLS, observability) | No mesh | Canary deployments critical during Mega Sales; mTLS for service-to-service security |
| **Gateway** | Kong API Gateway | Custom gateway | Rate limiting, auth, throttling at edge; Kong is proven at scale |
| **Language** | Go for new services, Python for existing | Single language | Go for performance-critical (order, catalog); Python for ML/data services |

### Tradeoff
- ✅ Independent scaling: catalog scales for browsing, order scales for checkout
- ✅ Independent deployment: 200+ services deploy independently
- ✅ gRPC reduces inter-service latency by ~50% vs REST
- ❌ 200+ services → complex service mesh, debugging, tracing
- ❌ K8s operational complexity
- ❌ Network hops between services add latency

### Interview Use
> "Meesho decomposed their monolith into 200+ microservices on EKS — gRPC for internal communication (50% faster than REST), Kafka for async events, and Istio for canary deployments (critical during Mega Sales). The key: domain-driven decomposition — Catalog, Order, Seller, Logistics each scale independently. Go for new performance-critical services, Python for ML/data."

---

## 3. Product Catalog — 100M+ Products

**Source**: Meesho engineering blog on catalog architecture

### Problem
Meesho hosts 100M+ products from millions of small sellers. Unlike Amazon/Flipkart where products have structured data, Meesho's sellers are small businesses — product data quality is inconsistent (blurry images, incomplete descriptions, wrong categories).

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Storage** | Elasticsearch (search + browse) + MySQL (source of truth) + Redis (hot cache) | Single database | ES for full-text search + faceted browse; MySQL for ACID; Redis for < 10ms reads |
| **Data quality** | ML-based catalog cleanup: auto-categorize, detect duplicate products, enhance titles | Manual curation | 100M products from millions of small sellers → manual curation is impossible |
| **Image handling** | ML-based: remove backgrounds, detect blurry images, auto-enhance | Accept all images | Product images are the #1 conversion factor; many seller photos are low quality |
| **Deduplication** | Image similarity + text similarity to merge duplicate listings | Allow duplicates | Same product from different sellers → group as variants to avoid clutter |
| **Caching** | Aggressive: product pages cached in Redis (30s TTL) + CDN | No caching | Product data changes infrequently; 95%+ cache hit rate for browsing |
| **Indexing** | Near real-time (Kafka → Elasticsearch within seconds) | Batch (hourly) | New seller listings must be searchable quickly |

### Tradeoff
- ✅ ML-based quality improvement at scale (100M products, impossible manually)
- ✅ 95%+ cache hit rate keeps catalog browsing fast
- ✅ Near real-time indexing: new products searchable in seconds
- ❌ ML quality models need continuous training and monitoring
- ❌ Aggressive caching means brief staleness (30s) for price changes
- ❌ Elasticsearch cluster is a significant operational cost

### Interview Use
> "For a catalog of 100M+ products with inconsistent data quality, I'd use ML for automated cleanup — auto-categorization, duplicate detection, image quality enhancement. Store in MySQL (source of truth) + Elasticsearch (search + browse) + Redis (hot cache, 30s TTL). Meesho uses ML to handle catalog quality at a scale where manual curation is impossible."

---

## 4. Order Management System

**Source**: Meesho engineering blog on order processing

### Problem
During Mega Sales, Meesho processes tens of millions of orders in 5 days. Each order involves: inventory check, payment processing, seller notification, logistics booking, and buyer notification. Must handle 20× spike without losing orders.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Order creation** | Sync ACK (< 500ms) + async processing (queue) | Fully synchronous | User gets "Order Placed" quickly; actual processing happens via queue |
| **Queue** | Kafka for order events + SQS for task processing | Single queue | Kafka for event streaming (multiple consumers); SQS for individual task distribution |
| **State machine** | Strict: placed → confirmed → shipped → delivered (or cancelled/returned) | Freeform status | State machine prevents invalid transitions; critical for financial reconciliation |
| **Inventory** | Soft reservation on order placement → hard reservation on payment confirmation | Check at browsing time | Browsing-time checks are instantly stale; soft reserve at order time, confirm on payment |
| **Idempotency** | Order creation with idempotency key | No protection | User retries, network retries → must not create duplicate orders |
| **Seller notification** | Async via Kafka → push notification + SMS + app | Synchronous | Don't block order creation waiting for seller acknowledgment |

### Order Pipeline
```
User clicks "Place Order":
  1. [SYNC] Validate cart, check soft inventory → Create order in DB (status: PLACED) → ACK (< 500ms)
  2. [KAFKA] order.placed event
  3. [ASYNC] Payment Worker: process payment (wallet/COD/UPI)
     → Success: order.confirmed event
     → Failure: order.cancelled event, release soft inventory
  4. [ASYNC] Seller Worker: notify seller (push + SMS), create shipping label
  5. [ASYNC] Logistics Worker: book pickup from seller location
  6. [ASYNC] Notification Worker: send buyer confirmation (push + SMS)

During Mega Sale: SQS absorbs burst; workers auto-scale based on queue depth
```

### Tradeoff
- ✅ < 500ms order creation (sync ACK, async processing)
- ✅ Kafka + SQS absorb 20× Mega Sale spike
- ✅ Soft inventory reservation prevents overselling
- ❌ Async processing means order confirmation is delayed (1-5 seconds)
- ❌ Soft reservation can block inventory for orders that fail payment
- ❌ Complex saga: if any step fails, must compensate previous steps

### Interview Use
> "For high-throughput order processing (millions/day during sales), I'd use sync ACK (< 500ms) + async pipeline. Order placed → Kafka event → payment, seller notification, logistics, buyer notification all process independently via SQS workers. Soft inventory reservation at order time, hard reservation on payment confirmation. Meesho processes tens of millions of orders during Mega Sales with this pipeline."

---

## 5. Search & Discovery — Tier 2-4 India

**Source**: Meesho engineering blog on search and personalization

### Problem
Meesho's users are predominantly from Tier 2-4 India — many are first-time internet users searching in Hindi/regional languages, using voice search, and misspelling brand names. Traditional e-commerce search (optimized for Tier 1 urban users) doesn't work.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Multi-language search (Hindi, Tamil, Telugu, Bengali + English) | English only | 80%+ users prefer regional language; English-only search misses most intent |
| **Transliteration** | Hindi in English script ("kurti" instead of "कुर्ती") + auto-detection | Require exact language match | Users type Hindi words in English characters; must handle transliteration |
| **Spell correction** | ML-based (trained on Meesho's user query logs) | Dictionary-based | Meesho's users have unique misspelling patterns; generic dictionaries don't help |
| **Voice search** | Supported (critical for low-literacy users) | Text only | Many Tier 2-4 users are more comfortable speaking than typing |
| **Ranking** | Personalized: user's past purchases, location, price sensitivity + engagement signals | Global popularity | A user in rural Bihar has different preferences than one in Bangalore |
| **Visual search** | Image-based search ("find similar products") | Text only | Users often share product screenshots via WhatsApp; visual search finds similar items |

### Tradeoff
- ✅ Multi-language + transliteration serves 80%+ of users who prefer regional language
- ✅ Voice search critical for low-literacy users (large Meesho user segment)
- ✅ Personalized ranking surfaces relevant products for diverse Tier 2-4 audience
- ❌ Multi-language search is complex (tokenization, stemming per language)
- ❌ Voice search accuracy lower for regional accents/dialects
- ❌ Transliteration has ambiguity (same English spelling → multiple Hindi words)

### Interview Use
> "For users in Tier 2-4 India, search must handle: multi-language (Hindi in English script — transliteration), voice search (low-literacy users), ML-based spell correction (trained on actual user queries, not dictionaries), and visual search (users share screenshots via WhatsApp). Meesho's search supports all of these because 80%+ of their users prefer regional languages. The lesson: search UX must match the actual user base, not the developer's assumptions."

---

## 6. Golang Adoption

**Source**: Meesho engineering blog + GopherCon India talks

### Problem
Meesho's Python/Django services couldn't handle Mega Sale traffic — high memory usage, GIL-limited concurrency, slow serialization. Needed a language that handles high concurrency with low memory.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Language** | Go for new high-traffic services | Java, Rust, stay on Python | Go: simple (fast onboarding), goroutines (true concurrency), low memory, fast compilation |
| **Which services** | Order Service, Catalog API, Payment Service (highest QPS) | All services | Migrate the bottleneck services first; Python stays for ML and low-traffic services |
| **Migration** | Strangler fig (new endpoints in Go, old in Python, shared DB) | Big-bang rewrite | Zero-downtime migration; gradually move endpoints |
| **Framework** | Standard library (net/http) + gRPC | Gin, Fiber | Fewer dependencies; stdlib performance is sufficient; gRPC for internal calls |

### Results
- **Latency**: P99 dropped 3-5× (from 500ms+ to 100-150ms)
- **Memory**: 60-70% reduction per pod
- **Throughput**: 4-5× more requests per pod
- **Cost**: ~40% reduction in compute costs for migrated services

### Interview Use
> "Meesho migrated performance-critical services from Python to Go — P99 latency dropped 3-5×, memory usage dropped 60-70%. Go's goroutines handle the Mega Sale concurrency that Python's GIL couldn't. The strangler fig pattern let them migrate endpoint by endpoint with zero downtime. Python stays for ML and data services where execution speed matters less."

---

## 7. Event-Driven Architecture with Kafka

**Source**: Meesho engineering blog on event architecture

### Problem
An order placement triggers dozens of downstream actions: payment, seller notification, logistics, analytics, fraud check, recommendation update. Coupling these synchronously in the order API would make it slow and fragile.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Event bus** | Kafka (MSK) | SQS, RabbitMQ | Multiple consumer groups, replay, ordering, high throughput for Mega Sales |
| **Key topics** | order.placed, order.confirmed, order.shipped, order.delivered, payment.completed, seller.notified | Single topic | Separate topics enable independent consumption per event type |
| **Partition key** | order_id for order events, seller_id for seller events | Random | Ordering per entity; seller events ordered per seller |
| **Consumer groups** | Independent: payment, logistics, notifications, analytics, fraud | Shared | Each domain processes at its own pace |
| **Delivery** | At-least-once + idempotent consumers | Exactly-once | Simpler; consumers deduplicate by order_id |

### Interview Use
> "I'd use Kafka as the event bus — order.placed triggers independent consumers for payment, logistics, seller notification, analytics, and fraud. Each consumer group scales independently. During Mega Sales, Kafka absorbs the spike — consumers auto-scale based on lag. Meesho's event-driven architecture decouples the order API from all downstream processing."

---

## 8. Caching Strategy — Low-Latency for Poor Networks

**Source**: Meesho engineering blog on performance

### Problem
Meesho's users are on 3G/4G connections with high latency and frequent packet loss. Every millisecond of server response time matters more because network latency is already high. Must minimize server-side processing time to compensate for slow networks.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **CDN** | CloudFront for all images + API response caching | No CDN | Images are the heaviest payload; CDN reduces download time for Tier 2-4 users |
| **Image optimization** | WebP format + aggressive compression + lazy loading | Full-size JPEG | WebP saves 30-50% bandwidth; lazy loading avoids downloading off-screen images |
| **API response caching** | Redis (product catalog, seller info, trending) | No caching | Catalog changes slowly; cache reduces DB load and server response time |
| **Local cache** | Client-side caching (app stores recently viewed products) | Server-only | Offline-capable browsing; reduces network requests on slow connections |
| **Compression** | Brotli at CDN edge | No compression | 20-30% smaller than gzip; meaningful on slow mobile networks |
| **Payload minimization** | GraphQL-like field selection (client specifies fields needed) | Return full objects | Don't send data the client doesn't need; saves bandwidth on 3G connections |

### Image Pipeline
```
Seller uploads image:
  1. Store original in S3
  2. Async processing (Lambda/worker):
     a. Resize to multiple sizes (thumbnail, small, medium, large)
     b. Convert to WebP (30-50% smaller than JPEG)
     c. Compress aggressively (quality 75% — acceptable for product images)
     d. Generate blur placeholder (for progressive loading)
  3. Serve via CDN with infinite TTL (content-addressable URLs)

On client:
  - Show blur placeholder immediately (< 100ms perceived)
  - Lazy load actual image only when scrolling near viewport
  - Request smallest sufficient size (thumbnail for grid, large for detail page)
```

### Tradeoff
- ✅ WebP + compression saves 30-50% bandwidth (critical for 3G users)
- ✅ Blur placeholder → instant perceived load
- ✅ Client-side caching enables offline browsing of recently viewed products
- ❌ WebP not supported on very old devices (fallback to JPEG)
- ❌ Aggressive compression reduces image quality (acceptable for Meesho's use case)
- ❌ Multiple image sizes × formats = significant storage multiplication

### Interview Use
> "For users on slow networks (3G/4G India), I'd optimize aggressively: WebP images (30-50% smaller), Brotli compression, blur placeholders for instant perceived load, lazy loading, and client-side caching for offline browsing. Field selection (GraphQL-style) sends only the data the client needs. Meesho optimizes for Tier 2-4 India where every kilobyte matters on slow, expensive data connections."

---

## 9. Logistics — Last-Mile in Rural India

**Source**: Meesho engineering blog on logistics

### Problem
Delivering to Tier 2-4 India is fundamentally different from Tier 1: addresses are unstructured ("near the temple, opposite the big tree"), pincodes are unreliable, many areas don't have formal address systems, and delivery partners may not have GPS.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Address handling** | ML-based address standardization + geocoding | Trust user-entered addresses | User addresses are often incomplete/inaccurate; ML parses "near temple" into structured location |
| **Delivery routing** | Cluster orders by pincode → assign to nearest delivery partner | Individual delivery per order | Clustering reduces delivery cost in dense areas; critical for low-value orders (₹200 avg) |
| **Tracking** | SMS-based (not app-only) | App-only tracking | Many users don't have the Meesho app installed on delivery day; SMS works on any phone |
| **Returns** | QR-code-based return pickup (no printing required) | Shipping label printing | Most users don't have printers; QR code on phone screen is sufficient |
| **Cash on Delivery** | Default payment method (COD is dominant in Tier 2-4) | UPI/online-first | 70%+ of Tier 2-4 orders are COD; online payment requires trust that's built over time |
| **Multi-partner** | Multiple logistics partners (Delhivery, Ecom Express, India Post) with ML-based allocation | Single partner | Different partners have different strengths (rural reach, metro speed, cost) |

### Tradeoff
- ✅ ML address standardization handles unstructured Indian addresses
- ✅ SMS tracking works on any phone (feature phones included)
- ✅ Multi-partner logistics optimizes for cost, speed, and reach
- ❌ COD has higher return rates and cash collection risk
- ❌ ML address parsing isn't perfect — some deliveries still fail
- ❌ Multi-partner adds complexity (different APIs, tracking formats, SLAs)

### Interview Use
> "For e-commerce in Tier 2-4 India, logistics must handle unstructured addresses (ML standardization), SMS-based tracking (not app-only — many users use feature phones), and COD as the default payment method (70%+ in rural India). Multi-partner logistics with ML-based allocation optimizes for cost vs speed vs rural reach. Meesho solves these unique challenges for 150M+ users in areas where Amazon/Flipkart logistics may not reach."

---

## 10. App Performance — Low-End Devices

**Source**: Meesho engineering blog on mobile performance

### Problem
Meesho's users often have devices with 2-3GB RAM, slow processors, limited storage, and Android Go editions. The app must work smoothly on these devices while competitors (Amazon, Flipkart) target higher-end devices.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **App size** | < 15 MB APK (vs 50-100 MB for competitors) | Feature-rich large app | Low storage devices can't install 100MB apps; app size = install conversion |
| **Memory usage** | Target < 100MB RAM at peak | Standard mobile app (200-300MB) | 2GB RAM devices have little free memory; must be lightweight |
| **Image loading** | Progressive: blur → thumbnail → full (on demand) | Load full resolution | Save bandwidth and memory; show something immediately |
| **Rendering** | RecyclerView with view recycling + flat view hierarchies | Complex nested layouts | Reduce overdraw and memory allocation on slow GPUs |
| **Network** | Offline-first for browsing (cache catalog locally) | Online-only | Users on intermittent connections should still be able to browse |
| **Startup time** | < 2 seconds cold start (pre-load critical data) | No optimization (5-10 second start) | Slow starts = users think app is broken and uninstall |
| **Data usage** | Show data consumption estimate before image-heavy pages | Load everything | Users on expensive data plans want to control data usage |

### Tradeoff
- ✅ < 15MB app size = higher install rate on low-storage devices
- ✅ Offline browsing works on intermittent connections
- ✅ Progressive image loading = instant perceived performance
- ❌ Small app size means some features are loaded on demand (modular architecture)
- ❌ Offline-first adds sync complexity (local data vs server data)
- ❌ Supporting old Android versions limits use of modern APIs

### Interview Use
> "For low-end device users, I'd optimize: small APK (< 15MB vs 100MB competitors), < 100MB RAM usage, progressive image loading (blur → thumbnail → full), offline-first browsing, and < 2 second cold start. Meesho targets 2-3GB RAM devices — their app is 5-7× smaller than Amazon/Flipkart. The lesson: performance optimization for the actual target device, not developer hardware."

---

## 🎯 Quick Reference: Meesho's Key Decisions

### Scale & Traffic
| Challenge | Solution |
|-----------|---------|
| 10-20× Mega Sale spikes | Pre-provision 3× + tiered graceful degradation |
| Millions of orders/day | Sync ACK → async Kafka/SQS pipeline |
| 100M+ product catalog | ES + MySQL + Redis; ML quality cleanup |
| 200+ microservices | EKS + Istio + gRPC + Kafka |

### Tier 2-4 India Optimizations
| Challenge | Solution |
|-----------|---------|
| Slow 3G/4G networks | WebP, Brotli, lazy loading, blur placeholders |
| Low-end devices (2-3GB RAM) | < 15MB APK, < 100MB RAM, offline-first |
| Regional languages | Multi-language search, transliteration, voice search |
| Unstructured addresses | ML address standardization, SMS tracking |
| Cash on Delivery dominant | COD as default; build trust for online payments over time |

### Data & Architecture
| System | Choice | Why |
|--------|--------|-----|
| Language | Python → Go (hot services) | 3-5× latency reduction, 60% memory savings |
| Event bus | Kafka (partition by order_id/seller_id) | Decouple order from payment, logistics, notifications |
| Product search | Elasticsearch + multi-language | Hindi transliteration, voice, visual search |
| Logistics | ML-based multi-partner allocation | Optimize cost vs speed vs rural reach |

---

## 🗣️ How to Use Meesho Examples in Interviews

### Example Sentences
- "For Mega Sale traffic spikes, I'd use tiered graceful degradation — disable recommendations at 5×, chat at 10×, reviews at 20×, protecting the core checkout flow. Meesho uses this for their 10-20× Mega Blockbuster Sale."
- "For Tier 2-4 users on slow networks, every kilobyte matters: WebP images (30-50% savings), blur placeholders, lazy loading, Brotli compression. Meesho's app is < 15MB vs 100MB competitors."
- "Multi-language search with transliteration is essential — 80% of Meesho's users search in Hindi typed in English characters. Train spell correction on actual user queries, not dictionaries."
- "For unstructured Indian addresses ('near the temple, opposite big tree'), ML-based address standardization parses into geocoded locations. SMS-based tracking works on feature phones."
- "COD is the default in Tier 2-4 India (70%+ of orders). Design the system for COD first, then optimize online payments over time as trust builds."
- "ML-based catalog quality improvement at scale: auto-categorize, detect duplicates, enhance images — 100M products from millions of small sellers can't be manually curated."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 10 design decisions focused on social commerce, Tier 2-4 India, and Mega Sale scaling  
**Status**: Complete & Interview-Ready ✅