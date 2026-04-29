# Dictionary App - HELLO Interview Framework

> **Companies**: Microsoft, Google  
> **Difficulty**: Medium  
> **Pattern**: Trie (prefix autocomplete) + HashMap (O(1) exact lookup)  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Dictionary App?

A system that stores words and their meanings/definitions. Users can look up a word to get its definition, search by prefix (autocomplete as they type), and manage entries (add, update, delete). Think: built-in Dictionary in macOS, Microsoft Word spell-check, Google "define:" search.

**Real-world**: Oxford English Dictionary has ~600,000 words. Merriam-Webster API handles millions of lookups daily. Dictionary.com serves 70M monthly users.

### For L63 Microsoft Interview

This tests your ability to choose the **right data structure for each operation**:
1. **Exact lookup by word**: HashMap → O(1)
2. **Prefix search / autocomplete**: Trie → O(L) navigate + O(M) collect
3. **Why both?** HashMap can't do prefix search; Trie can't do O(1) by exact word
4. **Multiple meanings per word**: `List<String>` in each DictionaryEntry
5. **Part of speech**: Noun, verb, adjective — stored per entry

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "Multiple meanings per word?" → Yes ("run" has 100+ meanings in English)
- "Prefix autocomplete?" → Yes (type "abs" → "abstract", "absorb", "absolute")
- "Case sensitive?" → Case-insensitive (store lowercase)
- "Delete a specific meaning or whole word?" → Whole word for P0

### Functional Requirements (P0)
1. **addWord(word, partOfSpeech, meaning)** — add word or add meaning to existing word
2. **lookup(word)** → DictionaryEntry with all meanings — O(1)
3. **searchByPrefix(prefix)** → list of matching words — O(L + M)
4. **deleteWord(word)** — remove word and all meanings — O(1)
5. **contains(word)** — check if word exists — O(1)
6. Case-insensitive operations

### Nice to Have (P1)
- Synonyms and antonyms (graph of word relationships)
- Etymology (word origin)
- Usage examples ("The algorithm was efficient.")
- Pronunciation guide
- Fuzzy search ("did you mean?")

### Non-Functional
- O(1) exact lookup (critical for spell-check)
- O(L) prefix navigation (critical for autocomplete)
- Handle 600K+ words efficiently
- Memory: O(N entries + Σ word characters for Trie)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌──────────────────────────────────────────────────────┐
│                   DictionaryApp                      │
│  - dictionary: HashMap<String, DictionaryEntry>      │
│  - trieRoot: DictTrieNode                            │
│  - addWord(word, pos, meaning)                       │
│  - lookup(word) → DictionaryEntry                    │
│  - searchByPrefix(prefix) → List<String>             │
│  - deleteWord(word) → boolean                        │
│  - contains(word) → boolean                          │
│  - size() → int                                      │
└──────────────────────┬───────────────────────────────┘
                       │ stores
                       ▼
┌──────────────────────────────────────────────────────┐
│                DictionaryEntry                       │
│  - word: String                                      │
│  - partOfSpeech: String (noun, verb, adjective)      │
│  - meanings: List<String> (multiple definitions!)     │
│  - addMeaning(String): void                          │
└──────────────────────────────────────────────────────┘
                       ▲
                       │ referenced via Trie
┌──────────────────────────────────────────────────────┐
│                  DictTrieNode                        │
│  - children: Map<Character, DictTrieNode>            │
│  - isWord: boolean                                   │
│  - word: String (stored at terminal node)            │
└──────────────────────────────────────────────────────┘
```

### Class: DictionaryEntry
| Attribute | Type | Description |
|-----------|------|-------------|
| word | String | Lowercase word |
| partOfSpeech | String | "noun", "verb", "adjective" |
| meanings | List<String> | Multiple definitions |

**Why List<String> for meanings?** The word "run" has 100+ meanings in English. Even "set" has 464 definitions in the OED. One word = many meanings is the norm, not the exception.

### Class: DictTrieNode
| Attribute | Type | Description |
|-----------|------|-------------|
| children | Map<Character, DictTrieNode> | One child per character |
| isWord | boolean | True if a complete word ends here |
| word | String | The full word (stored only at terminal nodes) |

---

## 3️⃣ API Design

```java
class DictionaryApp {
    /** Add word with meaning. If word exists, adds new meaning to existing entry. */
    void addWord(String word, String partOfSpeech, String meaning);
    
    /** Exact lookup — O(1) via HashMap */
    DictionaryEntry lookup(String word);
    
    /** Prefix search — O(L + M) via Trie. Returns matching word strings. */
    List<String> searchByPrefix(String prefix);
    
    /** Delete word and all its meanings — O(1) HashMap remove */
    boolean deleteWord(String word);
    
