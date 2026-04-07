# 🏢 Real-World Engineering Examples — Interview Cheatsheet

> **Purpose**: Quotable real-world examples from big tech companies that you can drop into any system design interview to demonstrate industry awareness. Each example cites the company, the decision, and why it matters.
>
> **How to use**: When discussing a design choice, say: *"This is similar to how [Company] does [X] — they published a blog post about [Y] and found that [Z]."*

---

## Table of Contents

- [🏢 Real-World Engineering Examples — Interview Cheatsheet](#-real-world-engineering-examples--interview-cheatsheet)
  - [Table of Contents](#table-of-contents)
  - [1. Database \& Storage Choices](#1-database--storage-choices)
  - [2. Caching \& Performance](#2-caching--performance)
  - [3. Message Queues \& Event Streaming](#3-message-queues--event-streaming)
  - [4. Search \& Indexing](#4-search--indexing)
  - [5. Scaling \& Infrastructure](#5-scaling--infrastructure)
  - [6. Real-Time Systems](#6-real-time-systems)
  - [7. Consistency \& Distributed Systems](#7-consistency--distributed-systems)
  - [8. Data Processing \& Analytics](#8-data-processing--analytics)
  - [9. ID Generation \& Unique Identifiers](#9-id-generation--unique-identifiers)
  - [10. Rate Limiting \& Abuse Prevention](#10-rate-limiting--abuse-prevention)
  - [11. Deployment \& Operations](#11-deployment--operations)
  - [12. Architecture Decisions](#12-architecture-decisions)
  - [13. Content Delivery \& Media](#13-content-delivery--media)
  - [14. Security \& Authentication](#14-security--authentication)
  - [15. Machine Learning in Production](#15-machine-learning-in-production)
  - [16. Lessons Learned / Post-Mortems](#16-lessons-learned--post-mortems)
  - [📚 How to Use These in an Interview](#-how-to-use-these-in-an-interview)
    - [The Pattern](#the-pattern)
    - [Examples](#examples)

---

## 1. Database & Storage Choices

**OpenAI — Single PostgreSQL for ChatGPT**: OpenAI reportedly ran ChatGPT's backend on a single PostgreSQL instance in the early days. Proves that you don't need a distributed database to handle massive traffic if your queries are simple and you cache aggressively. Start simple, scale when needed.
> *"Even OpenAI started ChatGPT on a single Postgres instance — you don't need Cassandra on day one."*

**Instagram — 3 Engineers, PostgreSQL, 30M Users**: When Instagram was acquired by Facebook for $1B, they had 30 million users served by just 3 backend engineers and a handful of PostgreSQL instances. They used Django + PostgreSQL + Redis + Memcached. No microservices.
> *"Instagram scaled to 30M users with 3 engineers and PostgreSQL. Start with a monolith, extract services when you hit real bottlenecks."*

**Uber — Migrated from PostgreSQL to MySQL (then to Schemaless on MySQL)**: Uber published a famous blog post about moving from Postgres to MySQL because of Postgres's write amplification with their update-heavy workload and replication issues during failover. They eventually built "Schemaless" — a key-value layer on top of MySQL.
> *"Uber moved from Postgres to MySQL because Postgres's MVCC write amplification was too costly for their update-heavy ride data. The database choice depends on the access pattern."*

**Facebook — TAO (Graph Store on MySQL)**: Facebook built TAO — a distributed graph data store backed by MySQL + Memcached. It serves billions of social graph queries per second. They didn't use a graph database — they built a caching layer over MySQL for their specific access pattern.
> *"Facebook serves their social graph from TAO — a custom caching layer over MySQL, not a graph database. Sometimes the best solution is a specialized cache over a boring database."*

**Discord — Moved from MongoDB to Cassandra to ScyllaDB**: Discord migrated their message storage from MongoDB (couldn't handle the load) to Cassandra (worked for years), then to ScyllaDB (Cassandra-compatible, but faster with lower tail latency). They store trillions of messages.
> *"Discord migrated messages from MongoDB → Cassandra → ScyllaDB, storing trillions of messages. Each migration solved a specific problem: MongoDB couldn't handle the write volume, Cassandra had tail latency issues under compaction."*

**Notion — Single PostgreSQL, Then Sharded**: Notion ran on a single large PostgreSQL instance for years. When it started hitting limits (~500K TPS), they sharded across 480 logical partitions using application-level routing.
> *"Notion ran on a single Postgres instance for years before sharding. They only sharded when they actually needed to — not prematurely."*

**Pinterest — HBase for Pin Data**: Pinterest uses HBase (wide-column store) for storing Pin data — billions of pins with denormalized metadata. They chose HBase over MySQL for its ability to handle massive write throughput and time-range scans.
> *"Pinterest uses HBase for Pin storage — billions of writes per day with denormalized data. Wide-column stores shine for write-heavy workloads with known access patterns."*

**Airbnb — Migrated from MySQL to PostgreSQL (or dual-writes)**: Airbnb used MySQL for years, then began migrating critical services to PostgreSQL for its advanced features (JSONB, better concurrency under load). They used a dual-write migration approach.
> *"Airbnb migrated from MySQL to Postgres using dual-writes and shadow reads — the same zero-downtime migration pattern we'd use."*

**Amazon DynamoDB — Built for Amazon.com Shopping Cart**: DynamoDB was originally created as Amazon's internal key-value store (called Dynamo) to power the shopping cart. The Dynamo paper (2007) introduced consistent hashing, vector clocks, and quorum reads/writes — foundational distributed systems concepts.
> *"Amazon built DynamoDB for their shopping cart — availability over consistency. The Dynamo paper is why we have consistent hashing and quorum-based replication today."*

**Slack — Vitess (Sharded MySQL)**: Slack uses Vitess to shard MySQL horizontally. Vitess provides connection pooling, query routing, and online schema migration for MySQL at scale.
> *"Slack uses Vitess to shard MySQL horizontally — it handles connection pooling and query routing, making MySQL act like a distributed database without rewriting queries."*

---

## 2. Caching & Performance

**Facebook — Memcached at Scale**: Facebook runs the world's largest Memcached deployment. They published papers on "scaling Memcache at Facebook" — handling billions of requests/sec across thousands of Memcached servers with a custom client that handles replication, failover, and thundering herd prevention.
> *"Facebook runs billions of cache requests per second across thousands of Memcached instances. They solved the thundering herd problem with lease tokens — only one request repopulates the cache."*

**Twitter — Manhattan (In-House KV Store)**: Twitter built Manhattan as an in-house key-value store (replacing early Cassandra usage) for serving timeline data. It uses a multi-tenancy model with per-application TTLs and is tuned for low-latency reads.
> *"Twitter built Manhattan — their own KV store — because off-the-shelf solutions couldn't meet their specific latency and multi-tenancy requirements for the timeline."*

**Netflix — EVCache (Ephemeral Volatile Cache)**: Netflix built EVCache on top of Memcached as their globally distributed caching layer. It replicates across AWS regions for availability and handles millions of requests/sec for personalization, homepages, and A/B test configurations.
> *"Netflix built EVCache on top of Memcached for cross-region caching. If one region goes down, the other region's cache is warm — no cold start problem."*

**Reddit — Entire Site Runs on Redis + PostgreSQL**: Reddit's architecture is surprisingly simple — PostgreSQL for persistent storage, Redis for everything ephemeral: sessions, rate limiting, queues, real-time vote counts, and trending calculations.
> *"Reddit runs primarily on PostgreSQL + Redis. Redis handles sessions, rate limits, queues, and real-time vote counts — proof that Redis is the Swiss Army knife of caching."*

**Shopify — Flash Sale Handling**: Shopify handles massive flash sales (like Kylie Jenner's product drops) by aggressively caching product pages at the CDN edge and using Redis for inventory counters. They pre-compute product pages and serve them entirely from cache during spikes.
> *"Shopify handles Kylie Jenner-level flash sales by pre-computing product pages to CDN cache and using Redis atomic decrements for inventory. The database is only touched for the actual purchase."*

---

## 3. Message Queues & Event Streaming

**LinkedIn — Kafka Was Born Here**: Apache Kafka was originally built at LinkedIn to handle their activity stream data (profile views, connection requests, messages). They needed a system that could handle 1 million events/sec with replay capability. Kafka is now the de facto standard for event streaming.
> *"Kafka was born at LinkedIn to handle their activity stream — 1M events/sec with replay capability. It's the standard now because it solved the 'multiple consumers reading the same event' problem."*

**Uber — Apache Kafka at Uber Scale**: Uber runs one of the largest Kafka deployments — trillions of messages per day across multiple data centers. They built on top of Kafka for ride matching events, pricing updates, and real-time analytics.
> *"Uber processes trillions of Kafka messages per day. Every ride event — request, match, pickup, dropoff, pricing — flows through Kafka before reaching downstream consumers."*

**Airbnb — From RabbitMQ to Kafka**: Airbnb migrated from RabbitMQ to Kafka because RabbitMQ couldn't handle their growing volume and they needed the replay capability that Kafka provides for rebuilding search indexes and analytics pipelines.
> *"Airbnb migrated from RabbitMQ to Kafka — they needed replay capability for reindexing. Once you need to reprocess events, a log-based system beats a traditional queue."*

**Stripe — Exactly-Once Processing for Payments**: Stripe built idempotency into every API endpoint. Every API call accepts an `Idempotency-Key` header. The server caches the response keyed by this value — retries return the same response without re-processing.
> *"Stripe's API accepts an Idempotency-Key header on every request. If you retry with the same key, you get the same response — no double charges. This is the gold standard for payment API design."*

---

## 4. Search & Indexing

**Elasticsearch at Scale — Uber, Netflix, Wikipedia**: Elasticsearch is used at massive scale: Uber uses it for trip search and geospatial queries. Netflix uses it for searching through their content catalog and error logs. Wikipedia uses it for full-text search across all articles.
> *"Uber uses Elasticsearch for trip search with geospatial queries. Netflix uses it for content search and log analysis. It's the go-to for full-text search at scale."*

**Google — Inverted Index + PageRank**: Google's search fundamentally uses an inverted index (word → list of documents) combined with PageRank (importance scoring based on link graph). Every modern search system uses inverted indexes — Google proved the approach at web scale.
> *"Google's entire search is built on inverted indexes — the same structure Elasticsearch uses. The insight was combining text relevance with link-based importance scoring."*

**Airbnb — Search Ranking with ML**: Airbnb rebuilt their search ranking to use a machine learning model that considers host response rate, listing quality, price competitiveness, and guest preferences. They saw a 5%+ improvement in bookings.
> *"Airbnb uses ML-based search ranking — the model considers 100+ features including host response rate and price relative to neighborhood average. Text matching is just the first stage."*

**Pinterest — Visual Search**: Pinterest's visual search lets users search by image — take a photo of a lamp, find similar products. They use deep learning (CNN) to extract image embeddings and approximate nearest-neighbor search (FAISS) for fast retrieval.
> *"Pinterest's visual search uses CNN embeddings + FAISS for approximate nearest-neighbor search. 200M product images indexed, visual search query in < 200ms."*

---

## 5. Scaling & Infrastructure

**Netflix — Chaos Monkey / Chaos Engineering**: Netflix pioneered chaos engineering with Chaos Monkey — randomly killing production instances to verify that their systems self-heal. They later expanded to Chaos Kong (kill entire AWS regions) and FIT (Failure Injection Testing).
> *"Netflix runs Chaos Monkey in production — it randomly kills instances to ensure everything recovers automatically. If your system can't handle a server dying, it can't handle production."*

**Google — Borg → Kubernetes**: Google's internal cluster manager Borg (running since 2003) was the inspiration for Kubernetes. Borg manages billions of containers across Google's fleet. Kubernetes is the open-source spiritual successor.
> *"Kubernetes is based on Google's Borg system, which has been managing containers at Google since 2003. If it works for Google's scale, it works for ours."*

**Twitter — Fail Whale → JVM Optimization**: Twitter's infamous "Fail Whale" (2008-2010) was caused by Ruby on Rails not scaling for their traffic. They rewrote critical services in Scala/JVM (Finagle framework), which eliminated the scaling bottleneck.
> *"Twitter's Fail Whale was solved by moving from Ruby to JVM (Scala). The lesson: language/runtime choice matters at scale. They also created Finagle — their RPC framework — which is now open source."*

**WhatsApp — 2 Million Connections Per Server (Erlang)**: WhatsApp famously handled 2 million concurrent connections per Erlang server with a team of ~50 engineers. Erlang's lightweight processes and fault tolerance made this possible.
> *"WhatsApp handled 2 million connections per server using Erlang. 900 million users with 50 engineers. The right language runtime for the problem made the difference."*

**Discord — Elixir for Millions of Concurrent Connections**: Discord uses Elixir (built on Erlang VM/BEAM) for their real-time gateway servers. Each server handles millions of concurrent WebSocket connections.
> *"Discord uses Elixir on the BEAM VM for their WebSocket gateway — millions of concurrent connections per server. Elixir inherited Erlang's concurrency model, which was designed for telecom-scale real-time systems."*

**Shopify — Modular Monolith at Scale**: Shopify runs one of the largest Ruby on Rails monoliths in the world. Rather than microservices, they organize code into modules (components) with enforced boundaries. They handle Black Friday traffic (millions of checkouts) with this architecture.
> *"Shopify runs one of the biggest Rails monoliths — and handles Black Friday traffic with it. They chose a modular monolith over microservices. Proof that monoliths can scale if well-organized."*

**GitHub — Scaled Ruby on Rails to Millions of Developers**: GitHub ran on Ruby on Rails for years, serving millions of developers. They eventually moved to Kubernetes and extracted some services, but the core remained Rails for a long time.
> *"GitHub served millions of developers on a Rails monolith for years. They didn't need microservices early on — they scaled through database optimization, caching, and background job processing."*

---

## 6. Real-Time Systems

**Slack — Real-Time Messaging Architecture**: Slack uses a WebSocket gateway layer backed by a custom message routing service. Each connection is stateful — messages are fanned out to all channel members in real-time. They handle millions of concurrent connections.
> *"Slack uses WebSocket gateways for real-time message delivery. Each gateway handles hundreds of thousands of connections. Message routing is done server-side based on channel membership."*

**WhatsApp — XMPP-Based, Then Custom Protocol**: WhatsApp originally used XMPP for messaging, then built a custom protocol optimized for mobile (smaller payloads, better battery life). Messages are stored temporarily and delivered via persistent connections.
> *"WhatsApp moved from XMPP to a custom binary protocol — smaller payloads, better battery life. Every byte matters when you're sending billions of messages per day on mobile networks."*

**Figma — CRDTs for Real-Time Collaboration**: Figma uses CRDTs (Conflict-free Replicated Data Types) for real-time collaborative design. Multiple users edit the same canvas simultaneously — changes merge automatically without conflicts.
> *"Figma uses CRDTs for real-time collaboration — two designers editing the same element simultaneously, and changes merge without conflicts. This is the same approach I'd use for collaborative editing."*

**Google Docs — Operational Transformation (OT)**: Google Docs uses Operational Transformation to handle concurrent edits. Each edit is transformed against concurrent operations to maintain consistency. It's simpler than CRDTs but requires a central server.
> *"Google Docs uses Operational Transformation for concurrent editing. OT is simpler than CRDTs but requires a central server. For a document editor where you already have a server, OT works great."*

---

## 7. Consistency & Distributed Systems

**Google Spanner — Globally Consistent Distributed DB**: Google Spanner uses atomic clocks (TrueTime) to achieve externally consistent distributed transactions globally. It's the only production system that provides strong consistency across continents.
> *"Google Spanner uses atomic clocks for globally consistent transactions. It's proof that strong consistency at global scale IS possible — but requires specialized hardware (atomic clocks in every data center)."*

**Amazon — Dynamo Paper (2007)**: Amazon's Dynamo paper introduced eventual consistency, consistent hashing, vector clocks, gossip protocol, and quorum-based reads/writes. It's the foundation for DynamoDB, Cassandra, and Riak.
> *"Amazon's Dynamo paper is the bible of distributed systems. Consistent hashing, quorum reads/writes, gossip protocol — all came from Amazon's need to keep the shopping cart available during failures."*

**Facebook — Cassandra (Then Open-Sourced)**: Facebook originally built Cassandra combining ideas from Amazon's Dynamo (partitioning, replication) and Google's Bigtable (column-family data model). They later moved to other systems internally but Cassandra became a major open-source project.
> *"Facebook created Cassandra by combining Amazon's Dynamo (distributed architecture) with Google's Bigtable (data model). It's purpose-built for write-heavy, eventually consistent workloads."*

**Netflix — Multi-Region Active-Active**: Netflix runs active-active across 3 AWS regions (us-east-1, us-west-2, eu-west-1). Each region serves traffic independently. If one goes down, DNS routes to surviving regions. They use EVCache for cross-region data.
> *"Netflix runs active-active across 3 AWS regions. If an entire region goes down, DNS failover routes traffic to surviving regions within 30 seconds. Users don't even notice."*

---

## 8. Data Processing & Analytics

**Netflix — ~90% of Netflix Engineering Is Data Pipelines**: Netflix's recommendation system, A/B testing framework, content analytics, and quality-of-experience monitoring are all powered by massive data pipelines (Spark, Flink, Kafka). They process petabytes of data daily.
> *"About 90% of what you see on Netflix is powered by data pipelines — recommendations, artwork personalization, A/B tests, even video encoding decisions. It's a data company that happens to stream video."*

**Uber — Real-Time Pricing (Surge)**: Uber's surge pricing uses real-time supply/demand data processed via Flink/Kafka. Every few seconds, it recalculates the pricing multiplier for each geo-cell based on available drivers vs pending rides.
> *"Uber's surge pricing recalculates every few seconds using real-time Flink processing. Supply (available drivers) vs demand (ride requests) per geographic cell determines the multiplier."*

**Spotify — Personalized Discover Weekly**: Spotify's Discover Weekly uses collaborative filtering + natural language processing of music reviews + audio analysis (spectrograms). The pipeline runs on GCP Dataflow (Apache Beam), processing listening data from 500M+ users.
> *"Spotify's Discover Weekly combines collaborative filtering, NLP from music blogs, and audio signal analysis. It's a batch pipeline that runs weekly, but the data collection is real-time Kafka streams."*

**LinkedIn — Unified Streaming & Batch (Lambda → Kappa)**: LinkedIn pioneered the Lambda Architecture (batch + streaming) and later moved toward Kappa Architecture (streaming-only). Jay Kreps (Kafka creator) wrote the influential blog post arguing that one streaming pipeline can replace batch.
> *"LinkedIn moved from Lambda (batch + streaming) to Kappa (streaming-only). Jay Kreps, who created Kafka at LinkedIn, argued that a single well-designed streaming pipeline eliminates the need for a separate batch layer."*

**Cloudflare — ClickHouse for Analytics**: Cloudflare uses ClickHouse to analyze HTTP request logs — billions of rows per day. ClickHouse's columnar storage and vectorized query execution lets them scan billions of rows in seconds.
> *"Cloudflare uses ClickHouse to analyze billions of HTTP requests per day. Columnar storage means scanning 1 billion rows for a GROUP BY query takes seconds, not minutes."*

---

## 9. ID Generation & Unique Identifiers

**Twitter — Snowflake ID**: Twitter created the Snowflake ID scheme: 64-bit = timestamp (41 bits) + machine ID (10 bits) + sequence (12 bits). Time-sortable, compact, and decentralized. It's now the de facto standard for distributed ID generation.
> *"Twitter created Snowflake IDs — 64-bit, time-sortable, decentralized. 4096 IDs per millisecond per machine with zero coordination. This is my default choice for any distributed system."*

**Flickr — Ticket Servers (Dual Counter)**: Flickr used two MySQL "ticket servers" — one generating odd IDs, one generating even. Auto-increment with step=2. Simple, reliable, and if one server dies, the other keeps working.
> *"Flickr used two ticket servers — odd and even IDs. Simple, reliable, and available. Sometimes the boring solution is the right one."*

**MongoDB — ObjectID**: MongoDB's ObjectID is 96-bit: timestamp (32 bits) + machine identifier (40 bits) + counter (24 bits). Time-sortable but larger than Snowflake. No coordination needed.
> *"MongoDB ObjectIDs are self-generated per machine — no coordination. Similar idea to Snowflake but 96 bits instead of 64. Native to MongoDB's architecture."*

---

## 10. Rate Limiting & Abuse Prevention

**Cloudflare — Rate Limiting at the Edge**: Cloudflare implements rate limiting at their edge nodes (300+ data centers worldwide). They process over 50 million HTTP requests per second, applying rate limits before traffic even reaches origin servers.
> *"Cloudflare rate-limits at the edge — 300+ data centers worldwide. Bad traffic is blocked before it even reaches your servers. That's why CDN-level rate limiting is the first line of defense."*

**GitHub — API Rate Limiting (Conditional Requests)**: GitHub's API uses a combination of rate limiting (5000 requests/hour for authenticated users) and conditional requests (ETag/If-None-Match) to reduce unnecessary data transfer.
> *"GitHub's API returns ETag headers so clients can make conditional requests — if nothing changed, the server returns 304 Not Modified without counting against the rate limit. Smart API design reduces both load and rate limit impact."*

**Stripe — Adaptive Rate Limiting**: Stripe uses adaptive rate limiting that considers not just request count but also the type of request and the caller's history. Trusted integrations get higher limits. New accounts start with stricter limits.
> *"Stripe uses adaptive rate limiting — trusted integrations get higher limits, new accounts start strict. The limit isn't just per-IP, it's per-account, per-action, and adjusted by trust level."*

---

## 11. Deployment & Operations

**Netflix — Full Cycle Developers (You Build It, You Run It)**: Netflix pioneered "full cycle development" — the team that writes the code also deploys it, monitors it, and is on-call for it. No separate ops team. This drives ownership and accountability.
> *"Netflix practices 'you build it, you run it' — the team that writes the code is on-call for it in production. This creates an incentive to write reliable, observable code."*

**Amazon — Two-Pizza Teams**: Amazon organizes around "two-pizza teams" — small enough to be fed by two pizzas (~6-8 people). Each team owns a service end-to-end: development, deployment, monitoring, on-call.
> *"Amazon's two-pizza teams own their service end-to-end. The small team size forces clear API boundaries and service decomposition — it's why Amazon was early to microservices."*

**Facebook — Canary Deployments**: Facebook deploys new code to a small percentage of servers first (canary), monitors key metrics for anomalies, then gradually rolls out. If metrics degrade, the deployment is automatically rolled back.
> *"Facebook auto-rolls back deployments if metrics degrade. They deploy to 1% of servers, compare error rates to the baseline, and only proceed if metrics are healthy. Automated canary analysis."*

**Google — SRE (Site Reliability Engineering)**: Google invented the SRE discipline — treating operations as a software engineering problem. Key concepts: error budgets, SLOs, toil reduction, and the idea that 100% uptime is the wrong target.
> *"Google's SRE team popularized error budgets — if your SLO is 99.9%, you have a budget of 52 minutes of downtime per year. If you're under budget, you can push features faster. If over, you focus on reliability."*

---

## 12. Architecture Decisions

**Netflix — Microservices Pioneer**: Netflix was one of the first companies to fully adopt microservices (2012+). They decomposed their monolith into 700+ microservices, each independently deployable. They also built Eureka (service discovery), Zuul (API gateway), Hystrix (circuit breaker), and Ribbon (client-side load balancing).
> *"Netflix decomposed into 700+ microservices and open-sourced most of their infrastructure: Eureka for discovery, Zuul for gateway, Hystrix for circuit breakers. Their Netflix OSS stack is essentially the microservices playbook."*

**Amazon — Service-Oriented Architecture (SOA) Mandate**: In 2002, Jeff Bezos mandated that all teams expose their data and functionality through service interfaces. No direct database access between teams. This forced Amazon into a service-oriented architecture years before "microservices" was a term.
> *"Bezos's 2002 mandate: all teams must communicate through APIs — no direct database access. This forced Amazon into SOA 10 years before microservices became trendy. The result: AWS."*

**Monzo — Microservices from Day One (1600 services, 200 engineers)**: Monzo (UK bank) built microservices from the start. By 2024, they had ~1600 services for ~200 engineers. They've spoken openly about the overhead and whether it was worth it.
> *"Monzo has 1600 microservices for 200 engineers — about 8 services per engineer. They've been transparent about the operational overhead. It works for a bank (isolation is critical), but it's not free."*

**Basecamp (37signals) — The Majestic Monolith**: DHH (creator of Ruby on Rails) advocates for "the majestic monolith" — a single, well-structured application. Basecamp serves millions of users with a monolithic Rails app. No microservices.
> *"Basecamp serves millions of users with a single Rails monolith. DHH calls it the 'majestic monolith.' The lesson: microservices are a tool, not a goal. Most companies don't need them."*

---

## 13. Content Delivery & Media

**Netflix — Open Connect (Custom CDN)**: Netflix built their own CDN called Open Connect. They place hardware appliances directly inside ISPs' data centers worldwide. During peak hours, Open Connect serves 100% of Netflix video traffic — no third-party CDN.
> *"Netflix built their own CDN — Open Connect — and placed hardware inside ISPs. During peak, it serves 100% of video traffic. At Netflix's scale, building a custom CDN is cheaper than paying Akamai."*

**YouTube — Adaptive Bitrate + Pre-Transcoding**: YouTube pre-transcodes every uploaded video into 10+ quality levels (144p to 4K). The player uses DASH/HLS adaptive bitrate streaming to switch quality based on bandwidth. This costs massive compute but enables smooth playback worldwide.
> *"YouTube pre-transcodes every video into 10+ quality levels. The compute cost is enormous, but it enables adaptive bitrate streaming — the player switches quality in real-time based on bandwidth."*

**Instagram — Presigned URLs for Direct Upload**: Instagram uses presigned S3 URLs so that mobile clients upload photos/videos directly to S3 without going through Instagram's servers. The backend only handles the lightweight URL generation.
> *"Instagram uses presigned URLs — the client uploads photos directly to S3. The backend server never touches the image bytes. This is the standard pattern for media-heavy applications."*

**Cloudflare — 300+ Edge Locations**: Cloudflare operates 300+ data centers worldwide. Static content is cached at the edge so users in Tokyo get content from a Tokyo server, not from a US origin. Average latency reduction: 10× for static assets.
> *"Cloudflare's 300+ edge locations mean your static content is usually served from a data center in the same city as the user — 15ms instead of 200ms. That's why CDN is the single biggest latency win."*

---

## 14. Security & Authentication

**Google — BeyondCorp (Zero Trust)**: Google's BeyondCorp model treats every network as untrusted — no VPN. Every request is authenticated and authorized based on user identity and device posture, regardless of network location. The origin of "zero trust" architecture.
> *"Google's BeyondCorp eliminated VPNs. Every request is authenticated regardless of network location — there's no 'trusted internal network.' This is the zero-trust model that the industry is now adopting."*

**WhatsApp — Signal Protocol for E2EE**: WhatsApp uses the Signal Protocol (developed by Open Whisper Systems) for end-to-end encryption. Every message, photo, and call is encrypted. Even WhatsApp's servers can't read the content.
> *"WhatsApp uses the Signal Protocol for end-to-end encryption — the same protocol I'd use. Even WhatsApp's servers can't read messages. The tradeoff: no server-side search or content moderation."*

**Auth0 / Okta — OAuth 2.0 + OIDC Standard**: Auth0 (now Okta) standardized how companies implement authentication. They proved that auth should be a service, not custom code — JWTs for stateless validation, OIDC for identity, OAuth 2.0 for delegation.
> *"Auth0 proved that authentication should be a service, not custom code. JWTs for stateless validation, OIDC for identity. The industry converged on this pattern."*

---

## 15. Machine Learning in Production

**Netflix — Recommendation System**: Netflix estimates their recommendation system is worth $1B/year in reduced churn. It uses collaborative filtering, content-based filtering, and deep learning. The famous "Netflix Prize" ($1M competition) accelerated ML research.
> *"Netflix's recommendation system saves them $1B/year in reduced churn. The entire UI — rows, ordering, artwork — is personalized. Everything is an A/B test."*

**Uber — ML for ETA Prediction**: Uber uses gradient-boosted trees for ETA prediction — estimating how long a ride will take. Features include: real-time traffic, historical data, time of day, weather, and road segments. The model updates every few minutes.
> *"Uber's ETA prediction uses gradient-boosted trees with real-time traffic data. The model is retrained frequently to account for changing road conditions. LightGBM in production — same algorithm I'd use for ranking."*

**TikTok — Recommendation Algorithm**: TikTok's For You page uses a deep learning recommendation model that considers: video content (what's in the video), user behavior (watch time, likes, shares, replays), and social graph (what similar users liked). Watch time is the strongest signal.
> *"TikTok's algorithm optimizes for watch time — the strongest signal for content quality. Every swipe, pause, and replay is a training signal. They process billions of interaction events per day to personalize the For You page."*

**Google — TFX (TensorFlow Extended) for ML Pipeline**: Google built TFX as a production ML platform: data validation, feature engineering, model training, model validation, serving, and monitoring — all in one pipeline.
> *"Google's TFX handles the full ML lifecycle in production: data validation catches schema drift, model validation catches regression, and monitoring catches model decay. The ML pipeline is as important as the model itself."*

---

## 16. Lessons Learned / Post-Mortems

**Amazon — The 2017 S3 Outage**: Amazon S3 went down for 4 hours because an engineer ran a maintenance command with a typo that removed more servers than intended. This cascaded because so many AWS services depend on S3. Lesson: human error is the #1 cause of outages.
> *"The 2017 S3 outage was caused by a typo in a maintenance command that removed too many servers. It cascaded across AWS because everything depends on S3. Lesson: human error needs guardrails — confirmation prompts, blast radius limits."*

**Facebook — 6-Hour Outage (2021)**: Facebook, Instagram, and WhatsApp went down for 6 hours because a BGP routing misconfiguration removed Facebook's DNS servers from the internet. Their own engineers couldn't access internal tools to fix it because those tools also depended on the same infrastructure.
> *"Facebook's 2021 outage lasted 6 hours because a BGP change removed their DNS from the internet. Engineers couldn't fix it because their repair tools ran on the same infrastructure. Lesson: out-of-band access for disaster recovery."*

**Cloudflare — Regular Expression Catastrophe (2019)**: A poorly written regular expression in Cloudflare's WAF rules caused CPU usage to spike to 100% on every edge server globally. The entire Cloudflare network went down for 27 minutes. Lesson: test regex performance.
> *"Cloudflare went down globally because of a bad regex that caused catastrophic backtracking. Every CPU on every edge server hit 100%. Lesson: regex can be O(2^N) — always test regex performance."*

**GitLab — Database Deletion Incident (2017)**: A GitLab engineer accidentally deleted the production database while trying to fix a replication issue. 5 out of 5 backup methods failed. They livestreamed the recovery process on YouTube. Lesson: test your backups.
> *"GitLab accidentally deleted their production database, and 5 out of 5 backup methods failed. They livestreamed the recovery. Lesson: backups are worthless unless you regularly test restoration."*

**Knight Capital — $440M Loss in 45 Minutes (2012)**: Knight Capital deployed code that accidentally reactivated a dormant trading algorithm, which bought high and sold low for 45 minutes. They lost $440M. Lesson: feature flags, staged rollouts, and kill switches.
> *"Knight Capital lost $440M in 45 minutes from a bad deployment. They deployed code without feature flags or a kill switch. This is why we use canary deployments and instant rollback — every deploy should be reversible in seconds."*

---

## 📚 How to Use These in an Interview

### The Pattern

*"This is similar to how [Company] handles [X]. They [published a blog post / built a system / experienced an outage] about [Y]. The key insight is [Z], and I'd apply the same principle here."*

### Examples

- **Choosing a database**: "This is similar to how Instagram ran on PostgreSQL with 3 engineers and 30M users. I'd start with Postgres and only move to a distributed database when we actually hit its limits."

- **Defending eventual consistency**: "Amazon built DynamoDB for their shopping cart with eventual consistency — they chose availability over consistency because a cart that sometimes shows stale data is better than a cart that returns errors."

- **Justifying a monolith**: "Shopify runs one of the biggest Rails monoliths and handles Black Friday traffic. Microservices aren't always the answer — a well-organized monolith scales further than people think."

- **Explaining cache strategy**: "Facebook runs the world's largest Memcached deployment and solved the thundering herd with lease tokens. I'd use a similar approach: distributed lock so only one request repopulates the cache on miss."

- **Discussing failure handling**: "Netflix runs Chaos Monkey in production — randomly killing instances. If our system can't survive a server dying, it's not production-ready."

---

**Total Examples: 60+ from 30+ companies**
**Coverage: Database, Caching, Streaming, Search, Scaling, Real-Time, Security, ML, Failures**
**Status: Interview-Ready ✅**
