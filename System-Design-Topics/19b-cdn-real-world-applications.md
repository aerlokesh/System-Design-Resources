# 🌐 CDN & Edge Caching Real-World Applications — Complete System Design Examples

> Each example below is a **full mini system design** showing exactly how CDNs are used in production, with **AWS CloudFront as the primary reference**. Every cache behavior, origin configuration, invalidation strategy, and edge compute pattern is explained with the **WHY** behind it.

---

## 📋 Table of Contents

1. [CloudFront Architecture — Complete Deep Dive](#1-cloudfront-architecture--complete-deep-dive)
2. [Netflix — Video Streaming via CDN](#2-netflix--video-streaming-via-cdn)
3. [Amazon.com — E-Commerce Product Pages & Images](#3-amazoncom--e-commerce-product-pages--images)
4. [Instagram — Image & Story Delivery](#4-instagram--image--story-delivery)
5. [Spotify — Audio Streaming & Manifest Files](#5-spotify--audio-streaming--manifest-files)
6. [Twitter/X — Media Delivery & Timeline Assets](#6-twitterx--media-delivery--timeline-assets)
7. [Shopify — Multi-Tenant Storefront Acceleration](#7-shopify--multi-tenant-storefront-acceleration)
8. [TikTok — Short Video Delivery at Scale](#8-tiktok--short-video-delivery-at-scale)
9. [GitHub — Static Asset & Release Downloads](#9-github--static-asset--release-downloads)
10. [Zoom — WebRTC Signaling & Static Assets](#10-zoom--webrtc-signaling--static-assets)
11. [News Sites — Breaking News Traffic Spikes](#11-news-sites--breaking-news-traffic-spikes)
12. [SaaS Platforms — Multi-Region API Acceleration](#12-saas-platforms--multi-region-api-acceleration)
13. [Gaming — Patch & Update Distribution](#13-gaming--patch--update-distribution)
14. [Lambda@Edge & CloudFront Functions — Edge Compute](#14-lambdaedge--cloudfront-functions--edge-compute)
15. [Security at the Edge — WAF, Shield, Signed URLs](#15-security-at-the-edge--waf-shield-signed-urls)
16. [16–20: Additional Real-World Applications](#1620-additional-real-world-applications)
17. [🏆 CloudFront Configuration Cheat Sheet](#-cloudfront-configuration-cheat-sheet)
18. [🎯 Interview Summary](#-interview-summary-how-would-you-design-a-cdn-architecture)

---

## 1. CloudFront Architecture — Complete Deep Dive

### How CloudFront Works

```
User Request                   Edge Location (450+ worldwide)           Origin
    │                               │                                      │
    │  GET /images/product.jpg      │                                      │
    │ ─────────────────────────────→│                                      │
    │                               │  Cache HIT?                          │
    │                               │  ├── YES → Return cached content     │
    │                               │  │         (< 5ms response)          │
    │                               │  │                                   │
    │                               │  └── NO → Fetch from origin          │
    │                               │           ──────────────────────────→│
    │                               │           ←──────────────────────────│
    │                               │           Cache it + return          │
    │  ←────────────────────────────│                                      │
    │  200 OK (cached)              │                                      │
```

### CloudFront Core Concepts

```
┌────────────────────────────────────────────────────────────────────────┐
│                     CLOUDFRONT ARCHITECTURE                            │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  Distribution: The CDN configuration (like a "virtual CDN instance")   │
│                                                                        │
│  Origin: Where content comes from                                      │
│  ├── S3 Bucket (static assets, images, videos)                        │
│  ├── ALB/EC2 (dynamic API responses)                                  │
│  ├── API Gateway (serverless APIs)                                    │
│  ├── MediaPackage (live/VOD video)                                    │
│  └── Custom Origin (any HTTP server)                                  │
│                                                                        │
│  Cache Behavior: Rules for how content is cached                       │
│  ├── Path pattern: /images/* → S3 origin (long cache)                 │
│  ├── Path pattern: /api/*    → ALB origin (short cache or no cache)   │
│  └── Default (*)  → S3 origin                                         │
│                                                                        │
│  Edge Location: 450+ points of presence globally                       │
│  Regional Edge Cache: 13 mid-tier caches (between edge & origin)      │
│                                                                        │
│  Edge → Regional Edge → Origin (3-tier caching hierarchy)             │
└────────────────────────────────────────────────────────────────────────┘
```

### Cache Key Components

```
# What makes a cache entry unique?
# Default: URL path only
# Can include:
# - Query strings: /search?q=shoes → different cache entry than /search?q=hats
# - Headers: Accept-Language → serve different content per language
# - Cookies: session_id → per-user caching (careful — kills cache hit rate!)

# Best practice: Include ONLY what changes the response
# ❌ BAD: Forward ALL headers/cookies → every request is unique → 0% cache hit
# ✅ GOOD: Forward only Accept-Language header → one cache per language
```

### Cache-Control Headers

```
# Origin tells CloudFront how long to cache:
Cache-Control: max-age=86400                    # Cache for 24 hours
Cache-Control: max-age=31536000, immutable      # Cache forever (versioned assets)
Cache-Control: no-cache                         # Always revalidate with origin
Cache-Control: no-store                         # Never cache (PII, auth responses)
Cache-Control: s-maxage=3600, max-age=60        # CDN caches 1hr, browser caches 1min
Cache-Control: public, max-age=3600             # CDN + browser can cache
Cache-Control: private, max-age=3600            # Browser only (not CDN) — user-specific

# ETag for conditional requests:
# First request: Response includes ETag: "abc123"
# Next request: If-None-Match: "abc123"
# → If unchanged: 304 Not Modified (no body transferred)
# → If changed: 200 OK with new content
```

### CloudFront TTL Hierarchy

```
# Priority order for cache duration:
1. Cache-Control / Expires headers from origin (if forwarded)
2. CloudFront minimum/maximum/default TTL settings
3. If origin sends no cache headers → CloudFront uses Default TTL (24 hours)

# Recommended settings per content type:
┌─────────────────────────────┬──────────────┬────────────────────────┐
│ Content Type                │ TTL          │ Cache-Control Header   │
├─────────────────────────────┼──────────────┼────────────────────────┤
│ Versioned assets (app.v3.js)│ 1 year       │ max-age=31536000       │
│ Images (product photos)     │ 24 hours     │ max-age=86400          │
│ HTML pages                  │ 5-60 minutes │ s-maxage=300           │
│ API responses (cacheable)   │ 1-60 seconds │ s-maxage=10            │
│ User-specific data          │ 0 (no cache) │ private, no-store      │
│ Auth tokens                 │ 0 (no cache) │ no-store               │
└─────────────────────────────┴──────────────┴────────────────────────┘
```

---

## 2. Netflix — Video Streaming via CDN

### 🏗️ Architecture Context

Netflix delivers **15%+ of all internet bandwidth** globally. They operate their own CDN called **Open Connect** (Netflix appliances placed inside ISPs), but the architecture is directly analogous to CloudFront.

### 📐 Architecture

```
User's TV/Phone
      │
      │ 1. "Play Stranger Things S4E1"
      ▼
┌──────────────┐
│  Netflix API  │  → Determines which video segments + quality
│  (CloudFront) │  → Returns manifest file (HLS/DASH)
└──────┬───────┘
       │ 2. Returns manifest: list of segment URLs
       ▼
┌──────────────────┐
│  Manifest File    │  segment001.ts, segment002.ts, ...
│  (m3u8 / mpd)     │  each segment = 2-10 seconds of video
└──────┬───────────┘
       │ 3. Client requests segments one by one
       ▼
┌──────────────────┐
│  CDN Edge / OCA   │  → Cache HIT: serve from edge (< 10ms)
│  (Open Connect /  │  → Cache MISS: fetch from origin, cache, serve
│   CloudFront)     │
└──────────────────┘
```

### 💡 CloudFront Implementation

```
# Distribution for Netflix-like video streaming:

# Origin: S3 bucket with transcoded video segments
# Behavior 1: /manifests/* → Origin: API Gateway (dynamic manifest generation)
#   TTL: 0 (always fresh — manifest determines quality/segments)
#   
# Behavior 2: /segments/* → Origin: S3 (pre-transcoded video files)
#   TTL: 1 year (segments are immutable — filename includes quality + hash)
#   Cache-Control: max-age=31536000, immutable
#   
# Behavior 3: /thumbnails/* → Origin: S3
#   TTL: 24 hours

# Adaptive Bitrate Streaming (ABR):
# Same video encoded at multiple qualities:
# /segments/stranger-things-s4e1/1080p/segment001.ts
# /segments/stranger-things-s4e1/720p/segment001.ts  
# /segments/stranger-things-s4e1/480p/segment001.ts
# Client switches quality based on bandwidth (no CDN logic needed)

# Pre-warming: Popular new releases pre-pushed to edge locations
# Before release: CloudFront origin-pull caches all segments at edges
# Launch day: 100% cache hit rate → zero origin load
```

### 🎯 Why CDN for Video?

| Without CDN | With CDN |
|-------------|----------|
| Origin serves every segment | 99%+ served from edge |
| 200ms+ latency (cross-continent) | <10ms from local edge |
| Origin bandwidth: petabytes/day | Origin bandwidth: negligible |
| Single point of failure | 450+ redundant edge locations |
| Rebuffering during peak hours | Consistent quality worldwide |

---

## 3. Amazon.com — E-Commerce Product Pages & Images

### 📐 Architecture

```
# CloudFront Distribution for amazon.com:

# Behavior 1: /images/* → Origin: S3 (product images)
#   Cache-Control: max-age=86400 (24 hours)
#   Multiple resolutions: /images/product_ABC_300x300.jpg
#                         /images/product_ABC_1200x1200.jpg
#   WebP auto-conversion via Lambda@Edge based on Accept header

# Behavior 2: /static/* → Origin: S3 (CSS, JS, fonts)
#   Cache-Control: max-age=31536000, immutable
#   Filename includes content hash: app.a3b4c5.js
#   Cache forever — new deploy = new filename

# Behavior 3: /api/* → Origin: ALB (dynamic API)
#   Cache-Control: no-store (personalized responses)
#   CloudFront still provides: TLS termination, WAF, DDoS protection

# Behavior 4: /dp/* → Origin: ALB (product detail pages)
#   Cache-Control: s-maxage=60 (CDN caches 1 min)
#   Cache key includes: query string (variant), Accept-Language header
#   Stale-While-Revalidate: serve stale while fetching fresh

# Default (*): Origin: ALB
```

### 💡 Image Optimization at Edge

```
# Lambda@Edge: Auto-convert images based on client capability
# Viewer Request event:

exports.handler = async (event) => {
    const request = event.Records[0].cf.request;
    const accept = request.headers['accept']?.[0]?.value || '';
    
    // If browser supports WebP → rewrite URL to WebP version
    if (accept.includes('image/webp')) {
        request.uri = request.uri.replace(/\.(jpg|png)$/, '.webp');
    }
    
    // Device-based image sizing
    const width = request.headers['cloudfront-is-mobile-viewer']?.[0]?.value === 'true' 
        ? '400' : '1200';
    request.uri = request.uri.replace(/(\.\w+)$/, `_${width}$1`);
    
    return request;
};

// Result:
// Desktop Chrome: /images/product_1200.webp (smallest, best quality)
// iPhone Safari: /images/product_400.jpg (small size for mobile)
// Old browser: /images/product_1200.jpg (fallback)
```

### Cache Invalidation Strategy

```
# Problem: Product price changes → cached page shows old price
# Solutions (ranked by preference):

# 1. BEST: Versioned URLs (no invalidation needed)
# /static/app.a3b4c5.js → deploy new version → /static/app.d6e7f8.js
# Old version naturally evicts from cache via TTL

# 2. GOOD: Short TTL + stale-while-revalidate
# Cache-Control: s-maxage=60, stale-while-revalidate=300
# Page is at most 1 min stale; serves stale while refreshing in background

# 3. OK: CloudFront Invalidation API
aws cloudfront create-invalidation \
  --distribution-id E1234567 \
  --paths "/dp/product_ABC" "/dp/product_DEF"
# Takes 5-15 minutes to propagate to all 450+ edge locations
# First 1000 invalidation paths/month free; $0.005/path after

# 4. AVOID: Wildcard invalidation
aws cloudfront create-invalidation --paths "/*"
# Invalidates EVERYTHING — kills cache hit rate for hours
# Only use in emergencies (security vulnerability, major bug)
```

---

## 4. Instagram — Image & Story Delivery

### 📐 Architecture

```
# User uploads photo:
1. Upload → API → S3 (original)
2. Async Lambda → resize to 6 sizes (thumbnail, feed, story, etc.)
3. Each size stored in S3: /photos/{userId}/{photoId}/{size}.jpg
4. CloudFront URL returned to app: d1234.cloudfront.net/photos/...

# CDN serves ALL image requests:
# - Profile pictures (heavily cached — rarely change)
# - Feed images (long cache — images are immutable once posted)
# - Stories (24-hour TTL — matches story expiration)
# - Reels thumbnails (long cache)

# Cache hit rate: ~98% (most images accessed within hours of upload)
```

### 💡 Story Delivery Optimization

```
# Story segments (15-second video clips):
# Similar to Netflix — HLS segments cached at edge
# Pre-fetch: When user views Story 1, client pre-fetches Story 2 & 3 segments
# TTL: 25 hours (story lives 24h + 1h buffer for ongoing viewers)

# Geo-restriction: None needed (Instagram is global)
# But: Stories from users in Region A are pre-warmed at Region A edges
# (users' followers are geographically concentrated)
```

---

## 5. Spotify — Audio Streaming & Manifest Files

```
# Audio file delivery via CDN:

# Songs stored as encrypted segments in S3
# Each song = multiple quality versions:
# /audio/{trackId}/320kbps/segment_001.mp4
# /audio/{trackId}/160kbps/segment_001.mp4
# /audio/{trackId}/96kbps/segment_001.mp4

# CloudFront behavior:
# /audio/* → S3 origin
# TTL: 30 days (audio files never change)
# Signed URLs: Each URL valid for 1 hour (prevent unauthorized downloads)

# Pre-caching popular tracks:
# Top 1000 songs → pre-warmed at ALL edge locations
# Long-tail songs → cached on first request at requesting edge only
# Result: Top 1000 = instant playback; long-tail = slight delay on first play

# Podcast episodes: Same pattern but larger files (50-200MB each)
# CloudFront handles range requests: client downloads in chunks
# Resume playback: client requests byte range from where it left off
```

---

## 6. Twitter/X — Media Delivery & Timeline Assets

```
# Separate CloudFront distributions per content type:

# Distribution 1: pbs.twimg.com (profile images, banners)
# Origin: S3
# TTL: 24 hours (profile pics change rarely)
# Cache key: URL only (no query strings)

# Distribution 2: video.twimg.com (video tweets)
# Origin: S3 (transcoded video segments)
# TTL: 7 days (tweets are immutable)
# Signed cookies: Authenticated users only

# Distribution 3: abs.twimg.com (static assets — JS, CSS, fonts)
# Origin: S3
# TTL: 1 year (versioned filenames)
# Brotli + gzip compression at edge

# Twitter's challenge: VIRAL CONTENT
# A tweet goes viral → 50M views in 1 hour
# Without CDN: origin crushed
# With CDN: first request caches at edge → next 49,999,999 served from cache
# Cache hit rate for viral content: 99.99%+
```

---

## 7. Shopify — Multi-Tenant Storefront Acceleration

```
# Each Shopify store = different domain, same CDN infrastructure
# shop1.myshopify.com → CloudFront → Shopify origin
# shop2.myshopify.com → CloudFront → Shopify origin

# CloudFront with custom domain via SNI:
# One distribution, hundreds of thousands of custom domains
# SSL/TLS via ACM (free certificates for all stores)

# Cache strategy per tenant:
# Problem: Can't cache HTML with store-specific content?
# Solution: Edge-Side Includes (ESI) or cache per Host header

# Cache key: Host header + path + selected query params
# shop1.myshopify.com/products/shoe → cache entry A
# shop2.myshopify.com/products/shoe → cache entry B (different store!)

# Product images: Stored in S3, served via CloudFront
# /cdn/shop1/product_abc_800x800.jpg
# On-the-fly resizing via Lambda@Edge if specific size not pre-generated
```

---

## 8. TikTok — Short Video Delivery at Scale

```
# TikTok's CDN challenge: 
# 1 BILLION daily active users, average 95 minutes/day
# Each video: 15-180 seconds, multiple qualities
# Feed: infinite scroll → pre-fetch next 3-5 videos while watching current

# Architecture:
# User opens app → API returns feed (list of video IDs)
# App immediately fetches first video from nearest CDN edge
# While playing: pre-fetches next 3 videos in background
# If user scrolls → video already cached locally → instant playback

# CloudFront configuration:
# Origin: S3 (transcoded videos)
# TTL: 30 days (videos are immutable)
# Range requests: Download first 2 seconds immediately, stream rest
# Cache-Control: max-age=2592000, immutable

# Regional pre-warming:
# Trending video in US → pre-warm all US edge locations
# Algorithm predicts which videos will trend → pre-cache proactively
```

---

## 9. GitHub — Static Asset & Release Downloads

```
# GitHub uses CDN for:
# 1. Repository release downloads (tarballs, binaries)
#    /releases/download/v1.0/app-linux-amd64
#    TTL: 30 days (releases are versioned, immutable)
#    Range requests: Resume interrupted downloads

# 2. Raw file serving (raw.githubusercontent.com)
#    /user/repo/main/README.md
#    TTL: 5 minutes (can change on push)
#    Cache key: URL (includes branch/commit hash)

# 3. GitHub Pages (static sites)
#    Custom domains → CloudFront → S3 origin
#    TTL: 10 minutes (short — pages update on push)
#    Invalidation on git push → rebuild → invalidate CDN

# 4. npm packages (via GitHub Packages)
#    /npm/v/@scope/package-1.0.0.tgz
#    TTL: 1 year (versioned, immutable)
```

---

## 10. Zoom — WebRTC Signaling & Static Assets

```
# CDN for Zoom:
# Static assets: React app bundle, CSS, images
# /static/app.a3b4c5.js → CloudFront → S3
# TTL: 1 year (versioned)

# Meeting waiting room backgrounds:
# /backgrounds/blur.wasm (WebAssembly for background blur)
# /backgrounds/custom/{userId}/bg.jpg (user's custom backgrounds)
# TTL: 24 hours (custom), 30 days (built-in)

# NOT via CDN: actual audio/video (WebRTC over UDP, not HTTP)
# CDN handles: everything EXCEPT the real-time media streams
```

---

## 11. News Sites — Breaking News Traffic Spikes

```
# The CNN/BBC problem: 
# Normal: 10K RPS
# Breaking news: 500K RPS in 5 minutes (50x spike)

# Without CDN: Origin servers crushed, site goes down during THE most important moment
# With CloudFront:

# Strategy: "Cache everything, invalidate on change"
# Homepage: TTL = 30 seconds (s-maxage=30)
#   → At most 30 seconds stale
#   → During 500K RPS, origin handles ~1 request per 30 seconds per edge location
#   → 450 edges × 1 req/30s = ~15 RPS to origin (instead of 500K!)

# Article pages: TTL = 5 minutes (s-maxage=300)
#   → Article text rarely changes after publication
#   → Breaking story: publish → 5 min max staleness → acceptable

# Live updates: API endpoint (no cache or 5-second cache)
#   → CloudFront forwards to ALB
#   → But still provides: DDoS protection, TLS termination, global anycast

# Origin Shield: Enable to reduce origin load further
#   → Only ONE request per cached object, even across all 450 edges
#   → 450 edges → 1 Regional Edge → 1 Origin Shield → Origin
```

---

## 12. SaaS Platforms — Multi-Region API Acceleration

```
# Problem: SaaS API origin in us-east-1, users worldwide
# Asia-Pacific users: 300ms latency to API

# Solution: CloudFront for API acceleration (even dynamic responses!)

# How? CloudFront uses AWS backbone network (faster than public internet)
# User in Tokyo → Tokyo edge (5ms) → AWS backbone → us-east-1 origin (80ms)
# Total: ~85ms instead of 300ms

# Configuration:
# Behavior: /api/* → ALB origin
# TTL: 0 (no caching — every request goes to origin)
# BUT: TLS is terminated at edge, connection reuse over backbone
# Result: Even uncached responses are 2-3x faster

# For cacheable API responses:
# GET /api/products?category=shoes → TTL: 60 seconds
# GET /api/config → TTL: 5 minutes
# POST /api/orders → TTL: 0 (never cache mutations)
```

---

## 13. Gaming — Patch & Update Distribution

```
# Game update: 20GB patch to 50 million players
# Without CDN: 50M × 20GB = 1 exabyte from origin = impossible
# With CloudFront: Origin serves ~20GB (one copy), CDN serves rest

# Strategy:
# 1. Upload patch to S3
# 2. Pre-warm: Push to all edge locations BEFORE announcing update
# 3. Announce update → all players download from nearest edge
# 4. Range requests: Resume interrupted downloads (common on game consoles)
# 5. Background download: Client downloads while player is in-game

# Configuration:
# Origin: S3
# TTL: 30 days (patch files are versioned, immutable)
# Transfer acceleration: S3 Transfer Acceleration + CloudFront
# Cost optimization: CloudFront origin shield → reduce S3 requests

# Pricing optimization:
# CloudFront price classes:
# Price Class All: All 450+ edge locations (most expensive)
# Price Class 200: US, EU, Asia, Africa (excludes South America, Australia)
# Price Class 100: US, EU only (cheapest)
# Game company selects based on player geography
```

---

## 14. Lambda@Edge & CloudFront Functions — Edge Compute

### When to Use Which

| Feature | CloudFront Functions | Lambda@Edge |
|---------|---------------------|-------------|
| Runtime | JavaScript only | Node.js, Python |
| Execution time | < 1ms | < 5-30 seconds |
| Memory | 2MB | 128-10,240 MB |
| Network access | ❌ | ✅ |
| Cost | 1/6th of Lambda@Edge | More expensive |
| Triggers | Viewer Request/Response | All 4 events |
| Use case | URL rewrites, headers, redirects | Auth, image resize, A/B test |

### Real-World Edge Compute Patterns

```javascript
// CloudFront Function: URL rewriting (< 1ms)
function handler(event) {
    var request = event.request;
    
    // Add /index.html for SPA routing
    if (!request.uri.includes('.')) {
        request.uri = '/index.html';
    }
    
    // Redirect www to non-www
    if (request.headers.host.value.startsWith('www.')) {
        return {
            statusCode: 301,
            headers: { 'location': { value: 'https://example.com' + request.uri } }
        };
    }
    
    return request;
}
```

```javascript
// Lambda@Edge: A/B Testing (Viewer Request)
exports.handler = async (event) => {
    const request = event.Records[0].cf.request;
    
    // Check for existing experiment cookie
    const cookies = request.headers.cookie || [];
    const experimentCookie = cookies.find(c => c.value.includes('experiment='));
    
    if (!experimentCookie) {
        // Assign to group A (70%) or B (30%)
        const group = Math.random() < 0.7 ? 'A' : 'B';
        request.headers['x-experiment-group'] = [{ value: group }];
    }
    
    // Route to different origin based on experiment group
    if (group === 'B') {
        request.origin.s3.path = '/experiment-b';
    }
    
    return request;
};
```

```python
# Lambda@Edge: Authentication (Viewer Request)
import jwt
import json

def handler(event, context):
    request = event['Records'][0]['cf']['request']
    headers = request['headers']
    
    # Check Authorization header
    auth = headers.get('authorization', [{}])[0].get('value', '')
    if not auth.startswith('Bearer '):
        return { 'status': '401', 'body': 'Unauthorized' }
    
    try:
        token = auth.split(' ')[1]
        claims = jwt.decode(token, 'secret', algorithms=['HS256'])
        # Add user info to request headers for origin
        request['headers']['x-user-id'] = [{'value': claims['sub']}]
        return request
    except:
        return { 'status': '403', 'body': 'Forbidden' }
```

---

## 15. Security at the Edge — WAF, Shield, Signed URLs

### AWS WAF + CloudFront

```
# WAF Web ACL attached to CloudFront distribution:

# Rule 1: Rate limiting
# → Block IPs sending > 2000 requests in 5 minutes
# → Prevents DDoS and scraping

# Rule 2: Geo-blocking
# → Block traffic from specific countries (compliance)

# Rule 3: SQL injection / XSS protection
# → AWS Managed Rules: AWSManagedRulesCommonRuleSet
# → Inspects query strings, headers, body

# Rule 4: Bot control
# → AWS Managed Rules: AWSManagedRulesBotControlRuleSet
# → Blocks bad bots, allows good bots (Googlebot)

# Rule 5: IP reputation
# → AWS Managed Rules: AWSManagedRulesAmazonIpReputationList
# → Blocks known malicious IPs
```

### Signed URLs & Signed Cookies

```
# Signed URL: One URL, one resource, time-limited
# Use case: Paid content (courses, premium videos)

# 1. User authenticates → your API generates signed URL
# 2. Signed URL: https://d1234.cloudfront.net/video.mp4?
#      Expires=1705410000&
#      Signature=base64signature&
#      Key-Pair-Id=APKAEIBAERJR2EXAMPLE

# 3. CloudFront validates signature → serves if valid
# 4. After expiry → 403 Forbidden

# Signed Cookie: Multiple resources, same authentication
# Use case: All videos in a course, all pages behind paywall
# Set cookie once → applies to all matching requests

# Origin Access Control (OAC):
# S3 bucket is PRIVATE → only CloudFront can access it
# Users can't bypass CDN and go directly to S3
# CloudFront signs requests to S3 using OAC policy
```

### AWS Shield (DDoS Protection)

```
# Shield Standard (FREE with CloudFront):
# → Automatic protection against Layer 3/4 DDoS
# → SYN floods, UDP reflection, DNS amplification

# Shield Advanced ($3,000/month):
# → Layer 7 DDoS protection (HTTP floods)
# → 24/7 DDoS Response Team (DRT)
# → Cost protection (won't charge for DDoS-induced scaling)
# → Real-time visibility into attacks
```

---

## 16–20: Additional Real-World Applications

### 16. E-Learning (Udemy/Coursera)
```
Video lessons via signed URLs (paid content protection).
CloudFront + S3 + MediaConvert for transcoding.
DRM: Widevine/FairPlay key delivery via CloudFront.
```

### 17. IoT Firmware Updates
```
Millions of devices download firmware from nearest edge.
S3 origin + CloudFront. Range requests for delta updates.
Signed URLs per device for access control.
```

### 18. WordPress/JAMstack Sites
```
Static site generator → S3 → CloudFront.
Invalidation on deploy: aws cloudfront create-invalidation.
Lambda@Edge for redirects, auth, A/B testing.
```

### 19. Mobile App Asset Delivery
```
App images, configs, feature flags served via CDN.
Cache-Control per asset type. ETag for conditional requests.
On app launch: check CDN for updated config → update UI without app store release.
```

### 20. Global SPA (React/Angular)
```
S3 hosts /index.html + /static/*.
CloudFront Function: Rewrite all non-file paths to /index.html (SPA routing).
Cache: /static/* → 1 year (hashed filenames). /index.html → 5 minutes.
```

---

## 🏆 CloudFront Configuration Cheat Sheet

```
┌────────────────────────────────────────────────────────────────────────┐
│         CLOUDFRONT CONFIGURATION CHEAT SHEET                           │
├────────────────────────────────────────────────────────────────────────┤
│ CONTENT TYPE      │ ORIGIN    │ TTL         │ CACHE-CONTROL           │
│───────────────────┼───────────┼─────────────┼─────────────────────────│
│ JS/CSS (hashed)   │ S3        │ 1 year      │ max-age=31536000        │
│ Images            │ S3        │ 24 hours    │ max-age=86400           │
│ Video segments    │ S3        │ 30 days     │ max-age=2592000         │
│ HTML pages        │ ALB/S3    │ 5-60 min    │ s-maxage=300            │
│ API (cacheable)   │ ALB/APIGW │ 10-60 sec   │ s-maxage=10             │
│ API (dynamic)     │ ALB/APIGW │ 0           │ no-store                │
│ User-specific     │ ALB       │ 0           │ private, no-store       │
│                                                                        │
│ CACHE INVALIDATION:                                                    │
│ • BEST: Versioned URLs (no invalidation needed)                       │
│ • GOOD: Short TTL + stale-while-revalidate                            │
│ • OK: Invalidation API (1000 free/month, 5-15 min propagation)        │
│ • AVOID: Wildcard /* invalidation                                     │
│                                                                        │
│ EDGE COMPUTE:                                                          │
│ • CloudFront Functions: URL rewrite, redirects, headers (< 1ms)       │
│ • Lambda@Edge: Auth, resize, A/B test, personalization (< 30s)        │
│                                                                        │
│ SECURITY:                                                              │
│ • WAF: Rate limiting, geo-block, SQL injection, bot control            │
│ • Shield: DDoS protection (Standard = free with CloudFront)           │
│ • Signed URLs/Cookies: Paid/restricted content                        │
│ • OAC: Lock S3 to CloudFront-only access                              │
│                                                                        │
│ OPTIMIZATION:                                                          │
│ • Origin Shield: One request per object across all edges               │
│ • Compression: Brotli + gzip auto (for text content)                  │
│ • HTTP/2 + HTTP/3: Enabled by default                                  │
│ • Price Class: 100 (cheapest) → 200 → All (most coverage)            │
│                                                                        │
│ MONITORING:                                                            │
│ • CloudWatch: CacheHitRate, 4xxErrorRate, 5xxErrorRate                │
│ • Real-time logs → Kinesis Data Firehose → S3 / OpenSearch            │
│ • Standard logs → S3 (free, delayed)                                   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Interview Summary: "How Would You Design a CDN Architecture?"

> *"I'd use CloudFront with S3 as origin for static assets and ALB for dynamic content. For static assets like JS/CSS, I'd use content-hashed filenames with 1-year TTL — no invalidation ever needed. For images, 24-hour TTL with WebP conversion via Lambda@Edge based on the Accept header. For API responses, I'd selectively cache GET endpoints with short TTLs (10-60 seconds) using `s-maxage`, while POST/PUT/DELETE go through uncached with TTL=0 — but still benefit from CloudFront's TLS termination and AWS backbone network for lower latency. For security, I'd attach WAF for rate limiting and bot protection, use Origin Access Control to lock S3, and signed URLs for paid content. For cache invalidation, I'd primarily rely on versioned URLs and only use the Invalidation API for emergencies. I'd enable Origin Shield to reduce origin load, and monitor CacheHitRate in CloudWatch — targeting 95%+ for static content."*

---

> **Related Topics**: [CDN & Edge Caching Deep Dive →](./19-cdn-and-edge-caching.md) | [Redis Real-World →](./40b-redis-real-world-applications.md) | [Observability Real-World →](./45b-observability-monitoring-real-world-applications.md)