    /** Check existence — O(1) */
    boolean contains(String word);
    
    /** Total words in dictionary */
    int size();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Add Word "Algorithm"

```
addWord("Algorithm", "noun", "A step-by-step procedure for solving a problem")
  │
  ├─ lower = "algorithm"
  ├─ dictionary.get("algorithm") → null (new word)
  ├─ Create DictionaryEntry("algorithm", "noun")
  │   └─ meanings = ["A step-by-step procedure for solving a problem"]
  ├─ dictionary.put("algorithm", entry)                     ← O(1) HashMap
  └─ insertTrie("algorithm")                                ← O(L) Trie
      root → 'a' → 'l' → 'g' → 'o' → 'r' → 'i' → 't' → 'h' → 'm'
      terminal: isWord=true, word="algorithm"
```

### Scenario 2: Add Second Meaning

```
addWord("Algorithm", "noun", "A set of rules for calculation")
  │
  ├─ lower = "algorithm"
  ├─ dictionary.get("algorithm") → EXISTS!
  ├─ entry.addMeaning("A set of rules for calculation")
  │   └─ meanings = ["A step-by-step...", "A set of rules..."]
  └─ No Trie insert needed (word already in Trie)
```

### Scenario 3: Prefix Search "ab"

```
searchByPrefix("ab")
  │
  ├─ Navigate Trie: root → 'a' → 'b'  (2 steps = O(L))
  │
  ├─ DFS from 'b' node:
  │   ├─ 'b' → 's' → 't' → 'r' → 'a' → 'c' → 't'
  │   │     terminal: word = "abstract" ✓
  │   └─ 'b' → 's' → 'o' → 'r' → 'b'  
  │         terminal: word = "absorb" ✓ (if added)
  │
  └─ Return: ["abstract", "absorb"]
```

### Scenario 4: Lookup "binary"

```
lookup("binary")
  │
  ├─ lower = "binary"
  ├─ dictionary.get("binary") → DictionaryEntry
  └─ Return:
      📖 binary (adjective)
         1. Composed of two parts
      📖 binary (noun)  
         2. A number system using only 0 and 1
```

---

## 5️⃣ Design + Implementation

### Why Both HashMap AND Trie?

| Operation | HashMap Only | Trie Only | Both ✅ |
|-----------|-------------|-----------|--------|
| Exact lookup | O(1) ✅ | O(L) | **O(1)** |
| Prefix search | O(N) scan ❌ | O(L + M) ✅ | **O(L + M)** |
| Add word | O(1) | O(L) | O(L) |
| Delete word | O(1) | O(L) complex | **O(1)** |
| Contains | O(1) | O(L) | **O(1)** |

**Trade-off**: Slightly more memory (both structures stored) for **optimal time on ALL operations**.

### Core Implementation

```java
class DictionaryApp {
    private final Map<String, DictionaryEntry> dictionary = new LinkedHashMap<>();
    private final DictTrieNode trieRoot = new DictTrieNode();

    void addWord(String word, String partOfSpeech, String meaning) {
        String lower = word.toLowerCase();
        DictionaryEntry entry = dictionary.get(lower);
        
        if (entry == null) {
            // New word — create entry + insert into both structures
            entry = new DictionaryEntry(lower, partOfSpeech);
            dictionary.put(lower, entry);
            insertTrie(lower);
        }
        
        entry.addMeaning(meaning);  // add meaning (works for new or existing)
    }

    DictionaryEntry lookup(String word) {
        return dictionary.get(word.toLowerCase());  // O(1)!
    }

    List<String> searchByPrefix(String prefix) {
        // Step 1: Navigate Trie to prefix node — O(L)
        String lower = prefix.toLowerCase();
        DictTrieNode node = trieRoot;
        for (char c : lower.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return Collections.emptyList();
        }
        
        // Step 2: DFS to collect all words under prefix — O(M)
        List<String> results = new ArrayList<>();
        collectWords(node, results);
        return results;
    }

    boolean deleteWord(String word) {
        return dictionary.remove(word.toLowerCase()) != null;  // O(1)
        // Note: lazy Trie delete — word stays in Trie but lookup returns null
    }

    boolean contains(String word) {
        return dictionary.containsKey(word.toLowerCase());  // O(1)
    }

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
        for (DictTrieNode child : node.children.values()) {
            collectWords(child, results);
        }
    }
}
```

### DictionaryEntry with Multiple Meanings

```java
class DictionaryEntry {
    private final String word;
    private final List<String> meanings;
    private final String partOfSpeech;

    DictionaryEntry(String word, String partOfSpeech) {
        this.word = word.toLowerCase();
        this.meanings = new ArrayList<>();
        this.partOfSpeech = partOfSpeech;
    }

    void addMeaning(String meaning) { meanings.add(meaning); }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 ").append(word).append(" (").append(partOfSpeech).append(")\n");
        for (int i = 0; i < meanings.size(); i++) {
            sb.append("   ").append(i + 1).append(". ").append(meanings.get(i)).append("\n");
        }
        return sb.toString();
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Trie vs Sorted Array vs HashMap for Prefix Search

| Structure | Prefix search | Exact lookup | Insert | Space |
|-----------|--------------|-------------|--------|-------|
| **Trie** | O(L + M) ✅ | O(L) | O(L) | O(Σ chars) |
| Sorted Array + Binary Search | O(L log N + M) | O(log N) | O(N) shift | O(N) |
| HashMap (brute scan) | O(N × L) ❌ | O(1) | O(1) | O(N) |
| Inverted Index | O(1) per prefix | O(1) | O(L) | O(N × K) |

Trie is the standard for prefix-based autocomplete. Sorted array works for static dictionaries.

### Deep Dive 2: Lazy vs Eager Trie Delete

**Lazy delete** (our approach): Remove from HashMap, leave in Trie.
- Pros: O(1), simple, no complex Trie traversal
- Cons: Stale entries in Trie (wastes memory over time)
- Solution: Periodic Trie rebuild (offline, batch)

**Eager delete**: Walk Trie backward, remove nodes with no other children.
```java
// Complex: must check if each parent node has other children
// If parent has only this branch → delete parent too
// If parent has other branches → only unset isWord flag
```

For interview: mention both, implement lazy. "In production, I'd add periodic compaction."

### Deep Dive 3: Fuzzy Search ("Did You Mean?")

```
User types: "algorthm" (misspelled)
System suggests: "algorithm" (Levenshtein distance = 1)

Levenshtein distance = minimum edits (insert, delete, replace) to transform one string into another:
  "algorthm" → "algorithm"  = 1 edit (insert 'i')

Implementation: for each word in dictionary, compute distance. Return words with distance ≤ 2.
Optimization: BK-tree (Burkhard-Keller tree) for efficient nearest-neighbor in edit distance space.
```

### Deep Dive 4: Compressed Trie (Patricia Trie)

Standard Trie wastes nodes for single-child chains:
```
Standard:  a → l → g → o → r → i → t → h → m     (9 nodes for "algorithm")
Compressed: "algorithm"                              (1 node!)

Savings: 8 fewer TrieNode objects
Used by: IP routing tables (radix tree), Solr/Lucene inverted indexes
```

### Deep Dive 5: Production Dictionary System

| Feature | Interview | Production |
|---------|-----------|------------|
| Storage | In-memory HashMap + Trie | Elasticsearch + Redis cache |
| Fuzzy search | Levenshtein (mention) | Elasticsearch fuzzy query |
| Autocomplete | Trie DFS | Elasticsearch completion suggester |
| Multi-language | Single Trie | Separate index per language |
| Stemming | Not needed | Porter Stemmer ("running" → "run") |
| Synonyms | P1 graph | WordNet integration |

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Add same word twice | Adds new meaning to existing entry (List.add) |
| Lookup non-existent word | Returns null |
| Delete non-existent word | Returns false |
| Empty prefix search | Returns empty list |
| Case sensitivity | All keys lowercased |
| Word with special characters | Allowed (stored as-is after lowering) |
| Very long word (50+ chars) | Trie handles fine, O(50) insert |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| addWord | O(L) Trie insert | O(L) new Trie nodes + O(1) HashMap |
| lookup | O(1) HashMap | — |
| searchByPrefix | O(L + M) | O(M) results |
| deleteWord | O(1) HashMap | O(1) |
| contains | O(1) HashMap | — |
| Total space | — | O(N entries + Σ character nodes in Trie) |

Where L = word length, M = matching words, N = total words

---

## 📋 Interview Checklist (L63)

- [ ] **Dual structure**: HashMap for O(1) exact + Trie for O(L) prefix — explain why both
- [ ] **Multiple meanings**: List<String> per entry, addMeaning accumulates
- [ ] **Trie implementation**: Insert with computeIfAbsent, DFS for prefix collection
- [ ] **Case insensitive**: Lowercase all keys
- [ ] **Lazy Trie delete**: Remove from HashMap, mention Trie compaction as production improvement
- [ ] **Fuzzy search**: Mention Levenshtein distance for "did you mean?"
- [ ] **Compressed Trie**: Mention Patricia Trie for space optimization
- [ ] **Production**: Elasticsearch, stemming, multi-language

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Entry, Trie) | 5 min |
| Implementation (HashMap + Trie) | 15 min |
| Deep Dives (fuzzy, compressed, production) | 10 min |
| **Total** | **~35 min** |

See `DictionaryAppSystem.java` for full implementation with multi-meaning words and prefix search demo.
