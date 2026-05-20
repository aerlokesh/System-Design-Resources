# 🪣 Topic 77: AWS S3 Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Amazon S3 using **AWS SDK v2**, **Transfer Manager**, and **S3 Presigned URLs**. Every pattern includes production-ready code with multipart uploads, streaming, lifecycle policies, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration](#1-client-setup--configuration)
- [2. Upload Objects (PutObject)](#2-upload-objects-putobject)
- [3. Download Objects (GetObject)](#3-download-objects-getobject)
- [4. Multipart Upload (Large Files)](#4-multipart-upload-large-files)
- [5. Presigned URLs (Temporary Access)](#5-presigned-urls-temporary-access)
- [6. Streaming Upload/Download](#6-streaming-uploaddownload)
- [7. Copy & Move Objects](#7-copy--move-objects)
- [8. List Objects (Pagination)](#8-list-objects-pagination)
- [9. Delete Objects (Single & Batch)](#9-delete-objects-single--batch)
- [10. S3 Select (Query in Place)](#10-s3-select-query-in-place)
- [11. Metadata & Tagging](#11-metadata--tagging)
- [12. Server-Side Encryption](#12-server-side-encryption)
- [13. Versioning & Lifecycle](#13-versioning--lifecycle)
- [14. Transfer Manager (High-Level)](#14-transfer-manager-high-level)
- [15. Event Notifications (Lambda Trigger)](#15-event-notifications-lambda-trigger)
- [16. Cross-Region Replication Check](#16-cross-region-replication-check)
- [17. Bucket Policies & ACLs](#17-bucket-policies--acls)
- [18. Error Handling & Retry](#18-error-handling--retry)
- [19. Performance Optimization](#19-performance-optimization)
- [20. Spring Boot Integration](#20-spring-boot-integration)
- [🏆 Configuration Cheat Sheet](#-configuration-cheat-sheet)

---

## 1. Client Setup & Configuration

```java
@Configuration
public class S3Config {
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(3)
                    .retryCondition(RetryCondition.defaultRetryCondition())
                    .build())
                .apiCallTimeout(Duration.ofMinutes(5))
                .build())
            .httpClientBuilder(ApacheHttpClient.builder()
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofMinutes(2)))
            .build();
    }
    
    @Bean
    public S3AsyncClient s3AsyncClient() {
        return S3AsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .multipartEnabled(true)  // Auto multipart for large uploads
            .build();
    }
    
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

---

## 2. Upload Objects (PutObject)

```java
@Service
public class S3UploadService {
    private final S3Client s3;
    private final String bucket = "my-app-uploads";
    
    // ====== Upload from bytes ======
    public String uploadBytes(String key, byte[] data, String contentType) {
        PutObjectResponse response = s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(Map.of(
                    "uploaded-by", "order-service",
                    "upload-time", Instant.now().toString()
                ))
                .build(),
            RequestBody.fromBytes(data)
        );
        
        log.info("Uploaded: key={}, etag={}", key, response.eTag());
        return response.eTag();
    }
    
    // ====== Upload from file ======
    public String uploadFile(String key, Path filePath) {
        String contentType = Files.probeContentType(filePath);
        
        PutObjectResponse response = s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            filePath
        );
        
        return response.eTag();
    }
    
    // ====== Upload from InputStream ======
    public String uploadStream(String key, InputStream inputStream, long contentLength, String contentType) {
        PutObjectResponse response = s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build(),
            RequestBody.fromInputStream(inputStream, contentLength)
        );
        
        return response.eTag();
    }
    
    // ====== Generate unique key with prefix (organized storage) ======
    public String generateKey(String userId, String filename) {
        String date = LocalDate.now().toString();  // 2024-01-15
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("uploads/%s/%s/%s-%s", userId, date, uuid, filename);
        // Result: uploads/user123/2024-01-15/a1b2c3d4-photo.jpg
    }
}
```

---

## 3. Download Objects (GetObject)

```java
@Service
public class S3DownloadService {
    private final S3Client s3;
    
    // ====== Download to bytes ======
    public byte[] downloadBytes(String bucket, String key) {
        ResponseInputStream<GetObjectResponse> response = s3.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        
        return response.readAllBytes();
    }
    
    // ====== Download to file ======
    public Path downloadToFile(String bucket, String key, Path destination) {
        s3.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build(),
            destination
        );
        return destination;
    }
    
    // ====== Stream download (for large files — don't load in memory) ======
    public void streamToOutputStream(String bucket, String key, OutputStream outputStream) {
        ResponseInputStream<GetObjectResponse> response = s3.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build());
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = response.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }
    
    // ====== Download with range (partial content — resume support) ======
    public byte[] downloadRange(String bucket, String key, long start, long end) {
        ResponseInputStream<GetObjectResponse> response = s3.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=" + start + "-" + end)
                .build());
        
        return response.readAllBytes();
    }
    
    // ====== Check if object exists ======
    public boolean exists(String bucket, String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
    
    // ====== Get object metadata ======
    public Map<String, String> getMetadata(String bucket, String key) {
        HeadObjectResponse response = s3.headObject(
            HeadObjectRequest.builder().bucket(bucket).key(key).build());
        return response.metadata();
    }
}
```

---

## 4. Multipart Upload (Large Files)

```java
@Service
public class S3MultipartUploadService {
    private final S3Client s3;
    private static final long PART_SIZE = 100 * 1024 * 1024;  // 100MB parts
    
    // ====== Multipart upload for files > 100MB ======
    public String multipartUpload(String bucket, String key, Path filePath) {
        long fileSize = Files.size(filePath);
        
        // Step 1: Initiate multipart upload
        CreateMultipartUploadResponse initResponse = s3.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(Files.probeContentType(filePath))
                .build());
        
        String uploadId = initResponse.uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();
        
        try {
            // Step 2: Upload parts
            int partNumber = 1;
            long position = 0;
            
            while (position < fileSize) {
                long partSize = Math.min(PART_SIZE, fileSize - position);
                
                UploadPartResponse partResponse = s3.uploadPart(
                    UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength(partSize)
                        .build(),
                    RequestBody.fromFile(filePath)  // SDK handles offset
                );
                
                completedParts.add(CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(partResponse.eTag())
                    .build());
                
                log.info("Uploaded part {}: {}MB", partNumber, partSize / (1024*1024));
                partNumber++;
                position += partSize;
            }
            
            // Step 3: Complete multipart upload
            CompleteMultipartUploadResponse completeResponse = s3.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                    .build());
            
            log.info("Multipart upload complete: {}", completeResponse.eTag());
            return completeResponse.eTag();
            
        } catch (Exception e) {
            // Abort on failure (clean up incomplete parts)
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId).build());
            throw new S3UploadException("Multipart upload failed", e);
        }
    }
}
```

---

## 5. Presigned URLs (Temporary Access)

```java
@Service
public class S3PresignedUrlService {
    private final S3Presigner presigner;
    
    // ====== Generate upload URL (client uploads directly to S3) ======
    public PresignedUrlResult generateUploadUrl(String key, String contentType, Duration expiry) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(PutObjectRequest.builder()
                    .bucket("user-uploads")
                    .key(key)
                    .contentType(contentType)
                    .build())
                .build());
        
        return new PresignedUrlResult(presigned.url().toString(), presigned.expiration());
    }
    
    // ====== Generate download URL (temporary read access) ======
    public String generateDownloadUrl(String bucket, String key, Duration expiry) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build())
                .build());
        
        return presigned.url().toString();
    }
    
    // ====== Use case: Direct browser upload ======
    public UploadCredentials getDirectUploadCredentials(String userId, String filename) {
        String key = generateKey(userId, filename);
        PresignedUrlResult upload = generateUploadUrl(key, "application/octet-stream", Duration.ofMinutes(15));
        
        return new UploadCredentials(
            upload.getUrl(),
            key,
            upload.getExpiration(),
            Map.of("Content-Type", "application/octet-stream")
        );
        // Frontend: PUT to this URL with file content — no server proxy needed!
    }
}
```

---

## 6. Streaming Upload/Download

```java
@Service
public class S3StreamingService {
    
    // ====== Stream processing: read from S3, process line by line (no full load) ======
    public void processLargeFile(String bucket, String key, Consumer<String> lineProcessor) {
        ResponseInputStream<GetObjectResponse> response = s3.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build());
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {
            String line;
            long lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineProcessor.accept(line);
                lineCount++;
                if (lineCount % 100_000 == 0) {
                    log.info("Processed {} lines from {}", lineCount, key);
                }
            }
        }
    }
    
    // ====== Pipe: S3 → transform → S3 (without loading full file in memory) ======
    public void transformAndUpload(String sourceBucket, String sourceKey,
                                    String destBucket, String destKey,
                                    Function<String, String> transformer) {
        ResponseInputStream<GetObjectResponse> input = s3.getObject(
            GetObjectRequest.builder().bucket(sourceBucket).key(sourceKey).build());
        
        // Write transformed content to temp file, then upload
        Path tempFile = Files.createTempFile("s3-transform-", ".tmp");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input));
             BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(transformer.apply(line));
                writer.newLine();
            }
        }
        
        s3.putObject(PutObjectRequest.builder().bucket(destBucket).key(destKey).build(), tempFile);
        Files.delete(tempFile);
    }
}
```

---

## 7. Copy & Move Objects

```java
@Service
public class S3CopyService {
    
