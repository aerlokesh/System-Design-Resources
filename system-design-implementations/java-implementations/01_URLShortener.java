import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * URL SHORTENER - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Base62 encoding (a-z, A-Z, 0-9)
 * - Auto-increment ID approach vs Hash-based approach
 * - In-memory storage with TTL (expiration)
 * - Analytics tracking (click count, last accessed)
 * - Collision handling
 * - Thread-safe operations
 * 
 * Real-world examples: bit.ly, tinyurl.com, t.co
 * 
 * Interview talking points:
 * - Read-heavy system (100:1 read/write ratio)
 * - Base62 gives 62^7 = 3.5 trillion unique URLs for 7 chars
 * - Cache popular URLs (80/20 rule)
 * - Database: NoSQL preferred (simple key-value lookup)
 * - 301 (permanent) vs 302 (temporary) redirect trade-offs
 */
class URLShortener {

    // ==================== BASE62 ENCODER ====================
    static class Base62Encoder {
        private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private static final int BASE = 62;

        static String encode(long id) {
            if (id == 0) return String.valueOf(CHARS.charAt(0));
            StringBuilder sb = new StringBuilder();
            while (id > 0) {
                sb.append(CHARS.charAt((int) (id % BASE)));
                id /= BASE;
            }
            return sb.reverse().toString();
        }

        static long decode(String shortCode) {
            long id = 0;
            for (char c : shortCode.toCharArray()) {
                id = id * BASE + CHARS.indexOf(c);
            }
            return id;
        }
    }

    // ==================== URL ENTRY ====================
    static class URLEntry {
        final String longURL;
        final String shortCode;
        final long createdAt;
        final long expiresAt;
        final String userId;
        final AtomicLong clickCount;
        volatile long lastAccessedAt;

        URLEntry(String longURL, String shortCode, long ttlSeconds, String userId) {
            this.longURL = longURL;
            this.shortCode = shortCode;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = ttlSeconds > 0 ? createdAt + (ttlSeconds * 1000) : 0;
            this.userId = userId;
            this.clickCount = new AtomicLong(0);
            this.lastAccessedAt = createdAt;
        }

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }

