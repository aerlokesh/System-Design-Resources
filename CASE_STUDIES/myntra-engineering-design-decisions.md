# Myntra Engineering — Design Decisions & Tradeoffs

> **Purpose**: Real-world architectural decisions from Myntra's engineering blog, conference talks, and tech articles. Each entry: **problem → what they chose → why → tradeoff → interview use.**
>
> **Sources**: Myntra Engineering Blog (medium.com/myntra-engineering), Rootconf/JSFoo talks, tech articles
>
> **Context**: Myntra is India's largest fashion e-commerce platform (Flipkart subsidiary). Their biggest challenges: **End of Reason Sale (EORS)** — 100× traffic spikes, plus fashion-specific problems like visual search, size recommendation, and return rate reduction.

---

## Table of Contents

1. [EORS Sale — Extreme Traffic Scaling](#1-eors-scaling)
2. [Visual Search — "Find Similar" Using Images](#2-visual-search)
3. [Size & Fit Recommendation — Reducing Returns](#3-size-recommendation)
4. [Personalized Feed — Fashion Discovery](#4-personalized-feed)
5. [Catalog Management — Fashion-Specific Challenges](#5-catalog-management)
6. [Event-Driven Architecture with Kafka](#6-kafka-events)
7. [Search & Ranking — Fashion Intent](#7-search-ranking)
8. [App Performance — Speed Optimization](#8-app-performance)
9. [Logistics — Fashion Returns & Reverse Logistics](#9-reverse-logistics)
10. [A/B Testing — Data-Driven Fashion](#10-ab-testing)

---

## 1. EORS — End of Reason Sale Scaling

**Source**: Myntra engineering talks on EORS architecture

### The Problem
Myntra's EORS (End of Reason Sale) is India's biggest fashion sale:
- **Normal day**: Moderate traffic
- **EORS peak**: 50-100× traffic spike in first hours
- **Unique to fashion**: Users browse extensively (view 20-50 products) before buying → read-heavy spike is larger than order spike
- **Duration**: Multi-day sale but first 6 hours = majority of traffic

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Pre-provisioning** | Scale to 5× expected peak 24h before sale | Reactive auto-scaling | EORS traffic is predictable; pre-provision avoids cold-start failures |
| **CDN** | Cache all product listing pages, images, category pages on CDN | Dynamic generation | CDN serves 85%+ of browse traffic; origin handles cart + checkout only |
| **Inventory** | Redis for hot inventory + MySQL source of truth | MySQL only | Popular items sell out fast; Redis gives atomic decrement for concurrent purchases |
| **Queue** | Kafka for order processing (absorbs burst) | Synchronous | Sync ACK to user (< 500ms) + async order pipeline |
| **Degradation** | Disable recommendations, style tips, social proof during peak | All features on | Core browse + search + checkout must be protected |
| **Read scaling** | Aggressive caching: product data (Redis 30s TTL), category pages (CDN 60s) | No caching | Fashion browsing = 20-50 product views per user; each view must be fast |

### Tradeoff
- ✅ CDN absorbs 85%+ of browse traffic (fashion is read-heavy)
- ✅ Redis inventory prevents overselling during flash deals
- ✅ Degradation protects checkout flow
- ❌ CDN-cached prices may be briefly stale during dynamic repricing
- ❌ Pre-provisioning costs money for off-peak capacity
- ❌ Disabled recommendations reduce cross-sell during sale

### Interview Use
> "Fashion e-commerce is uniquely read-heavy — users browse 20-50 products per purchase (vs 2-3 for electronics). For EORS, I'd cache aggressively on CDN (product pages, images, category lists) — 85%+ of traffic never hits origin. Only cart + checkout + inventory hits backend services. Redis for hot inventory (atomic decrement), Kafka for async order processing."

---

## 2. Visual Search — "Find Similar" Using Images

**Source**: Myntra engineering blog on visual search

### Problem
Users see a dress they like (on Instagram, on the street, in a movie) and want to find similar items on Myntra. Text search can't describe "that blue floral maxi dress with ruffled sleeves" — visual search can.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Model** | CNN (ResNet/EfficientNet) → embedding vector per image | Text-based attribute matching | Visual features capture style, pattern, silhouette that text attributes miss |
| **Similarity** | Approximate Nearest Neighbor (FAISS) in embedding space | Exact search | ANN finds similar images in milliseconds across 10M+ products |
| **Embeddings** | Pre-compute embedding for every catalog image; store in vector DB | Compute at query time | Pre-computed: query is just ANN lookup (< 100ms); compute would be seconds per query |
| **Index** | FAISS (Facebook's vector search library) | Custom implementation | FAISS: proven, GPU-accelerated, handles billions of vectors |
| **Attributes** | Extract fashion attributes from images (color, pattern, sleeve type, neckline) | Manual tagging | ML-extracted attributes enable filter + visual search combination |
| **User flow** | Upload photo / screenshot → extract embedding → ANN search → show similar products | Text description of what user saw | Visual is natural — "I want something like THIS" (points at photo) |

### Architecture
```
User uploads photo of a dress:
  1. Image preprocessing (resize, crop to fashion item)
  2. CNN model extracts embedding vector (512/1024 dimensions)
  3. ANN search in FAISS index (10M+ product embeddings) → top 50 similar
  4. Filter by availability, size, price range
  5. Return results with similarity scores (< 500ms total)

Catalog indexing (offline):
  - For each product image → extract embedding → add to FAISS index
  - Also extract fashion attributes (color, pattern, category) → store in Elasticsearch
  - Re-index new products daily; incremental updates via Kafka
```

### Tradeoff
- ✅ Users can search by photo — more natural than describing fashion
- ✅ Pre-computed embeddings: query is < 100ms ANN lookup
- ✅ Fashion attribute extraction enables faceted visual search (similar dress + blue + under ₹2000)
- ❌ CNN embeddings are opaque — hard to explain "why is this similar?"
- ❌ Works poorly for highly stylized or artistic photos (model trained on catalog images)
- ❌ Pre-computing embeddings for 10M+ products is compute-intensive

### Interview Use
> "For visual search in fashion ('find similar to this photo'), I'd use CNN embeddings + ANN search (FAISS). Pre-compute an embedding vector for every catalog image (offline). At query time: extract embedding from user's photo → ANN search across 10M+ product embeddings → return top 50 similar in < 500ms. Myntra combines visual similarity with fashion attribute filters (color, pattern, price) for refined results."

---

## 3. Size & Fit Recommendation — Reducing Returns

**Source**: Myntra engineering blog on size recommendation

### Problem
Size is the #1 reason for returns in fashion e-commerce (30-40% of returns). "Size M" means different things for different brands. Users don't know their exact measurements. Wrong size = return = expensive reverse logistics.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Approach** | ML model: user body measurements + brand size chart + purchase/return history | Static size chart only | ML learns that "User X returns M in Brand A but keeps L in Brand B" |
| **Features** | Past purchases, returns, browsing (size selected), stated body measurements, brand patterns | Stated measurements only | Purchase/return history is the strongest signal — actual behavior > stated preference |
| **Recommendation** | "Your recommended size for this product is L" (personalized per user per product) | Generic size chart | Personalized: considers user's body shape + this brand's specific sizing |
| **Return prediction** | Predict return probability per size → recommend the size with lowest return probability | Recommend most common size | Optimizing for "will keep" not just "might fit" |
| **Confidence** | Show confidence: "High confidence — based on 5 past purchases from this brand" | Show recommendation without context | Confidence helps user trust the recommendation |
| **Data collection** | Ask for body measurements at onboarding (optional) + learn from behavior | Require measurements | Optional: don't force; learn from purchase/return patterns over time |

### Results
- **15-20% reduction** in size-related returns
- **Higher conversion**: users more confident → more purchases
- **Better NPS**: fewer returns = happier customers

### Tradeoff
- ✅ 15-20% fewer size-related returns (huge logistics cost saving)
- ✅ Higher conversion (confidence → purchase)
- ❌ Needs purchase history (cold-start for new users)
- ❌ Brand sizing inconsistency makes cross-brand recommendations hard
- ❌ Body measurement data is sensitive (privacy concerns)

### Interview Use
> "For size recommendation in fashion e-commerce, I'd use ML trained on purchase and return history — not just static size charts. The model learns that 'User X returns M in Brand A but keeps L in Brand B.' Predict return probability per size and recommend the one with lowest return probability. Myntra reduced size-related returns by 15-20% with this approach — each prevented return saves ₹200-500 in reverse logistics."

---

## 4. Personalized Feed — Fashion Discovery

**Source**: Myntra engineering blog on personalization

### Problem
Fashion is discovery-driven — users don't always know what they want. The feed must surface products that match the user's style (casual, formal, trendy, classic), price sensitivity, brand preferences, and recent browsing patterns.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Architecture** | Two-stage: candidate generation (collaborative + content-based) → ML ranking | Single-stage | Two-stage reduces computation: filter millions to thousands, then rank top 100 |
| **Style profile** | ML-inferred user style from browsing + purchase history | User-declared preferences | Actions speak louder than declarations; users browse "trendy" even if they declare "classic" |
| **Freshness** | Boost new arrivals (fashion is seasonal; old inventory is stale) | Pure relevance | Fashion is inherently temporal; last season's clothes are less relevant |
| **Diversity** | Inject diversity (don't show 10 blue t-shirts in a row) | Pure relevance score | Fashion browsing requires variety; monotony kills engagement |
| **Visual** | Use product image embeddings as features (not just text attributes) | Text/attribute features only | Visual similarity captures style nuances text misses |

### Tradeoff
- ✅ Style-inferred profiles better than declared preferences
- ✅ New arrival boosting keeps feed fresh and seasonal
- ✅ Visual embeddings capture style nuances
- ❌ Cold-start: new users get generic popular items
- ❌ Diversity injection may reduce individual item relevance
- ❌ Seasonal fashion means models need frequent retraining

### Interview Use
> "For a fashion discovery feed, I'd boost new arrivals (fashion is seasonal) and inject diversity (don't show 10 blue t-shirts in a row). User style profile inferred from browsing + purchase behavior — not declared preferences (users browse differently than they claim). Visual embeddings capture style nuances text can't describe. Two-stage: candidate generation → ML ranking with freshness and diversity rules."

---

## 5. Catalog Management — Fashion-Specific

**Source**: Myntra engineering blog on catalog quality

### Problem
Myntra hosts 10M+ products from thousands of brands. Fashion catalog has unique challenges: subjective attributes (is this "casual" or "smart casual"?), poor seller-provided descriptions, duplicate listings with different photos, and rapidly changing trends.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Attribute extraction** | ML models extract attributes from images: color, pattern, sleeve type, fabric, occasion | Manual tagging | 10M products × 20+ attributes → manual tagging is impossible at scale |
| **Image quality** | Automated scoring: check resolution, detect multiple products, detect mannequin vs model | Accept all images | Product images directly impact conversion; low quality = low conversion |
| **Dedup** | Image similarity + attribute matching to group duplicates | Allow duplicates | Same product from different sellers → group as one listing with multiple sellers |
| **Trend tagging** | ML model trained on social media + purchase trends to tag "trending" products | Manual trend curation | Trends change weekly in fashion; ML keeps up; manual can't |
| **Standardization** | Normalize brand-specific terms: "kurta" = "kurti" = "kurtis" | Keep as-is | Search and filters must work across inconsistent brand naming |

### Tradeoff
- ✅ ML attribute extraction scales to 10M products
- ✅ Automated quality scoring maintains catalog standard
- ✅ Trend detection keeps catalog seasonally relevant
- ❌ ML attribute extraction isn't perfect (subjective attributes like "trendy")
- ❌ Image quality scoring may reject legitimate product photos
- ❌ Fashion taxonomy is inherently fuzzy ("smart casual" vs "business casual")

### Interview Use
> "For a fashion catalog (10M+ products), ML-based attribute extraction from images is essential — extract color, pattern, sleeve type, occasion automatically. Automated image quality scoring (resolution, composition) maintains standards. Fashion-specific challenge: subjective taxonomy ('smart casual' vs 'business casual') requires fuzzy matching and normalization."

---

## 6. Event-Driven Architecture with Kafka

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Event bus** | Kafka (shared cluster with Flipkart ecosystem) | Separate messaging | Leverage Flipkart's Kafka infrastructure; shared events across platforms |
| **Key events** | product.viewed, product.added_to_cart, order.placed, order.returned, user.style_profile_updated | Single topic | Separate topics per event type; consumers subscribe independently |
| **Consumers** | Recommendations, search index, analytics, inventory, notification, fraud | Shared | Each domain processes at its own pace |
| **Fashion-specific** | style_event (user interacted with a fashion trend) → update recommendation models | N/A | Fashion recommendation needs real-time style signal updates |

### Interview Use
> "Kafka as the central event bus — product.viewed events feed recommendations, search indexing, and analytics independently. Fashion-specific: style_event (user interaction with trends) updates the recommendation model in near real-time. Shared Kafka infrastructure with parent Flipkart reduces operational cost."

---

## 7. Search & Ranking — Fashion Intent

**Source**: Myntra engineering blog on search

### Problem
Fashion search queries are ambiguous: "party dress" means different things to different people. "Red dress" could be a gown, a midi, or a t-shirt dress. Must understand fashion intent, not just keyword match.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Query understanding** | Fashion-aware NLP: parse "red party dress under 2000" → attributes + price filter | Keyword matching | Structured understanding enables faceted search + better ranking |
| **Ranking** | ML model: relevance + style match + popularity + freshness + margin | Text relevance (BM25) | Fashion ranking considers trendiness, style fit, and inventory health |
| **Personalization** | "Red dress" shows different styles for different users based on their style profile | Same results for all | User who browses "western" sees different "red dress" than user who browses "ethnic" |
| **Spelling** | Fashion-specific spell correction: "lehnga" → "lehenga", "kurtha" → "kurta" | Generic spell check | Fashion terms are often misspelled with Indian English variants |
| **Filters** | Fashion-specific facets: color, size, brand, occasion, discount%, pattern, fabric | Generic price + rating | Fashion users filter by occasion, pattern, fabric — not just price |

### Tradeoff
- ✅ Fashion-aware NLP handles ambiguous queries better than keyword matching
- ✅ Personalized results based on user's style profile
- ✅ Fashion-specific spell correction handles Indian English variants
- ❌ Fashion NLP requires domain-specific training data
- ❌ Personalization can feel like a filter bubble
- ❌ Fashion-specific facets require rich attribute data (from ML extraction)

### Interview Use
> "Fashion search needs domain-specific NLP: 'party dress under 2000' → occasion: party, category: dress, price: < ₹2000. Personalized ranking shows different 'red dress' results for different style profiles. Fashion-specific spell correction handles Indian English variants ('lehnga' → 'lehenga'). The ranking model considers trendiness and freshness — stale fashion is dead fashion."

---

## 8. App Performance

**Source**: Myntra engineering blog on mobile performance

### Problem
Fashion browsing involves viewing many images (20-50 product cards per session). Each product card has a large hero image. Slow image loading = users leave. Myntra must optimize for India's 4G/LTE networks.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Image format** | WebP (30-50% smaller than JPEG) | JPEG everywhere | Bandwidth savings critical for image-heavy fashion browsing |
| **Lazy loading** | Load images only when scrolling near viewport | Load all images upfront | Fashion pages have 50+ product images; loading all wastes bandwidth |
| **Image quality** | Adaptive: high quality on WiFi, compressed on cellular | Fixed quality | Save bandwidth on cellular (expensive data plans in India) |
| **Skeleton screens** | Show gray placeholder → fade in product card | Blank → sudden appearance | Perceived performance is better — user sees the layout immediately |
| **Prefetching** | Prefetch next page of results when user scrolls to 80% | Load on demand only | Seamless infinite scroll — next page ready before user scrolls there |
| **Bundle size** | Code splitting: load fashion-specific features on demand | Single large bundle | Homepage loads fast; visual search, AR try-on loaded only when accessed |

### Interview Use
> "For image-heavy fashion browsing (50+ product images per page), I'd use WebP (30-50% smaller), lazy loading (only load visible images), adaptive quality (compress on cellular, high quality on WiFi), and skeleton screens for perceived performance. Prefetch the next page at 80% scroll position for seamless infinite scroll. Myntra optimizes for India's cellular networks where every KB costs the user money."

---

## 9. Reverse Logistics — Fashion Returns

**Source**: Myntra engineering blog on returns

### Problem
Fashion has the highest return rate in e-commerce (20-30%). Common reasons: wrong size (30-40%), quality different from photo (20%), style different from expectation (20%), ordered multiple sizes to try (10%). Returns are expensive — pickup, transport, quality check, restocking.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Return prediction** | ML model predicts return probability at order time | No prediction | Flag high-return-probability orders: suggest size exchange instead of buy-and-return |
| **Size recommendation** | Reduce wrong-size returns via ML size recommendation (see #3) | Let users guess | 15-20% fewer size returns; biggest single improvement |
| **Try & Buy** | Allow trying before paying (selected products, selected users) | Pay upfront only | Reduces "ordered 3 sizes, return 2" pattern; user pays only for what they keep |
| **Quality check** | ML image classification: is returned item in sellable condition? | Manual inspection | Speed up return processing; manual inspection is a bottleneck |
| **Restocking speed** | Fast restocking pipeline: return received → quality check → relisted in < 24h | Multi-day restocking | Faster relisting = more sellable days (fashion is seasonal) |
| **Return analytics** | Track return rate per brand + per product + per size | Aggregate return rate | Identify problematic products (sizing issue) vs problematic users (serial returners) |

### Tradeoff
- ✅ ML return prediction enables proactive intervention (suggest exchange before purchase)
- ✅ Size recommendation reduces the #1 return reason by 15-20%
- ✅ Fast restocking maximizes sellable window for seasonal fashion
- ❌ "Try & Buy" has higher return rates (but converts users who wouldn't have purchased otherwise)
- ❌ Return quality ML misclassifies some items (needs human review fallback)
- ❌ Serial returner detection must balance fraud prevention with customer experience

### Interview Use
> "Fashion returns (20-30% of orders) are the biggest cost challenge. I'd attack the #1 reason (wrong size: 30-40% of returns) with ML size recommendation — Myntra reduced size returns by 15-20%. Predict return probability at order time: flag high-risk orders and suggest size exchange. Fast restocking (< 24h from return to relisted) maximizes the selling window for seasonal fashion."

---

## 10. A/B Testing — Data-Driven Fashion

**Source**: Myntra engineering blog on experimentation

### Problem
Fashion is subjective — "does a larger product image increase conversion?" "does showing outfit combinations increase cart value?" Every UX change needs testing because intuition is unreliable in fashion.

### Design Decisions

| Decision | Chose | Alternative | Why |
|----------|-------|-------------|-----|
| **Platform** | Centralized experimentation platform | Each team tests independently | Consistent methodology, no interfering experiments, shared analysis tools |
| **Assignment** | User-level (not session) | Session-level | Consistent experience across sessions; accurate measurement |
| **Metrics** | Conversion rate + cart value + return rate (not just clicks) | Click-through only | In fashion, clicks without conversion or with returns are bad metrics |
| **Duration** | Run until statistical significance (typically 1-4 weeks) | Fixed 1-week test | Fashion has weekly patterns (weekend vs weekday); need ≥ 1 full cycle |
| **Fashion-specific tests** | Test visual merchandising: product image size, model vs mannequin, outfit suggestions | Only technical tests | Visual presentation is critical in fashion; A/B test merchandising decisions |

### Tradeoff
- ✅ Data-driven fashion merchandising (test image sizes, layouts, outfit suggestions)
- ✅ Measuring conversion + return rate (not just engagement)
- ❌ Fashion A/B tests take longer (weekly seasonality)
- ❌ Some fashion decisions are brand/editorial (not everything should be A/B tested)

### Interview Use
> "For fashion e-commerce, A/B testing must measure conversion + return rate — not just clicks. A change that increases clicks but increases returns is bad. Fashion has weekly seasonality (weekday vs weekend shopping patterns), so tests need ≥ 1 full weekly cycle. Myntra A/B tests visual merchandising: product image sizes, model vs mannequin photos, outfit combination suggestions."

---

## 🎯 Quick Reference: Myntra's Key Decisions

### Sale & Traffic
| Challenge | Solution |
|-----------|---------|
| EORS 50-100× spike | CDN (85% of browse traffic), Redis inventory, Kafka orders |
| Read-heavy fashion browsing | Aggressive caching: 30s Redis, 60s CDN; product images on CDN |
| Feature degradation | Disable recommendations, style tips during peak |

### Fashion-Specific ML
| System | Decision | Impact |
|--------|----------|--------|
| Visual search | CNN embeddings + FAISS ANN | Find similar products from photos |
| Size recommendation | ML on purchase/return history | 15-20% fewer size-related returns |
| Catalog attributes | ML extraction from images | 10M products, 20+ attributes auto-tagged |
| Return prediction | ML at order time | Proactive size exchange suggestion |
| Feed personalization | Inferred style profile + freshness + diversity | Discovery-driven fashion browsing |

### India-Specific
| Challenge | Solution |
|-----------|---------|
| Image-heavy browsing on 4G | WebP, lazy loading, adaptive quality, prefetching |
| Fashion-specific search | Hindi transliteration, fashion NLP, style-personalized ranking |
| High return rates (20-30%) | Size recommendation, return prediction, fast restocking |
| Seasonal fashion | New arrival boosting, trend detection ML, fast restocking |

---

## 🗣️ How to Use Myntra Examples in Interviews

### Example Sentences
- "Fashion e-commerce is uniquely read-heavy (20-50 views per purchase). CDN must serve 85%+ of browse traffic. Only cart + checkout hits the origin."
- "Visual search: CNN extracts embedding from user's photo → ANN search (FAISS) across 10M product embeddings → similar products in < 500ms."
- "Size recommendation ML reduced returns 15-20% — trained on purchase + return history, not just size charts. Predict return probability per size."
- "Fashion ranking considers freshness (seasonal) + diversity (don't show 10 similar items) + style profile (personalized). Stale fashion is dead fashion."
- "For reverse logistics, predict return probability at order time → suggest size exchange proactively. Fast restocking (< 24h) maximizes seasonal selling window."
- "Fashion A/B testing must measure conversion + return rate, not just clicks. A change that increases clicks but increases returns is net negative."

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Coverage**: 10 design decisions focused on fashion e-commerce, visual ML, and India-specific challenges  
**Status**: Complete & Interview-Ready ✅