    // ====== Copy within same bucket (rename/move) ======
    public void moveObject(String bucket, String sourceKey, String destKey) {
        // Copy to new location
        s3.copyObject(CopyObjectRequest.builder()
            .sourceBucket(bucket)
            .sourceKey(sourceKey)
            .destinationBucket(bucket)
            .destinationKey(destKey)
            .build());
        
        // Delete original
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(sourceKey).build());
    }
    
    // ====== Copy cross-region ======
    public void copyToRegion(String sourceBucket, String key, String destBucket) {
        s3.copyObject(CopyObjectRequest.builder()
            .sourceBucket(sourceBucket)
            .sourceKey(key)
            .destinationBucket(destBucket)
            .destinationKey(key)
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build());
    }
}
```

---

## 8. List Objects (Pagination)

```java
@Service
public class S3ListService {
    
    // ====== List with prefix (folder-like browsing) ======
    public List<S3ObjectSummary> listObjects(String bucket, String prefix, int maxKeys) {
        ListObjectsV2Response response = s3.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .maxKeys(maxKeys)
            .build());
        
        return response.contents().stream()
            .map(obj -> new S3ObjectSummary(obj.key(), obj.size(), obj.lastModified()))
            .collect(Collectors.toList());
    }
    
    // ====== Paginated listing (for large buckets) ======
    public void listAllObjects(String bucket, String prefix, Consumer<List<S3Object>> batchProcessor) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(bucket).prefix(prefix).maxKeys(1000).build();
        
        ListObjectsV2Iterable paginator = s3.listObjectsV2Paginator(request);
        
        for (ListObjectsV2Response page : paginator) {
            batchProcessor.accept(page.contents());
        }
    }
    
    // ====== List "directories" (common prefixes) ======
    public List<String> listDirectories(String bucket, String prefix) {
        ListObjectsV2Response response = s3.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .delimiter("/")  // Treat '/' as directory separator
            .build());
        
        return response.commonPrefixes().stream()
            .map(CommonPrefix::prefix)
            .collect(Collectors.toList());
    }
}
```

---

## 9. Delete Objects (Single & Batch)

```java
@Service
public class S3DeleteService {
    