        void recordAccess() {
            clickCount.incrementAndGet();
            lastAccessedAt = System.currentTimeMillis();
        }
    }

    // ==================== APPROACH 1: AUTO-INCREMENT ID ====================
    static class AutoIncrementShortener {
        private final AtomicLong counter = new AtomicLong(100000);
        private final ConcurrentHashMap<String, URLEntry> shortToLong = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> longToShort = new ConcurrentHashMap<>();

        String shorten(String longURL, long ttlSeconds, String userId) {
            String existing = longToShort.get(longURL);
            if (existing != null && !shortToLong.get(existing).isExpired()) {
                return existing;
            }
            long id = counter.getAndIncrement();
            String shortCode = Base62Encoder.encode(id);
            URLEntry entry = new URLEntry(longURL, shortCode, ttlSeconds, userId);
            shortToLong.put(shortCode, entry);
            longToShort.put(longURL, shortCode);
            return shortCode;
        }

        String resolve(String shortCode) {
            URLEntry entry = shortToLong.get(shortCode);
            if (entry == null) return null;
            if (entry.isExpired()) {
                shortToLong.remove(shortCode);
                longToShort.remove(entry.longURL);
                return null;
            }
            entry.recordAccess();
            return entry.longURL;
        }

        URLEntry getStats(String shortCode) {
            return shortToLong.get(shortCode);
        }
    }

    // ==================== APPROACH 2: HASH-BASED ====================
    static class HashBasedShortener {
        private final ConcurrentHashMap<String, URLEntry> shortToLong = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> longToShort = new ConcurrentHashMap<>();
        private final int codeLength;

        HashBasedShortener(int codeLength) { this.codeLength = codeLength; }

        String shorten(String longURL, long ttlSeconds, String userId) {
            String existing = longToShort.get(longURL);
            if (existing != null && !shortToLong.get(existing).isExpired()) return existing;

            String shortCode = generateHash(longURL);
            int attempt = 0;
            while (shortToLong.containsKey(shortCode) && !shortToLong.get(shortCode).longURL.equals(longURL)) {
                attempt++;
                shortCode = generateHash(longURL + attempt);
            }
            URLEntry entry = new URLEntry(longURL, shortCode, ttlSeconds, userId);
            shortToLong.put(shortCode, entry);
            longToShort.put(longURL, shortCode);
            return shortCode;
        }

        private String generateHash(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) hex.append(String.format("%02x", b));
                long numeric = Math.abs(Long.parseLong(hex.toString().substring(0, 12), 16));
                String encoded = Base62Encoder.encode(numeric);
                return encoded.substring(0, Math.min(codeLength, encoded.length()));
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        String resolve(String shortCode) {
            URLEntry entry = shortToLong.get(shortCode);
            if (entry == null) return null;
            if (entry.isExpired()) { shortToLong.remove(shortCode); longToShort.remove(entry.longURL); return null; }
            entry.recordAccess();
            return entry.longURL;
        }
    }

    // ==================== APPROACH 3: CUSTOM ALIAS ====================
    static class CustomAliasShortener {
        private final AutoIncrementShortener fallback = new AutoIncrementShortener();
        private final ConcurrentHashMap<String, URLEntry> customMappings = new ConcurrentHashMap<>();

        String shorten(String longURL, String customAlias, long ttlSeconds, String userId) {
            if (customAlias != null && !customAlias.isEmpty()) {
                if (customAlias.length() < 3 || customAlias.length() > 16)
                    throw new IllegalArgumentException("Custom alias must be 3-16 characters");
                if (!customAlias.matches("[a-zA-Z0-9_-]+"))
                    throw new IllegalArgumentException("Invalid characters in alias");
                if (customMappings.containsKey(customAlias))
                    throw new IllegalArgumentException("Alias already taken: " + customAlias);
                URLEntry entry = new URLEntry(longURL, customAlias, ttlSeconds, userId);
                customMappings.put(customAlias, entry);
                return customAlias;
            }
            return fallback.shorten(longURL, ttlSeconds, userId);
        }

        String resolve(String shortCode) {
            URLEntry entry = customMappings.get(shortCode);
            if (entry != null) {
                if (entry.isExpired()) { customMappings.remove(shortCode); return null; }
                entry.recordAccess();
                return entry.longURL;
            }
            return fallback.resolve(shortCode);
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== URL SHORTENER - System Design Demo ===\n");

        // Base62 Encoding
        System.out.println("--- 1. Base62 Encoding ---");
        long[] testIds = {1, 62, 1000, 123456, 999999999L};
        for (long id : testIds) {
            String encoded = Base62Encoder.encode(id);
            long decoded = Base62Encoder.decode(encoded);
            System.out.printf("  ID: %-12d -> Base62: %-8s -> Decoded: %d%n", id, encoded, decoded);
        }

        // Auto-Increment
        System.out.println("\n--- 2. Auto-Increment Approach ---");
        AutoIncrementShortener autoShortener = new AutoIncrementShortener();
        String[][] urls = {
            {"https://www.example.com/very/long/path?param=value", "user1"},
            {"https://docs.google.com/document/d/1abc/edit", "user1"},
            {"https://github.com/user/repo/pull/123", "user2"},
        };
        for (String[] u : urls) {
            String code = autoShortener.shorten(u[0], 3600, u[1]);
            System.out.printf("  %s -> %s%n", u[0].substring(0, Math.min(50, u[0].length())), code);
        }
        String dupCode = autoShortener.shorten(urls[0][0], 3600, "user1");
        System.out.printf("  Dedup (same URL): reuses code %s%n", dupCode);

        // Hash-Based
        System.out.println("\n--- 3. Hash-Based Approach ---");
        HashBasedShortener hashShortener = new HashBasedShortener(7);
        for (String[] u : urls) {
            String code = hashShortener.shorten(u[0], 0, u[1]);
            System.out.printf("  %s... -> %s%n", u[0].substring(0, Math.min(40, u[0].length())), code);
        }

        // Custom Alias
        System.out.println("\n--- 4. Custom Alias ---");
        CustomAliasShortener customShortener = new CustomAliasShortener();
        String c1 = customShortener.shorten("https://example.com/portfolio", "my-site", 0, "user1");
        System.out.printf("  Custom: %s -> %s%n", c1, customShortener.resolve(c1));
        String c2 = customShortener.shorten("https://example.com/page", null, 0, "user1");
        System.out.printf("  Auto:   %s -> %s%n", c2, customShortener.resolve(c2));
        try {
            customShortener.shorten("https://other.com", "my-site", 0, "user2");
        } catch (IllegalArgumentException e) {
            System.out.printf("  Duplicate rejected: %s%n", e.getMessage());
        }

        // Concurrent test
        System.out.println("\n--- 5. Concurrent Access ---");
        AutoIncrementShortener concShortener = new AutoIncrementShortener();
        int threads = 10, perThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        Set<String> allCodes = ConcurrentHashMap.newKeySet();
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    allCodes.add(concShortener.shorten("https://example.com/t" + tid + "/p" + i, 3600, "u" + tid));
                }
                latch.countDown();
            }).start();
        }
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("  %d URLs across %d threads in %dms. All unique: %s%n",
                allCodes.size(), threads, ms, allCodes.size() == threads * perThread);

        System.out.println("\n--- Design Trade-offs ---");
        System.out.println("  Auto-Increment: No collisions, simple, but predictable & needs central counter");
        System.out.println("  Hash-Based:     No central state, deterministic, but collisions possible");
        System.out.println("  Custom Alias:   User-friendly, memorable, but namespace management needed");
    }
}
