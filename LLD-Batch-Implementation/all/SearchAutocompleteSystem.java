import java.util.*;

/**
 * Google Search Autocomplete - HELLO Interview Framework
 * 
 * Companies: Google, Amazon, Microsoft +3 more
 * Pattern: Trie (Prefix Tree) + Priority Queue for top-K
 * Difficulty: Medium
 * 
 * Key Design Decisions:
 * 1. Trie — O(L) prefix lookup where L = prefix length
 * 2. Frequency tracking — each word has a hit count
 * 3. Top-K results — PriorityQueue (min-heap) to get top K by frequency
 * 4. DFS from prefix node to collect all matching words
 */

// ==================== Trie Node ====================

class TrieNode {
    final Map<Character, TrieNode> children = new HashMap<>();
    boolean isEndOfWord = false;
    String word = null;     // stored at terminal node
    int frequency = 0;      // search frequency / popularity

    TrieNode getOrCreate(char c) {
        return children.computeIfAbsent(c, k -> new TrieNode());
    }

    TrieNode get(char c) { return children.get(c); }
}

// ==================== Autocomplete Result ====================

class AutocompleteResult implements Comparable<AutocompleteResult> {
    final String word;
    final int frequency;

    AutocompleteResult(String word, int frequency) {
        this.word = word; this.frequency = frequency;
    }

    @Override
    public int compareTo(AutocompleteResult o) {
        // Higher frequency first, then alphabetical
        int cmp = Integer.compare(o.frequency, this.frequency);
        return cmp != 0 ? cmp : this.word.compareTo(o.word);
    }

    @Override
    public String toString() {
        return word + " (" + frequency + ")";
    }
}

// ==================== Autocomplete Service ====================

class AutocompleteService {
    private final TrieNode root = new TrieNode();
    private final int defaultTopK;

    AutocompleteService(int defaultTopK) { this.defaultTopK = defaultTopK; }
    AutocompleteService() { this(5); }

    /** Add a word with initial frequency */
    void addWord(String word, int frequency) {
        if (word == null || word.isEmpty()) return;
        String lower = word.toLowerCase();
        TrieNode node = root;
        for (char c : lower.toCharArray()) {
            node = node.getOrCreate(c);
        }
        node.isEndOfWord = true;
        node.word = lower;
        node.frequency += frequency;
    }

    /** Add a word with default frequency 1 */
    void addWord(String word) { addWord(word, 1); }

    /** Record a search (increment frequency) */
    void recordSearch(String word) {
        addWord(word, 1); // creates if not exists, increments if exists
    }

    /** Get top-K autocomplete suggestions for prefix */
    List<AutocompleteResult> autocomplete(String prefix) {
        return autocomplete(prefix, defaultTopK);
    }

    List<AutocompleteResult> autocomplete(String prefix, int topK) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase();

        // Navigate to prefix node
        TrieNode node = root;
        for (char c : lower.toCharArray()) {
            node = node.get(c);
            if (node == null) return Collections.emptyList(); // no matches
        }

        // DFS to collect all words under this prefix
        List<AutocompleteResult> allMatches = new ArrayList<>();
        collectWords(node, allMatches);

        // Sort by frequency (desc) then alphabetical, return top K
        Collections.sort(allMatches);
        return allMatches.subList(0, Math.min(topK, allMatches.size()));
    }

    /** DFS to collect all terminal nodes under a trie node */
    private void collectWords(TrieNode node, List<AutocompleteResult> results) {
        if (node.isEndOfWord) {
            results.add(new AutocompleteResult(node.word, node.frequency));
        }
        for (TrieNode child : node.children.values()) {
            collectWords(child, results);
        }
    }

    /** Check if a word exists in the trie */
    boolean contains(String word) {
        if (word == null) return false;
        TrieNode node = root;
        for (char c : word.toLowerCase().toCharArray()) {
            node = node.get(c);
            if (node == null) return false;
        }
        return node.isEndOfWord;
    }

    /** Delete a word from the trie */
    boolean delete(String word) {
        if (word == null) return false;
        TrieNode node = root;
        for (char c : word.toLowerCase().toCharArray()) {
            node = node.get(c);
            if (node == null) return false;
        }
        if (node.isEndOfWord) {
            node.isEndOfWord = false;
            node.word = null;
            node.frequency = 0;
            return true;
        }
        return false;
    }

    /** Count total words in trie */
    int size() {
        return countWords(root);
    }

    private int countWords(TrieNode node) {
        int count = node.isEndOfWord ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            count += countWords(child);
        }
        return count;
    }
}

