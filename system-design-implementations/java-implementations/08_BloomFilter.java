import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * BLOOM FILTER - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Probabilistic data structure for membership testing
 * - Multiple hash functions setting bits in a bit array
 * - False positives possible, false negatives impossible
 * - Space-efficient compared to HashSet
 * - Counting Bloom Filter for deletions
 * 
 * Interview talking points:
 * - Used by: Cassandra (SSTable lookup), Chrome (malicious URLs),
 *   Medium (article recommendations), Akamai (cache)
 * - False positive rate: (1 - e^(-kn/m))^k
 *   k=hash functions, n=elements, m=bit array size
 * - Optimal k = (m/n) * ln(2)
 * - Cannot remove elements (standard BF); use Counting BF
 * - Space: ~10 bits per element for 1% FP rate
 */
class BloomFilter {

    // ==================== STANDARD BLOOM FILTER ====================
    static class StandardBloomFilter {
        private final boolean[] bits;
        private final int size;
        private final int numHashFunctions;
        private int insertedCount;

        StandardBloomFilter(int expectedElements, double falsePositiveRate) {
            // Optimal size: m = -n*ln(p) / (ln2)^2
            this.size = optimalSize(expectedElements, falsePositiveRate);
            // Optimal hash functions: k = (m/n) * ln(2)
            this.numHashFunctions = optimalHashCount(size, expectedElements);
            this.bits = new boolean[size];
            this.insertedCount = 0;
        }

        void add(String element) {
            for (int i = 0; i < numHashFunctions; i++) {
                int hash = getHash(element, i);
                bits[hash] = true;
            }
            insertedCount++;
        }

        boolean mightContain(String element) {
            for (int i = 0; i < numHashFunctions; i++) {
                int hash = getHash(element, i);
                if (!bits[hash]) return false;
            }
            return true; // Might be a false positive
        }

        double expectedFalsePositiveRate() {
            // (1 - e^(-kn/m))^k
            double exponent = -1.0 * numHashFunctions * insertedCount / size;
            return Math.pow(1 - Math.exp(exponent), numHashFunctions);
        }

        int getBitCount() {
            int count = 0;
            for (boolean b : bits) if (b) count++;
            return count;
        }

        double fillRatio() { return (double) getBitCount() / size; }
        int getSize() { return size; }
        int getHashCount() { return numHashFunctions; }
        int getInsertedCount() { return insertedCount; }

        private int getHash(String element, int seed) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update((element + seed).getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();
                int hash = 0;
                for (int i = 0; i < 4; i++) {
                    hash = (hash << 8) | (digest[i] & 0xFF);
                }
                return Math.abs(hash) % size;
            } catch (Exception e) { throw new RuntimeException(e); }
        }

        private static int optimalSize(int n, double p) {
            return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        }

