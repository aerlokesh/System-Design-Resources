# 🗄️ Database Schema Design — Real-World Examples

> **System Design Interview — Deep Dive**
> Complete production-ready schemas for 15+ real-world systems. Each includes table design, indexes, relationships, constraints, and the **WHY** behind every decision. Covers SQL (PostgreSQL) and NoSQL (DynamoDB) side by side.

---

## 📋 Table of Contents

1. [Schema Design Principles](#1-schema-design-principles)
2. [E-Commerce (Amazon)](#2-e-commerce-amazon)
3. [Social Media (Instagram)](#3-social-media-instagram)
4. [Chat/Messaging (WhatsApp/Slack)](#4-chatmessaging-whatsappslack)
5. [Ride-Sharing (Uber)](#5-ride-sharing-uber)
6. [Video Streaming (Netflix)](#6-video-streaming-netflix)
7. [Payment System (Stripe)](#7-payment-system-stripe)
8. [URL Shortener](#8-url-shortener)
9. [Notification System](#9-notification-system)
10. [Job Scheduler](#10-job-scheduler)
11. [Booking System (Airbnb)](#11-booking-system-airbnb)
12. [Food Delivery (DoorDash)](#12-food-delivery-doordash)
13. [Leaderboard/Gaming](#13-leaderboardgaming)
14. [Content Management (Reddit)](#14-content-management-reddit)
15. [Calendar/Scheduling (Calendly)](#15-calendarscheduling-calendly)
16. [DynamoDB Single-Table Patterns](#16-dynamodb-single-table-patterns)
17. [🏆 Schema Design Cheat Sheet](#-schema-design-cheat-sheet)

---

## 1. Schema Design Principles

### SQL Schema Design Rules

```
1. NORMALIZE first (3NF), denormalize only for performance
2. Every table needs a PRIMARY KEY (prefer BIGSERIAL or UUID)
3. Use FOREIGN KEYS for referential integrity
4. Add indexes for columns in WHERE, JOIN, ORDER BY
5. Use appropriate types: BIGINT for money (cents), TIMESTAMPTZ for dates
6. Add CHECK constraints for business rules
7. Use UNIQUE constraints to prevent duplicates
8. Composite indexes: put high-cardinality column FIRST
9. Partial indexes for common filtered queries
10. created_at + updated_at on every table
```

### Naming Conventions

```sql
-- Tables: plural, snake_case
users, orders, order_items, payment_methods

-- Columns: snake_case, descriptive
user_id, created_at, total_amount_cents, is_active

-- Indexes: idx_{table}_{columns}
idx_orders_user_id_created_at

-- Foreign keys: fk_{table}_{referenced_table}
fk_orders_user_id

-- Constraints: chk_{table}_{rule}
chk_orders_amount_positive
```

### When SQL vs DynamoDB

| Choose SQL (PostgreSQL/Aurora) | Choose DynamoDB |
|-------------------------------|-----------------|
| Complex JOINs, aggregations | Known access patterns, no JOINs |
| ACID across multiple tables | Single-table, single-digit ms |
| Ad-hoc queries, reporting | Unlimited horizontal scale |
| Small-medium scale (<10TB) | Any scale (petabytes) |
| Schema evolves gradually | Schema evolves per item |
| Strong consistency needed | Eventual consistency OK (or strong per-item) |

---

## 2. E-Commerce (Amazon)

### PostgreSQL Schema

```sql
-- Users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Addresses (one user → many addresses)
CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label           VARCHAR(50) DEFAULT 'Home',  -- Home, Work, Other
    street          VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(50),
    zip_code        VARCHAR(20) NOT NULL,
    country         VARCHAR(50) NOT NULL DEFAULT 'US',
    is_default      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_addresses_user ON addresses(user_id);

-- Products
CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price_cents     BIGINT NOT NULL CHECK (price_cents > 0),
    category_id     BIGINT REFERENCES categories(id),
    inventory_count INTEGER NOT NULL DEFAULT 0 CHECK (inventory_count >= 0),
    images          TEXT[],  -- Array of image URLs
    tags            TEXT[],
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_tags ON products USING GIN(tags);
CREATE INDEX idx_products_active ON products(is_active) WHERE is_active = TRUE;

-- Categories (self-referencing tree)
CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    parent_id       BIGINT REFERENCES categories(id),
    slug            VARCHAR(100) UNIQUE NOT NULL,
    level           INTEGER NOT NULL DEFAULT 0
);

-- Orders
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','confirmed','shipped','delivered','cancelled','refunded')),
    total_cents     BIGINT NOT NULL CHECK (total_cents >= 0),
    shipping_address_id BIGINT REFERENCES addresses(id),
    payment_method  VARCHAR(50),
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status) WHERE status IN ('pending', 'confirmed', 'shipped');

-- Order Items (junction table)
CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents > 0),
    -- Snapshot price at time of order (price may change later)
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Shopping Cart (separate from orders — ephemeral)
CREATE TABLE cart_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    added_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, product_id)  -- One entry per product per user
);
CREATE INDEX idx_cart_user ON cart_items(user_id);

-- Reviews
CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title           VARCHAR(255),
    body            TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, product_id)  -- One review per user per product
);
CREATE INDEX idx_reviews_product ON reviews(product_id, rating);
```

### Key Design Decisions

```
1. price_cents BIGINT: Never use FLOAT for money! $19.99 = 1999 cents
2. unit_price_cents in order_items: Snapshot — product price may change
3. UNIQUE(user_id, product_id) on cart: prevents duplicate cart entries
4. CHECK constraints: status enum, positive amounts, rating range
5. Partial index on orders: only index active statuses (smaller, faster)
6. ON DELETE CASCADE: deleting user removes their cart, addresses
7. TEXT[] for images/tags: PostgreSQL arrays avoid junction tables
8. categories self-reference: parent_id for hierarchical categories
```

### Common Queries

```sql
-- Get user's order history with items
SELECT o.*, json_agg(json_build_object(
    'product', p.name, 'quantity', oi.quantity, 'price', oi.unit_price_cents
)) as items
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE o.user_id = 123
GROUP BY o.id
ORDER BY o.created_at DESC
LIMIT 10;

-- Product search by category and tags
SELECT * FROM products
WHERE category_id = 5 AND tags @> ARRAY['electronics']
AND is_active = TRUE
ORDER BY created_at DESC;

-- Atomic inventory decrement (purchase)
UPDATE products SET inventory_count = inventory_count - 1
WHERE id = 456 AND inventory_count > 0
RETURNING id;  -- Empty result = out of stock
```

---

## 3. Social Media (Instagram)

```sql
-- Users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(30) UNIQUE NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    bio             TEXT,
    avatar_url      TEXT,
    is_private      BOOLEAN DEFAULT FALSE,
    follower_count  INTEGER DEFAULT 0,  -- Denormalized counter
    following_count INTEGER DEFAULT 0,
    post_count      INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Follows (social graph)
CREATE TABLE follows (
    follower_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id != following_id)  -- Can't follow yourself
);
CREATE INDEX idx_follows_following ON follows(following_id);
-- PK already indexes (follower_id, following_id)

-- Posts
CREATE TABLE posts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_urls      TEXT[] NOT NULL,  -- Multiple images/videos
    media_type      VARCHAR(10) NOT NULL CHECK (media_type IN ('image', 'video', 'carousel')),
    caption         TEXT,
    location_name   VARCHAR(255),
    location_point  GEOGRAPHY(POINT, 4326),  -- PostGIS
    like_count      INTEGER DEFAULT 0,  -- Denormalized
    comment_count   INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_posts_user_created ON posts(user_id, created_at DESC);
CREATE INDEX idx_posts_location ON posts USING GIST(location_point);

-- Likes (prevents double-liking)
CREATE TABLE likes (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id         BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);
CREATE INDEX idx_likes_post ON likes(post_id);

-- Comments (threaded)
CREATE TABLE comments (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    parent_id       BIGINT REFERENCES comments(id),  -- Reply threading
    body            TEXT NOT NULL,
    like_count      INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_comments_post ON comments(post_id, created_at);

-- Stories (auto-expire)
CREATE TABLE stories (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_url       TEXT NOT NULL,
    media_type      VARCHAR(10) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours',
    view_count      INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_stories_user_expires ON stories(user_id, expires_at DESC);

-- Hashtags (many-to-many)
CREATE TABLE hashtags (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) UNIQUE NOT NULL,
    post_count      INTEGER DEFAULT 0
);
CREATE TABLE post_hashtags (
    post_id         BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    hashtag_id      BIGINT NOT NULL REFERENCES hashtags(id),
    PRIMARY KEY (post_id, hashtag_id)
);
CREATE INDEX idx_post_hashtags_hashtag ON post_hashtags(hashtag_id);
```

### Key Design Decisions

```
1. Denormalized counters (follower_count, like_count): Avoid COUNT(*) on millions of rows
   → Update atomically: UPDATE posts SET like_count = like_count + 1 WHERE id = X
   → Or maintain via triggers / async workers

2. PRIMARY KEY (follower_id, following_id): Composite PK = unique constraint + index
   → "Am I following this person?" = instant lookup
   
3. CHECK (follower_id != following_id): DB prevents following yourself

4. TEXT[] for media_urls: Carousel posts have multiple images

5. Stories with expires_at: Query "active stories" = WHERE expires_at > NOW()
   → Background job cleans up expired stories

6. Hashtag junction table: Many-to-many, searchable by hashtag
```

---

## 4. Chat/Messaging (WhatsApp/Slack)

```sql
-- Conversations (1:1 or group)
CREATE TABLE conversations (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('direct', 'group')),
    name            VARCHAR(255),  -- Group name (null for direct)
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Conversation Members
CREATE TABLE conversation_members (
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    role            VARCHAR(20) DEFAULT 'member' CHECK (role IN ('owner','admin','member')),
    joined_at       TIMESTAMPTZ DEFAULT NOW(),
    last_read_at    TIMESTAMPTZ,  -- For unread count calculation
    is_muted        BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_conv_members_user ON conversation_members(user_id);

-- Messages
CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id),
    sender_id       BIGINT NOT NULL REFERENCES users(id),
    content         TEXT,  -- Encrypted in production
    message_type    VARCHAR(20) DEFAULT 'text'
                    CHECK (message_type IN ('text','image','video','file','system')),
    media_url       TEXT,
    reply_to_id     BIGINT REFERENCES messages(id),  -- Reply threading
    is_edited       BOOLEAN DEFAULT FALSE,
    is_deleted      BOOLEAN DEFAULT FALSE,  -- Soft delete
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_messages_conv_created ON messages(conversation_id, created_at DESC);
-- This is THE most important index — "get latest messages in conversation"

-- Message Read Receipts
CREATE TABLE message_reads (
    message_id      BIGINT NOT NULL REFERENCES messages(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    read_at         TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id)
);

-- Attachments
CREATE TABLE attachments (
    id              BIGSERIAL PRIMARY KEY,
    message_id      BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    file_url        TEXT NOT NULL,
    file_name       VARCHAR(255),
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### Key Queries

```sql
-- Get conversations for user (with last message preview)
SELECT c.*, m.content as last_message, m.created_at as last_message_at,
       (SELECT COUNT(*) FROM messages WHERE conversation_id = c.id 
        AND created_at > cm.last_read_at) as unread_count
FROM conversations c
JOIN conversation_members cm ON c.id = cm.conversation_id
LEFT JOIN LATERAL (
    SELECT content, created_at FROM messages 
    WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1
) m ON true
WHERE cm.user_id = 123
ORDER BY COALESCE(m.created_at, c.created_at) DESC;

-- Get paginated messages in conversation
SELECT m.*, u.username as sender_name
FROM messages m
JOIN users u ON m.sender_id = u.id
WHERE m.conversation_id = 456 AND m.is_deleted = FALSE
ORDER BY m.created_at DESC
LIMIT 50 OFFSET 0;
```

---

## 5. Ride-Sharing (Uber)

```sql
CREATE EXTENSION postgis;

-- Drivers
CREATE TABLE drivers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT UNIQUE NOT NULL REFERENCES users(id),
    license_number  VARCHAR(50) NOT NULL,
    vehicle_make    VARCHAR(50),
    vehicle_model   VARCHAR(50),
    vehicle_year    INTEGER,
    vehicle_plate   VARCHAR(20),
    rating          NUMERIC(3,2) DEFAULT 5.00,
    total_rides     INTEGER DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'offline'
                    CHECK (status IN ('offline','available','on_trip','busy')),
    current_location GEOGRAPHY(POINT, 4326),
    last_location_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_drivers_status_location ON drivers USING GIST(current_location) WHERE status = 'available';

-- Trips
CREATE TABLE trips (
    id              BIGSERIAL PRIMARY KEY,
    rider_id        BIGINT NOT NULL REFERENCES users(id),
    driver_id       BIGINT REFERENCES drivers(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'requested'
                    CHECK (status IN ('requested','accepted','driver_arrived','in_progress',
                                      'completed','cancelled')),
    pickup_location GEOGRAPHY(POINT, 4326) NOT NULL,
    pickup_address  VARCHAR(255),
    dropoff_location GEOGRAPHY(POINT, 4326),
    dropoff_address VARCHAR(255),
    estimated_fare_cents BIGINT,
    actual_fare_cents BIGINT,
    distance_meters  DOUBLE PRECISION,
    duration_seconds INTEGER,
    surge_multiplier NUMERIC(3,2) DEFAULT 1.00,
    rider_rating    SMALLINT CHECK (rider_rating BETWEEN 1 AND 5),
    driver_rating   SMALLINT CHECK (driver_rating BETWEEN 1 AND 5),
    requested_at    TIMESTAMPTZ DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ
);
CREATE INDEX idx_trips_rider ON trips(rider_id, requested_at DESC);
CREATE INDEX idx_trips_driver ON trips(driver_id, requested_at DESC);
CREATE INDEX idx_trips_status ON trips(status) WHERE status IN ('requested', 'accepted', 'in_progress');

-- Trip Route (GPS waypoints)
CREATE TABLE trip_waypoints (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id),
    location        GEOGRAPHY(POINT, 4326) NOT NULL,
    recorded_at     TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_waypoints_trip ON trip_waypoints(trip_id, recorded_at);
```

### Key Query: Find Nearby Drivers

```sql
SELECT d.id, d.rating, 
       ST_Distance(d.current_location, ST_MakePoint(-73.985, 40.748)::GEOGRAPHY) AS distance_m
FROM drivers d
WHERE d.status = 'available'
AND ST_DWithin(d.current_location, ST_MakePoint(-73.985, 40.748)::GEOGRAPHY, 5000)
ORDER BY distance_m
LIMIT 10;
```

---

## 6. Video Streaming (Netflix)

```sql
CREATE TABLE titles (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('movie', 'series')),
    description     TEXT,
    release_year    INTEGER,
    duration_minutes INTEGER,  -- For movies
    maturity_rating VARCHAR(10),  -- PG, PG-13, R
    genres          TEXT[],
    thumbnail_url   TEXT,
    trailer_url     TEXT,
    average_rating  NUMERIC(3,2),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_titles_genres ON titles USING GIN(genres);

-- Seasons & Episodes (for series)
CREATE TABLE episodes (
    id              BIGSERIAL PRIMARY KEY,
    title_id        BIGINT NOT NULL REFERENCES titles(id),
    season_number   INTEGER NOT NULL,
    episode_number  INTEGER NOT NULL,
    name            VARCHAR(255),
    duration_minutes INTEGER,
    video_url       TEXT NOT NULL,  -- S3/CloudFront URL
    thumbnail_url   TEXT,
    UNIQUE(title_id, season_number, episode_number)
);

-- User Watch History (continue watching)
CREATE TABLE watch_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title_id        BIGINT NOT NULL REFERENCES titles(id),
    episode_id      BIGINT REFERENCES episodes(id),
    progress_seconds INTEGER NOT NULL DEFAULT 0,
    duration_seconds INTEGER NOT NULL,
    completed       BOOLEAN DEFAULT FALSE,
    watched_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, title_id, episode_id)  -- One entry per user+content
);
CREATE INDEX idx_watch_user_recent ON watch_history(user_id, watched_at DESC);

-- User Watchlist ("My List")
CREATE TABLE watchlist (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title_id        BIGINT NOT NULL REFERENCES titles(id),
    added_at        TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, title_id)
);

-- Ratings
CREATE TABLE ratings (
    user_id         BIGINT NOT NULL REFERENCES users(id),
    title_id        BIGINT NOT NULL REFERENCES titles(id),
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    rated_at        TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, title_id)
);
```

---

## 7. Payment System (Stripe)

```sql
-- Accounts (merchants)
CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    currency        VARCHAR(3) DEFAULT 'USD',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Payments
CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),
    currency        VARCHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','processing','succeeded','failed','refunded')),
    idempotency_key VARCHAR(255) UNIQUE,  -- Prevent duplicate charges!
    description     VARCHAR(255),
    metadata        JSONB DEFAULT '{}',
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_payments_account ON payments(account_id, created_at DESC);
CREATE INDEX idx_payments_pending ON payments(status) WHERE status = 'pending';

-- Ledger (double-entry bookkeeping)
CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      UUID REFERENCES payments(id),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    amount_cents    BIGINT NOT NULL,  -- Positive=credit, Negative=debit
    entry_type      VARCHAR(20) NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
-- INVARIANT: SUM(amount_cents) across ALL entries = 0 (balanced books)
CREATE INDEX idx_ledger_account ON ledger_entries(account_id, created_at DESC);

-- Refunds
CREATE TABLE refunds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payments(id),
    amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),
    reason          VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'pending',
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 8. URL Shortener

```sql
CREATE TABLE urls (
    id              BIGSERIAL PRIMARY KEY,
    short_code      VARCHAR(10) UNIQUE NOT NULL,
    original_url    TEXT NOT NULL,
    user_id         BIGINT REFERENCES users(id),
    click_count     BIGINT DEFAULT 0,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_urls_short_code ON urls(short_code);
-- This is THE critical index — every redirect hits it

-- Click Analytics
CREATE TABLE url_clicks (
    id              BIGSERIAL PRIMARY KEY,
    url_id          BIGINT NOT NULL REFERENCES urls(id),
    ip_address      INET,
    user_agent      TEXT,
    referer         TEXT,
    country         VARCHAR(2),
    clicked_at      TIMESTAMPTZ DEFAULT NOW()
) PARTITION BY RANGE (clicked_at);
-- Partition by month for efficient cleanup
CREATE TABLE url_clicks_2024_01 PARTITION OF url_clicks
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

---

## 9. Notification System

```sql
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,  -- 'like', 'comment', 'follow', 'mention'
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    data            JSONB DEFAULT '{}',  -- Flexible payload
    is_read         BOOLEAN DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    channel         VARCHAR(20) DEFAULT 'in_app'
                    CHECK (channel IN ('in_app','push','email','sms')),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, created_at DESC) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_user_all ON notifications(user_id, created_at DESC);
```

---

## 10. Job Scheduler

```sql
CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    job_type        VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    cron_expression VARCHAR(100),  -- NULL = one-time job
    status          VARCHAR(20) NOT NULL DEFAULT 'scheduled'
                    CHECK (status IN ('scheduled','running','completed','failed','cancelled')),
    priority        SMALLINT DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    max_retries     INTEGER DEFAULT 3,
    retry_count     INTEGER DEFAULT 0,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    error_message   TEXT,
    locked_by       VARCHAR(255),  -- Worker ID that claimed this job
    locked_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_jobs_scheduled ON jobs(scheduled_at) WHERE status = 'scheduled';
CREATE INDEX idx_jobs_priority ON jobs(priority DESC, scheduled_at) WHERE status = 'scheduled';

-- Claim a job (atomic, prevents double-execution)
UPDATE jobs SET status = 'running', locked_by = 'worker-1', locked_at = NOW(), started_at = NOW()
WHERE id = (
    SELECT id FROM jobs
    WHERE status = 'scheduled' AND scheduled_at <= NOW()
    ORDER BY priority DESC, scheduled_at
    LIMIT 1
    FOR UPDATE SKIP LOCKED  -- Skip jobs being claimed by other workers!
)
RETURNING *;
```

---

## 11. Booking System (Airbnb)

```sql
CREATE TABLE listings (
    id              BIGSERIAL PRIMARY KEY,
    host_id         BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    property_type   VARCHAR(50),
    price_per_night_cents BIGINT NOT NULL CHECK (price_per_night_cents > 0),
    location        GEOGRAPHY(POINT, 4326),
    address         VARCHAR(255),
    max_guests      INTEGER DEFAULT 1,
    bedrooms        INTEGER DEFAULT 1,
    amenities       TEXT[],
    images          TEXT[],
    average_rating  NUMERIC(3,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_listings_location ON listings USING GIST(location);
CREATE INDEX idx_listings_amenities ON listings USING GIN(amenities);

-- Bookings (with date range overlap prevention!)
CREATE TABLE bookings (
    id              BIGSERIAL PRIMARY KEY,
    listing_id      BIGINT NOT NULL REFERENCES listings(id),
    guest_id        BIGINT NOT NULL REFERENCES users(id),
    check_in        DATE NOT NULL,
    check_out       DATE NOT NULL,
    date_range      DATERANGE GENERATED ALWAYS AS (daterange(check_in, check_out)) STORED,
    total_cents     BIGINT NOT NULL,
    status          VARCHAR(20) DEFAULT 'pending'
                    CHECK (status IN ('pending','confirmed','cancelled','completed')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CHECK (check_out > check_in),
    -- PREVENTS OVERLAPPING BOOKINGS at database level!
    EXCLUDE USING GIST (listing_id WITH =, date_range WITH &&) 
        WHERE (status NOT IN ('cancelled'))
);
```

---

## 12. Food Delivery (DoorDash)

```sql
CREATE TABLE restaurants (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    location        GEOGRAPHY(POINT, 4326),
    cuisine_type    TEXT[],
    rating          NUMERIC(3,2),
    delivery_radius_meters INTEGER DEFAULT 5000,
    is_open         BOOLEAN DEFAULT TRUE,
    opens_at        TIME,
    closes_at       TIME
);

CREATE TABLE menu_items (
    id              BIGSERIAL PRIMARY KEY,
    restaurant_id   BIGINT NOT NULL REFERENCES restaurants(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price_cents     BIGINT NOT NULL,
    category        VARCHAR(50),
    is_available    BOOLEAN DEFAULT TRUE,
    image_url       TEXT
);

CREATE TABLE delivery_orders (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES users(id),
    restaurant_id   BIGINT NOT NULL REFERENCES restaurants(id),
    dasher_id       BIGINT REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'placed'
                    CHECK (status IN ('placed','confirmed','preparing','dasher_assigned',
                                      'picked_up','en_route','delivered','cancelled')),
    subtotal_cents  BIGINT NOT NULL,
    delivery_fee_cents BIGINT DEFAULT 0,
    tip_cents       BIGINT DEFAULT 0,
    total_cents     BIGINT NOT NULL,
    delivery_address VARCHAR(255),
    delivery_location GEOGRAPHY(POINT, 4326),
    estimated_delivery_at TIMESTAMPTZ,
    actual_delivery_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 13. Leaderboard/Gaming

```sql
CREATE TABLE player_scores (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    game_id         VARCHAR(50) NOT NULL,
    score           BIGINT NOT NULL,
    metadata        JSONB DEFAULT '{}',  -- level, time, etc.
    achieved_at     TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_scores_game_score ON player_scores(game_id, score DESC);

-- Materialized view for ranking (refresh periodically)
CREATE MATERIALIZED VIEW leaderboard AS
SELECT user_id, game_id, MAX(score) as best_score,
       RANK() OVER (PARTITION BY game_id ORDER BY MAX(score) DESC) as rank
FROM player_scores
GROUP BY user_id, game_id;
CREATE INDEX idx_leaderboard_game_rank ON leaderboard(game_id, rank);
```

---

## 14. Content Management (Reddit)

```sql
-- Subreddits
CREATE TABLE subreddits (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) UNIQUE NOT NULL,
    description     TEXT,
    member_count    INTEGER DEFAULT 0,
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Posts
CREATE TABLE posts (
    id              BIGSERIAL PRIMARY KEY,
    subreddit_id    BIGINT NOT NULL REFERENCES subreddits(id),
    author_id       BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(300) NOT NULL,
    body            TEXT,
    url             TEXT,
    post_type       VARCHAR(10) CHECK (post_type IN ('text','link','image','video')),
    score           INTEGER DEFAULT 0,  -- upvotes - downvotes
    comment_count   INTEGER DEFAULT 0,
    is_pinned       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_posts_sub_score ON posts(subreddit_id, score DESC);  -- Hot posts
CREATE INDEX idx_posts_sub_new ON posts(subreddit_id, created_at DESC);  -- New posts

-- Votes (one per user per post)
CREATE TABLE post_votes (
    user_id         BIGINT NOT NULL REFERENCES users(id),
    post_id         BIGINT NOT NULL REFERENCES posts(id),
    direction       SMALLINT NOT NULL CHECK (direction IN (-1, 1)),
    PRIMARY KEY (user_id, post_id)
);

-- Upsert vote (change or insert)
INSERT INTO post_votes (user_id, post_id, direction) VALUES (123, 456, 1)
ON CONFLICT (user_id, post_id) DO UPDATE SET direction = EXCLUDED.direction;
```

---

## 15. Calendar/Scheduling (Calendly)

```sql
-- Availability slots
CREATE TABLE availability (
    id              BIGSERIAL PRIMARY KEY,
    host_id         BIGINT NOT NULL REFERENCES users(id),
    day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    timezone        VARCHAR(50) DEFAULT 'UTC',
    CHECK (end_time > start_time)
);

-- Bookings (overlap prevention)
CREATE TABLE calendar_bookings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id         BIGINT NOT NULL REFERENCES users(id),
    guest_email     VARCHAR(255) NOT NULL,
    guest_name      VARCHAR(100),
    time_slot       TSTZRANGE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status          VARCHAR(20) DEFAULT 'confirmed',
    meeting_link    TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    -- NO OVERLAPPING bookings per host!
    EXCLUDE USING GIST (host_id WITH =, time_slot WITH &&)
);
```

---

## 16. DynamoDB Single-Table Patterns

### E-Commerce on DynamoDB

```
Table: ECommerce
┌─────────────────────┬────────────────────────────┬──────────────────────┐
│ PK                  │ SK                         │ Attributes           │
├─────────────────────┼────────────────────────────┼──────────────────────┤
│ USER#user_123       │ PROFILE                    │ name, email, phone   │
│ USER#user_123       │ ADDR#addr_1                │ street, city, zip    │
│ USER#user_123       │ ORDER#2024-01-15#ord_abc   │ total, status        │
│ USER#user_123       │ ITEM#ord_abc#prod_xyz      │ qty, price           │
│ PRODUCT#prod_xyz    │ META                       │ name, price, stock   │
│ PRODUCT#prod_xyz    │ REVIEW#user_123            │ rating, text         │
│ CART#user_123       │ PROD#prod_xyz              │ quantity             │
└─────────────────────┴────────────────────────────┴──────────────────────┘

GSI1: GSI1PK=orderId → lookup order by ID
GSI2: GSI2PK=product_category, GSI2SK=price → browse by category
```

### Access Patterns → DynamoDB Operations

```
Get user profile      → GetItem(PK="USER#123", SK="PROFILE")
Get user's orders     → Query(PK="USER#123", SK begins_with "ORDER#")
Get order items       → Query(PK="USER#123", SK begins_with "ITEM#ord_abc")
Get product           → GetItem(PK="PRODUCT#xyz", SK="META")
Get product reviews   → Query(PK="PRODUCT#xyz", SK begins_with "REVIEW#")
Get/update cart       → Query(PK="CART#123") / PutItem / DeleteItem
```

---

## 🏆 Schema Design Cheat Sheet

```
┌────────────────────────────────────────────────────────────────────────┐
│         SCHEMA DESIGN INTERVIEW CHEAT SHEET                            │
├────────────────────────────────────────────────────────────────────────┤
│ RULES:                                                                 │
│ 1. BIGINT for money (cents) — NEVER float/double                      │
│ 2. TIMESTAMPTZ for all dates (timezone-aware)                         │
│ 3. UUID or BIGSERIAL for primary keys                                 │
│ 4. CHECK constraints for business rules (status, ranges)              │
│ 5. UNIQUE constraints prevent duplicates (idempotency_key, user+post) │
│ 6. Composite PK on junction tables (follows, likes, votes)           │
│ 7. Partial indexes for filtered queries (WHERE status = 'active')    │
│ 8. EXCLUDE constraints prevent overlaps (bookings, schedules)         │
│ 9. Denormalize counters (like_count) — avoid COUNT(*) at scale       │
│ 10. Soft delete (is_deleted) vs hard delete (ON DELETE CASCADE)       │
│                                                                        │
│ INDEX STRATEGY:                                                        │
│ • Compound: (user_id, created_at DESC) — most common pattern          │
│ • Partial: WHERE status IN ('active') — smaller, faster               │
│ • GIN: Arrays (tags), JSONB, full-text search (tsvector)             │
│ • GIST: Geography/geometry (PostGIS), range types (DATERANGE)        │
│ • UNIQUE: Enforce business rules at DB level                          │
│                                                                        │
│ PATTERNS:                                                              │
│ • 1:Many → FK on "many" side (user_id on orders)                     │
│ • M:N → Junction table with composite PK (post_hashtags)             │
│ • Tree → Self-referencing FK (parent_id on categories/comments)       │
│ • Polymorphic → type column + CHECK constraint (notification type)    │
│ • Temporal → DATERANGE + EXCLUDE constraint (prevent overlaps)        │
│ • Audit → created_at + updated_at on every table                     │
│ • Idempotency → UNIQUE on idempotency_key + ON CONFLICT DO NOTHING   │
│ • Pagination → Keyset (WHERE id < last_id) > OFFSET (slow at depth)  │
│                                                                        │
│ ANTI-PATTERNS TO AVOID:                                                │
│ ✗ FLOAT for money (precision loss)                                    │
│ ✗ VARCHAR for dates (can't sort/filter properly)                      │
│ ✗ EAV (Entity-Attribute-Value) — use JSONB instead                   │
│ ✗ Too many indexes (each slows writes)                                │
│ ✗ Missing foreign keys (orphaned data)                                │
│ ✗ OFFSET pagination at depth (use keyset/cursor instead)             │
│ ✗ SELECT * in production (fetch only needed columns)                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

> **Related Topics**: [Database Selection & Modeling →](./02-database-selection-and-data-modeling.md) | [SQL vs NoSQL →](./03-sql-vs-nosql.md) | [PostgreSQL Deep Dive →](./50-postgresql-deep-dive.md) | [PostgreSQL Real-World →](./50b-postgresql-real-world-applications.md) | [DynamoDB Real-World →](./49c-dynamodb-real-world-applications.md)