    // ====== Single delete ======
    public void deleteObject(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
    
    // ====== Batch delete (up to 1000 per call) ======
    public void batchDelete(String bucket, List<String> keys) {
        List<List<String>> chunks = Lists.partition(keys, 1000);
        
        for (List<String> chunk : chunks) {
            List<ObjectIdentifier> objects = chunk.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .collect(Collectors.toList());
            
            DeleteObjectsResponse response = s3.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).build())
                .build());
            
            if (!response.errors().isEmpty()) {
                response.errors().forEach(err -> 
                    log.error("Delete failed: key={}, code={}", err.key(), err.code()));
            }
        }
    }
    
    // ====== Delete all objects with prefix (empty a "folder") ======
    public int deleteByPrefix(String bucket, String prefix) {
        int deleted = 0;
        ListObjectsV2Iterable paginator = s3.listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build());
        
        for (ListObjectsV2Response page : paginator) {
            List<String> keys = page.contents().stream()
                .map(S3Object::key).collect(Collectors.toList());
            if (!keys.isEmpty()) {
                batchDelete(bucket, keys);
                deleted += keys.size();
            }
        }
        
        log.info("Deleted {} objects with prefix: {}", deleted, prefix);
        return deleted;
    }
}
```

---

## 10-20: Additional Patterns (Key Code)

### 10. S3 Select (Query CSV/JSON in Place)
```java
public List<String> queryS3Select(String bucket, String key, String sql) {
    SelectObjectContentResponse response = s3.selectObjectContent(
        SelectObjectContentRequest.builder()
            .bucket(bucket).key(key)
            .expression(sql)  // e.g., "SELECT s.name, s.age FROM S3Object s WHERE s.age > 30"
            .expressionType(ExpressionType.SQL)
            .inputSerialization(InputSerialization.builder()
                .csv(CSVInput.builder().fileHeaderInfo(FileHeaderInfo.USE).build()).build())
            .outputSerialization(OutputSerialization.builder()
                .json(JSONOutput.builder().build()).build())
            .build());
    // Process results stream...
}
```

### 12. Server-Side Encryption
```java
// SSE-S3 (Amazon managed keys)
PutObjectRequest.builder().serverSideEncryption(ServerSideEncryption.AES256).build();

