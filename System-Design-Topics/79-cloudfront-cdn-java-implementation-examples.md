# 🌐 Topic 79: AWS CloudFront (CDN) Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Amazon CloudFront using **AWS SDK v2**, **signed URLs/cookies**, **Lambda@Edge**, and **cache invalidation**. Every pattern includes production-ready code with origin configuration, security, cache behaviors, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration](#1-client-setup--configuration)
- [2. Create Distribution (S3 Origin)](#2-create-distribution-s3-origin)
- [3. Signed URLs (Private Content)](#3-signed-urls-private-content)
- [4. Signed Cookies (Session-Based Access)](#4-signed-cookies-session-based-access)
- [5. Cache Invalidation](#5-cache-invalidation)
- [6. Custom Origin (ALB/API Gateway)](#6-custom-origin-albapi-gateway)
- [7. Multiple Origins with Behaviors](#7-multiple-origins-with-behaviors)
- [8. Origin Access Control (OAC)](#8-origin-access-control-oac)
- [9. Lambda@Edge (Request/Response Manipulation)](#9-lambdaedge-requestresponse-manipulation)
- [10. CloudFront Functions (Lightweight Edge Logic)](#10-cloudfront-functions-lightweight-edge-logic)
- [11. Geo-Restriction](#11-geo-restriction)
- [12. Custom Error Pages](#12-custom-error-pages)
- [13. Real-Time Logging & Monitoring](#13-real-time-logging--monitoring)
- [14. Cache Policy & TTL Configuration](#14-cache-policy--ttl-configuration)
- [15. Origin Failover (High Availability)](#15-origin-failover-high-availability)
- [16. WebSocket Support](#16-websocket-support)
- [17. Field-Level Encryption](#17-field-level-encryption)
- [18. Pre-Signed URL Service (Video Streaming)](#18-pre-signed-url-service-video-streaming)
- [19. Programmatic Distribution Management](#19-programmatic-distribution-management)
- [20. Performance Optimization Patterns](#20-performance-optimization-patterns)
- [🏆 Configuration Cheat Sheet](#-configuration-cheat-sheet)

---

## 1. Client Setup & Configuration

```java
@Configuration
public class CloudFrontConfig {
    
    @Bean
    public CloudFrontClient cloudFrontClient() {
        return CloudFrontClient.builder()
            .region(Region.AWS_GLOBAL)  // CloudFront is global
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .apiCallTimeout(Duration.ofSeconds(30))
                .build())
            .build();
    }
    
    // For signing URLs/cookies
    @Bean
    public CloudFrontUtilities cloudFrontUtilities() {
        return CloudFrontUtilities.create();
    }
    
    @Value("${cloudfront.domain}")
    private String distributionDomain;  // e.g., "d1234abcdef.cloudfront.net"
    
    @Value("${cloudfront.key-pair-id}")
    private String keyPairId;  // CloudFront key pair ID for signing
    
    @Value("${cloudfront.private-key-path}")
    private String privateKeyPath;  // Path to PEM private key file
}
```

---

## 2. Create Distribution (S3 Origin)

```java
@Service
public class DistributionService {
    private final CloudFrontClient cf;
    
    // ====== Create CloudFront distribution with S3 origin ======
    public String createS3Distribution(String bucketName, String domainAlias) {
        String originId = "S3-" + bucketName;
        String oaiId = createOriginAccessIdentity(bucketName);
        
        CreateDistributionResponse response = cf.createDistribution(
            CreateDistributionRequest.builder()
                .distributionConfig(DistributionConfig.builder()
                    .callerReference(UUID.randomUUID().toString())
                    .comment("CDN for " + bucketName)
                    .enabled(true)
                    .defaultRootObject("index.html")
                    
                    // Origins
                    .origins(Origins.builder()
                        .quantity(1)
                        .items(Origin.builder()
                            .id(originId)
                            .domainName(bucketName + ".s3.amazonaws.com")
                            .s3OriginConfig(S3OriginConfig.builder()
                                .originAccessIdentity("origin-access-identity/cloudfront/" + oaiId)
                                .build())
                            .build())
                        .build())
                    
                    // Default cache behavior
                    .defaultCacheBehavior(DefaultCacheBehavior.builder()
                        .targetOriginId(originId)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_HTTPS)
                        .allowedMethods(AllowedMethods.builder()
                            .quantity(2).items(Method.GET, Method.HEAD).build())
                        .compress(true)  // Gzip/Brotli compression
                        .cachePolicyId("658327ea-f89d-4fab-a63d-7e88639e58f6")  // CachingOptimized
                        .build())
                    
                    // HTTPS
                    .viewerCertificate(ViewerCertificate.builder()
                        .cloudFrontDefaultCertificate(true)
                        .build())
                    
                    // Price class (edge locations)
                    .priceClass(PriceClass.PRICE_CLASS_100)  // US, Canada, Europe
                    
                    .build())
                .build());
        
        String distributionId = response.distribution().id();
        String domain = response.distribution().domainName();
        log.info("Distribution created: id={}, domain={}", distributionId, domain);
        return distributionId;
    }
}
```

---

## 3. Signed URLs (Private Content)

```java
@Service
public class CloudFrontSignedUrlService {
    private final CloudFrontUtilities cfUtils;
    private final String distributionDomain;
    private final String keyPairId;
    private final Path privateKeyPath;
    
    // ====== Generate signed URL for private content ======
    public String generateSignedUrl(String resourcePath, Duration validFor) {
        Instant expirationDate = Instant.now().plus(validFor);
        String resourceUrl = "https://" + distributionDomain + "/" + resourcePath;
        
        CannedSignerRequest request = CannedSignerRequest.builder()
            .resourceUrl(resourceUrl)
            .privateKey(privateKeyPath)
            .keyPairId(keyPairId)
            .expirationDate(expirationDate)
            .build();
        
        SignedUrl signedUrl = cfUtils.getSignedUrlWithCannedPolicy(request);
        return signedUrl.url();
    }
    
    // ====== Signed URL with custom policy (IP restriction + date range) ======
    public String generateSignedUrlWithPolicy(String resourcePath, Duration validFor, 
                                                String clientIp) {
        Instant activeDate = Instant.now();
        Instant expirationDate = Instant.now().plus(validFor);
        String resourceUrl = "https://" + distributionDomain + "/" + resourcePath;
        
        CustomSignerRequest request = CustomSignerRequest.builder()
            .resourceUrl(resourceUrl)
            .privateKey(privateKeyPath)
            .keyPairId(keyPairId)
            .expirationDate(expirationDate)
            .activeDate(activeDate)
            .ipRange(clientIp + "/32")  // Restrict to specific IP
            .build();
        
        SignedUrl signedUrl = cfUtils.getSignedUrlWithCustomPolicy(request);
        return signedUrl.url();
    }
    
    // ====== Use case: Video streaming with time-limited access ======
    public VideoAccessResponse getVideoAccess(String userId, String videoId) {
        // Verify user has permission
        if (!subscriptionService.hasAccess(userId, videoId)) {
            throw new AccessDeniedException("No access to video: " + videoId);
        }
        
        String hlsManifest = "videos/" + videoId + "/master.m3u8";
        String signedManifest = generateSignedUrl(hlsManifest, Duration.ofHours(4));
        
        // Also sign segment pattern (wildcard)
        String segmentPattern = "videos/" + videoId + "/*";
        String signedSegments = generateSignedUrlWithPolicy(segmentPattern, 
            Duration.ofHours(4), null);
        
        return new VideoAccessResponse(signedManifest, Duration.ofHours(4));
    }
}
```

---

## 4. Signed Cookies (Session-Based Access)

```java
@Service
public class CloudFrontSignedCookieService {
    private final CloudFrontUtilities cfUtils;
    
    // ====== Generate signed cookies (access all resources under a path) ======
    public Map<String, String> generateSignedCookies(String resourcePathPattern, Duration validFor) {
        // Pattern: https://d1234.cloudfront.net/premium/*
        String resourceUrl = "https://" + distributionDomain + "/" + resourcePathPattern;
        Instant expirationDate = Instant.now().plus(validFor);
        
        CustomSignerRequest request = CustomSignerRequest.builder()
            .resourceUrl(resourceUrl)
            .privateKey(privateKeyPath)
            .keyPairId(keyPairId)
            .expirationDate(expirationDate)
            .activeDate(Instant.now())
            .build();
        
        CookiesForCustomPolicy cookies = cfUtils.getCookiesForCustomPolicy(request);
        
        // Return 3 cookies that must be set on the client
        Map<String, String> cookieMap = new HashMap<>();
        cookieMap.put("CloudFront-Policy", cookies.policyHeaderValue());
        cookieMap.put("CloudFront-Signature", cookies.signatureHeaderValue());
        cookieMap.put("CloudFront-Key-Pair-Id", cookies.keyPairIdHeaderValue());
        
        return cookieMap;
    }
    
    // ====== Use case: Premium content access for logged-in users ======
    public ResponseEntity<Void> grantPremiumAccess(HttpServletResponse response, String userId) {
        if (!subscriptionService.isPremium(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        Map<String, String> cookies = generateSignedCookies("premium/*", Duration.ofHours(24));
        
        cookies.forEach((name, value) -> {
            Cookie cookie = new Cookie(name, value);
            cookie.setDomain(".mycdn.example.com");
            cookie.setPath("/");
            cookie.setSecure(true);
            cookie.setHttpOnly(true);
            cookie.setMaxAge((int) Duration.ofHours(24).getSeconds());
            response.addCookie(cookie);
        });
        
        return ResponseEntity.ok().build();
    }
}
```

---

## 5. Cache Invalidation

```java
@Service
public class CacheInvalidationService {
    private final CloudFrontClient cf;
    private final String distributionId;
    
    // ====== Invalidate specific paths ======
    public String invalidatePaths(List<String> paths) {
        CreateInvalidationResponse response = cf.createInvalidation(
            CreateInvalidationRequest.builder()
                .distributionId(distributionId)
                .invalidationBatch(InvalidationBatch.builder()
                    .callerReference(UUID.randomUUID().toString())
                    .paths(Paths.builder()
                        .quantity(paths.size())
                        .items(paths)  // e.g., ["/images/logo.png", "/css/*"]
                        .build())
                    .build())
                .build());
        
        String invalidationId = response.invalidation().id();
        log.info("Invalidation created: id={}, paths={}", invalidationId, paths);
        return invalidationId;
    }
    
    // ====== Invalidate everything (use sparingly — expensive) ======
    public String invalidateAll() {
        return invalidatePaths(List.of("/*"));
    }
    
    // ====== Smart invalidation: only changed files ======
    public void invalidateChangedFiles(List<String> changedFiles) {
        if (changedFiles.isEmpty()) return;
        
        // CloudFront limit: 3000 paths per invalidation, 15 wildcard per invalidation
        // First 1000 paths/month are free, then $0.005 per path
        
        if (changedFiles.size() > 15) {
            // Too many files — use wildcard patterns instead
            Set<String> directories = changedFiles.stream()
                .map(f -> f.substring(0, f.lastIndexOf('/') + 1) + "*")
                .collect(Collectors.toSet());
            invalidatePaths(new ArrayList<>(directories));
        } else {
            invalidatePaths(changedFiles);
        }
    }
    
    // ====== Check invalidation status ======
    public String getInvalidationStatus(String invalidationId) {
        GetInvalidationResponse response = cf.getInvalidation(
            GetInvalidationRequest.builder()
                .distributionId(distributionId)
                .id(invalidationId)
                .build());
        
        return response.invalidation().status();  // "InProgress" or "Completed"
    }
    
    // ====== Event-driven invalidation (on S3 upload) ======
    @EventListener
    public void onContentUpdated(ContentUpdatedEvent event) {
        List<String> paths = event.getUpdatedPaths().stream()
            .map(p -> "/" + p)  // CloudFront paths start with /
            .collect(Collectors.toList());
        
        invalidatePaths(paths);
        log.info("Auto-invalidated {} paths after content update", paths.size());
    }
}
```

---

## 6. Custom Origin (ALB/API Gateway)

```java
@Service
public class CustomOriginDistribution {
    
    // ====== CloudFront in front of Application Load Balancer ======
    public String createAlbDistribution(String albDomain) {
        String originId = "ALB-backend";
        
        CreateDistributionResponse response = cf.createDistribution(
            CreateDistributionRequest.builder()
                .distributionConfig(DistributionConfig.builder()
                    .callerReference(UUID.randomUUID().toString())
                    .enabled(true)
                    .origins(Origins.builder()
                        .quantity(1)
                        .items(Origin.builder()
                            .id(originId)
                            .domainName(albDomain)  // e.g., "my-alb-123.us-east-1.elb.amazonaws.com"
                            .customOriginConfig(CustomOriginConfig.builder()
                                .httpPort(80)
                                .httpsPort(443)
                                .originProtocolPolicy(OriginProtocolPolicy.HTTPS_ONLY)
                                .originSslProtocols(OriginSslProtocols.builder()
                                    .quantity(1).items(SslProtocol.TL_SV1_2).build())
                                .originReadTimeout(30)    // 30s timeout
                                .originKeepaliveTimeout(5) // 5s keep-alive
                                .build())
                            .customHeaders(CustomHeaders.builder()
                                .quantity(1)
                                .items(OriginCustomHeader.builder()
                                    .headerName("X-CloudFront-Secret")
                                    .headerValue("my-secret-token")  // Verify origin requests
                                    .build())
                                .build())
                            .build())
                        .build())
                    .defaultCacheBehavior(DefaultCacheBehavior.builder()
                        .targetOriginId(originId)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_HTTPS)
                        .allowedMethods(AllowedMethods.builder()
                            .quantity(7)
                            .items(Method.GET, Method.HEAD, Method.OPTIONS, 
                                   Method.PUT, Method.POST, Method.PATCH, Method.DELETE)
                            .build())
                        .cachePolicyId("4135ea2d-6df8-44a3-9df3-4b5a84be39ad")  // CachingDisabled
                        .originRequestPolicyId("216adef6-5c7f-47e4-b989-5492eafa07d3") // AllViewer
                        .compress(true)
                        .build())
                    .build())
                .build());
        
        return response.distribution().id();
    }
}
```

---

## 7. Multiple Origins with Behaviors

```java
// Route different URL paths to different origins
public DistributionConfig multiOriginConfig() {
    return DistributionConfig.builder()
        .origins(Origins.builder()
            .quantity(3)
            .items(
                // Origin 1: S3 for static assets
                Origin.builder()
                    .id("S3-static")
                    .domainName("static-assets.s3.amazonaws.com")
                    .build(),
                // Origin 2: ALB for API
                Origin.builder()
                    .id("ALB-api")
                    .domainName("api-alb.us-east-1.elb.amazonaws.com")
                    .customOriginConfig(CustomOriginConfig.builder()
                        .originProtocolPolicy(OriginProtocolPolicy.HTTPS_ONLY)
                        .httpPort(80).httpsPort(443).build())
                    .build(),
                // Origin 3: S3 for media
                Origin.builder()
                    .id("S3-media")
                    .domainName("media-bucket.s3.amazonaws.com")
                    .build()
            )
            .build())
        
        // Default: static assets
        .defaultCacheBehavior(DefaultCacheBehavior.builder()
            .targetOriginId("S3-static")
            .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_HTTPS)
            .compress(true)
            .cachePolicyId(CACHING_OPTIMIZED)
            .build())
        
        // Path-specific behaviors
        .cacheBehaviors(CacheBehaviors.builder()
            .quantity(2)
            .items(
                // /api/* → ALB (no caching)
                CacheBehavior.builder()
                    .pathPattern("/api/*")
                    .targetOriginId("ALB-api")
                    .viewerProtocolPolicy(ViewerProtocolPolicy.HTTPS_ONLY)
                    .allowedMethods(AllowedMethods.builder()
                        .quantity(7).items(Method.values()).build())
                    .cachePolicyId(CACHING_DISABLED)
                    .originRequestPolicyId(ALL_VIEWER)
                    .build(),
                // /media/* → S3 media (long cache)
                CacheBehavior.builder()
                    .pathPattern("/media/*")
                    .targetOriginId("S3-media")
                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_HTTPS)
                    .compress(true)
                    .cachePolicyId(CACHING_OPTIMIZED)
                    .build()
            )
            .build())
        .build();
}
```

---

## 8. Origin Access Control (OAC)

```java
// ====== Create OAC (replaces Origin Access Identity — newer, more secure) ======
public String createOriginAccessControl(String bucketName) {
    CreateOriginAccessControlResponse response = cf.createOriginAccessControl(
        CreateOriginAccessControlRequest.builder()
            .originAccessControlConfig(OriginAccessControlConfig.builder()
                .name("OAC-" + bucketName)
                .description("OAC for " + bucketName)
                .signingProtocol(OriginAccessControlSigningProtocols.SIGV4)
                .signingBehavior(OriginAccessControlSigningBehaviors.ALWAYS)
                .originAccessControlOriginType(OriginAccessControlOriginTypes.S3)
                .build())
            .build());
    
    return response.originAccessControl().id();
}

// S3 bucket policy to allow CloudFront OAC:
// {
//   "Statement": [{
//     "Effect": "Allow",
//     "Principal": {"Service": "cloudfront.amazonaws.com"},
//     "Action": "s3:GetObject",
//     "Resource": "arn:aws:s3:::my-bucket/*",
//     "Condition": {
//       "StringEquals": {
//         "AWS:SourceArn": "arn:aws:cloudfront::123456:distribution/EDFDVBD6EXAMPLE"
//       }
//     }
//   }]
// }
```

---

## 9. Lambda@Edge (Request/Response Manipulation)

```java
// Lambda@Edge runs at CloudFront edge locations
// Use cases: Auth, A/B testing, URL rewrites, dynamic content

// ====== Viewer Request: Authentication check at edge ======
public class EdgeAuthHandler implements RequestHandler<CloudFrontEvent, CloudFrontEvent> {
    
    @Override
    public CloudFrontEvent handleRequest(CloudFrontEvent event, Context context) {
        CloudFrontEvent.Record record = event.getRecords().get(0);
        CloudFrontEvent.CF cf = record.getCf();
        Map<String, List<Map<String, String>>> headers = cf.getRequest().getHeaders();
        
        // Check for auth token
        List<Map<String, String>> authHeaders = headers.get("authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            // Return 401 directly from edge (no origin hit)
            CloudFrontEvent.Response response = new CloudFrontEvent.Response();
            response.setStatus("401");
            response.setStatusDescription("Unauthorized");
            response.setBody("Missing authorization header");
            return createResponse(response);
        }
        
        String token = authHeaders.get(0).get("value").replace("Bearer ", "");
        if (!validateJwt(token)) {
            CloudFrontEvent.Response response = new CloudFrontEvent.Response();
            response.setStatus("403");
            response.setStatusDescription("Forbidden");
            response.setBody("Invalid token");
            return createResponse(response);
        }
        
        // Token valid — forward to origin
        return event;
    }
}

// ====== Origin Request: A/B testing (route to different origins) ======
public class ABTestingEdgeHandler implements RequestHandler<CloudFrontEvent, CloudFrontEvent> {
    
    @Override
    public CloudFrontEvent handleRequest(CloudFrontEvent event, Context context) {
        CloudFrontEvent.Record record = event.getRecords().get(0);
        CloudFrontEvent.Request request = record.getCf().getRequest();
        
        // Check cookie for test group
        String testGroup = getCookieValue(request.getHeaders(), "ab-test-group");
        
        if (testGroup == null) {
            // Assign randomly
            testGroup = Math.random() < 0.5 ? "A" : "B";
        }
        
        if ("B".equals(testGroup)) {
            // Route to variant B origin
            request.setOrigin(Map.of(
                "custom", Map.of(
                    "domainName", "variant-b.example.com",
                    "port", 443,
                    "protocol", "https"
                )
            ));
            request.getHeaders().put("host", List.of(Map.of("value", "variant-b.example.com")));
        }
        
        return event;
    }
}

// ====== Viewer Response: Add security headers ======
public class SecurityHeadersHandler implements RequestHandler<CloudFrontEvent, CloudFrontEvent> {
    
    @Override
    public CloudFrontEvent handleRequest(CloudFrontEvent event, Context context) {
        CloudFrontEvent.Response response = event.getRecords().get(0).getCf().getResponse();
        Map<String, List<Map<String, String>>> headers = response.getHeaders();
        
        headers.put("strict-transport-security", 
            List.of(Map.of("value", "max-age=31536000; includeSubdomains; preload")));
        headers.put("x-content-type-options", 
            List.of(Map.of("value", "nosniff")));
        headers.put("x-frame-options", 
            List.of(Map.of("value", "DENY")));
        headers.put("content-security-policy", 
            List.of(Map.of("value", "default-src 'self'")));
        headers.put("x-xss-protection", 
            List.of(Map.of("value", "1; mode=block")));
        
        return event;
    }
}
```

---

## 10. CloudFront Functions (Lightweight Edge Logic)

```java
// CloudFront Functions are lighter than Lambda@Edge (1ms max execution)
// Written in JavaScript but managed via Java SDK

@Service
public class CloudFrontFunctionService {
    
    // ====== Create function (URL rewrite example) ======
    public String createUrlRewriteFunction() {
        String functionCode = """
            function handler(event) {
                var request = event.request;
                var uri = request.uri;
                
                // Add index.html for directory paths (SPA routing)
                if (uri.endsWith('/')) {
                    request.uri += 'index.html';
                } else if (!uri.includes('.')) {
                    // No file extension — SPA route, serve index.html
                    request.uri = '/index.html';
                }
                
                return request;
            }
            """;
        
        CreateFunctionResponse response = cf.createFunction(CreateFunctionRequest.builder()
            .name("url-rewrite-spa")
            .functionConfig(FunctionConfig.builder()
                .comment("SPA URL rewrite for React/Angular apps")
                .runtime(FunctionRuntime.CLOUDFRONT_JS_1_0)
                .build())
            .functionCode(SdkBytes.fromUtf8String(functionCode))
            .build());
        
        // Publish the function
        cf.publishFunction(PublishFunctionRequest.builder()
            .name("url-rewrite-spa")
            .ifMatch(response.eTag())
            .build());
        
        return response.functionSummary().functionMetadata().functionARN();
    }
}
```

---

## 11. Geo-Restriction

```java
// ====== Restrict content to specific countries ======
public void updateGeoRestriction(String distributionId, List<String> allowedCountries) {
    GetDistributionConfigResponse current = cf.getDistributionConfig(
        GetDistributionConfigRequest.builder().id(distributionId).build());
    
    DistributionConfig config = current.distributionConfig().toBuilder()
        .restrictions(Restrictions.builder()
            .geoRestriction(GeoRestriction.builder()
                .restrictionType(GeoRestrictionType.WHITELIST)
                .quantity(allowedCountries.size())
                .items(allowedCountries)  // e.g., ["US", "CA", "GB", "DE"]
                .build())
            .build())
        .build();
    
    cf.updateDistribution(UpdateDistributionRequest.builder()
        .id(distributionId)
        .distributionConfig(config)
        .ifMatch(current.eTag())
        .build());
}
```

---

## 12-20: Additional Patterns (Key Code)

### 13. Real-Time Logging
```java
// Enable real-time logs to Kinesis Data Streams
public void enableRealtimeLogging(String distributionId, String kinesisStreamArn) {
    cf.createRealtimeLogConfig(CreateRealtimeLogConfigRequest.builder()
        .name("cdn-realtime-logs")
        .samplingRate(100L)  // 100% sampling (adjust for cost)
        .endPoints(EndPoint.builder()
            .streamType("Kinesis")
            .kinesisStreamConfig(KinesisStreamConfig.builder()
                .roleArn("arn:aws:iam::123:role/cloudfront-kinesis-role")
                .streamArn(kinesisStreamArn)
                .build())
            .build())
        .fields("timestamp", "c-ip", "cs-uri-stem", "sc-status", 
                "time-taken", "cs-bytes", "sc-bytes", "x-edge-result-type")
        .build());
}
```

### 14. Cache Policy
```java
// Custom cache policy for API responses
public String createApiCachePolicy() {
    CreateCachePolicyResponse response = cf.createCachePolicy(
        CreateCachePolicyRequest.builder()
            .cachePolicyConfig(CachePolicyConfig.builder()
                .name("API-Cache-30s")
                .comment("Cache API responses for 30 seconds")
                .defaultTTL(30L)
                .maxTTL(60L)
                .minTTL(0L)
                .parametersInCacheKeyAndForwardedToOrigin(
                    ParametersInCacheKeyAndForwardedToOrigin.builder()
                        .headersConfig(CachePolicyHeadersConfig.builder()
                            .headerBehavior(CachePolicyHeaderBehavior.WHITELIST)
                            .headers(Headers.builder()
                                .items("Authorization", "Accept-Language")
                                .quantity(2).build())
                            .build())
                        .queryStringsConfig(CachePolicyQueryStringsConfig.builder()
                            .queryStringBehavior(CachePolicyQueryStringBehavior.ALL)
                            .build())
                        .cookiesConfig(CachePolicyCookiesConfig.builder()
                            .cookieBehavior(CachePolicyCookieBehavior.NONE)
                            .build())
                        .enableAcceptEncodingGzip(true)
                        .enableAcceptEncodingBrotli(true)
                        .build())
                .build())
            .build());
    
    return response.cachePolicy().id();
}
```

### 15. Origin Failover
```java
// Automatic failover: if primary origin returns 5xx, try secondary
OriginGroup failoverGroup = OriginGroup.builder()
    .id("origin-group-failover")
    .failoverCriteria(OriginGroupFailoverCriteria.builder()
        .statusCodes(StatusCodes.builder()
            .quantity(2)
            .items(500, 502)  // Failover on 500 or 502
            .build())
        .build())
    .members(OriginGroupMembers.builder()
        .quantity(2)
        .items(
            OriginGroupMember.builder().originId("primary-ALB").build(),
            OriginGroupMember.builder().originId("secondary-S3-failover").build()
        )
        .build())
    .build();
```

### 18. Pre-Signed URL Service (Video Streaming)
```java
@Service
public class VideoStreamingService {
    
    public VideoStreamUrls getStreamUrls(String userId, String videoId) {
        // Verify access
        if (!hasAccess(userId, videoId)) throw new AccessDeniedException();
        
        // HLS manifest signed URL
        String manifest = signUrl("videos/" + videoId + "/master.m3u8", Duration.ofHours(4));
        
        // Signed cookie for all segments (wildcard path)
        Map<String, String> cookies = generateSignedCookies(
            "videos/" + videoId + "/*", Duration.ofHours(4));
        
        return new VideoStreamUrls(manifest, cookies);
    }
}
```

---

## 🏆 Configuration Cheat Sheet

| Scenario | Configuration | Why |
|---|---|---|
| Static site (React/Angular) | S3 origin + OAC + cache forever | Immutable assets with hash filenames |
| API behind CDN | Custom origin + no cache | Edge SSL termination, DDoS protection |
| Private content | Signed URLs/cookies | Time-limited access with crypto verification |
| Video streaming | Signed cookies + HLS | One cookie covers all segments |
| Multi-region | Origin failover group | Auto-switch on 5xx errors |
| SPA routing | CloudFront Function rewrite | Serve index.html for all routes |
| A/B testing | Lambda@Edge viewer request | Route users to different origins |
| Security headers | Lambda@Edge viewer response | Add HSTS, CSP at edge |
| Real-time monitoring | Kinesis real-time logs | Sub-second log delivery |
| Cost optimization | Price Class 100 | Only US/EU/Asia (not all 450+ POPs) |

| Key Limits | Value |
|---|---|
| Max file size | 30 GB (with range GETs) |
| Free invalidations | 1,000 paths/month |
| Cache behavior rules | 25 per distribution |
| Lambda@Edge timeout | 5s (viewer), 30s (origin) |
| CloudFront Function timeout | 1ms |
| Signed URL max expiry | No limit (custom policy) |
| Origins per distribution | 25 |

| Cache Strategy Decision |
|---|
| Static assets (js/css/img): Cache 1 year + fingerprinted filenames |
| HTML pages: Cache 5-60 min + invalidate on deploy |
| API responses: No cache OR 5-30s for read-heavy endpoints |
| User-specific: Vary by Authorization header (separate cache per user) |
| Dynamic: Pass through (CachingDisabled policy) |

---

*All examples use AWS SDK v2 for Java. CloudFront is a global service — always use Region.AWS_GLOBAL for the client.*
