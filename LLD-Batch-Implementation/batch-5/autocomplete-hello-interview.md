# Google Search Autocomplete - HELLO Interview Framework

> **Companies**: Google, Amazon, Microsoft +3 more  
> **Difficulty**: Medium  
> **Pattern**: Trie (Prefix Tree) + Top-K by Frequency  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [Data Flow](#4️⃣-data-flow)
5. [Design + Implementation](#5️⃣-design--implementation)
6. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is Autocomplete?

User types "ama" → system suggests ["amazon", "amazon prime", "amazon web services"] ranked by popularity/frequency. Google processes **8.5 billion searches per day**. Autocomplete saves ~200ms per query × billions = massive efficiency.

### For L63 Microsoft

1. **Trie**: O(L) prefix navigation where L = prefix length
2. **Frequency ranking**: Not just any match — MOST POPULAR matches first  
3. **Top-K**: Return only top K results, not all matches
4. **Production discussion**: Pre-computed top-K per node, Redis caching, offline MapReduce

---

## 1️⃣ Requirements

### Functional (P0)
1. Add words with frequency/popularity scores
2. `autocomplete(prefix, K)` → top K suggestions ranked by frequency
3. `recordSearch(word)` → increment frequency (learning from usage)
4. Case-insensitive search
5. Delete word

### Non-Functional
- Navigate to prefix: O(L) where L = prefix length
- Collect matches: O(M) where M = total words under prefix
- Return top K: O(M log M) sort (or O(M log K) with heap)

---

## 2️⃣ Core Entities

```
TrieNode
  - children: Map<Character, TrieNode>   ← HashMap for O(1) child lookup
  - isEndOfWord: boolean
  - word: String (stored at terminal node)
  - frequency: int (search popularity)

AutocompleteService
  - root: TrieNode
  - addWord(word, frequency)
  - recordSearch(word)               ← increments frequency
  - autocomplete(prefix, topK) → List<AutocompleteResult>
  - contains(word), delete(word)
```

---

## 5️⃣ Design + Implementation

### Trie Insert: O(L)

```java
void addWord(String word, int frequency) {
    String lower = word.toLowerCase();
    TrieNode node = root;
    for (char c : lower.toCharArray()) {
        node = node.children.computeIfAbsent(c, k -> new TrieNode());
    }
    node.isEndOfWord = true;
    node.word = lower;
    node.frequency += frequency;  // accumulate frequency
}
```

### Autocomplete: Navigate + DFS + Sort

```java
List<AutocompleteResult> autocomplete(String prefix, int topK) {
    // Step 1: Navigate to prefix node — O(L)
    TrieNode node = root;
    for (char c : prefix.toLowerCase().toCharArray()) {
        node = node.children.get(c);
        if (node == null) return emptyList();  // no matches
    }
    
    // Step 2: DFS to collect ALL words under this prefix — O(all descendants)
    List<AutocompleteResult> matches = new ArrayList<>();
    collectWords(node, matches);  // recursive DFS
    
    // Step 3: Sort by frequency (desc), return top K — O(M log M)
    Collections.sort(matches);  // AutocompleteResult implements Comparable
    return matches.subList(0, Math.min(topK, matches.size()));
}

private void collectWords(TrieNode node, List<AutocompleteResult> results) {
    if (node.isEndOfWord) {
        results.add(new AutocompleteResult(node.word, node.frequency));
    }
    for (TrieNode child : node.children.values()) {
        collectWords(child, results);
    }
}
```

### recordSearch — Learning from Usage

```java
void recordSearch(String word) {
    addWord(word, 1);  // creates if not exists, increments if exists
}
// After 500 searches for "ansible": frequency becomes 200 + 500 = 700
// Now "ansible" ranks higher than "angular" (300)
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Production Optimization — Pre-computed Top-K

Instead of DFS on every query, **store top-K at each TrieNode**:

```java
class TrieNode {
    List<String> topK;  // pre-computed: top 10 words under this node
}

// Query: O(L) navigate + O(1) return pre-computed list!
// Update: Offline MapReduce job recomputes top-K per prefix weekly

// Google: 8.5B queries/day → can't DFS on every keystroke
// Solution: Pre-computed in batch, served from memory/Redis
```

### Deep Dive 2: Trie vs Alternatives

| Structure | Prefix search | Exact lookup | Space | Best for |
|-----------|--------------|-------------|-------|----------|
| **Trie** | O(L) ✅ | O(L) | O(Σ word lengths) | Autocomplete |
| Sorted array + binary search | O(L log N) | O(log N) | O(N) | Static dictionaries |
| HashMap | O(N) scan ❌ | O(1) | O(N) | Exact lookups only |
| Inverted index | O(L) ✅ | O(1) | O(N × K) | Full-text search |

### Deep Dive 3: Space Optimization

**Compressed Trie (Patricia Trie)**: Merge single-child chains:
```
Standard:  a → m → a → z → o → n
Compressed: "amazon" (single node for the whole chain)

Saves: 5 TrieNode objects → 1 node
Used by: Radix trees in networking (IP routing)
```

### Deep Dive 4: Real-time Trending

Combine Trie with **time-decayed frequency**:
```
frequency_score = base_frequency + recent_searches × time_weight
// "world cup" spikes during tournament, decays after
// Separate Trie for trending vs all-time popular
```

### Deep Dive 5: Complexity

| Operation | Time | Space |
|-----------|------|-------|
| addWord | O(L) | O(L) per word |
| autocomplete | O(L + M log M) | O(M) results |
| recordSearch | O(L) | O(1) |
| contains | O(L) | O(1) |
| Total Trie space | — | O(Σ all word chars) |

Where L = prefix length, M = matching words

---

## 📋 Interview Checklist (L63)

- [ ] **Trie structure**: TrieNode with children Map, isEndOfWord, word, frequency
- [ ] **O(L) prefix navigation**: Not O(N) — key advantage over HashMap
- [ ] **DFS collection + frequency sort**: Top-K results
- [ ] **recordSearch**: Frequency updates for learning from usage
- [ ] **Pre-computed top-K**: Production optimization (mention MapReduce)
- [ ] **Compressed Trie**: Patricia trie for space optimization
- [ ] **Trending**: Time-decayed frequency scoring

See `SearchAutocompleteSystem.java` for full implementation with 20 search terms and frequency update demos.