// SSE-KMS (Customer managed KMS key)
PutObjectRequest.builder()
    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
    .ssekmsKeyId("arn:aws:kms:us-east-1:123456:key/abc-def-ghi")
    .build();
```

### 14. Transfer Manager (High-Level)
```java
S3TransferManager tm = S3TransferManager.builder().s3Client(s3AsyncClient).build();

// Upload directory
DirectoryUpload upload = tm.uploadDirectory(UploadDirectoryRequest.builder()
    .source(Paths.get("/local/data"))
    .bucket("my-bucket")
    .s3Prefix("data/")
    .build());
upload.completionFuture().join();

// Download file with progress
FileDownload download = tm.downloadFile(DownloadFileRequest.builder()
    .getObjectRequest(GetObjectRequest.builder().bucket("b").key("k").build())
    .destination(Paths.get("/tmp/file"))
    .addTransferListener(LoggingTransferListener.create())
    .build());
download.completionFuture().join();
```

### 19. Performance Optimization
```java
// Prefix randomization for high-throughput (>3,500 PUT/sec per prefix)
// Instead of: logs/2024-01-15/event1.json (all same prefix → hot partition)
// Use:        a1b2/logs/2024-01-15/event1.json (random prefix → distributed)
public String randomizedKey(String logicalPath) {
    String hash = Integer.toHexString(logicalPath.hashCode()).substring(0, 4);
    return hash + "/" + logicalPath;
}
```

---

## 🏆 Configuration Cheat Sheet

| Scenario | Pattern | Why |
|---|---|---|
| Files > 100MB | Multipart upload | Parallel parts, resume on failure |
| Direct browser upload | Presigned PUT URL | No server proxy needed, reduces backend load |
| Temporary download link | Presigned GET URL | Time-limited access without credentials |
| Large file processing | Streaming (BufferedReader) | Don't load entire file in memory |
| High throughput (>3.5K/sec) | Prefix randomization | Avoids S3 partition hotspot |
| Secure storage | SSE-KMS | Customer-managed encryption keys |
| Cost optimization | Lifecycle rules → Glacier | Move old data to cheaper storage |
| Cross-region DR | Cross-region replication | Automatic async replication |
| Query without download | S3 Select | Filter CSV/JSON server-side |
| Batch operations | Transfer Manager | Parallel uploads/downloads |

| Limits | Value |
|---|---|
| Max object size | 5 TB |
| Max PUT size (single) | 5 GB |
| Multipart min part | 5 MB |
| Multipart max parts | 10,000 |
| Max prefix throughput | 3,500 PUT + 5,500 GET/sec |
| Presigned URL max expiry | 7 days |

---

*All examples use AWS SDK v2 for Java. Transfer Manager uses the async client for parallel operations.*
