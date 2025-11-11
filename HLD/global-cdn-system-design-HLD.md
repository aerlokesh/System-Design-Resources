# Global CDN (Content Delivery Network) System Design - High-Level Design (HLD)

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Functional Requirements](#functional-requirements)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Capacity Estimation](#capacity-estimation)
5. [High-Level Architecture](#high-level-architecture)
6. [Core Components](#core-components)
7. [Database Design](#database-design)
8. [API Design](#api-design)
9. [Deep Dives](#deep-dives)
10. [Scalability & Reliability](#scalability--reliability)
11. [Trade-offs & Alternatives](#trade-offs--alternatives)

---

## Problem Statement

Design a global Content Delivery Network (CDN) that:
- Delivers static and dynamic content with minimal latency
- Handles millions of requests per second across the globe
- Provides high availability and fault tolerance
- Optimizes bandwidth usage and reduces origin server load
- Supports multiple content types (images, videos, HTML, CSS, JS, APIs)
- Offers DDoS protection and web application firewall
- Provides real-time analytics and monitoring
- Enables instant cache purging and content updates

### Scale Requirements
- **1000+ edge locations** worldwide
- **10+ Tbps** aggregate bandwidth capacity
- **100 million+ requests per second** at peak
- **99.99% uptime** SLA
- **< 50ms latency** for 95% of requests globally
- **10 PB+** of cached content
- **Support 1 million+ customer domains**
- **Handle 10+ Tbps DDoS attacks**

---

## Functional Requirements

### Must Have (P0)
1. **Content Delivery**
   - Serve static content (images, CSS, JS, fonts)
   - Serve dynamic content with edge computing
   - Support HTTP/HTTPS protocols (HTTP/1.1, HTTP/2, HTTP/3)
   - Handle various content types (text, images, video, binary)
   - Support range requests for large files
   - Enable streaming protocols (HLS, DASH)

2. **Caching**
   - Intelligent caching at edge locations
   - Cache invalidation and purging
   - Cache key customization
   - TTL (Time to Live) management
   - Cache bypass for specific requests
   - Stale content serving during origin failures

3. **Request Routing**
   - Geographic routing (nearest edge location)
   - Latency-based routing
   - Load balancing across edge servers
   - Anycast routing for optimal path
   - Automatic failover to healthy servers
   - Origin shield to protect origin servers

4. **Security**
   - DDoS protection and mitigation
   - Web Application Firewall (WAF)
   - SSL/TLS encryption
   - Certificate management
   - Bot detection and mitigation
   - Rate limiting per IP/user
   - Access control lists (ACLs)

5. **Performance Optimization**
   - Image optimization (compression, format conversion)
   - Minification of CSS/JS
   - HTTP/2 server push
   - Brotli/Gzip compression
   - TCP optimization
   - Connection pooling

6. **Monitoring & Analytics**
   - Real-time traffic monitoring
   - Cache hit ratio tracking
   - Bandwidth usage reporting
   - Error rate monitoring
   - Geographic traffic distribution
   - Performance metrics (latency, throughput)
   - Security event logging

### Nice to Have (P1)
- Edge computing / serverless functions
- Video streaming optimization
- WebSocket support
- Custom SSL certificate deployment
- A/B testing at edge
- Predictive cache warming
- Multi-CDN failover
- GraphQL API acceleration
- IoT edge processing

---

## Non-Functional Requirements

### Performance
- **Latency**: < 50ms for 95% of requests
- **Time to First Byte (TTFB)**: < 100ms for cached content
- **Cache hit ratio**: > 90% for static content
- **Throughput**: 10+ Tbps aggregate bandwidth
- **Connection setup**: < 10ms for SSL/TLS handshake

### Scalability
- Handle 100M+ requests per second
- Support 1M+ customer websites
- Scale to 10+ PB of cached content
- Add new edge locations dynamically
- Auto-scale based on traffic patterns

### Availability
- **99.99% uptime** SLA (52 minutes downtime/year)
- No single point of failure
- Automatic failover within 10 seconds
- Multi-region redundancy
- Graceful degradation during attacks

### Consistency
- **Eventual consistency** for cache updates
- **Strong consistency** for SSL certificates
- **Cache coherence** across edge locations
- Immediate purge propagation (< 5 seconds)

### Security
- **DDoS mitigation**: Handle 10+ Tbps attacks
- **SSL/TLS**: Support TLS 1.2, 1.3
- **Certificate management**: Auto-renewal, custom certs
- **WAF**: OWASP Top 10 protection
- **Access control**: IP whitelist/blacklist, geographic restrictions

### Reliability
- **Geographic redundancy**: Multiple edge locations per region
- **Origin protection**: Origin shield to reduce load
- **Automatic failover**: < 10 seconds to backup servers
- **Data durability**: Replicate configuration across regions

---

## Capacity Estimation

### Traffic Estimates
```
Customer websites: 1 million
Average requests per website per day: 1 million
Total daily requests: 1 trillion (1,000,000,000,000)

Requests per second:
Average: 1T / 86,400 = 11.5M RPS
Peak (3x): 34.5M RPS per region
Global peak: 100M RPS

Average request size: 100 KB
Daily bandwidth: 1T requests × 100 KB = 100 PB/day
Peak bandwidth: 100M RPS × 100 KB = 10 TB/s = 80 Tbps
```

### Storage Estimates
```
Cached content per edge location:
- Hot cache (frequently accessed): 10 TB
- Warm cache (moderately accessed): 40 TB
- Total cache per edge: 50 TB

Edge locations: 1,000
Total cached storage: 1,000 × 50 TB = 50 PB

Origin storage (customer content): 100 PB
Configuration data: 100 GB
Logs and metrics: 1 PB/month
```

### Edge Server Estimates
```
Single edge server capacity:
- Requests: 100K RPS
- Bandwidth: 10 Gbps
- Storage: 10 TB SSD

Servers per edge location:
- Peak RPS per location: 100K RPS
- Servers needed: 100K / 100K = 1 server (+ redundancy)
- Actual deployment: 10 servers per location (for redundancy and growth)

Total edge servers: 1,000 locations × 10 servers = 10,000 servers
```

### Network Estimates
```
Edge to origin traffic (cache miss):
- Cache hit ratio: 90%
- Cache miss ratio: 10%
- Origin requests: 100M × 0.1 = 10M RPS

Origin bandwidth:
- 10M RPS × 100 KB = 1 TB/s = 8 Tbps

Inter-edge communication (cache coherence):
- Cache purge notifications: 10K/sec globally
- Configuration updates: 1K/sec
```

### Cost Estimates (Monthly)
```
Infrastructure:
- Edge servers (10,000 × $500): $5M/month
- Network bandwidth (80 Tbps): $10M/month
- Storage (50 PB × $0.02/GB): $1M/month
- Origin servers and shielding: $2M/month
- DNS and routing infrastructure: $500K/month
- Total: ~$18.5M/month

Per customer (1M customers): $18.50/month average infrastructure cost
Typical CDN pricing: $100-500/month per customer
Margin: High profitability at scale
```

---

## High-Level Architecture

```
                            [Global Users - Billions]
                                         |
                            ┌────────────┴────────────┐
                            ↓                         ↓
                     [Web Browsers]            [Mobile Apps]
                     (Desktop/Mobile)          (iOS/Android)
                            |                         |
                            └────────────┬────────────┘
                                         ↓
                              [DNS Resolution Layer]
                         (GeoDNS + Anycast Routing)
                                         |
                    ┌────────────────────┼────────────────────┐
                    ↓                    ↓                    ↓
          [Edge Location A]    [Edge Location B]    [Edge Location C]
          (North America)        (Europe)             (Asia Pacific)
               |                      |                     |
          ┌────┴────┐            ┌────┴────┐          ┌────┴────┐
          ↓         ↓            ↓         ↓          ↓         ↓
    [Edge Servers] [Cache]  [Edge Servers] [Cache] [Edge Servers] [Cache]
          |                      |                     |
          └────────────────────┬┴─────────────────────┘
                               ↓
                     [Regional Shield Servers]
                    (Aggregate & Protect Origins)
                               |
          ┌────────────────────┼────────────────────┐
          ↓                    ↓                    ↓
    [Origin Server A]    [Origin Server B]    [Origin Server C]
    (Customer Site 1)    (Customer Site 2)    (Customer Site 3)
          |                    |                    |
          └────────────────────┴────────────────────┘
                               |
                    [Control Plane Services]
                         (Configuration & Management)
                               |
        ┌──────────────────────┼──────────────────────┐
        ↓                      ↓                      ↓
   [Config Service]    [Analytics Service]    [Purge Service]
        |                      |                      |
        └──────────────────────┼──────────────────────┘
                               ↓
                        [Data Layer]
                               |
        ┌──────────────────────┼──────────────────────┐
        ↓                      ↓                      ↓
   [PostgreSQL]           [Cassandra]          [S3/Object Store]
   (Config Data)          (Logs/Metrics)       (Origin Content)
        |                      |                      |
        └──────────────────────┼──────────────────────┘
                               ↓
                    [Real-time Analytics]
                               |
        ┌──────────────────────┼──────────────────────┐
        ↓                      ↓                      ↓
   [Kafka Streams]        [ClickHouse]         [Prometheus]
   (Event Processing)     (Analytics DB)       (Metrics Store)
```

---

## Core Components

### 1. Edge Server Infrastructure

**Purpose**: Serve content from locations closest to end users

**Architecture**:
- **1000+ edge locations** across 100+ countries
- **10-20 servers per location** for redundancy
- **Anycast IP addressing** for optimal routing
- **SSD-based caching** for fast content retrieval
- **10/25/100 Gbps network interfaces**

**Key Responsibilities**:
- Accept incoming HTTP/HTTPS requests
- Check local cache for requested content
- Serve cached content immediately (cache hit)
- Fetch from origin if cache miss
- Apply security policies (WAF, rate limiting)
- Compress responses (Gzip, Brotli)
- Log request metadata for analytics

**Hardware Specifications (Per Server)**:
```
CPU: 32 cores (Intel Xeon or AMD EPYC)
Memory: 256 GB RAM
Storage: 10 TB NVMe SSD (for caching)
Network: 100 Gbps NIC (dual for redundancy)
```

**Software Stack**:
- **Web Server**: NGINX or custom-built C/C++ server
- **Cache**: Redis or custom in-memory cache
- **SSL/TLS**: OpenSSL with hardware acceleration
- **Compression**: zlib (Gzip), Brotli
- **Monitoring Agent**: Prometheus node exporter

**Load Distribution**:
```
Per server capacity:
- 100K requests per second
- 10 Gbps sustained throughput
- 1M concurrent connections
- 10 TB cached content

Edge location capacity (10 servers):
- 1M requests per second
- 100 Gbps aggregate bandwidth
- 10M concurrent connections
- 100 TB total cache
```

### 2. Intelligent Request Routing

**Purpose**: Route user requests to optimal edge location

**Components**:

#### A. GeoDNS (Geographic DNS)
```
User Request Flow:
1. User requests www.example.com
2. DNS query sent to CDN's authoritative DNS
3. GeoDNS analyzes:
   - User's IP address
   - Geographic location
   - Network latency to edge locations
   - Current load on edge locations
   - Health status of servers
4. Returns IP of nearest healthy edge location
5. User connects to edge server
```

**Example DNS Response**:
```python
def resolve_dns(user_ip, domain):
    """
    Resolve domain to optimal edge location
    """
    user_location = geolocate(user_ip)
    
    # Get all edge locations
    edge_locations = get_edge_locations()
    
    # Filter healthy locations
    healthy_locations = [
        loc for loc in edge_locations 
        if loc.health_score > 0.9 and loc.capacity_available > 0.3
    ]
    
    # Calculate score for each location
    scored_locations = []
    for location in healthy_locations:
        distance = calculate_distance(user_location, location.coordinates)
        latency = estimate_latency(user_ip, location.ip)
        load = location.current_load / location.max_capacity
        
        # Lower is better
        score = (distance * 0.3) + (latency * 0.4) + (load * 0.3)
        scored_locations.append((location, score))
    
    # Return best location
    best_location = min(scored_locations, key=lambda x: x[1])
    return best_location[0].ip_address
```

#### B. Anycast Routing
```
How Anycast Works:
1. Multiple edge locations share same IP address (e.g., 203.0.113.1)
2. User sends request to 203.0.113.1
3. Internet routing protocols (BGP) route to nearest location
4. Edge server at that location handles request
5. If location fails, BGP automatically reroutes to next nearest

Benefits:
- Automatic failover (BGP convergence)
- Optimal routing by internet itself
- Simple client configuration (single IP)
- DDoS mitigation (traffic distributed)
```

#### C. Latency-Based Routing
```python
class LatencyBasedRouter:
    def __init__(self):
        self.latency_matrix = {}  # Cache of measured latencies
    
    def route_request(self, user_ip, edge_locations):
        """
        Route based on actual measured latency
        """
        latencies = []
        
        for location in edge_locations:
            # Check cached latency measurement
            cached_latency = self.latency_matrix.get((user_ip, location.id))
            
            if cached_latency and cached_latency.age < 3600:  # 1 hour cache
                latency = cached_latency.value
            else:
                # Perform active measurement
                latency = measure_latency(user_ip, location.ip)
                self.latency_matrix[(user_ip, location.id)] = {
                    'value': latency,
                    'age': 0
                }
            
            latencies.append((location, latency))
        
        # Return location with lowest latency
        best_location = min(latencies, key=lambda x: x[1])
        return best_location[0]
```

### 3. Caching System

**Purpose**: Store frequently accessed content at edge for fast delivery

**Cache Architecture**:
```
Level 1: In-Memory Cache (Hot Cache)
- Size: 100 GB per server
- Data: Most frequently accessed content
- TTL: 5-60 minutes
- Technology: Redis, Memcached, or custom

Level 2: SSD Cache (Warm Cache)
- Size: 10 TB per server
- Data: Frequently accessed content
- TTL: 1 hour - 7 days
- Technology: Local filesystem with LRU eviction

Level 3: Origin Shield (Regional Cache)
- Size: 100 TB per region
- Data: Aggregated cache for multiple edges
- TTL: Same as edge cache
- Purpose: Reduce origin server load
```

**Cache Key Generation**:
```python
def generate_cache_key(request):
    """
    Generate unique cache key for request
    """
    components = [
        request.url.path,
        request.url.query_string,
        request.headers.get('Accept-Encoding', ''),
        request.headers.get('Accept', ''),
        request.cookies.get('session_id', '') if cache_by_session else '',
    ]
    
    # Create hash of components
    cache_key = hashlib.sha256('|'.join(components).encode()).hexdigest()
    
    return f"cache:{request.hostname}:{cache_key}"
```

**Cache Policy Decision Tree**:
```python
class CachePolicy:
    def should_cache(self, request, response):
        """
        Determine if response should be cached
        """
        # Don't cache if explicit no-cache headers
        if 'no-cache' in response.headers.get('Cache-Control', ''):
            return False
        
        # Don't cache if response has Set-Cookie
        if 'Set-Cookie' in response.headers:
            return False
        
        # Don't cache POST/PUT/DELETE requests
        if request.method not in ['GET', 'HEAD']:
            return False
        
        # Don't cache if response status not cacheable
        if response.status_code not in [200, 203, 204, 206, 300, 301, 404, 405, 410, 414, 501]:
            return False
        
        # Cache static content by default
        if self.is_static_content(request.url.path):
            return True
        
        # Check explicit caching headers
        if 'public' in response.headers.get('Cache-Control', ''):
            return True
        
        return False
    
    def get_ttl(self, request, response):
        """
        Determine cache TTL for response
        """
        # Check Cache-Control max-age
        cache_control = response.headers.get('Cache-Control', '')
        if 'max-age=' in cache_control:
            max_age = int(cache_control.split('max-age=')[1].split(',')[0])
            return max_age
        
        # Check Expires header
        if 'Expires' in response.headers:
            expires = parse_http_date(response.headers['Expires'])
            ttl = (expires - datetime.now()).total_seconds()
            return max(0, ttl)
        
        # Default TTL based on content type
        if self.is_static_content(request.url.path):
            return 86400  # 1 day for static content
        
        return 3600  # 1 hour default
    
    def is_static_content(self, path):
        """
        Check if path is static content
        """
        static_extensions = ['.jpg', '.png', '.gif', '.css', '.js', '.woff', '.woff2', '.ttf']
        return any(path.endswith(ext) for ext in static_extensions)
```

**Cache Invalidation**:
```python
class CacheInvalidation:
    def __init__(self):
        self.message_queue = KafkaProducer(bootstrap_servers=['kafka:9092'])
    
    def purge_url(self, url, customer_id):
        """
        Purge specific URL from all edge locations
        """
        purge_message = {
            'type': 'purge_url',
            'url': url,
            'customer_id': customer_id,
            'timestamp': time.time()
        }
        
        # Publish to Kafka topic
        self.message_queue.send('cache-purge', purge_message)
        
        # Edge servers subscribe to this topic and purge their local cache
        
    def purge_by_tag(self, tag, customer_id):
        """
        Purge all URLs with specific tag
        """
        purge_message = {
            'type': 'purge_tag',
            'tag': tag,
            'customer_id': customer_id,
            'timestamp': time.time()
        }
        
        self.message_queue.send('cache-purge', purge_message)
    
    def purge_all(self, customer_id):
        """
        Purge all content for customer
        """
        purge_message = {
            'type': 'purge_all',
            'customer_id': customer_id,
            'timestamp': time.time()
        }
        
        self.message_queue.send('cache-purge', purge_message)
```

**LRU Cache Implementation**:
```python
class LRUCache:
    def __init__(self, capacity_bytes):
        self.capacity = capacity_bytes
        self.current_size = 0
        self.cache = OrderedDict()  # Maintains insertion order
        self.lock = threading.Lock()
    
    def get(self, key):
        """Get item from cache and mark as recently used"""
        with self.lock:
            if key not in self.cache:
                return None
            
            # Move to end (most recently used)
            self.cache.move_to_end(key)
            return self.cache[key]
    
    def put(self, key, value):
        """Add item to cache, evict LRU if needed"""
        with self.lock:
            value_size = len(value)
            
            # If key exists, update it
            if key in self.cache:
                old_value = self.cache[key]
                self.current_size -= len(old_value['data'])
                self.cache.move_to_end(key)
            
            # Evict LRU items until space available
            while self.current_size + value_size > self.capacity:
                if not self.cache:
                    break
                lru_key, lru_value = self.cache.popitem(last=False)
                self.current_size -= len(lru_value['data'])
            
            # Add new item
            self.cache[key] = {
                'data': value,
                'timestamp': time.time()
            }
            self.current_size += value_size
    
    def delete(self, key):
        """Remove item from cache"""
        with self.lock:
            if key in self.cache:
                value = self.cache.pop(key)
                self.current_size -= len(value['data'])
```

### 4. Origin Shield

**Purpose**: Protect origin servers from high traffic and reduce duplicate requests

**Architecture**:
```
                [Edge Servers]
                 (1000 locations)
                       |
                       ↓
            [Origin Shield Servers]
            (10-20 per region)
                       |
                       ↓
              [Customer Origin]
```

**How Origin Shield Works**:
```
Without Origin Shield:
- 1000 edge locations might all cache miss simultaneously
- 1000 requests sent to origin server
- Origin server overwhelmed

With Origin Shield:
- 1000 edge locations send requests to shield
- Shield collapses duplicate requests (request coalescing)
- Only 1 request sent to origin server
- Response cached at shield
- Shield distributes to all edges
```

**Request Coalescing**:
```python
class OriginShield:
    def __init__(self):
        self.pending_requests = {}  # Key: URL, Value: Future/Promise
        self.cache = LRUCache(capacity_bytes=100 * 1024**4)  # 100 TB
        self.lock = threading.Lock()
    
    async def fetch_content(self, url):
        """
        Fetch content with request coalescing
        """
        # Check local cache first
        cached = self.cache.get(url)
        if cached:
            return cached
        
        # Check if request already in-flight
        with self.lock:
            if url in self.pending_requests:
                # Wait for existing request to complete
                return await self.pending_requests[url]
            
            # Create new future for this request
            future = asyncio.Future()
            self.pending_requests[url] = future
        
        try:
            # Fetch from origin
            response = await self.fetch_from_origin(url)
            
            # Cache the response
            self.cache.put(url, response)
            
            # Resolve future for waiting requests
            future.set_result(response)
            
            return response
        
        finally:
            # Clean up pending request
            with self.lock:
                del self.pending_requests[url]
    
    async def fetch_from_origin(self, url):
        """
        Actually fetch from customer origin
        """
        async with aiohttp.ClientSession() as session:
            async with session.get(url) as response:
                content = await response.read()
                return {
                    'status': response.status,
                    'headers': dict(response.headers),
                    'body': content
                }
```

### 5. DDoS Protection System

**Purpose**: Detect and mitigate Distributed Denial of Service attacks

**Multi-Layer Defense**:

**Layer 1: Network-Level (L3/L4)**
```
Anycast + BGP Blackholing:
- Distribute attack traffic across all edge locations
- Each location handles portion of attack
- Use BGP blackholing to drop malicious traffic upstream
- Capacity: 10+ Tbps aggregate

SYN Flood Protection:
- SYN cookies to prevent connection exhaustion
- Rate limit SYN packets per source IP
- TCP connection state tracking
```

**Layer 2: Application-Level (L7)**
```python
class DDoSProtection:
    def __init__(self):
        self.rate_limiters = {}  # Per IP rate limiters
        self.attack_signatures = self.load_attack_signatures()
        self.anomaly_detector = AnomalyDetector()
    
    def analyze_request(self, request, client_ip):
        """
        Analyze request for DDoS patterns
        """
        # Rate limiting per IP
        if self.is_rate_limited(client_ip):
            return {'action': 'block', 'reason': 'rate_limit_exceeded'}
        
        # Check against known attack signatures
        if self.matches_attack_signature(request):
            return {'action': 'block', 'reason': 'attack_signature_match'}
        
        # Anomaly detection
        if self.anomaly_detector.is_anomalous(request, client_ip):
            return {'action': 'challenge', 'reason': 'anomalous_behavior'}
        
        # Check request characteristics
        if self.is_suspicious_request(request):
            return {'action': 'challenge', 'reason': 'suspicious_request'}
        
        return {'action': 'allow'}
    
    def is_rate_limited(self, client_ip):
        """
        Check if IP exceeded rate limit
        """
        if client_ip not in self.rate_limiters:
            self.rate_limiters[client_ip] = TokenBucket(
                capacity=100,
                refill_rate=10  # 10 requests per second
            )
        
        return not self.rate_limiters[client_ip].consume(1)
    
    def is_suspicious_request(self, request):
        """
        Check request characteristics
        """
        # No user-agent
        if not request.headers.get('User-Agent'):
            return True
        
        # Unusual request methods
        if request.method not in ['GET', 'POST', 'HEAD', 'PUT', 'DELETE']:
            return True
        
        # Malformed headers
        if self.has_malformed_headers(request):
            return True
        
        # Excessive header count
        if len(request.headers) > 50:
            return True
        
        # Very large headers
        total_header_size = sum(len(k) + len(v) for k, v in request.headers.items())
        if total_header_size > 16384:  # 16 KB
            return True
        
        return False
```

**Challenge Mechanism (CAPTCHA/Proof of Work)**:
```python
class ChallengeSystem:
    def issue_challenge(self, client_ip, request):
        """
        Issue JavaScript/CAPTCHA challenge to verify human
        """
        challenge_token = self.generate_challenge_token(client_ip)
        
        return {
            'status': 403,
            'body': self.render_challenge_page(challenge_token),
            'headers': {
                'Content-Type': 'text/html',
                'Cache-Control': 'no-cache'
            }
        }
    
    def generate_challenge_token(self, client_ip):
        """
        Generate cryptographic challenge token
        """
        timestamp = int(time.time())
        nonce = secrets.token_hex(16)
        
        # Sign with HMAC
        data = f"{client_ip}:{timestamp}:{nonce}"
        signature = hmac.new(
            self.secret_key.encode(),
            data.encode(),
            hashlib.sha256
        ).hexdigest()
        
        return f"{data}:{signature}"
    
    def verify_challenge_response(self, token, response):
        """
        Verify challenge was solved correctly
        """
        # Parse token
        parts = token.split(':')
        client_ip, timestamp, nonce, signature = parts
        
        # Verify signature
        data = f"{client_ip}:{timestamp}:{nonce}"
        expected_sig = hmac.new(
            self.secret_key.encode(),
            data.encode(),
            hashlib.sha256
        ).hexdigest()
        
        if signature != expected_sig:
            return False
        
        # Check timestamp not expired (5 minutes)
        if time.time() - int(timestamp) > 300:
            return False
        
        # Verify proof of work or CAPTCHA solution
        return self.verify_pow(response) or self.verify_captcha(response)
```

### 6. Web Application Firewall (WAF)

**Purpose**: Protect against application-layer attacks

**Rule Categories**:

**1. SQL Injection Protection**
```python
class SQLInjectionDetector:
    def __init__(self):
        self.patterns = [
            r"(\bunion\b.*\bselect\b)",
            r"(\bselect\b.*\bfrom\b)",
            r"(--|\#|\/\*)",
            r"(\bor\b\s+\d+\s*=\s*\d+)",
            r"(\bdrop\b\s+\btable\b)",
            r"(\bexec\b\s*\()",
        ]
        self.compiled_patterns = [re.compile(p, re.IGNORECASE) for p in self.patterns]
    
    def detect(self, value):
        """
        Check if value contains SQL injection attempt
        """
        for pattern in self.compiled_patterns:
            if pattern.search(value):
                return True
        return False
```

**2. XSS (Cross-Site Scripting) Protection**
```python
class XSSDetector:
    def __init__(self):
        self.patterns = [
            r"<script[^>]*>.*?</script>",
            r"javascript:",
            r"on\w+\s*=",  # Event handlers
            r"<iframe",
            r"<object",
            r"<embed",
        ]
        self.compiled_patterns = [re.compile(p, re.IGNORECASE) for p in self.patterns]
    
    def detect(self, value):
        """
        Check if value contains XSS attempt
        """
        for pattern in self.compiled_patterns:
            if pattern.search(value):
                return True
        return False
```

**3. Path Traversal Protection**
```python
class PathTraversalDetector:
    def detect(self, path):
        """
        Check if path contains directory traversal attempt
        """
        dangerous_patterns = ['../', '..\\', '%2e%2e', '..;']
        normalized_path = path.lower()
        
        return any(pattern in normalized_path for pattern in dangerous_patterns)
```

---

## Database Design

### Configuration Database (PostgreSQL)

```sql
-- Customer accounts
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    subscription_tier VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Customer domains/websites
CREATE TABLE domains (
    domain_id UUID PRIMARY KEY,
    customer_id UUID REFERENCES customers(customer_id),
    domain_name VARCHAR(255) UNIQUE NOT NULL,
    origin_url VARCHAR(512) NOT NULL,
    ssl_cert_id UUID,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Caching rules
CREATE TABLE cache_rules (
    rule_id UUID PRIMARY KEY,
    domain_id UUID REFERENCES domains(domain_id),
    path_pattern VARCHAR(512),
    cache_ttl INTEGER,  -- seconds
    cache_key_template TEXT,
    bypass_cache BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- SSL certificates
CREATE TABLE ssl_certificates (
    cert_id UUID PRIMARY KEY,
    domain_id UUID REFERENCES domains(domain_id),
    certificate_pem TEXT,
    private_key_pem TEXT,
    expires_at TIMESTAMP,
    auto_renew BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- WAF rules
CREATE TABLE waf_rules (
    rule_id UUID PRIMARY KEY,
    domain_id UUID REFERENCES domains(domain_id),
    rule_type VARCHAR(50),  -- sql_injection, xss, rate_limit
    rule_config JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Edge locations
CREATE TABLE edge_locations (
    location_id UUID PRIMARY KEY,
    location_code VARCHAR(10) UNIQUE,
    city VARCHAR(100),
    country VARCHAR(100),
    latitude DECIMAL(9,6),
    longitude DECIMAL(9,6),
    anycast_ip INET,
    capacity_gbps INTEGER,
    status VARCHAR(20),  -- active, maintenance, disabled
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Logs and Analytics (Cassandra)

```sql
-- Request logs (time-series)
CREATE TABLE request_logs (
    edge_location TEXT,
    timestamp TIMESTAMP,
    request_id UUID,
    domain TEXT,
    path TEXT,
    method TEXT,
    status_code INT,
    response_time_ms INT,
    bytes_sent BIGINT,
    cache_status TEXT,  -- hit, miss, stale
    client_ip TEXT,
    user_agent TEXT,
    referer TEXT,
    PRIMARY KEY (edge_location, timestamp, request_id)
) WITH CLUSTERING ORDER BY (timestamp DESC);

-- Cache statistics
CREATE TABLE cache_stats (
    edge_location TEXT,
    hour TIMESTAMP,  -- Truncated to hour
    domain TEXT,
    total_requests BIGINT,
    cache_hits BIGINT,
    cache_misses BIGINT,
    bytes_served BIGINT,
    PRIMARY KEY (edge_location, hour, domain)
);

-- Security events
CREATE TABLE security_events (
    edge_location TEXT,
    timestamp TIMESTAMP,
    event_id UUID,
    event_type TEXT,  -- ddos, waf_block, rate_limit
    client_ip TEXT,
    domain TEXT,
    blocked BOOLEAN,
    details TEXT,
    PRIMARY KEY (edge_location, timestamp, event_id)
) WITH CLUSTERING ORDER BY (timestamp DESC);
```

### Metrics Database (ClickHouse)

```sql
-- Performance metrics
CREATE TABLE performance_metrics (
    timestamp DateTime,
    edge_location String,
    domain String,
    requests_per_sec UInt32,
    avg_response_time_ms UInt32,
    p95_response_time_ms UInt32,
    p99_response_time_ms UInt32,
    error_rate Float32,
    bandwidth_mbps UInt32
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (edge_location, domain, timestamp);

-- Cache metrics
CREATE TABLE cache_metrics (
    timestamp DateTime,
    edge_location String,
    domain String,
    cache_hit_ratio Float32,
    cache_size_gb Float32,
    evictions_per_sec UInt32
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (edge_location, domain, timestamp);
```

---

## API Design

### Customer Control Plane APIs

```
# Authentication
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
GET    /api/v1/auth/validate

# Domain Management
GET    /api/v1/domains
POST   /api/v1/domains
GET    /api/v1/domains/{domain_id}
PUT    /api/v1/domains/{domain_id}
DELETE /api/v1/domains/{domain_id}

# Cache Management
POST   /api/v1/domains/{domain_id}/purge
Body: {
  "type": "url|tag|all",
  "urls": ["https://example.com/image.jpg"],
  "tags": ["product-images"]
}

GET    /api/v1/domains/{domain_id}/cache-rules
POST   /api/v1/domains/{domain_id}/cache-rules
PUT    /api/v1/domains/{domain_id}/cache-rules/{rule_id}
DELETE /api/v1/domains/{domain_id}/cache-rules/{rule_id}

# SSL Certificate Management
GET    /api/v1/domains/{domain_id}/ssl
POST   /api/v1/domains/{domain_id}/ssl
Body: {
  "certificate": "-----BEGIN CERTIFICATE-----\n...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...",
  "auto_renew": true
}

DELETE /api/v1/domains/{domain_id}/ssl

# WAF Configuration
GET    /api/v1/domains/{domain_id}/waf/rules
POST   /api/v1/domains/{domain_id}/waf/rules
PUT    /api/v1/domains/{domain_id}/waf/rules/{rule_id}
DELETE /api/v1/domains/{domain_id}/waf/rules/{rule_id}

# Analytics
GET    /api/v1/domains/{domain_id}/analytics?start_time={}&end_time={}
Response: {
  "requests_total": 1000000,
  "bandwidth_gb": 5000,
  "cache_hit_ratio": 0.92,
  "avg_response_time_ms": 45,
  "error_rate": 0.001,
  "top_paths": [...],
  "geographic_distribution": {...}
}

GET    /api/v1/domains/{domain_id}/analytics/realtime
Response: {
  "requests_per_second": 1500,
  "bandwidth_mbps": 2000,
  "active_connections": 50000,
  "top_countries": [...]
}

# Health & Status
GET    /api/v1/edge-locations
Response: [
  {
    "location_id": "uuid",
    "code": "NYC",
    "city": "New York",
    "country": "USA",
    "status": "active",
    "capacity_used": 0.65,
    "latency_ms": 12
  }
]
```

### Edge Server Internal APIs

```
# Configuration sync
GET    /internal/v1/config/domain/{domain_id}
GET    /internal/v1/config/cache-rules/{domain_id}
GET    /internal/v1/config/waf-rules/{domain_id}

# Health check
GET    /internal/v1/health
Response: {
  "status": "healthy",
  "cache_size_gb": 8.5,
  "cache_hit_ratio": 0.93,
  "cpu_usage": 0.45,
  "memory_usage": 0.67,
  "active_connections": 150000
}

# Metrics reporting
POST   /internal/v1/metrics
Body: {
  "location_id": "uuid",
  "timestamp": 1234567890,
  "metrics": {
    "requests_per_sec": 50000,
    "cache_hit_ratio": 0.92,
    "avg_response_time_ms": 35
  }
}

# Cache purge (Kafka-based)
Topic: cache-purge
Message: {
  "domain_id": "uuid",
  "type": "url",
  "urls": ["https://example.com/image.jpg"],
  "timestamp": 1234567890
}
```

---

## Deep Dives

### 1. Content Delivery Flow (End-to-End)

```
1. DNS Resolution
   ↓
[User requests https://www.example.com/image.jpg]
   ↓
[DNS query for www.example.com]
   ↓
[GeoDNS returns edge location IP: 203.0.113.1]
   ↓
[User browser connects to 203.0.113.1]

2. Edge Server Processing
   ↓
[HTTPS connection established (TLS handshake)]
   ↓
[Edge server receives: GET /image.jpg]
   ↓
[Check cache for: cache:example.com:hash(image.jpg)]
   ↓
Cache Hit? → Yes
   ↓
[Validate TTL not expired]
   ↓
[Serve from cache (< 10ms)]
   ↓
[Log: cache_status=hit, response_time=8ms]

Cache Miss? → Yes
   ↓
3. Origin Shield Request
   ↓
[Forward to Origin Shield in same region]
   ↓
[Shield checks its cache]
   ↓
Shield Hit? → Serve to edge, cache at edge
Shield Miss? → Fetch from origin
   ↓
4. Origin Fetch
   ↓
[Shield requests from customer origin]
   ↓
[Origin returns: 200 OK, image data]
   ↓
[Shield caches response]
   ↓
[Shield returns to edge]
   ↓
[Edge caches response]
   ↓
[Edge serves to user]
   ↓
[Log: cache_status=miss, response_time=150ms]

5. Response Optimization
   ↓
[Apply compression (Brotli/Gzip)]
   ↓
[Add security headers]
   ↓
[Add cache headers (Cache-Control, ETag)]
   ↓
[Send response to user]
```

### 2. HTTP/2 and HTTP/3 Support

**HTTP/2 Benefits**:
```
- Multiplexing: Multiple requests over single connection
- Header compression: HPACK reduces overhead
- Server push: Proactively send resources
- Stream prioritization: Critical resources first
```

**HTTP/3 (QUIC) Benefits**:
```
- Faster connection establishment (0-RTT)
- No head-of-line blocking
- Better performance on lossy networks
- Built-in encryption (TLS 1.3)
```

**Implementation**:
```nginx
# NGINX configuration
server {
    listen 443 ssl http2;
    listen 443 quic reuseport;
    
    http2_push_preload on;
    http3 on;
    quic_retry on;
    
    # Add Alt-Svc header for HTTP/3 discovery
    add_header Alt-Svc 'h3=":443"; ma=86400';
    
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
}
```

### 3. Image Optimization

**Purpose**: Automatically optimize images for faster delivery

**Techniques**:

**1. Format Conversion**
```python
class ImageOptimizer:
    def optimize_image(self, image_data, accept_header):
        """
        Convert image to optimal format based on browser support
        """
        # Check browser support from Accept header
        supports_webp = 'image/webp' in accept_header
        supports_avif = 'image/avif' in accept_header
        
        if supports_avif:
            # AVIF: Best compression (30-50% smaller than WebP)
            return self.convert_to_avif(image_data)
        elif supports_webp:
            # WebP: Good compression, wide support
            return self.convert_to_webp(image_data)
        else:
            # Fallback to optimized JPEG/PNG
            return self.optimize_original(image_data)
    
    def convert_to_webp(self, image_data):
        """Convert image to WebP format"""
        from PIL import Image
        import io
        
        img = Image.open(io.BytesIO(image_data))
        output = io.BytesIO()
        img.save(output, format='WEBP', quality=85, method=6)
        return output.getvalue()
```

**2. Responsive Images**
```python
def generate_responsive_variants(self, image_data):
    """
    Generate multiple resolutions for responsive images
    """
    sizes = [
        (320, 'mobile-small'),
        (640, 'mobile'),
        (1024, 'tablet'),
        (1920, 'desktop'),
        (3840, '4k')
    ]
    
    variants = {}
    for width, label in sizes:
        resized = self.resize_image(image_data, width)
        variants[label] = resized
    
    return variants
```

**3. Lazy Loading Headers**
```python
def add_lazy_loading_hints(self, response):
    """
    Add headers for browser lazy loading
    """
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['Loading'] = 'lazy'  # Hint for native lazy loading
    return response
```

### 4. SSL/TLS Certificate Management

**Automated Certificate Provisioning**:
```python
class CertificateManager:
    def __init__(self):
        self.acme_client = ACMEClient()  # Let's Encrypt client
    
    async def provision_certificate(self, domain):
        """
        Automatically provision SSL certificate for domain
        """
        # Step 1: Create account if doesn't exist
        account = await self.acme_client.get_or_create_account()
        
        # Step 2: Create order for domain
        order = await self.acme_client.create_order([domain])
        
        # Step 3: Get challenges
        authorizations = await order.get_authorizations()
        
        # Step 4: Complete HTTP-01 challenge
        for authz in authorizations:
            challenge = authz.get_challenge('http-01')
            
            # Deploy challenge token to all edge servers
            token = challenge.token
            key_auth = challenge.key_authorization
            
            await self.deploy_challenge_to_edges(domain, token, key_auth)
            
            # Notify ACME server
            await challenge.process()
            await challenge.wait_for_validation()
        
        # Step 5: Finalize order and get certificate
        certificate = await order.finalize_and_download()
        
        # Step 6: Deploy certificate to all edge servers
        await self.deploy_certificate_to_edges(domain, certificate)
        
        # Step 7: Store in database
        await self.store_certificate(domain, certificate)
        
        return certificate
    
    async def auto_renew_certificates(self):
        """
        Automatically renew certificates expiring soon
        """
        # Find certificates expiring in next 30 days
        expiring_certs = await self.get_expiring_certificates(days=30)
        
        for cert in expiring_certs:
            try:
                new_cert = await self.provision_certificate(cert.domain)
                logger.info(f"Renewed certificate for {cert.domain}")
            except Exception as e:
                logger.error(f"Failed to renew {cert.domain}: {e}")
                await self.alert_certificate_renewal_failure(cert.domain)
```

### 5. Real-Time Analytics Pipeline

**Architecture**:
```
[Edge Servers]
     ↓ (batch logs every 10 sec)
[Kafka Topics]
     ↓
[Kafka Streams Processing]
     ↓ (aggregate metrics)
[ClickHouse (Analytics DB)]
     ↓
[Real-time Dashboard]
```

**Log Aggregation**:
```python
class LogAggregator:
    def __init__(self):
        self.kafka_producer = KafkaProducer(
            bootstrap_servers=['kafka:9092'],
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        self.buffer = []
        self.buffer_size = 1000
    
    def log_request(self, request_data):
        """
        Buffer and batch log requests
        """
        self.buffer.append(request_data)
        
        if len(self.buffer) >= self.buffer_size:
            self.flush()
    
    def flush(self):
        """
        Send buffered logs to Kafka
        """
        if not self.buffer:
            return
        
        # Send batch to Kafka
        for log in self.buffer:
            self.kafka_producer.send('request-logs', log)
        
        self.kafka_producer.flush()
        self.buffer.clear()
```

**Real-Time Metrics Calculation**:
```python
class MetricsCalculator:
    def process_logs(self, logs):
        """
        Calculate metrics from request logs
        """
        metrics = {
            'timestamp': time.time(),
            'total_requests': len(logs),
            'cache_hits': 0,
            'cache_misses': 0,
            'total_bytes': 0,
            'response_times': [],
            'status_codes': defaultdict(int),
            'paths': defaultdict(int)
        }
        
        for log in logs:
            # Cache stats
            if log['cache_status'] == 'hit':
                metrics['cache_hits'] += 1
            else:
                metrics['cache_misses'] += 1
            
            # Bandwidth
            metrics['total_bytes'] += log['bytes_sent']
            
            # Response times
            metrics['response_times'].append(log['response_time_ms'])
            
            # Status codes
            metrics['status_codes'][log['status_code']] += 1
            
            # Popular paths
            metrics['paths'][log['path']] += 1
        
        # Calculate percentiles
        metrics['avg_response_time'] = np.mean(metrics['response_times'])
        metrics['p50_response_time'] = np.percentile(metrics['response_times'], 50)
        metrics['p95_response_time'] = np.percentile(metrics['response_times'], 95)
        metrics['p99_response_time'] = np.percentile(metrics['response_times'], 99)
        
        # Calculate cache hit ratio
        metrics['cache_hit_ratio'] = metrics['cache_hits'] / metrics['total_requests']
        
        return metrics
```

---

## Scalability & Reliability

### Horizontal Scaling

**Edge Server Scaling**:
```
Auto-scaling triggers:
- CPU > 70% for 5 minutes → Add server
- Requests per second > 80K → Add server
- CPU < 30% for 15 minutes → Remove server
- Minimum servers per location: 5
- Maximum servers per location: 50

Deployment process:
1. Provision new server
2. Install software stack
3. Sync configuration from control plane
4. Warm up cache (pre-fetch popular content)
5. Add to load balancer rotation
6. Monitor health for 10 minutes
7. Mark as active
```

**Geographic Expansion**:
```
Adding new edge location:
1. Identify high-latency region
2. Select datacenter with good connectivity
3. Deploy hardware (10 servers initially)
4. Configure anycast IP announcement
5. Replicate configuration data
6. Test connectivity from region
7. Gradually ramp traffic (10% → 50% → 100%)
8. Monitor performance for 1 week
9. Full activation
```

### High Availability

**Multi-Level Failover**:
```
Level 1: Server-Level
- Health check every 5 seconds
- 3 consecutive failures → Remove from rotation
- Automatic traffic redistribution to healthy servers
- Recovery time: < 15 seconds

Level 2: Location-Level
- Monitor aggregate location health
- If > 50% servers down → Mark location unhealthy
- GeoDNS stops routing to location
- Traffic redirected to next-nearest location
- Recovery time: < 60 seconds (DNS TTL)

Level 3: Region-Level
- If entire region fails
- Anycast automatically reroutes to other regions
- Global capacity absorbs traffic
- Recovery time: < 30 seconds (BGP convergence)
```

**Split-Brain Prevention**:
```python
class HealthChecker:
    def __init__(self):
        self.etcd_client = etcd3.client()
        self.lease_ttl = 10  # seconds
    
    async def maintain_leadership(self, server_id):
        """
        Use distributed consensus for leader election
        """
        lease = self.etcd_client.lease(self.lease_ttl)
        
        try:
            # Try to acquire lock
            success = self.etcd_client.put_if_not_exists(
                f'/leader/{server_id}',
                server_id,
                lease=lease
            )
            
            if success:
                # This server is the leader
                while True:
                    await asyncio.sleep(self.lease_ttl / 2)
                    lease.refresh()
            else:
                # Another server is leader, become follower
                await self.follow_leader()
        
        except Exception as e:
            # Lost leadership, gracefully step down
            logger.warning(f"Lost leadership: {e}")
            await self.become_follower()
```

### Load Balancing

**Multi-Tier Load Balancing**:
```
Tier 1: DNS-Level (GeoDNS)
- Route to nearest edge location
- Consider health and capacity
- TTL: 60 seconds

Tier 2: Anycast Routing
- Internet routers distribute to nearest location
- Automatic failover via BGP

Tier 3: Location-Level (NGINX)
- Distribute across servers in location
- Least connections algorithm
- Health check every 5 seconds
- Sticky sessions for stateful requests

Tier 4: Server-Level (Kernel)
- Network interface bonding
- Receive Packet Steering (RPS)
- Transmit Packet Steering (XPS)
```

**Load Balancing Algorithm**:
```python
class LoadBalancer:
    def select_server(self, servers):
        """
        Select optimal server using weighted least connections
        """
        if not servers:
            return None
        
        # Calculate score for each server
        scored_servers = []
        for server in servers:
            # Factors: connections, CPU, response time
            connection_score = server.active_connections / server.max_connections
            cpu_score = server.cpu_usage
            latency_score = server.avg_response_time / 100  # Normalize to 0-1
            
            # Weighted score (lower is better)
            score = (connection_score * 0.4) + (cpu_score * 0.3) + (latency_score * 0.3)
            
            scored_servers.append((server, score))
        
        # Return server with lowest score
        best_server = min(scored_servers, key=lambda x: x[1])
        return best_server[0]
```

---

## Trade-offs & Alternatives

### 1. Pull-Based vs Push-Based CDN

**Chose: Pull-Based (Origin Pull)**

**Pros**:
- Simpler for customers (no upload needed)
- Automatic cache updates
- Lower storage costs (only cache popular content)
- No content duplication

**Cons**:
- Initial request is slower (cache miss)
- Origin server must stay online

**Alternative: Push-Based**
- Customer uploads content to CDN
- Guaranteed availability
- Higher storage costs
- Manual content updates

### 2. Anycast vs GeoDNS

**Chose: Both (Hybrid)**

**Anycast for**:
- DDoS protection (distributed traffic)
- Automatic failover
- Simple client configuration

**GeoDNS for**:
- Fine-grained control
- Consider server load
- Implement custom routing logic

### 3. Centralized vs Distributed Configuration

**Chose: Centralized with Edge Cache**

**Architecture**:
- Central PostgreSQL database for configuration
- Edge servers cache configuration (Redis)
- Periodic sync every 60 seconds
- Kafka for immediate updates

**Pros**:
- Consistent configuration across edges
- Easy to manage and update
- Audit trail of changes

**Cons**:
- Slight delay for configuration propagation
- Dependency on control plane

### 4. Custom vs NGINX

**Chose: NGINX + Custom Extensions**

**Reasoning**:
- NGINX is battle-tested and performant
- Extensible via modules
- Large community and documentation
- Custom C modules for CDN-specific features

**Custom components**:
- Cache key generation
- DDoS protection
- Origin shield logic
- Real-time analytics

### 5. Cache Eviction: LRU vs LFU

**Chose: LRU (Least Recently Used)**

**Reasoning**:
- Simpler to implement
- Better for time-sensitive content
- Good performance for web content
- Can be enhanced with frequency tracking

**Alternative: LFU (Least Frequently Used)**
- Better for stable content
- More complex
- Can suffer from "cache pollution"

---

## Security & Compliance

### Data Privacy

**GDPR Compliance**:
```
- Data minimization: Log only necessary data
- Retention period: 30 days for logs
- Right to access: Customer API for log export
- Right to erasure: Purge logs on request
- Data encryption: At rest and in transit
```

**PCI DSS for Payment Data**:
```
- Never log credit card numbers
- Tokenize sensitive data
- Encrypt all transmission
- Regular security audits
- Access control and monitoring
```

### Access Control

```python
class AccessControl:
    def check_permission(self, user, resource, action):
        """
        Role-Based Access Control (RBAC)
        """
        user_roles = self.get_user_roles(user)
        
        for role in user_roles:
            permissions = self.get_role_permissions(role)
            
            if self.has_permission(permissions, resource, action):
                return True
        
        return False
    
    def has_permission(self, permissions, resource, action):
        """
        Check if permissions allow action on resource
        """
        for perm in permissions:
            if perm.matches(resource, action):
                return True
        
        return False
```

### Audit Logging

```python
class AuditLogger:
    def log_action(self, user, action, resource, result):
        """
        Log all administrative actions
        """
        audit_log = {
            'timestamp': time.time(),
            'user_id': user.id,
            'user_email': user.email,
            'action': action,
            'resource_type': resource.type,
            'resource_id': resource.id,
            'result': result,  # success, denied, error
            'ip_address': user.ip_address,
            'user_agent': user.user_agent
        }
        
        # Store in audit database
        self.db.insert('audit_logs', audit_log)
        
        # Alert on sensitive actions
        if action in ['delete_domain', 'modify_waf', 'purge_all']:
            self.send_alert(audit_log)
```

---

## Cost Optimization

### Strategies

**1. Bandwidth Optimization**
```
- Compression: 60-80% bandwidth savings
- Image optimization: 40-60% savings
- Aggressive caching: 90%+ cache hit ratio
- Origin shield: Reduce origin bandwidth by 80%
```

**2. Storage Optimization**
```
- Tiered caching: Hot (SSD) + Warm (HDD)
- LRU eviction: Keep only popular content
- Deduplication: Single copy for identical content
- Compression: Compressed storage format
```

**3. Compute Optimization**
```
- Efficient C/C++ web server
- Asynchronous I/O (avoid blocking)
- Connection pooling
- Keep-alive connections
- Hardware acceleration for SSL
```

**Cost Breakdown**:
```
Monthly costs for 100M RPS:
- Servers (10K × $500): $5M (27%)
- Bandwidth (80 Tbps): $10M (54%)
- Storage (50 PB): $1M (5%)
- Data centers (power, cooling): $2M (11%)
- Network equipment: $500K (3%)
Total: $18.5M/month

Per request cost: $0.000185
Revenue per request: $0.0005-0.001
Profit margin: 2-5x
```

---

## Technology Stack Summary

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Web Server** | NGINX, Custom C/C++ | Request handling |
| **Cache** | Redis, Custom LRU | In-memory caching |
| **Storage** | NVMe SSD | Disk caching |
| **Database** | PostgreSQL | Configuration |
| **Logs** | Cassandra, ClickHouse | Time-series data |
| **Message Queue** | Apache Kafka | Event streaming |
| **DNS** | Custom GeoDNS | Routing |
| **Routing** | BGP, Anycast | Network routing |
| **SSL/TLS** | OpenSSL, Let's Encrypt | Encryption |
| **DDoS** | Custom + Kernel | Protection |
| **WAF** | ModSecurity, Custom | Application firewall |
| **Compression** | Brotli, Gzip | Response compression |
| **
