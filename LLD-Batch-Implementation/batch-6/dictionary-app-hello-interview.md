# Dictionary App - HELLO Interview Framework

> **Companies**: Microsoft, Google  
> **Difficulty**: Medium  
> **Pattern**: Trie (prefix search) + HashMap (O(1) exact lookup)  
> **Time**: 35 minutes

## Understanding the Problem

Store words with their meanings/definitions. Support exact lookup, prefix-based autocomplete, multiple meanings per word, and CRUD. Think: built-in dictionary in Microsoft Word, macOS Dictionary, Google search definitions.

### For L63 Microsoft
1. **Dual data structure**: HashMap<word, DictionaryEntry> for O(1) lookup + Trie for O(L) prefix
2. **Multiple meanings per word**: "abstract" = adjective ("existing in thought") + noun ("a summary")
3. **Part of speech**: noun, verb, adjective stored per entry
4. **Why both?** HashMap can't do prefix search; Trie can't do O(1) exact lookup

---

## Key Design

```
DictionaryEntry — word, List<meanings>, partOfSpeech
DictionaryApp
  - dictionary: HashMap<String, DictionaryEntry>  ← O(1) exact lookup
  - trieRoot: DictTrieNode                        ← O(L) prefix search
  
  addWord(word, partOfSpeech, meaning):
    1. Check if word exists → add meaning to existing entry
    2. If new → create entry, insert into HashMap AND Trie
  
  lookup(word) → DictionaryEntry  — O(1) via HashMap
  searchByPrefix(prefix) → List<String>  — O(L + M) via Trie DFS
  deleteWord(word) — O(1) HashMap remove
  contains(word) — O(1) HashMap containsKey
```

### Why Both HashMap AND Trie?

| Operation | HashMap only | Trie only | Both ✅ |
|-----------|-------------|-----------|--------|
| Exact lookup | O(1) ✅ | O(L) | O(1) |
| Prefix search | O(N) scan ❌ | O(L + M) ✅ | O(L + M) |
| Add word | O(1) | O(L) | O(L) |
| Delete | O(1) | O(L) complex | O(1) |
| Memory | O(N) | O(Σ chars) | O(N + Σ chars) |

**Trade-off**: Slightly more memory (both structures) for optimal time on ALL operations.

### Multiple Meanings

```java
class DictionaryEntry {
    String word;
    String partOfSpeech;  // noun, verb, adjective
    List<String> meanings; // multiple definitions
    
    void addMeaning(String meaning) { meanings.add(meaning); }
}

// "abstract" (adjective):
//   1. Existing in thought but not physical
//   2. A summary of a document
// addWord("abstract", "adjective", "Existing in thought...")
// addWord("abstract", "adjective", "A summary of a document")
// → Same DictionaryEntry, 2 meanings in the List
```

### Deep Dives
- **Fuzzy search**: Levenshtein distance for "did you mean?" suggestions
- **Synonyms/Antonyms**: Graph of word relationships
- **Etymology**: Word origin/history as additional field
- **Offline**: Trie serialization to file for mobile dictionary apps
- **Production**: Elasticsearch with analyzers for stemming, lemmatization

### Complexity

| Operation | Time | Space |
|-----------|------|-------|
| addWord | O(L) trie insert | O(L) per word |
| lookup | O(1) HashMap | O(1) |
| searchByPrefix | O(L + M) | O(M) results |
| deleteWord | O(1) HashMap | O(1) |
| Total | — | O(N entries + Σ word chars for Trie) |

See `DictionaryAppSystem.java` for full implementation with multi-meaning words and prefix search demo.
