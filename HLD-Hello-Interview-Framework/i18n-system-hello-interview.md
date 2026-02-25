# Design an Internationalization (i18n) System - Hello Interview Framework

> **Question**: Design an internationalization (i18n) system that translates static UI elements like buttons, labels, and system messages into hundreds of languages while keeping user-generated content in its original form. The system must support correct formatting, pluralization, and locale-aware behavior with fast iteration by non-engineers.
>
> **Asked at**: Meta
>
> **Difficulty**: Medium-Hard | **Level**: Senior/Staff

## Table of Contents
- [1️⃣ Requirements](#1️⃣-requirements)
- [2️⃣ Core Entities](#2️⃣-core-entities)
- [3️⃣ API Design](#3️⃣-api-design)
- [4️⃣ Data Flow](#4️⃣-data-flow)
- [5️⃣ High-Level Design](#5️⃣-high-level-design)
- [6️⃣ Deep Dives](#6️⃣-deep-dives)

---

## 1️⃣ Requirements

### Functional Requirements

#### Core Requirements (P0)
1. **String Translation**: Translate static UI strings (buttons, labels, error messages, system notifications) into 100+ languages. Strings identified by unique keys (e.g., `home.feed.new_posts`).
2. **Locale Resolution**: Determine user's locale from: explicit preference > browser Accept-Language header > geo-IP. Fallback chain: regional variant (pt-BR) → base language (pt) → default (en).
3. **Pluralization**: Handle language-specific plural forms (English: 1 item / 2 items; Arabic: 6 forms; Russian: 3 forms). Use ICU MessageFormat for plural rules.
4. **String Interpolation**: Support dynamic values in translations: `"You have {count} new messages from {name}"` → `"Tienes {count} mensajes nuevos de {name}"`.
5. **Translation Authoring Workflow**: Non-engineer translators author/review translations via a Translation Management System (TMS). Strings flow: developer → TMS → translator → review → publish.
6. **Fast & Safe Rollout**: Translations published without code deploys. Rollout to 1% → 10% → 100% with instant rollback if errors detected.

#### Nice to Have (P1)
- Right-to-left (RTL) layout support (Arabic, Hebrew).
- Date/time/number/currency formatting per locale (`$1,234.56` vs `1.234,56 €`).
- Machine translation (MT) for initial drafts, human review for production.
- Context/screenshots for translators (show where string appears in UI).
- String change detection (notify translators when source string changes).
- A/B testing of translations (test which translation drives better engagement).

#### Below the Line (Out of Scope)
- User-generated content translation (posts, comments — those stay in original language).
- Full CMS (content management system) for articles/help pages.
- Accessibility (a11y) beyond text direction.
- Font/typography per language (CJK font loading).

### Non-Functional Requirements

| Attribute | Target | Rationale |
|-----------|--------|-----------|
| **String Fetch Latency** | < 50ms P99 | Must not delay page render |
| **Bundle Size** | < 50 KB per locale per page | Mobile performance; lazy load per page |
| **Translation Freshness** | New translations live within 5 minutes | Translators iterate fast without deploys |
| **Availability** | 99.99% | If i18n fails, entire UI shows raw keys — unacceptable |
| **Supported Locales** | 100+ languages, 200+ locale variants | Global product (Facebook-scale) |
| **String Scale** | 500K unique source strings | Large product surface |
| **Consistency** | Strong for authoring, eventual for serving (< 5 min) | Translators see changes immediately; users get them within minutes |

### Capacity Estimation

```
Strings:
  Total source strings: 500K
  Translations per string: 100 languages avg = 50M translation entries
  Average string length: 100 bytes
  Total translation data: 50M × 100 bytes = ~5 GB

Client requests:
  DAU: 500M users
  Page loads per user per day: 10
  String bundle requests per day: 500M × 10 = 5B
  Requests per second: ~58K sustained, 500K peak
  Bundle size: 200 strings × 100 bytes = ~20 KB per page bundle (compressed)

CDN bandwidth:
  5B requests × 20 KB = ~100 PB/day → BUT 95%+ cache hit rate
  CDN egress: ~5 PB/day (5% miss rate)
  
Translation authoring:
  Active translators: 5,000
  Translations per day: 50K new/updated strings
  String changes: ~1K source string changes/day (product development)

Storage:
  Translation DB: ~5 GB (all translations, all versions)
  CDN cache: ~500 GB (per-locale, per-page bundles × edge locations)
  Version history: ~50 GB (all string versions for audit/rollback)
```

---

## 2️⃣ Core Entities

### Entity 1: Source String
```java
public class SourceString {
    private final String stringKey;         // "home.feed.new_posts" (hierarchical key)
    private final String namespace;         // "home", "settings", "errors" (page/feature grouping)
    private final String sourceText;        // English source: "You have {count} new posts"
    private final String description;       // Context for translators: "Shown on home feed banner"
    private final String screenshotUrl;     // Screenshot showing where this string appears
    private final StringType type;          // PLAIN, ICU_MESSAGE, PLURAL
    private final List<String> placeholders;// ["count", "name"]
    private final int version;              // Incremented on source text change
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String updatedBy;         // Developer who last modified
}

public enum StringType { PLAIN, ICU_MESSAGE, PLURAL, GENDER_SELECT }
```

### Entity 2: Translation
```java
public class Translation {
    private final String translationId;     // UUID
    private final String stringKey;         // FK to SourceString
    private final String locale;            // "es-ES", "pt-BR", "zh-CN"
    private final String translatedText;    // "Tienes {count} publicaciones nuevas"
    private final TranslationStatus status; // DRAFT, REVIEWED, APPROVED, PUBLISHED
    private final int sourceVersion;        // Which source version this translates
    private final String translatedBy;      // Translator ID
    private final String reviewedBy;        // Reviewer ID
    private final Instant translatedAt;
    private final Instant publishedAt;
}

public enum TranslationStatus { DRAFT, REVIEWED, APPROVED, PUBLISHED, DEPRECATED }
```

### Entity 3: Locale
```java
public class Locale {
    private final String localeCode;        // "pt-BR", "en-US", "ar-SA"
    private final String language;          // "pt"
    private final String region;            // "BR"
    private final String displayName;       // "Português (Brasil)"
    private final TextDirection direction;  // LTR or RTL
    private final String fallbackLocale;    // "pt" → "en" (fallback chain)
    private final PluralRules pluralRules;  // Language-specific plural categories
    private final boolean enabled;
}

public enum TextDirection { LTR, RTL }
```

### Entity 4: String Bundle (per page/namespace per locale)
```java
public class StringBundle {
    private final String bundleId;          // "home:es-ES:v42"
    private final String namespace;         // "home"
    private final String locale;            // "es-ES"
    private final int version;              // Bundle version (incremented on any string change)
    private final Map<String, String> strings; // { "home.feed.new_posts": "Tienes {count}..." }
    private final String hash;              // Content hash for cache busting
    private final Instant generatedAt;
}
```

### Entity 5: Rollout Configuration
```java
public class RolloutConfig {
    private final String rolloutId;
    private final int bundleVersion;
    private final double rolloutPercentage; // 0.0 to 1.0 (1%, 10%, 50%, 100%)
    private final List<String> targetLocales; // Which locales this rollout applies to
    private final Instant startedAt;
    private final RolloutStatus status;     // ACTIVE, PAUSED, ROLLED_BACK, COMPLETED
}
```

### Entity 6: Plural Rule
```java
public class PluralRule {
    private final String locale;
    private final Map<PluralCategory, String> forms;
    // English: { ONE: "{count} item", OTHER: "{count} items" }
    // Arabic: { ZERO: "لا عناصر", ONE: "عنصر واحد", TWO: "عنصران", FEW: "{count} عناصر", MANY: "{count} عنصرًا", OTHER: "{count} عنصر" }
}

public enum PluralCategory { ZERO, ONE, TWO, FEW, MANY, OTHER }
```

---

## 3️⃣ API Design

### 1. Get String Bundle (Client — Hot Path)
```
GET /api/v1/strings/{namespace}?locale=es-ES&version=41

Response (200 OK):
{
  "namespace": "home",
  "locale": "es-ES",
  "version": 42,
  "strings": {
    "home.feed.new_posts": "Tienes {count} publicaciones nuevas",
    "home.feed.refresh": "Actualizar",
    "home.feed.empty": "No hay publicaciones aún",
    "home.nav.search": "Buscar",
    "home.nav.profile": "Perfil"
  },
  "hash": "a3f2b1c4",
  "cache_control": "public, max-age=300"
}
```

> Served from CDN edge cache. Client caches locally (IndexedDB). Version check for updates.

### 2. Resolve Locale
```
GET /api/v1/locale/resolve
Headers: Accept-Language: pt-BR,pt;q=0.9,en;q=0.8

Response (200):
{
  "resolved_locale": "pt-BR",
  "fallback_chain": ["pt-BR", "pt", "en"],
  "direction": "LTR",
  "available_locales": ["pt-BR", "pt", "en-US", "es-ES", ...]
}
```

### 3. Create/Update Source String (Developer API)
```
POST /api/v1/strings

Request:
{
  "key": "home.feed.new_posts",
  "namespace": "home",
  "source_text": "You have {count, plural, one {# new post} other {# new posts}}",
  "type": "PLURAL",
  "description": "Banner text shown when new posts are available",
  "placeholders": ["count"],
  "screenshot_url": "https://screenshots.internal/home-feed-banner.png"
}

Response (201): { "key": "home.feed.new_posts", "version": 3 }
```

### 4. Submit Translation (Translator API via TMS)
```
POST /api/v1/translations

Request:
{
  "string_key": "home.feed.new_posts",
  "locale": "es-ES",
  "translated_text": "{count, plural, one {Tienes # publicación nueva} other {Tienes # publicaciones nuevas}}",
  "source_version": 3
}

Response (201): { "translation_id": "tr_001", "status": "DRAFT" }
```

### 5. Publish Translations (Trigger Bundle Regeneration)
```
POST /api/v1/bundles/publish

Request:
{
  "namespaces": ["home"],
  "locales": ["es-ES", "pt-BR"],
  "rollout_percentage": 0.1
}

Response (202): { "rollout_id": "roll_001", "status": "ACTIVE", "percentage": 10 }
```

### 6. Format String (Server-Side — for emails, push notifications)
```
POST /api/v1/strings/format

Request:
{
  "key": "home.feed.new_posts",
  "locale": "es-ES",
  "params": { "count": 5 }
}

Response (200): { "formatted": "Tienes 5 publicaciones nuevas" }
```

---

## 4️⃣ Data Flow

### Flow 1: Client Fetching Translations (Hot Path)
```
1. User opens app / navigates to page
   ↓
2. Client checks local cache (IndexedDB/localStorage):
   a. Has bundle for this namespace + locale + version? → Use cached
   b. No cache or stale version → fetch from server
   ↓
3. Client: GET /api/v1/strings/home?locale=es-ES&version=41
   ↓
4. CDN Edge:
   a. Cache HIT (95% of requests) → return cached bundle (< 10ms)
   b. Cache MISS → forward to Origin
   ↓
5. Origin (String Service):
   a. Read from Redis cache (pre-built bundles)
   b. If Redis miss → build from DB + cache
   c. Return bundle with cache headers
   ↓
6. Client receives bundle → store in local cache → render UI with translations
   ↓
7. For each UI element: t("home.feed.new_posts", { count: 5 })
   → ICU MessageFormat engine evaluates plurals/interpolation
   → Renders: "Tienes 5 publicaciones nuevas"
```

### Flow 2: Translation Authoring & Publishing
```
1. Developer adds new string key in code: t("home.feed.new_posts", ...)
   ↓
2. CI/CD extracts string keys → pushes to Translation DB
   ↓
3. TMS (Translation Management System):
   a. Translators see new strings needing translation
   b. Context: source text, description, screenshot, where it appears
   c. Translator writes translation → status: DRAFT
   d. Reviewer approves → status: APPROVED
   ↓
4. Publish trigger (manual or automated):
   a. Build new string bundles for affected namespaces + locales
   b. Upload bundles to CDN (with new version hash)
   c. Invalidate old CDN cache for affected bundles
   d. Rollout: 1% → 10% → 100% (check error rates between)
   ↓
5. Clients fetch new version on next page load (version check)
   Latency: translator submits → user sees: < 5 minutes
```

### Flow 3: Locale Fallback Chain
```
1. User's locale: "pt-BR" (Brazilian Portuguese)
   ↓
2. Client requests bundle for namespace "home", locale "pt-BR"
   ↓
3. String Service resolves each key:
   a. Key "home.feed.new_posts":
      - Check pt-BR translation → found! Use it.
   b. Key "home.settings.beta_feature":
      - Check pt-BR → NOT FOUND
      - Fallback to "pt" (generic Portuguese) → found! Use it.
   c. Key "home.admin.debug_mode":
      - Check pt-BR → NOT FOUND
      - Check pt → NOT FOUND
      - Fallback to "en" (default) → found! Use it.
   ↓
4. Bundle returned with mix of sources (pt-BR + pt + en fallbacks)
   Client renders correctly; user never sees raw string keys
```

### Flow 4: Pluralization with ICU MessageFormat
```
Source string (English):
  "{count, plural, one {You have # new message} other {You have # new messages}}"

Spanish (es-ES) — 2 plural forms:
  "{count, plural, one {Tienes # mensaje nuevo} other {Tienes # mensajes nuevos}}"

Arabic (ar-SA) — 6 plural forms:
  "{count, plural,
    zero {ليس لديك رسائل جديدة}
    one {لديك رسالة جديدة واحدة}
    two {لديك رسالتان جديدتان}
    few {لديك # رسائل جديدة}
    many {لديك # رسالة جديدة}
    other {لديك # رسالة جديدة}}"

Evaluation: t("key", { count: 3 }, locale="ar-SA")
  → PluralCategory for 3 in Arabic = "few"
  → "لديك 3 رسائل جديدة"
```

---

## 5️⃣ High-Level Design

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐                               │
│  │ Web (React)│  │ iOS App   │  │ Android   │                               │
│  │            │  │           │  │ App       │                               │
│  │ t("key")  │  │ NSLocal.. │  │ getString │                               │
│  │ ↓ ICU fmt │  │           │  │           │                               │
│  │ Local     │  │ Local     │  │ Local     │                               │
│  │ bundle    │  │ bundle    │  │ bundle    │                               │
│  │ cache     │  │ cache     │  │ cache     │                               │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘                               │
└────────┼───────────────┼──────────────┼──────────────────────────────────────┘
         └───────────────┼──────────────┘
                         │ GET /strings/{ns}?locale=xx
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CDN EDGE (CloudFront / Fastly)                       │
│                                                                               │
│  • Cache string bundles per namespace + locale + version                     │
│  • 95%+ hit rate → < 10ms for cached bundles                                │
│  • Invalidated on publish (new version hash)                                │
│  • ~500 GB cached across edge locations                                     │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ Cache MISS (5%)
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        STRING SERVICE (Origin)                               │
│                        (Stateless, 30-50 pods)                               │
│                                                                               │
│  • Serve string bundles from Redis cache                                    │
│  • Build bundles on cache miss (DB → assemble → cache)                      │
│  • Locale resolution (Accept-Language → locale)                              │
│  • Fallback chain resolution                                                │
│  • ICU MessageFormat server-side rendering (for emails/push)                │
└──────────┬──────────────────────────────┬────────────────────────────────────┘
           │                              │
     ┌─────▼──────┐               ┌──────▼──────────┐
     │ BUNDLE     │               │ TRANSLATION DB   │
     │ CACHE      │               │                  │
     │            │               │ PostgreSQL       │
     │ Redis      │               │                  │
     │ Cluster    │               │ • source_strings │
     │            │               │ • translations   │
     │ Pre-built  │               │ • locales        │
     │ bundles    │               │ • versions       │
     │ ~5 GB      │               │                  │
     └────────────┘               │ ~5 GB            │
                                  └──────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                 TRANSLATION MANAGEMENT SYSTEM (TMS)                           │
│                                                                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐      │
│  │ STRING EXTRACTION │  │ TRANSLATOR UI    │  │ BUNDLE BUILDER       │      │
│  │                   │  │                  │  │                      │      │
│  │ • CI/CD scans code│  │ • Web UI for     │  │ • On publish: build  │      │
│  │   for t("key")    │  │   translators    │  │   bundles per ns     │      │
│  │ • Push new keys   │  │ • Context/screenshots│ │   per locale      │      │
│  │   to Translation  │  │ • Review workflow│  │ • Upload to CDN      │      │
│  │   DB              │  │ • MT suggestions │  │ • Invalidate cache   │      │
│  │                   │  │                  │  │ • Rollout controls   │      │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Purpose | Technology | Scaling |
|-----------|---------|------------|---------|
| **CDN Edge** | Cache and serve string bundles globally | CloudFront / Fastly | 95%+ hit rate, < 10ms |
| **String Service** | Bundle serving, locale resolution, fallback | Go on K8s | 30-50 pods |
| **Bundle Cache** | Pre-built bundles per namespace + locale | Redis Cluster | ~5 GB |
| **Translation DB** | Source strings, translations, versions | PostgreSQL | ~5 GB, replicated |
| **TMS (Translation Management)** | Authoring, review, publishing workflow | React + Java | Internal tool |
| **Bundle Builder** | Generate bundles on publish, upload to CDN | Job runner (K8s jobs) | On-demand |
| **String Extraction** | CI/CD pipeline extracting keys from code | CI plugin (Babel/custom) | Per-deploy |

---

## 6️⃣ Deep Dives

### Deep Dive 1: String Bundle Architecture — CDN-First for < 50ms Delivery

**The Problem**: 500K strings × 100 locales = 50M entries. We can't send all strings to every client. We must organize strings into small, page-specific bundles served from CDN edge.

```
Bundle strategy:
  - Strings organized into NAMESPACES by page/feature:
    "home" (200 strings), "settings" (150 strings), "search" (100 strings), etc.
  - Each namespace × locale = one bundle file: home.es-ES.v42.json
  - Bundle size: ~20 KB compressed (200 strings × 100 bytes avg)
  - Client loads only bundles for current page (lazy loading)
  
CDN caching:
  - URL: /strings/home.es-ES.a3f2b1c4.json (content hash in filename)
  - Cache-Control: public, max-age=31536000 (immutable — hash changes on update)
  - New version = new hash = new URL = old cache naturally evicted
  - No explicit invalidation needed (immutable URLs)

Client-side caching:
  - IndexedDB (web) or SQLite (mobile): store bundles locally
  - On app start: check if version has changed (lightweight version endpoint)
  - If version same → use local cache (zero network request)
  - If version changed → fetch new bundle (only changed namespaces)

Pre-warming:
  - On publish: pre-warm CDN by requesting bundles from each edge location
  - Ensures first user after publish gets cache HIT, not MISS
```

---

### Deep Dive 2: Pluralization Engine — ICU MessageFormat

**The Problem**: English has 2 plural forms (singular/plural). Arabic has 6. Russian has 3 with complex rules. The i18n system must evaluate correct plural form at runtime for any language.

```
ICU MessageFormat standard:
  {count, plural,
    =0 {No messages}
    one {# message}
    other {# messages}}

CLDR (Unicode Common Locale Data Repository) defines plural rules:
  English: one (n=1), other (everything else)
  Russian: one (n%10=1 && n%100≠11), few (n%10=2..4 && n%100≠12..14), many (n%10=0 || n%10=5..9 || n%100=11..14), other
  Arabic: zero (n=0), one (n=1), two (n=2), few (n%100=3..10), many (n%100=11..99), other

Client-side evaluation:
  1. Parse ICU message string into AST (at bundle load time, cached)
  2. On render: evaluate AST with params → select correct branch
  3. Libraries: FormatJS (React), ICU4J (Java), icu4c (C++)
  
  Example: t("key", { count: 3 }, locale="ru")
    → CLDR rule for Russian: 3 matches "few"
    → Select "few" branch: "# сообщения"
    → Replace #: "3 сообщения"

Performance: AST parsing ~0.1ms (cached); evaluation ~0.01ms per string
```

---

### Deep Dive 3: Translation Authoring Workflow — Non-Engineers Ship Translations

**The Problem**: 5,000 translators across 100+ languages need to translate 500K strings. They are NOT engineers. They need a simple UI with context, review workflow, and safe publishing.

```
Workflow: Developer → Extract → Translate → Review → Publish

1. DEVELOPER writes code: t("home.feed.new_posts", { count })
   → CI pipeline extracts string key + source text → pushes to Translation DB
   → New strings appear in TMS as "needs translation"

2. TRANSLATOR opens TMS UI:
   → Sees strings needing translation, grouped by namespace/priority
   → For each string: source text, description, screenshot, existing translations
   → Types translation → saves as DRAFT

3. REVIEWER (senior translator) reviews:
   → Checks grammar, placeholders, plural forms, context accuracy
   → Approves → status: APPROVED

4. PUBLISH (automated or manual):
   → Bundle Builder generates new bundles for affected namespace + locale
   → Uploads to CDN with new content hash
   → Rollout: 1% → 10% → 100%
   → Monitor: if error rate spikes (missing keys, rendering errors) → auto-rollback

5. STALE DETECTION:
   → When source text changes (new version), all translations marked "stale"
   → TMS highlights stale translations for re-review
   → Until re-translated: old translation still served (better than showing English)
```

---

### Deep Dive 4: Fallback Chain — Never Show Raw Keys

**The Problem**: Not every string is translated into every language. A new feature might only have English strings initially. The user must NEVER see a raw key like `home.feed.new_posts`.

```
Fallback resolution (per string):

1. Exact locale: "pt-BR" → check if translation exists
2. Base language: "pt" → check Portuguese (generic)
3. Related locale: "pt-PT" → check Portugal Portuguese (if configured)
4. Default language: "en" → always has translation (source language)
5. Raw key: NEVER returned to client (en is guaranteed fallback)

Implementation:
  Bundle building pre-resolves fallbacks:
    - For each string in namespace:
      - If pt-BR translation exists → use it
      - Else if pt translation exists → use it  
      - Else → use en (source)
    - Bundle for pt-BR already contains all fallbacks baked in
    - Client does NOT need to fetch multiple bundles or resolve fallbacks
    - Zero runtime fallback logic on client → simpler, faster

  Monitoring:
    - Track "fallback rate" per locale: % of strings served from fallback
    - Alert if fallback rate > 20% for a locale (translation coverage too low)
    - Dashboard: translation coverage per locale per namespace
```

---

### Deep Dive 5: Safe Rollout & Instant Rollback

**The Problem**: A bad translation can break UI (e.g., missing placeholder causes crash, extremely long string overflows layout, offensive mistranslation). Must roll out gradually and roll back instantly.

```
Rollout strategy:

1. VERSIONED BUNDLES: every publish creates a new version
   home.es-ES.v41.json (old) → home.es-ES.v42.json (new)
   Both versions exist on CDN simultaneously

2. GRADUAL ROLLOUT:
   - Rollout config: { version: 42, percentage: 10, locale: "es-ES" }
   - String Service: hash(user_id) % 100 < percentage → serve v42, else v41
   - Increase: 1% → 10% → 50% → 100% over hours/days

3. MONITORING during rollout:
   - Client-side error tracking: any rendering error with new bundle?
   - String rendering failures (missing placeholders, format errors)
   - User reports / support tickets for locale
   - A/B comparison: engagement metrics (old vs new translations)

4. INSTANT ROLLBACK:
   - Set rollout percentage to 0% → all users get previous version
   - Old version still cached on CDN (immutable URLs) → no CDN purge needed
   - Rollback latency: < 1 minute (just config change in Redis)

5. AUTOMATED SAFEGUARDS:
   - Pre-publish validation: check all placeholders present, no empty strings
   - Max string length check (prevent layout overflow)
   - Plural form completeness (all required categories present)
   - Profanity filter on translations (basic, human review for accuracy)
```

---

### Deep Dive 6: Locale-Aware Formatting — Dates, Numbers, Currency

**The Problem**: `1,234.56` in English = `1.234,56` in German = `1 234,56` in French. Dates: `01/10/2025` (US) = `10/01/2025` (UK) = `2025年1月10日` (Japan). The system must format all values correctly per locale.

```
Formatting types:

1. NUMBERS: Intl.NumberFormat (JS) / NumberFormat (Java)
   format(1234.56, "en-US") → "1,234.56"
   format(1234.56, "de-DE") → "1.234,56"
   format(1234.56, "fr-FR") → "1 234,56"

2. CURRENCY:
   formatCurrency(1234.56, "USD", "en-US") → "$1,234.56"
   formatCurrency(1234.56, "EUR", "de-DE") → "1.234,56 €"
   formatCurrency(1234.56, "JPY", "ja-JP") → "￥1,235" (no decimals for JPY)

3. DATES:
   formatDate("2025-01-10", "en-US") → "January 10, 2025"
   formatDate("2025-01-10", "de-DE") → "10. Januar 2025"
   formatDate("2025-01-10", "ja-JP") → "2025年1月10日"

4. RELATIVE TIME:
   formatRelative(-2, "hours", "en") → "2 hours ago"
   formatRelative(-2, "hours", "es") → "hace 2 horas"
   formatRelative(1, "days", "ja") → "明日"

Implementation: use platform-native Intl APIs (browser/OS)
  - Web: Intl.NumberFormat, Intl.DateTimeFormat, Intl.RelativeTimeFormat
  - iOS: NSNumberFormatter, NSDateFormatter
  - Android: java.text.NumberFormat, DateFormat
  - Server (Java): ICU4J library
  
  These APIs use CLDR locale data built into every OS/browser.
  No custom formatting logic needed — leverage the platform.
```

---

### Deep Dive 7: RTL (Right-to-Left) Layout Support

**The Problem**: Arabic, Hebrew, Persian, and Urdu are written right-to-left. The entire UI layout must mirror: navigation on the right, text aligned right, icons flipped.

```
RTL strategy:

1. DETECT: locale.direction from Locale entity (ar-SA → RTL, en-US → LTR)
2. HTML: set <html dir="rtl" lang="ar"> on page
3. CSS: use CSS Logical Properties (modern, automatic):
   - margin-left → margin-inline-start (auto-flips for RTL)
   - padding-right → padding-inline-end
   - text-align: left → text-align: start
4. ICONS: some icons need mirroring (→ becomes ←), some don't (✓ stays ✓)
   - Maintain a "mirror list" of icon IDs that flip in RTL
5. BIDIRECTIONAL TEXT: when RTL text contains LTR words (e.g., brand names)
   - Unicode BiDi algorithm handles automatically
   - Wrap embedded LTR with <bdi> tag for isolation

Testing: visual regression tests for top 10 pages in both LTR and RTL modes
```

---

### Deep Dive 8: String Extraction from Code — Developer Experience

**The Problem**: Developers write `t("key", params)` in code. We need to automatically extract all string keys, detect new/changed/deleted keys, and sync with the Translation DB — without manual bookkeeping.

```
Extraction pipeline (CI/CD):

1. STATIC ANALYSIS: Babel plugin (JS) / custom parser scans source code
   - Find all calls to t(), formatMessage(), <FormattedMessage>
   - Extract: key, default text, description, placeholders
   - Output: strings.json manifest per module

2. DIFF: compare extracted keys with Translation DB
   - NEW keys (in code, not in DB): → create in DB, mark "needs translation"
   - CHANGED source text: → increment version, mark translations "stale"
   - DELETED keys (in DB, not in code): → mark "deprecated" (don't delete immediately)

3. VALIDATION (pre-merge CI check):
   - No duplicate keys across modules
   - All keys have default English text
   - Placeholders in source match ICU syntax
   - No hardcoded strings outside t() calls (lint rule)

4. PUSH: upload new/changed strings to Translation DB → triggers TMS notifications

Developer experience:
  // In React code:
  const { t } = useTranslation('home');
  
  return (
    <div>
      <h1>{t('home.feed.title')}</h1>
      <p>{t('home.feed.new_posts', { count: 5 })}</p>
    </div>
  );
  
  // Babel plugin extracts at build time:
  // { key: "home.feed.title", namespace: "home" }
  // { key: "home.feed.new_posts", namespace: "home", placeholders: ["count"] }
```

---

## Summary: Key Trade-offs

| Decision | Chosen | Why |
|----------|--------|-----|
| **Bundle delivery** | CDN-first with immutable URLs (content hash) | 95% cache hit; < 10ms delivery; no cache invalidation needed |
| **Bundle granularity** | Per-namespace (page/feature) per locale | ~20 KB per bundle; lazy load per page; avoids loading 500K strings upfront |
| **Fallback resolution** | Pre-baked into bundle at build time | Client needs only one bundle (no multi-bundle fallback at runtime); simpler client |
| **Pluralization** | ICU MessageFormat (CLDR rules) | Industry standard; handles all 100+ language plural rules; FormatJS on client |
| **Authoring workflow** | TMS with draft → review → approve → publish | Non-engineers can translate safely; review catches errors before publish |
| **Rollout** | Versioned bundles with gradual % rollout | Both versions on CDN simultaneously; instant rollback via config change |
| **Formatting (dates/numbers)** | Platform-native Intl APIs | Zero custom logic; CLDR data built into every browser/OS; always up-to-date |
| **String extraction** | CI/CD static analysis (Babel plugin) | Automatic sync; no manual key management; detect new/changed/deleted strings |
| **Client caching** | IndexedDB + version check | Zero network for unchanged bundles; instant render from local cache |
| **Consistency** | Strong for authoring DB; eventual for CDN/client (< 5 min) | Translators see changes immediately; users get new translations within minutes |

## Interview Talking Points

1. **"CDN-first with immutable URLs: content hash in filename for cache busting"** — Bundle URL includes hash: `home.es-ES.a3f2b1c4.json`. New content = new hash = new URL. Old cache auto-evicts. No explicit CDN invalidation. 95% hit rate, < 10ms delivery.

2. **"Namespace-based bundles: ~20 KB per page per locale, lazy loaded"** — 500K strings split into ~100 namespaces (pages/features). Client loads only current page's bundle. IndexedDB local cache → zero network for repeat visits.

3. **"Fallback baked into bundles at build time"** — pt-BR bundle already contains Portuguese (pt) and English (en) fallbacks for missing strings. Client never resolves fallback at runtime. User NEVER sees raw keys.

4. **"ICU MessageFormat for pluralization with CLDR rules"** — `{count, plural, one {...} other {...}}`. Arabic gets 6 forms, Russian 3, English 2. FormatJS on client parses + evaluates. CLDR data maintained by Unicode consortium.

5. **"Translation workflow: developer → CI extraction → TMS → translator → reviewer → publish"** — Babel plugin extracts t() calls in CI. New strings auto-appear in TMS. Translators use web UI with screenshots/context. Reviewer approves. Bundle Builder publishes to CDN.

6. **"Gradual rollout: 1% → 10% → 100% with instant rollback"** — Both old and new bundle versions on CDN. hash(user_id) determines version. Rollback = set percentage to 0 (config change in Redis, < 1 min). Pre-publish validation catches missing placeholders.

---

## 🔗 Related System Design Problems

| Problem | Relationship | Key Difference |
|---------|-------------|----------------|
| **Content Delivery Network (CDN)** | Same caching/delivery pattern | i18n bundles are small JSON files; CDN also serves media (images, video) |
| **Feature Flags (LaunchDarkly)** | Same gradual rollout pattern | Feature flags toggle code; i18n rollout toggles string bundle versions |
| **CMS (Content Management)** | Same content authoring | CMS manages long-form content (articles); i18n manages short UI strings |
| **Config Management** | Same distributed config delivery | i18n is content-specific config; general config includes feature flags, A/B tests |
| **Search Engine** | Same text processing | Search indexes user content; i18n serves pre-authored translations |

## 🔧 Technology Alternatives

| Component | Chosen | Alternative | When to use alternative |
|-----------|--------|-------------|------------------------|
| **CDN** | CloudFront / Fastly | Akamai / Cloudflare | Akamai: enterprise; Cloudflare: Workers for edge compute |
| **String format** | ICU MessageFormat | Fluent (Mozilla) / gettext | Fluent: more expressive for complex grammar; gettext: legacy C/Python ecosystems |
| **TMS** | Custom (internal) | Crowdin / Phrase / Lokalise | SaaS TMS: faster setup, less customizable; Custom: full control for large orgs |
| **Client library** | FormatJS (react-intl) | i18next / LinguiJS | i18next: framework-agnostic; LinguiJS: smaller bundle, compile-time optimization |
| **Bundle storage** | Redis + CDN | S3 + CloudFront / Edge KV | S3: cheaper for cold storage; Edge KV (Cloudflare): edge-native, lowest latency |
| **Extraction** | Babel plugin | Custom regex / AST parser | Regex: simpler but fragile; AST: framework-specific (e.g., Swift, Kotlin) |

---

**Created**: February 2026
**Framework**: Hello Interview (6-step)
**Estimated Interview Time**: 45-60 minutes
**Deep Dives**: 8 topics (choose 2-3 based on interviewer interest)

---

**References**:
- Facebook i18n Architecture (FBT Framework)
- ICU MessageFormat Specification
- Unicode CLDR (Common Locale Data Repository)
- FormatJS (react-intl) Library
- CSS Logical Properties for RTL Support
- CDN Caching Strategies for Static Assets
- Crowdin / Phrase Translation Management Platforms
- Babel Plugin for Static Analysis / String Extraction
