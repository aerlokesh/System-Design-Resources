# 🔍 Topic 74: Elasticsearch Java Implementation Examples — Production Patterns

> Complete Java implementation guide for Elasticsearch using the **official Java client (8.x)**, **Spring Data Elasticsearch**, and **RestHighLevelClient**. Every pattern includes production-ready code with index design, search queries, aggregations, and the **WHY** behind each design decision.

---

## 📋 Table of Contents

- [1. Client Setup & Configuration](#1-client-setup--configuration)
- [2. Index Management & Mappings](#2-index-management--mappings)
- [3. Document CRUD Operations](#3-document-crud-operations)
- [4. Full-Text Search](#4-full-text-search)
- [5. Multi-Field Search (BoolQuery)](#5-multi-field-search-boolquery)
- [6. Autocomplete / Typeahead](#6-autocomplete--typeahead)
- [7. Fuzzy Search & Typo Tolerance](#7-fuzzy-search--typo-tolerance)
- [8. Aggregations (Analytics)](#8-aggregations-analytics)
- [9. Pagination (Deep & Search After)](#9-pagination-deep--search-after)
- [10. Bulk Indexing](#10-bulk-indexing)
- [11. Index Aliases & Zero-Downtime Reindex](#11-index-aliases--zero-downtime-reindex)
- [12. Nested & Parent-Child Queries](#12-nested--parent-child-queries)
- [13. Geo-Spatial Search](#13-geo-spatial-search)
- [14. Highlighting Search Results](#14-highlighting-search-results)
- [15. Suggesters (Did You Mean)](#15-suggesters-did-you-mean)
- [16. Spring Data Elasticsearch](#16-spring-data-elasticsearch)
- [17. Scroll API (Large Exports)](#17-scroll-api-large-exports)
- [18. Index Templates & ILM](#18-index-templates--ilm)
- [19. Monitoring & Performance](#19-monitoring--performance)
- [20. Error Handling & Retry](#20-error-handling--retry)
- [🏆 Query Selection Cheat Sheet](#-query-selection-cheat-sheet)

---

## 1. Client Setup & Configuration

```java
@Configuration
public class ElasticsearchConfig {
    
    // ====== Official Java Client (8.x — recommended) ======
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
            new HttpHost("es-node-1.prod", 9200, "https"),
            new HttpHost("es-node-2.prod", 9200, "https"),
            new HttpHost("es-node-3.prod", 9200, "https")
        )
        .setHttpClientConfigCallback(httpBuilder -> httpBuilder
            .setDefaultCredentialsProvider(credentialsProvider())
            .setMaxConnTotal(100)
            .setMaxConnPerRoute(50)
            .setKeepAliveStrategy((response, context) -> 60_000))  // 60s keep-alive
        .setRequestConfigCallback(requestBuilder -> requestBuilder
            .setConnectTimeout(5000)
            .setSocketTimeout(30000))
        .build();
        
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());
        
        return new ElasticsearchClient(transport);
    }
    
    // ====== Spring Data Elasticsearch Configuration ======
    @Bean
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
            .connectedTo("es-node-1.prod:9200", "es-node-2.prod:9200")
            .usingSsl()
            .withBasicAuth("elastic", "${ES_PASSWORD}")
            .withConnectTimeout(Duration.ofSeconds(5))
            .withSocketTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    @Bean
    public ElasticsearchOperations elasticsearchOperations(ElasticsearchClient client) {
        return new ElasticsearchTemplate(client);
    }
}
```

---

## 2. Index Management & Mappings

```java
@Service
public class IndexManagementService {
    private final ElasticsearchClient client;
    
    // ====== Create index with optimized mappings ======
    public void createProductIndex() {
        client.indices().create(c -> c
            .index("products")
            .settings(s -> s
                .numberOfShards("3")
                .numberOfReplicas("1")
                .analysis(a -> a
                    .analyzer("product_analyzer", an -> an
                        .custom(cu -> cu
                            .tokenizer("standard")
                            .filter("lowercase", "asciifolding", "edge_ngram_filter")))
                    .filter("edge_ngram_filter", f -> f
                        .definition(d -> d
                            .edgeNgram(ng -> ng.minGram(2).maxGram(20))))
                ))
            .mappings(m -> m
                .properties("name", p -> p.text(t -> t
                    .analyzer("product_analyzer")
                    .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
                    .fields("raw", f -> f.keyword(k -> k))))
                .properties("description", p -> p.text(t -> t.analyzer("standard")))
                .properties("price", p -> p.double_(d -> d))
                .properties("category", p -> p.keyword(k -> k))
                .properties("brand", p -> p.keyword(k -> k))
                .properties("tags", p -> p.keyword(k -> k))
                .properties("inStock", p -> p.boolean_(b -> b))
                .properties("rating", p -> p.float_(f -> f))
                .properties("createdAt", p -> p.date(d -> d))
                .properties("location", p -> p.geoPoint(g -> g))
                .properties("suggest", p -> p.completion(comp -> comp))
            )
        );
    }
    
    // ====== Check if index exists ======
    public boolean indexExists(String indexName) {
        return client.indices().exists(e -> e.index(indexName)).value();
    }
    
    // ====== Delete index ======
    public void deleteIndex(String indexName) {
        client.indices().delete(d -> d.index(indexName));
    }
}
```

---

## 3. Document CRUD Operations

```java
@Service
public class ProductDocumentService {
    private final ElasticsearchClient client;
    
    // ====== Index (create/update) document ======
    public void indexProduct(Product product) {
        client.index(i -> i
            .index("products")
            .id(product.getId())
            .document(product)
            .refresh(Refresh.WaitFor)  // Make immediately searchable (use sparingly!)
        );
    }
    
    // ====== Get document by ID ======
    public Optional<Product> getProduct(String productId) {
        GetResponse<Product> response = client.get(g -> g
            .index("products")
            .id(productId),
            Product.class
        );
        
        return response.found() ? Optional.of(response.source()) : Optional.empty();
    }
    
    // ====== Partial update (only specified fields) ======
    public void updateProductPrice(String productId, double newPrice) {
        client.update(u -> u
            .index("products")
            .id(productId)
            .doc(Map.of("price", newPrice, "updatedAt", Instant.now().toString()))
            .retryOnConflict(3),  // Retry on version conflict
            Product.class
        );
    }
    
    // ====== Delete document ======
    public void deleteProduct(String productId) {
        client.delete(d -> d
            .index("products")
            .id(productId)
        );
    }
    
    // ====== Upsert (update or insert) ======
    public void upsertProduct(Product product) {
        client.update(u -> u
            .index("products")
            .id(product.getId())
            .doc(product)
            .docAsUpsert(true),  // Create if not exists
            Product.class
        );
    }
}
```

---

## 4. Full-Text Search

```java
@Service
public class ProductSearchService {
    private final ElasticsearchClient client;
    
    // ====== Simple full-text search ======
    public SearchResult<Product> searchProducts(String query, int page, int size) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .multiMatch(mm -> mm
                    .query(query)
                    .fields("name^3", "description", "brand^2", "tags")  // Boost name 3x
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO")  // Typo tolerance
                ))
            .from(page * size)
            .size(size)
            .highlight(h -> h
                .fields("name", hf -> hf.preTags("<b>").postTags("</b>"))
                .fields("description", hf -> hf.preTags("<b>").postTags("</b>")))
            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc))),
            Product.class
        );
        
        List<SearchHit<Product>> hits = response.hits().hits().stream()
            .map(hit -> new SearchHit<>(hit.source(), hit.score(), hit.highlight()))
            .collect(Collectors.toList());
        
        return new SearchResult<>(hits, response.hits().total().value(), page, size);
    }
    
    // ====== Search with filters (e-commerce style) ======
    public SearchResult<Product> searchWithFilters(ProductSearchRequest request) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .bool(b -> {
                    // Must: text relevance
                    if (request.getQuery() != null) {
                        b.must(m -> m.multiMatch(mm -> mm
                            .query(request.getQuery())
                            .fields("name^3", "description", "brand^2")
                            .type(TextQueryType.BestFields)));
                    }
                    
                    // Filter: exact match (no scoring — faster)
                    if (request.getCategory() != null) {
                        b.filter(f -> f.term(t -> t
                            .field("category").value(request.getCategory())));
                    }
                    if (request.getBrand() != null) {
                        b.filter(f -> f.term(t -> t
                            .field("brand").value(request.getBrand())));
                    }
                    if (request.getMinPrice() != null || request.getMaxPrice() != null) {
                        b.filter(f -> f.range(r -> {
                            r.field("price");
                            if (request.getMinPrice() != null) r.gte(JsonData.of(request.getMinPrice()));
                            if (request.getMaxPrice() != null) r.lte(JsonData.of(request.getMaxPrice()));
                            return r;
                        }));
                    }
                    b.filter(f -> f.term(t -> t.field("inStock").value(true)));
                    
                    return b;
                }))
            .from(request.getPage() * request.getSize())
            .size(request.getSize())
            .sort(buildSort(request.getSortBy())),
            Product.class
        );
        
        return mapResponse(response, request.getPage(), request.getSize());
    }
}
```

---

## 5. Multi-Field Search (BoolQuery)

```java
@Service
public class AdvancedSearchService {
    
    // ====== Boolean query with must, should, must_not, filter ======
    public List<Product> advancedSearch(AdvancedSearchRequest req) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .bool(b -> b
                    // MUST: Required conditions
                    .must(m -> m.match(mt -> mt
                        .field("name").query(req.getQuery())))
                    
                    // SHOULD: Boost score if matched (not required)
                    .should(sh -> sh.term(t -> t
                        .field("isPrime").value(true).boost(2.0f)))
                    .should(sh -> sh.range(r -> r
                        .field("rating").gte(JsonData.of(4.0))))
                    
                    // MUST_NOT: Exclude results
                    .mustNot(mn -> mn.term(t -> t
                        .field("status").value("discontinued")))
                    
                    // FILTER: Exact match without scoring
                    .filter(f -> f.term(t -> t
                        .field("category").value(req.getCategory())))
                    .filter(f -> f.range(r -> r
                        .field("price")
                        .gte(JsonData.of(req.getMinPrice()))
                        .lte(JsonData.of(req.getMaxPrice()))))
                    
                    .minimumShouldMatch("1")  // At least 1 should clause must match
                )),
            Product.class
        );
        
        return response.hits().hits().stream()
            .map(Hit::source)
            .collect(Collectors.toList());
    }
}
```

---

## 6. Autocomplete / Typeahead

```java
@Service
public class AutocompleteService {
    
    // ====== Using completion suggester (fastest) ======
    public List<String> autocomplete(String prefix) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .suggest(su -> su
                .suggesters("product-suggest", sg -> sg
                    .prefix(prefix)
                    .completion(c -> c
                        .field("suggest")
                        .size(10)
                        .fuzzy(f -> f.fuzziness("1"))  // Allow 1 typo
                    ))),
            Product.class
        );
        
        return response.suggest().get("product-suggest").stream()
            .flatMap(s -> s.completion().options().stream())
            .map(o -> o.text())
            .collect(Collectors.toList());
    }
    
    // ====== Using edge_ngram (more flexible) ======
    public List<String> searchAsYouType(String prefix) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .match(m -> m
                    .field("name")  // Uses edge_ngram analyzer
                    .query(prefix)))
            .size(10)
            .source(src -> src.filter(f -> f.includes("name")))  // Only return name field
            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc))),
            Product.class
        );
        
        return response.hits().hits().stream()
            .map(h -> h.source().getName())
            .distinct()
            .collect(Collectors.toList());
    }
}
```

---

## 7. Fuzzy Search & Typo Tolerance

```java
@Service
public class FuzzySearchService {
    
    // ====== Fuzzy matching (handles typos) ======
    public List<Product> fuzzySearch(String query) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .multiMatch(mm -> mm
                    .query(query)
                    .fields("name^3", "description", "brand")
                    .fuzziness("AUTO")       // 0-2 chars: exact, 3-5: 1 edit, 6+: 2 edits
                    .prefixLength(1)          // First char must match (performance)
                    .maxExpansions(50)         // Max fuzzy term expansions
                    .operator(Operator.And)))  // All terms must match (more precise)
            .size(20),
            Product.class
        );
        
        return response.hits().hits().stream()
            .map(Hit::source)
            .collect(Collectors.toList());
    }
}
```

---

## 8. Aggregations (Analytics)

```java
@Service
public class AggregationService {
    
    // ====== Faceted search (category counts, price ranges, brands) ======
    public SearchWithFacets searchWithFacets(String query) {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q.match(m -> m.field("name").query(query)))
            .size(20)
            .aggregations("categories", a -> a
                .terms(t -> t.field("category").size(20)))
            .aggregations("brands", a -> a
                .terms(t -> t.field("brand").size(20)))
            .aggregations("price_ranges", a -> a
                .range(r -> r
                    .field("price")
                    .ranges(
                        rng -> rng.to("25"),
                        rng -> rng.from("25").to("50"),
                        rng -> rng.from("50").to("100"),
                        rng -> rng.from("100").to("500"),
                        rng -> rng.from("500")
                    )))
            .aggregations("avg_price", a -> a.avg(av -> av.field("price")))
            .aggregations("avg_rating", a -> a.avg(av -> av.field("rating"))),
            Product.class
        );
        
        // Extract facets
        Map<String, Long> categories = response.aggregations().get("categories")
            .sterms().buckets().array().stream()
            .collect(Collectors.toMap(b -> b.key().stringValue(), 
                                      MultiBucketBase::docCount));
        
        Map<String, Long> brands = response.aggregations().get("brands")
            .sterms().buckets().array().stream()
            .collect(Collectors.toMap(b -> b.key().stringValue(), 
                                      MultiBucketBase::docCount));
        
        double avgPrice = response.aggregations().get("avg_price").avg().value();
        
        return new SearchWithFacets(
            response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()),
            categories, brands, avgPrice
        );
    }
    
    // ====== Date histogram (time series analytics) ======
    public Map<String, Long> getOrdersPerDay(Instant from, Instant to) {
        SearchResponse<Void> response = client.search(s -> s
            .index("orders")
            .size(0)  // Only aggregations, no docs
            .query(q -> q.range(r -> r
                .field("createdAt")
                .gte(JsonData.of(from.toString()))
                .lte(JsonData.of(to.toString()))))
            .aggregations("orders_per_day", a -> a
                .dateHistogram(dh -> dh
                    .field("createdAt")
                    .calendarInterval(CalendarInterval.Day))),
            Void.class
        );
        
        return response.aggregations().get("orders_per_day")
            .dateHistogram().buckets().array().stream()
            .collect(Collectors.toMap(
                b -> b.keyAsString(),
                MultiBucketBase::docCount
            ));
    }
}
```

---

## 9. Pagination (Deep & Search After)

```java
@Service
public class PaginationService {
    
    // ====== Offset pagination (simple, limited to 10K results) ======
    public SearchResult<Product> offsetPagination(String query, int page, int size) {
        // ⚠️ from + size cannot exceed 10,000 (index.max_result_window)
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q.match(m -> m.field("name").query(query)))
            .from(page * size)
            .size(size),
            Product.class
        );
        return mapResponse(response);
    }
    
    // ====== Search After (efficient deep pagination — production preferred) ======
    public SearchAfterResult<Product> searchAfterPagination(String query, 
                                                             List<String> searchAfter, int size) {
        SearchResponse<Product> response = client.search(s -> {
            s.index("products")
                .query(q -> q.match(m -> m.field("name").query(query)))
                .size(size)
                .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                .sort(so -> so.field(f -> f.field("_id").order(SortOrder.Asc)));  // Tiebreaker
            
            if (searchAfter != null && !searchAfter.isEmpty()) {
                s.searchAfter(searchAfter.stream()
                    .map(FieldValue::of)
                    .collect(Collectors.toList()));
            }
            return s;
        }, Product.class);
        
        List<Product> products = response.hits().hits().stream()
            .map(Hit::source).collect(Collectors.toList());
        
        // Get sort values from last hit for next page
        List<String> nextSearchAfter = null;
        if (!response.hits().hits().isEmpty()) {
            Hit<Product> lastHit = response.hits().hits().get(response.hits().hits().size() - 1);
            nextSearchAfter = lastHit.sort().stream()
                .map(String::valueOf).collect(Collectors.toList());
        }
        
        return new SearchAfterResult<>(products, nextSearchAfter, 
            response.hits().total().value());
    }
}
```

---

## 10. Bulk Indexing

```java
@Service
public class BulkIndexingService {
    private final ElasticsearchClient client;
    
    // ====== Bulk index (production — with error handling) ======
    public BulkIndexResult bulkIndex(List<Product> products) {
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        
        for (Product product : products) {
            bulkBuilder.operations(op -> op
                .index(idx -> idx
                    .index("products")
                    .id(product.getId())
                    .document(product)));
        }
        
        BulkResponse response = client.bulk(bulkBuilder.build());
        
        // Handle errors
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    failed++;
                    errors.add(item.id() + ": " + item.error().reason());
                } else {
                    success++;
                }
            }
            log.warn("Bulk index partial failure: {} success, {} failed", success, failed);
        } else {
            success = products.size();
        }
        
        return new BulkIndexResult(success, failed, errors);
    }
    
    // ====== Chunked bulk index (for large datasets) ======
    public void bulkIndexLargeDataset(Stream<Product> products, int batchSize) {
        AtomicInteger count = new AtomicInteger(0);
        List<Product> batch = new ArrayList<>(batchSize);
        
        products.forEach(product -> {
            batch.add(product);
            if (batch.size() >= batchSize) {
                bulkIndex(new ArrayList<>(batch));
                count.addAndGet(batch.size());
                batch.clear();
                log.info("Indexed {} documents so far", count.get());
            }
        });
        
        // Final batch
        if (!batch.isEmpty()) {
            bulkIndex(batch);
            count.addAndGet(batch.size());
        }
        
        log.info("Bulk indexing complete: {} total documents", count.get());
    }
}
```

---

## 11. Index Aliases & Zero-Downtime Reindex

```java
@Service
public class ReindexService {
    
    // ====== Zero-downtime reindex strategy ======
    public void reindexWithZeroDowntime(String aliasName) {
        String oldIndex = aliasName + "_v1";
        String newIndex = aliasName + "_v2";
        
        // Step 1: Create new index with updated mappings
        createIndexWithNewMappings(newIndex);
        
        // Step 2: Reindex data from old to new
        client.reindex(r -> r
            .source(s -> s.index(oldIndex))
            .dest(d -> d.index(newIndex))
            .waitForCompletion(true)
        );
        
        // Step 3: Atomic alias swap (zero downtime!)
        client.indices().updateAliases(a -> a
            .actions(
                Action.of(ac -> ac.remove(rem -> rem.index(oldIndex).alias(aliasName))),
                Action.of(ac -> ac.add(add -> add.index(newIndex).alias(aliasName)))
            )
        );
        
        // Step 4: Delete old index (after verification)
        log.info("Reindex complete. Old index: {}, New index: {}", oldIndex, newIndex);
    }
}
```

---

## 12. Nested & Parent-Child Queries

```java
@Service
public class NestedQueryService {
    
    // ====== Nested query (e.g., search within order items) ======
    public List<Order> findOrdersWithExpensiveItems(double minItemPrice) {
        SearchResponse<Order> response = client.search(s -> s
            .index("orders")
            .query(q -> q
                .nested(n -> n
                    .path("items")
                    .query(nq -> nq
                        .range(r -> r
                            .field("items.price")
                            .gte(JsonData.of(minItemPrice)))))),
            Order.class
        );
        
        return response.hits().hits().stream()
            .map(Hit::source).collect(Collectors.toList());
    }
    
    // ====== Nested aggregation ======
    public Map<String, Double> avgPriceByCategory() {
        SearchResponse<Void> response = client.search(s -> s
            .index("orders")
            .size(0)
            .aggregations("items_nested", a -> a
                .nested(n -> n.path("items"))
                .aggregations("by_category", sub -> sub
                    .terms(t -> t.field("items.category"))
                    .aggregations("avg_price", avg -> avg
                        .avg(av -> av.field("items.price"))))),
            Void.class
        );
        
        return response.aggregations().get("items_nested")
            .nested().aggregations().get("by_category")
            .sterms().buckets().array().stream()
            .collect(Collectors.toMap(
                b -> b.key().stringValue(),
                b -> b.aggregations().get("avg_price").avg().value()
            ));
    }
}
```

---

## 13. Geo-Spatial Search

```java
@Service
public class GeoSearchService {
    
    // ====== Find stores within radius ======
    public List<Store> findNearbyStores(double lat, double lng, double radiusKm) {
        SearchResponse<Store> response = client.search(s -> s
            .index("stores")
            .query(q -> q
                .bool(b -> b
                    .filter(f -> f.geoDistance(g -> g
                        .field("location")
                        .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                        .distance(radiusKm + "km")))))
            .sort(so -> so.geoDistance(g -> g
                .field("location")
                .location(l -> l.latlon(ll -> ll.lat(lat).lon(lng)))
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Kilometers)))
            .size(20),
            Store.class
        );
        
        return response.hits().hits().stream()
            .map(hit -> {
                Store store = hit.source();
                store.setDistanceKm(hit.sort().get(0).doubleValue());
                return store;
            })
            .collect(Collectors.toList());
    }
    
    // ====== Geo bounding box (map viewport) ======
    public List<Store> findInBoundingBox(double topLat, double leftLng, 
                                          double bottomLat, double rightLng) {
        SearchResponse<Store> response = client.search(s -> s
            .index("stores")
            .query(q -> q
                .geoBoundingBox(g -> g
                    .field("location")
                    .boundingBox(bb -> bb
                        .tlbr(tlbr -> tlbr
                            .topLeft(tl -> tl.latlon(ll -> ll.lat(topLat).lon(leftLng)))
                            .bottomRight(br -> br.latlon(ll -> ll.lat(bottomLat).lon(rightLng)))))))
            .size(100),
            Store.class
        );
        
        return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
    }
}
```

---

## 14. Highlighting Search Results

```java
public SearchResult<Product> searchWithHighlights(String query) {
    SearchResponse<Product> response = client.search(s -> s
        .index("products")
        .query(q -> q.multiMatch(mm -> mm
            .query(query)
            .fields("name^3", "description")))
        .highlight(h -> h
            .fields("name", hf -> hf
                .preTags("<mark>").postTags("</mark>")
                .numberOfFragments(0))  // Return full field highlighted
            .fields("description", hf -> hf
                .preTags("<mark>").postTags("</mark>")
                .fragmentSize(150)
                .numberOfFragments(3)))
        .size(20),
        Product.class
    );
    
    return response.hits().hits().stream()
        .map(hit -> new HighlightedProduct(
            hit.source(),
            hit.highlight().getOrDefault("name", List.of()),
            hit.highlight().getOrDefault("description", List.of())
        ))
        .collect(Collectors.toList());
}
```

---

## 15. Suggesters (Did You Mean)

```java
public List<String> didYouMean(String misspelledQuery) {
    SearchResponse<Void> response = client.search(s -> s
        .index("products")
        .suggest(su -> su
            .text(misspelledQuery)
            .suggesters("spelling", sg -> sg
                .phrase(p -> p
                    .field("name")
                    .size(3)
                    .gramSize(2)
                    .confidence(1.0)
                    .directGenerator(dg -> dg
                        .field("name")
                        .suggestMode(SuggestMode.Always))))),
        Void.class
    );
    
    return response.suggest().get("spelling").stream()
        .flatMap(s -> s.phrase().options().stream())
        .map(PhraseSuggestOption::text)
        .collect(Collectors.toList());
}
```

---

## 16. Spring Data Elasticsearch

```java
@Document(indexName = "products")
@Setting(shards = 3, replicas = 1)
public class ProductDocument {
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;
    
    @Field(type = FieldType.Text)
    private String description;
    
    @Field(type = FieldType.Keyword)
    private String category;
    
    @Field(type = FieldType.Double)
    private double price;
    
    @Field(type = FieldType.Boolean)
    private boolean inStock;
    
    @GeoPointField
    private GeoPoint location;
    
    @CompletionField(maxInputLength = 100)
    private Completion suggest;
}

@Repository
public interface ProductElasticsearchRepository 
        extends ElasticsearchRepository<ProductDocument, String> {
    
    List<ProductDocument> findByNameContaining(String name);
    List<ProductDocument> findByCategoryAndPriceGreaterThan(String category, double price);
    Page<ProductDocument> findByInStockTrue(Pageable pageable);
    
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description\"]}}")
    Page<ProductDocument> searchByQuery(String query, Pageable pageable);
}
```

---

## 17. Scroll API (Large Exports)

```java
public void exportAllProducts(Consumer<List<Product>> batchProcessor) {
    // Open scroll
    SearchResponse<Product> response = client.search(s -> s
        .index("products")
        .scroll(Time.of(t -> t.time("5m")))  // Keep scroll context alive 5 min
        .size(1000)
        .query(q -> q.matchAll(m -> m)),
        Product.class
    );
    
    String scrollId = response.scrollId();
    List<Hit<Product>> hits = response.hits().hits();
    
    while (!hits.isEmpty()) {
        batchProcessor.accept(hits.stream().map(Hit::source).collect(Collectors.toList()));
        
        // Get next batch
        ScrollResponse<Product> scrollResponse = client.scroll(s -> s
            .scrollId(scrollId)
            .scroll(Time.of(t -> t.time("5m"))),
            Product.class
        );
        
        scrollId = scrollResponse.scrollId();
        hits = scrollResponse.hits().hits();
    }
    
    // Clean up scroll context
    client.clearScroll(c -> c.scrollId(scrollId));
}
```

---

## 18-20: Additional Patterns

### 18. Index Templates & ILM
```java
public void createIndexTemplate() {
    client.indices().putIndexTemplate(t -> t
        .name("logs-template")
        .indexPatterns("logs-*")
        .template(tmpl -> tmpl
            .settings(s -> s
                .numberOfShards("3")
                .numberOfReplicas("1"))
            .mappings(m -> m
                .properties("timestamp", p -> p.date(d -> d))
                .properties("level", p -> p.keyword(k -> k))
                .properties("message", p -> p.text(tx -> tx))))
    );
}
```

### 20. Error Handling & Retry
```java
public <T> T executeWithRetry(Supplier<T> operation, int maxRetries) {
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            return operation.get();
        } catch (ElasticsearchException e) {
            if (e.status() == 429 || e.status() == 503) {  // Too many requests / unavailable
                if (attempt == maxRetries) throw e;
                long backoff = (long) Math.pow(2, attempt) * 200;
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                Metrics.counter("es.retry", "status", String.valueOf(e.status())).increment();
            } else {
                throw e;  // Non-retryable error
            }
        }
    }
    throw new RuntimeException("Unreachable");
}
```

---

## 🏆 Query Selection Cheat Sheet

| Use Case | Query Type | Why |
|---|---|---|
| Full-text search | `multi_match` | Search across multiple fields with relevance |
| Exact filter (category, status) | `term` in `filter` | No scoring, cached, fastest |
| Range (price, date) | `range` in `filter` | Efficient numeric/date filtering |
| Autocomplete | `completion` suggester or `edge_ngram` | Sub-10ms prefix matching |
| Typo tolerance | `fuzziness: AUTO` | Edit distance based correction |
| Find nearby (geo) | `geo_distance` | Radius search with distance sort |
| Analytics/facets | `aggregations` | Category counts, price ranges, averages |
| Deep pagination | `search_after` | Consistent, efficient, no 10K limit |
| Large export | `scroll` API | Process all documents in batches |
| Structured + text | `bool` query | Combine must/should/filter/must_not |

| Performance Tips | Setting | Impact |
|---|---|---|
| Use `filter` context | No scoring | 2-5x faster for exact matches |
| Limit `_source` fields | `_source: ["name","price"]` | Reduce network payload |
| Use `search_after` | Not `from/size` for deep pages | Avoids O(n) skip |
| Bulk operations | Batch 1000-5000 docs | 10x faster than individual |
| Refresh interval | `30s` (not `1s` for write-heavy) | Reduces segment creation |

---

*All examples use the official Elasticsearch Java Client 8.x. For Spring Boot projects, Spring Data Elasticsearch provides repository abstractions on top.*