        private static int optimalHashCount(int m, int n) {
            return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
        }
    }

    // ==================== COUNTING BLOOM FILTER ====================
    /**
     * Uses counters instead of bits, allowing removal of elements.
     * Trade-off: Uses more memory (4 bytes per counter vs 1 bit).
     */
    static class CountingBloomFilter {
        private final int[] counters;
        private final int size;
        private final int numHashFunctions;

        CountingBloomFilter(int size, int numHashFunctions) {
            this.size = size;
            this.numHashFunctions = numHashFunctions;
            this.counters = new int[size];
        }

        void add(String element) {
            for (int i = 0; i < numHashFunctions; i++) {
                int hash = getHash(element, i);
                counters[hash]++;
            }
        }

        void remove(String element) {
            if (!mightContain(element)) return;
            for (int i = 0; i < numHashFunctions; i++) {
                int hash = getHash(element, i);
                if (counters[hash] > 0) counters[hash]--;
            }
        }

        boolean mightContain(String element) {
            for (int i = 0; i < numHashFunctions; i++) {
                int hash = getHash(element, i);
                if (counters[hash] == 0) return false;
            }
            return true;
        }

        private int getHash(String element, int seed) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update((element + seed).getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();
                int hash = 0;
                for (int i = 0; i < 4; i++) hash = (hash << 8) | (digest[i] & 0xFF);
                return Math.abs(hash) % size;
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) {
        System.out.println("=== BLOOM FILTER - System Design Demo ===\n");

        // 1. Basic Bloom Filter
        System.out.println("--- 1. Standard Bloom Filter ---");
        StandardBloomFilter bf = new StandardBloomFilter(1000, 0.01);
        System.out.printf("  Config: size=%d bits, hash functions=%d%n", bf.getSize(), bf.getHashCount());
        System.out.printf("  Memory: %.1f KB (vs HashSet ~40 KB for 1000 strings)%n", bf.getSize() / 8.0 / 1024);

        // Add elements
        String[] emails = {"alice@test.com", "bob@test.com", "charlie@test.com"};
        for (String email : emails) bf.add(email);

        // Check membership
        System.out.println("\n  Membership checks:");
        for (String email : emails) {
            System.out.printf("    %s: %s (inserted)%n", email, bf.mightContain(email) ? "PROBABLY YES" : "DEFINITELY NO");
        }
        System.out.printf("    unknown@test.com: %s%n",
                bf.mightContain("unknown@test.com") ? "PROBABLY YES (false positive!)" : "DEFINITELY NO");

        // 2. False positive rate measurement
        System.out.println("\n--- 2. False Positive Rate Analysis ---");
        int[] sizes = {100, 1000, 10000};
        double[] fpRates = {0.1, 0.01, 0.001};

        for (double targetFP : fpRates) {
            StandardBloomFilter testBF = new StandardBloomFilter(10000, targetFP);
            // Insert 10000 elements
            for (int i = 0; i < 10000; i++) testBF.add("element-" + i);

            // Test 10000 non-existing elements
            int falsePositives = 0;
            for (int i = 10000; i < 20000; i++) {
                if (testBF.mightContain("element-" + i)) falsePositives++;
            }
            double actualFP = falsePositives / 10000.0;
            System.out.printf("  Target FP=%.3f: bits=%d, hashes=%d, actual FP=%.4f, fill=%.1f%%%n",
                    targetFP, testBF.getSize(), testBF.getHashCount(), actualFP, testBF.fillRatio() * 100);
        }

        // 3. Space comparison
        System.out.println("\n--- 3. Space Efficiency ---");
        int numElements = 1_000_000;
        StandardBloomFilter largeBF = new StandardBloomFilter(numElements, 0.01);
        double bfMemoryKB = largeBF.getSize() / 8.0 / 1024;
        double hashSetMemoryKB = numElements * 50.0 / 1024; // ~50 bytes per entry estimate
        System.out.printf("  For %d elements (1%% FP):%n", numElements);
        System.out.printf("    Bloom Filter: %.0f KB (%.1f MB)%n", bfMemoryKB, bfMemoryKB / 1024);
        System.out.printf("    HashSet:      %.0f KB (%.1f MB)%n", hashSetMemoryKB, hashSetMemoryKB / 1024);
        System.out.printf("    Savings:      %.0fx less memory%n", hashSetMemoryKB / bfMemoryKB);

        // 4. Counting Bloom Filter (with deletion)
        System.out.println("\n--- 4. Counting Bloom Filter (supports deletion) ---");
        CountingBloomFilter cbf = new CountingBloomFilter(10000, 7);
        cbf.add("item-A");
        cbf.add("item-B");
        cbf.add("item-C");

        System.out.printf("  A: %s, B: %s, C: %s%n",
                cbf.mightContain("item-A"), cbf.mightContain("item-B"), cbf.mightContain("item-C"));

        cbf.remove("item-B");
        System.out.printf("  After removing B:%n");
        System.out.printf("  A: %s, B: %s, C: %s%n",
                cbf.mightContain("item-A"), cbf.mightContain("item-B"), cbf.mightContain("item-C"));

        // 5. Real-world use cases
        System.out.println("\n--- 5. Use Case: Username Availability Check ---");
        StandardBloomFilter usernameBF = new StandardBloomFilter(100000, 0.001);
        String[] takenUsernames = {"john", "alice", "admin", "root", "test"};
        for (String u : takenUsernames) usernameBF.add(u);

        String[] checkUsernames = {"john", "newuser123", "alice", "bob2024"};
        for (String u : checkUsernames) {
            boolean taken = usernameBF.mightContain(u);
            System.out.printf("  '%s': %s%n", u,
                    taken ? "MIGHT BE TAKEN (check DB)" : "DEFINITELY AVAILABLE (skip DB)");
        }
        System.out.println("  -> Bloom filter saves DB lookups for definitely-available names");

        // 6. Bloom filter as cache miss preventer
        System.out.println("\n--- 6. Use Case: Cache Miss Prevention ---");
        StandardBloomFilter cacheBF = new StandardBloomFilter(100000, 0.01);
        // Simulate adding all known keys
        for (int i = 0; i < 1000; i++) cacheBF.add("product:" + i);

        int dbQueriesSaved = 0, totalQueries = 0;
        for (int i = 0; i < 2000; i++) {
            String key = "product:" + i;
            totalQueries++;
            if (!cacheBF.mightContain(key)) {
                dbQueriesSaved++; // Definitely not in DB, skip query
            }
        }
        System.out.printf("  Total queries: %d, DB queries saved: %d (%.1f%%)%n",
                totalQueries, dbQueriesSaved, dbQueriesSaved * 100.0 / totalQueries);

        // Summary
        System.out.println("\n--- Properties ---");
        System.out.println("  False Positive:  Possible (says yes when answer is no)");
        System.out.println("  False Negative:  IMPOSSIBLE (never says no when answer is yes)");
        System.out.println("  Add:             O(k) where k = number of hash functions");
        System.out.println("  Query:           O(k)");
        System.out.println("  Delete:          Not supported (use Counting BF)");
        System.out.println("  Space:           ~10 bits/element for 1% FP rate");
    }
}