// ==================== Main Demo ====================

public class SearchAutocompleteSystem {
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Search Autocomplete - Trie + Top-K by Frequency   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        AutocompleteService service = new AutocompleteService(5);

        // ── Setup: Add words with frequencies ──
        System.out.println("━━━ Setup: Adding search terms ━━━");
        String[][] terms = {
            {"amazon", "1000"}, {"amazon prime", "800"}, {"amazon web services", "600"},
            {"apple", "900"}, {"apple store", "500"}, {"apple music", "400"},
            {"android", "700"}, {"angular", "300"}, {"ansible", "200"},
            {"google", "1200"}, {"google maps", "900"}, {"google drive", "700"},
            {"gmail", "800"}, {"github", "600"}, {"git", "500"},
            {"java", "1100"}, {"javascript", "1000"}, {"java stream", "400"},
            {"json", "300"}, {"junit", "200"},
        };
        for (String[] t : terms) service.addWord(t[0], Integer.parseInt(t[1]));
        System.out.println("  Added " + service.size() + " terms\n");

        // ── Scenario 1: Basic autocomplete ──
        System.out.println("━━━ Scenario 1: Autocomplete for 'a' (top 5) ━━━");
        printResults("a", service.autocomplete("a"));

        System.out.println("━━━ Autocomplete for 'am' ━━━");
        printResults("am", service.autocomplete("am"));

        System.out.println("━━━ Autocomplete for 'app' ━━━");
        printResults("app", service.autocomplete("app"));

        // ── Scenario 2: Google-related ──
        System.out.println("━━━ Scenario 2: Autocomplete for 'g' ━━━");
        printResults("g", service.autocomplete("g"));

        System.out.println("━━━ Autocomplete for 'go' ━━━");
        printResults("go", service.autocomplete("go"));

        // ── Scenario 3: Java-related ──
        System.out.println("━━━ Scenario 3: Autocomplete for 'ja' ━━━");
        printResults("ja", service.autocomplete("ja"));

        // ── Scenario 4: No matches ──
        System.out.println("━━━ Scenario 4: No matches ('xyz') ━━━");
        printResults("xyz", service.autocomplete("xyz"));

        // ── Scenario 5: Record searches (frequency update) ──
        System.out.println("━━━ Scenario 5: Frequency update via search recording ━━━");
        System.out.println("  Before: ");
        printResults("an", service.autocomplete("an", 3));

        // Simulate users searching "ansible" many times
        for (int i = 0; i < 500; i++) service.recordSearch("ansible");
        System.out.println("  After 500 'ansible' searches:");
        printResults("an", service.autocomplete("an", 3));

        // ── Scenario 6: Case insensitivity ──
        System.out.println("━━━ Scenario 6: Case insensitive ━━━");
        printResults("JAVA", service.autocomplete("JAVA"));
        printResults("Google", service.autocomplete("Google"));

        // ── Scenario 7: Contains and Delete ──
        System.out.println("━━━ Scenario 7: Contains and Delete ━━━");
        System.out.println("  contains('amazon') = " + service.contains("amazon"));
        System.out.println("  contains('facebook') = " + service.contains("facebook"));
        System.out.println("  delete('amazon prime')");
        service.delete("amazon prime");
        System.out.println("  contains('amazon prime') = " + service.contains("amazon prime"));
        System.out.println("  Autocomplete 'amazon' after delete:");
        printResults("amazon", service.autocomplete("amazon"));

        // ── Scenario 8: Single character ──
        System.out.println("━━━ Scenario 8: Single char 'j' ━━━");
        printResults("j", service.autocomplete("j"));

        // ── Scenario 9: Full word match ──
        System.out.println("━━━ Scenario 9: Exact word 'google maps' ━━━");
        printResults("google maps", service.autocomplete("google maps"));

        System.out.println("✅ All Autocomplete scenarios complete.");
    }

    static void printResults(String prefix, List<AutocompleteResult> results) {
        System.out.println("  \"" + prefix + "\" → " + results.size() + " results:");
        for (int i = 0; i < results.size(); i++) {
            System.out.println("    " + (i + 1) + ". " + results.get(i));
        }
        System.out.println();
    }
}
