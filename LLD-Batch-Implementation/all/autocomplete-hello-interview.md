# Google Search Autocomplete - HELLO Interview Framework

> **Companies**: Google, Amazon, Microsoft +3 more  
> **Difficulty**: Medium  
> **Pattern**: Trie (Prefix Tree) + Top-K by Frequency  
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

### 🎯 What is Autocomplete?

User types "ama" → system suggests ["amazon", "amazon prime", "amazon web services"] ranked by popularity/frequency. Google processes **8.5 billion searches per day**. Autocomplete saves ~200ms per query × billions = massive efficiency gain.

**Real-world**: Google Search, YouTube search, Amazon product search, Slack user @mentions, IDE code completion, Outlook recipient autocomplete.

### For L63 Microsoft

1. **Trie**: O(L) prefix navigation where L = prefix length — key data structure
2. **Frequency ranking**: Not just ANY match — MOST POPULAR matches first
3. **Top-K**: Return only top K results, sorted by frequency
4. **DFS collection**: Recursive traversal to find all words under a prefix
5. **Production optimization**: Pre-computed top-K per node (mention MapReduce)
6. **Space optimization**: Patricia Trie / compressed Trie

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What data do we autocomplete?" → Search terms / words with frequency scores
- "Ranked?" → Yes, by frequency/popularity (not alphabetical)
- "How many suggestions?" → Top K (configurable, default 5)
- "Case sensitive?" → Case-insensitive (store lowercase)
- "Real-time updates?" → Yes, frequency increases with usage

### Functional Requirements (P0)
1. **addWord(word, frequency)**: Insert word with popularity score
2. **recordSearch(word)**: Increment frequency when word is searched (learning)
3. **autocomplete(prefix, K)**: Return top K suggestions ranked by frequency
4. **contains(word)**: Check if word exists
5. **delete(word)**: Remove a word
6. Case-insensitive

### Non-Functional
- O(L) prefix navigation (L = prefix length)
- O(M log M) collection + sort (M = matching words)
- Handle 1M+ words efficiently

---

## 2️⃣ Core Entities

### Trie Structure Diagram
```
Root
├─ 'a'
│  ├─ 'm'
│  │  ├─ 'a'
│  │  │  ├─ 'z'
│  │  │  │  ├─ 'o'
│  │  │  │  │  └─ 'n' ★ [word="amazon", freq=1000]
│  │  │  │  │     ├─ ' '
│  │  │  │  │     │  └─ 'p' → ... → 'e' ★ [word="amazon prime", freq=800]
│  │  │  │  │     └─ ' '
│  │  │  │  │        └─ 'w' → ... → 's' ★ [word="amazon web services", freq=600]
│  ├─ 'p'
│  │  ├─ 'p'
│  │  │  ├─ 'l'
│  │  │  │  └─ 'e' ★ [word="apple", freq=900]
│  │  │  │     ├─ ' '
│  │  │  │     │  └─ 's' → ... → 'e' ★ [word="apple store", freq=500]
│  ├─ 'n'
│  │  ├─ 'd' → ... → 'd' ★ [word="android", freq=700]
│  │  ├─ 'g' → ... → 'r' ★ [word="angular", freq=300]
│  │  ├─ 's' → ... → 'e' ★ [word="ansible", freq=200]
```

### Class: TrieNode
| Attribute | Type | Description |
|-----------|------|-------------|
| children | Map<Character, TrieNode> | One child per character |
| isEndOfWord | boolean | True if complete word ends here |
| word | String | The full word (stored only at terminal) |
| frequency | int | Popularity score (higher = more popular) |

### Class: AutocompleteResult
| Attribute | Type | Description |
|-----------|------|-------------|
| word | String | The suggestion |
| frequency | int | Popularity score |

Implements `Comparable`: sort by frequency descending, then alphabetically.

### Class: AutocompleteService
| Attribute | Type | Description |
|-----------|------|-------------|
| root | TrieNode | Root of the Trie |
| defaultTopK | int | Default number of suggestions (5) |

---

## 3️⃣ API Design

