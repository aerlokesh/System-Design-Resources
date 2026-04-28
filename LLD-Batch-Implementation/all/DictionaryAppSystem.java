import java.util.*;

/**
 * Dictionary App - Store words and their meanings
 * 
 * Companies: Microsoft, Google
 * Pattern: Trie for prefix search + HashMap for O(1) lookup
 * Difficulty: Medium
 * 
 * Key: Trie enables prefix-based autocomplete, HashMap for exact lookup.
 * Each word can have multiple meanings. Support add, search, delete, prefix search.
 */

class DictionaryEntry {
    private final String word;
    private final List<String> meanings;
    private final String partOfSpeech; // noun, verb, adjective, etc.

    DictionaryEntry(String word, String partOfSpeech) {
        this.word = word.toLowerCase();
        this.meanings = new ArrayList<>();
        this.partOfSpeech = partOfSpeech;
    }

    void addMeaning(String meaning) { meanings.add(meaning); }
    String getWord() { return word; }
    List<String> getMeanings() { return Collections.unmodifiableList(meanings); }
    String getPartOfSpeech() { return partOfSpeech; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  📖 ").append(word).append(" (").append(partOfSpeech).append(")\n");
        for (int i = 0; i < meanings.size(); i++) {
            sb.append("     ").append(i + 1).append(". ").append(meanings.get(i)).append("\n");
        }
        return sb.toString();
    }
}

class DictTrieNode {
    final Map<Character, DictTrieNode> children = new HashMap<>();
    boolean isWord = false;
    String word = null;
}

class DictionaryApp {
    private final Map<String, DictionaryEntry> dictionary = new LinkedHashMap<>();
    private final DictTrieNode trieRoot = new DictTrieNode();

    /** Add a word with meaning */
    void addWord(String word, String partOfSpeech, String meaning) {
        String lower = word.toLowerCase();
        DictionaryEntry entry = dictionary.get(lower);
        if (entry == null) {
            entry = new DictionaryEntry(lower, partOfSpeech);
            dictionary.put(lower, entry);
            insertTrie(lower);
        }
        entry.addMeaning(meaning);
        System.out.println("  ✅ Added: " + lower + " → " + meaning);
    }

    /** Look up a word — O(1) via HashMap */
    DictionaryEntry lookup(String word) {
        return dictionary.get(word.toLowerCase());
    }

    /** Delete a word */
    boolean deleteWord(String word) {
        String lower = word.toLowerCase();
        if (dictionary.remove(lower) != null) {
            System.out.println("  🗑️ Deleted: " + lower);
            return true;
        }
        System.out.println("  ✗ Not found: " + lower);
        return false;
    }

    /** Prefix search — find all words starting with prefix (via Trie) */
    List<String> searchByPrefix(String prefix) {
        String lower = prefix.toLowerCase();
        DictTrieNode node = trieRoot;
        for (char c : lower.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        collectWords(node, results);
        return results;
    }

    /** Check if word exists */
    boolean contains(String word) { return dictionary.containsKey(word.toLowerCase()); }

    int size() { return dictionary.size(); }

    // ─── Trie operations ───
    private void insertTrie(String word) {
        DictTrieNode node = trieRoot;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new DictTrieNode());
        }
        node.isWord = true;
        node.word = word;
    }

    private void collectWords(DictTrieNode node, List<String> results) {
        if (node.isWord) results.add(node.word);
        for (DictTrieNode child : node.children.values()) collectWords(child, results);
    }

    /** Display all words */
    String displayAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("  📚 Dictionary (").append(dictionary.size()).append(" words)\n");
        for (DictionaryEntry e : dictionary.values()) sb.append(e);
        return sb.toString();
    }
}

public class DictionaryAppSystem {
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  Dictionary App - Trie + HashMap Lookup    ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        DictionaryApp dict = new DictionaryApp();

        // ── Add words ──
        System.out.println("━━━ Adding words ━━━");
        dict.addWord("Algorithm", "noun", "A step-by-step procedure for solving a problem");
        dict.addWord("Algorithm", "noun", "A set of rules for calculation");
        dict.addWord("Array", "noun", "An ordered collection of elements");
        dict.addWord("Abstract", "adjective", "Existing in thought but not physical");
        dict.addWord("Abstract", "adjective", "A summary of a document");
        dict.addWord("Binary", "adjective", "Composed of two parts");
        dict.addWord("Binary", "noun", "A number system using only 0 and 1");
        dict.addWord("Cache", "noun", "A temporary storage for fast access");
        dict.addWord("Compile", "verb", "To translate source code into machine code");
        dict.addWord("Debug", "verb", "To find and fix errors in code");
        System.out.println();

        // ── Lookup ──
        System.out.println("━━━ Lookup ━━━");
        DictionaryEntry entry = dict.lookup("algorithm");
        if (entry != null) System.out.println(entry);
        entry = dict.lookup("binary");
        if (entry != null) System.out.println(entry);
        System.out.println("  lookup('xyz') = " + dict.lookup("xyz"));
        System.out.println();

        // ── Prefix search ──
        System.out.println("━━━ Prefix search ━━━");
        System.out.println("  Prefix 'a': " + dict.searchByPrefix("a"));
        System.out.println("  Prefix 'ab': " + dict.searchByPrefix("ab"));
        System.out.println("  Prefix 'b': " + dict.searchByPrefix("b"));
        System.out.println("  Prefix 'co': " + dict.searchByPrefix("co"));
        System.out.println("  Prefix 'xyz': " + dict.searchByPrefix("xyz"));
        System.out.println();

        // ── Delete ──
        System.out.println("━━━ Delete ━━━");
        dict.deleteWord("cache");
        System.out.println("  contains('cache') = " + dict.contains("cache"));
        System.out.println("  Size: " + dict.size());
        System.out.println();

        // ── Display all ──
        System.out.println("━━━ All entries ━━━");
        System.out.println(dict.displayAll());

        System.out.println("✅ All Dictionary scenarios complete.");
    }
}