```java
class AutocompleteService {
    /** Add word with initial frequency. If exists, frequency is accumulated. */
    void addWord(String word, int frequency);
    
    /** Record a search — increments frequency by 1. Creates if not exists. */
    void recordSearch(String word);
    
    /** Get top K suggestions for prefix, ranked by frequency. */
    List<AutocompleteResult> autocomplete(String prefix, int topK);
    
    /** Check if word exists in dictionary */
    boolean contains(String word);
    
    /** Delete a word */
    boolean delete(String word);
    
    /** Total words in the Trie */
    int size();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: autocomplete("am", 3)

```
autocomplete("am", 3)
  │
  ├─ Step 1: Navigate Trie to prefix "am" — O(L=2)
  │   root → 'a' → 'm'    (2 steps)
  │   Node found? YES → proceed to DFS
  │
  ├─ Step 2: DFS from 'm' node — collect ALL words below
  │   DFS finds:
  │   ├─ "amazon"              (freq=1000)
  │   ├─ "amazon prime"        (freq=800)
  │   └─ "amazon web services" (freq=600)
  │
  ├─ Step 3: Sort by frequency (desc) — O(M log M = 3 log 3)
  │   [amazon(1000), amazon prime(800), amazon web services(600)]
  │
  ├─ Step 4: Return top K=3
  │   → [amazon(1000), amazon prime(800), amazon web services(600)]
  │
  └─ Total: O(L + M log M) = O(2 + 3 log 3) ≈ O(5)
```

### Scenario 2: autocomplete("a", 5) — Lots of Matches

```
autocomplete("a", 5)
  │
  ├─ Navigate: root → 'a'  (1 step)
  ├─ DFS collects ALL words starting with 'a': 
  │   amazon(1000), apple(900), amazon prime(800), android(700),
  │   amazon web services(600), apple store(500), apple music(400),
  │   angular(300), ansible(200)
  │
  ├─ Sort: amazon(1000) > apple(900) > amazon prime(800) > android(700) > aws(600) > ...
  │
  └─ Top 5: [amazon, apple, amazon prime, android, amazon web services]
```

### Scenario 3: recordSearch("ansible") × 500

```
// Before: ansible frequency = 200
for (i = 0; i < 500; i++) recordSearch("ansible");
// After: ansible frequency = 700

autocomplete("an", 3):
  Before: [android(700), angular(300), ansible(200)]
  After:  [android(700), ansible(700), angular(300)]
  // ansible moved up because more people searched for it!
```

### Scenario 4: No Matches

```
autocomplete("xyz", 5)
  ├─ Navigate: root → 'x' → 'y' → 'z'? 
  │   'x' not in root.children!
  └─ Return: [] (empty list)
```

---

## 5️⃣ Design + Implementation

### Trie Insert — O(L)

```java
void addWord(String word, int frequency) {
    if (word == null || word.isEmpty()) return;
    String lower = word.toLowerCase();
    TrieNode node = root;
    for (char c : lower.toCharArray()) {
        node = node.children.computeIfAbsent(c, k -> new TrieNode());
    }
    node.isEndOfWord = true;
    node.word = lower;
    node.frequency += frequency;  // ACCUMULATE, not overwrite!
}

void recordSearch(String word) {
    addWord(word, 1);  // creates if not exists, increments if exists
}
```

**Why `+= frequency` (not `= frequency`)?** Multiple calls to addWord should accumulate. `recordSearch` calls `addWord(word, 1)` — each search adds 1 to the frequency.

### Autocomplete — Navigate + DFS + Sort

```java
List<AutocompleteResult> autocomplete(String prefix, int topK) {
    if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
    String lower = prefix.toLowerCase();

    // Step 1: Navigate to prefix node — O(L)
    TrieNode node = root;
    for (char c : lower.toCharArray()) {
        node = node.children.get(c);
        if (node == null) return Collections.emptyList(); // no matches!
    }

    // Step 2: DFS to collect all words under this prefix — O(all descendants)
    List<AutocompleteResult> allMatches = new ArrayList<>();
    collectWords(node, allMatches);

    // Step 3: Sort by frequency (desc) then alphabetical — O(M log M)
    Collections.sort(allMatches);
    
    // Step 4: Return top K
    return allMatches.subList(0, Math.min(topK, allMatches.size()));
}

/** Recursive DFS to collect all terminal nodes */
private void collectWords(TrieNode node, List<AutocompleteResult> results) {
    if (node.isEndOfWord) {
        results.add(new AutocompleteResult(node.word, node.frequency));
    }
    for (TrieNode child : node.children.values()) {
        collectWords(child, results);
    }
}
```

### AutocompleteResult — Sorting

```java
class AutocompleteResult implements Comparable<AutocompleteResult> {
    final String word;
    final int frequency;

    @Override
    public int compareTo(AutocompleteResult o) {
        int cmp = Integer.compare(o.frequency, this.frequency); // higher freq first
        return cmp != 0 ? cmp : this.word.compareTo(o.word);   // then alphabetical
    }
}
```

### Delete — Lazy

```java
boolean delete(String word) {
    TrieNode node = root;
    for (char c : word.toLowerCase().toCharArray()) {
        node = node.children.get(c);
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
// Lazy: node still exists in Trie, but isEndOfWord=false so DFS skips it
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Production — Pre-computed Top-K Per Node

Instead of DFS + sort on every keystroke:

```java
class TrieNode {
    List<String> topK;  // pre-computed: top 10 words under this node
}

// Query: O(L) navigate + O(1) return pre-computed list!
// Update: Offline MapReduce job recomputes top-K per prefix weekly

// Google: 8.5B queries/day → can't DFS on every keystroke
// Solution: Pre-compute in batch, serve from memory/Redis
```

**Latency comparison:**
| Approach | Per keystroke | Memory |
|----------|-------------|--------|
| DFS + sort (our approach) | O(L + M log M) | O(1) per query |
| **Pre-computed top-K** | **O(L)** | O(K) per node |

### Deep Dive 2: Trie vs Alternatives

| Structure | Prefix search | Exact lookup | Space | Best for |
|-----------|--------------|-------------|-------|----------|
| **Trie** | O(L + M) ✅ | O(L) | O(Σ chars) | Autocomplete |
| Sorted array + binary search | O(L log N + M) | O(log N) | O(N) | Static dictionaries |
| HashMap | O(N) scan ❌ | O(1) | O(N) | Exact lookups only |
| Inverted index | O(1) per prefix | O(1) | O(N × K) | Full-text search |

### Deep Dive 3: Compressed Trie (Patricia Trie)

Standard Trie wastes nodes for single-child chains:
```
Standard:  a → m → a → z → o → n     (6 nodes)
Compressed: "amazon"                    (1 node!)
// Merge single-child chains into one node with string label

Savings: Up to 80% fewer nodes for English dictionaries
Used by: Radix trees in networking (IP routing), Lucene
```

### Deep Dive 4: Real-Time Trending Queries

```
frequency_score = base_frequency + recent_searches × decay_weight

// "world cup" during tournament: base=100, recent=50000 → score=50100
// "world cup" after tournament: base=100, recent=50 → score=150
// Decay: recent_searches halves every 24 hours

Implementation:
  - Separate Trie for "trending" vs "all-time popular"
  - Redis Sorted Set with TTL for trending scores
```

### Deep Dive 5: Personalized Autocomplete

```
User's search history: ["azure devops", "azure portal", "azure functions"]
Global popular: ["amazon", "apple", "android"]

Personalized autocomplete for "a":
  → [azure devops, azure portal, azure functions, amazon, apple]
  // User's history ranked higher than global popular

Implementation:
  - Per-user Trie (expensive) or
  - Global Trie + user history boost (multiply frequency × user_weight)
```

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Empty prefix | Return empty list |
| No matches | Return empty list (Trie navigation fails) |
| Deleted word | isEndOfWord=false, DFS skips it |
| Very long prefix | O(L) navigation, still fast for L=50 |
| Same word added twice | Frequency accumulated (not overwritten) |
| Case insensitive | All keys lowercased |
| Prefix = entire word | Still works (DFS from that node collects it + descendants) |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| addWord | O(L) | O(L) per word |
| autocomplete | O(L + M log M) | O(M) results |
| recordSearch | O(L) | O(1) if exists, O(L) if new |
| contains | O(L) | O(1) |
| delete | O(L) | O(1) |
| Total Trie space | — | O(Σ all word characters) |

Where L = word/prefix length, M = matching words under prefix

**Optimization**: Use min-heap of size K instead of sorting all M matches:
```
O(M log M) sort → O(M log K) heap
For M=100000 and K=5: log(100000) vs log(5) → 17× faster
```

---

## 📋 Interview Checklist (L63)

- [ ] **Trie structure**: TrieNode with HashMap children, isEndOfWord, word, frequency
- [ ] **O(L) prefix navigation**: Key advantage over HashMap (can't do prefix) or Array (O(N))
- [ ] **DFS collection**: Recursive traversal from prefix node to find all words
- [ ] **Frequency sorting**: Sort by frequency descending, alphabetical tiebreak
- [ ] **recordSearch**: Learning from usage — frequency increments with each search
- [ ] **Pre-computed top-K**: Production optimization per Trie node (MapReduce)
- [ ] **Patricia Trie**: Space optimization by merging single-child chains
- [ ] **Trending + Personalized**: Time-decayed frequency, per-user boosting
- [ ] **Edge cases**: Empty prefix, no matches, deleted word, case insensitive

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (TrieNode, Service) | 5 min |
| Implementation (insert, autocomplete, DFS) | 15 min |
| Deep Dives (pre-computed, Patricia, trending) | 10 min |
| **Total** | **~35 min** |

See `SearchAutocompleteSystem.java` for full Trie implementation with 20 search terms, frequency updates, and top-K demos.
