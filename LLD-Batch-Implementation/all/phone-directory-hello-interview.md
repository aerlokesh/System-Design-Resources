# Phone Directory - HELLO Interview Framework

> **Companies**: Google, Microsoft  
> **Difficulty**: Medium  
> **Pattern**: Trie (name autocomplete) + HashMap (O(1) phone lookup)  
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

### 🎯 What is a Phone Directory?

A system to store contacts (name, phone, email) and efficiently search them. The key challenge: support **both** O(1) exact lookup by phone AND O(L) prefix-based name search (autocomplete).

**Real-world**: Contacts app on iPhone/Android, Microsoft Outlook address book, Google Contacts. When you type "Al" in the search bar, it instantly shows "Alice Johnson", "Alice Smith", "Alfred King".

### For L63 Microsoft
1. **Dual data structure**: HashMap for O(1) exact phone lookup + Trie for O(L) prefix search
2. **Why not just HashMap?** HashMap can't do prefix search without O(N) scan of all entries
3. **Why not just Trie?** Trie can't do O(1) lookup by phone number (phone ≠ name)
4. **Contact references in Trie**: Leaf nodes store Contact objects, not just strings

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "Search by name or phone?" → Both
- "Prefix autocomplete?" → Yes (primary feature)
- "Duplicate names?" → Allowed (same name, different phone)
- "Duplicate phones?" → Not allowed (unique constraint)
- "Case sensitive?" → Case-insensitive search

### Functional Requirements (P0)
1. **Add contact** (name, phone, email) — validate unique phone
2. **Search by phone** — O(1) exact match
3. **Search by name** — O(1) exact match
4. **Search by name prefix** — O(L) autocomplete (type "al" → all names starting with "al")
5. **Delete contact** by phone number
6. Case-insensitive search

### Non-Functional
- O(1) exact lookup by phone or name
- O(L + M) prefix search where L = prefix length, M = matching contacts
- Handle 100K+ contacts

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌────────────────────────────────────────────────────────┐
│                  PhoneDirectory                        │
│  - byPhone: HashMap<String, Contact>  ← O(1) phone    │
│  - byName: HashMap<String, List<Contact>> ← O(1) name │
│  - trieRoot: PhoneDirTrieNode         ← O(L) prefix   │
│  - addContact(name, phone, email)                      │
│  - searchByPhone(phone) → Contact                      │
│  - searchByName(name) → List<Contact>                  │
│  - searchByPrefix(prefix) → List<Contact>              │
│  - deleteContact(phone)                                │
└────────────────────────┬───────────────────────────────┘
                         │ stores
                         ▼
┌────────────────────────────────────────────────────────┐
│                    Contact                             │
│  - name: String                                        │
│  - phoneNumber: String                                 │
│  - email: String (nullable)                            │
└────────────────────────────────────────────────────────┘
                         ▲
                         │ referenced by
┌────────────────────────────────────────────────────────┐
│               PhoneDirTrieNode                         │
│  - children: Map<Character, PhoneDirTrieNode>          │
│  - contacts: List<Contact>  ← stored at terminal node! │
└────────────────────────────────────────────────────────┘
```

### Class: Contact
| Attribute | Type | Description |
|-----------|------|-------------|
| name | String | Full name ("Alice Johnson") |
| phoneNumber | String | Unique phone ("555-0101") |
| email | String | Email (nullable) |

### Class: PhoneDirectory
| Attribute | Type | Description |
|-----------|------|-------------|
| byPhone | HashMap<String, Contact> | O(1) phone → contact |
| byName | HashMap<String, List<Contact>> | O(1) name → contacts (multiple) |
| trieRoot | PhoneDirTrieNode | Trie for prefix search |

### Class: PhoneDirTrieNode
| Attribute | Type | Description |
|-----------|------|-------------|
| children | Map<Character, PhoneDirTrieNode> | Child nodes per character |
| contacts | List<Contact> | Contacts whose name ends here |

**Why contacts at leaf nodes?** When DFS finds a terminal node during prefix search, we immediately have the full Contact object — no second lookup needed.

---

## 3️⃣ API Design

```java
class PhoneDirectory {
    /** Add a contact. Rejects duplicate phone numbers. */
    void addContact(String name, String phone, String email);
    
    /** Exact lookup by phone — O(1) */
    Contact searchByPhone(String phone);
    
    /** Exact lookup by name — O(1), returns list (multiple contacts can share name) */
    List<Contact> searchByName(String name);
    
    /** Prefix autocomplete — O(L + M) via Trie */
    List<Contact> searchByPrefix(String prefix);
    
    /** Delete by phone number — O(1) */
    boolean deleteContact(String phone);
    
    int size();
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Add Contact

```
addContact("Alice Johnson", "555-0101", "alice@msft.com")
  │
  ├─ Validate: byPhone.containsKey("555-0101")?
  │   └─ NO → proceed
  │
  ├─ Create Contact("Alice Johnson", "555-0101", "alice@msft.com")
  │
  ├─ byPhone.put("555-0101", contact)                    ← O(1)
  ├─ byName.computeIfAbsent("alice johnson", ArrayList)
  │         .add(contact)                                 ← O(1)
  │
  └─ Insert into Trie (lowercased "alice johnson"):
      root → 'a' → 'l' → 'i' → 'c' → 'e' → ' ' → 'j' → 'o' → ... → 'n'
      terminal node.contacts.add(contact)                 ← O(L)
```

### Scenario 2: Prefix Search "al"

```
searchByPrefix("al")
  │
  ├─ Navigate Trie: root → 'a' → 'l'  (2 steps = O(L))
  │
  ├─ DFS from 'l' node:
  │   ├─ 'l' → 'i' → 'c' → 'e' → ' ' → 'j' → 'o' → 'h' → ... → 'n'
  │   │     terminal: contacts = [Alice Johnson contact]
  │   └─ 'l' → 'i' → 'c' → 'e' → ' ' → 's' → 'm' → ... → 'h'
  │         terminal: contacts = [Alice Smith contact]
  │
  └─ Return: [Alice Johnson, Alice Smith]
```

### Scenario 3: Duplicate Phone Rejected

```
addContact("Eve", "555-0101", "eve@msft.com")
  ├─ byPhone.containsKey("555-0101")? → YES (Alice has this phone!)
  └─ Print "⚠️ Phone 555-0101 already exists" → return
```

---

## 5️⃣ Design + Implementation

### Three Data Structures Working Together

```java
class PhoneDirectory {
    private final Map<String, Contact> byPhone = new LinkedHashMap<>();
    private final Map<String, List<Contact>> byName = new LinkedHashMap<>();
    private final PhoneDirTrieNode trieRoot = new PhoneDirTrieNode();

    void addContact(String name, String phone, String email) {
        // 1. Unique phone constraint
        if (byPhone.containsKey(phone)) {
            System.out.println("Phone already exists");
            return;
        }
        
        Contact contact = new Contact(name, phone, email);
        
        // 2. HashMap for O(1) phone lookup
        byPhone.put(phone, contact);
        
        // 3. HashMap for O(1) name lookup (supports multiple contacts per name)
        byName.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(contact);
        
        // 4. Trie for O(L) prefix search
        insertTrie(name.toLowerCase(), contact);
    }
    
    List<Contact> searchByPrefix(String prefix) {
        // Navigate to prefix node
        PhoneDirTrieNode node = trieRoot;
        for (char c : prefix.toLowerCase().toCharArray()) {
            node = node.children.get(c);
            if (node == null) return Collections.emptyList();  // no matches
        }
        // DFS to collect all contacts under this prefix
        List<Contact> results = new ArrayList<>();
        collectContacts(node, results);
        return results;
    }
    
    private void collectContacts(PhoneDirTrieNode node, List<Contact> results) {
        results.addAll(node.contacts);  // add contacts at this node
        for (PhoneDirTrieNode child : node.children.values()) {
            collectContacts(child, results);  // recurse into children
        }
    }
    
    boolean deleteContact(String phone) {
        Contact c = byPhone.remove(phone);
        if (c == null) return false;
        // Also remove from byName list
        List<Contact> nameList = byName.get(c.getName().toLowerCase());
        if (nameList != null) nameList.remove(c);
        return true;
    }
}
```

### Why Three Structures?

| Need | Structure | Time |
|------|-----------|------|
| Find contact by phone | `byPhone` HashMap | O(1) |
| Find contacts by exact name | `byName` HashMap | O(1) |
| Find contacts by name prefix | Trie | O(L + M) |

Without all three, at least one operation would be O(N).

---

## 6️⃣ Deep Dives

### Deep Dive 1: Same Name, Different Phones

```java
addContact("Alice Johnson", "555-0101", "alice@home.com");
addContact("Alice Johnson", "555-0202", "alice@work.com");

searchByName("alice johnson") → [Contact(555-0101), Contact(555-0202)]
// Two different contacts with same name — both stored in List
// Phone is unique, name is not
```

### Deep Dive 2: Trie vs Sorted List for Prefix Search

| Structure | Prefix search | Insert | Space |
|-----------|--------------|--------|-------|
| **Trie** | O(L + M) ✅ | O(L) | O(Σ name chars) |
| Sorted List + Binary Search | O(L log N + M) | O(N) shift | O(N) |
| HashMap (scan all) | O(N × L) ❌ | O(1) | O(N) |

Trie is best for **frequent prefix searches** (autocomplete-as-you-type).

### Deep Dive 3: Production Extensions

| Feature | Implementation |
|---------|---------------|
| **Fuzzy search** | Levenshtein distance: "Alic" matches "Alice" |
| **Phonetic search** | Soundex/Metaphone: "Jon" matches "John" |
| **T9 keyboard** | Map digits 2=ABC, 3=DEF → Trie with digit keys |
| **Favorites** | Separate LRU list for recently/frequently contacted |
| **Contacts sync** | Google Contacts API, Exchange ActiveSync |
| **Elasticsearch** | Production alternative to Trie for full-text search |

### Deep Dive 4: Delete from Trie (Complexity)

Our implementation removes from HashMap but NOT from Trie (lazy). In production:
- **Lazy delete**: Mark as deleted, skip during DFS. Rebuild Trie periodically.
- **Eager delete**: Remove Trie path if no other words share prefix. Complex: need to track if internal nodes have other children.

Lazy delete is simpler and good enough for interview.

### Deep Dive 5: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Duplicate phone | Rejected ("already exists") |
| Same name, different phone | Both stored (List<Contact>) |
| Delete non-existent phone | Return false |
| Empty prefix search | Return all contacts (DFS from root) |
| Case insensitive | Lowercase all keys in Trie and HashMap |
| Null email | Allowed (nullable field) |
| Phone format validation | P1: regex for format consistency |

### Deep Dive 6: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| addContact | O(L) Trie insert | O(L) per name + O(1) per HashMap entry |
| searchByPhone | O(1) | O(1) |
| searchByName | O(1) | O(1) |
| searchByPrefix | O(L + M) | O(M) results |
| deleteContact | O(1) HashMap | O(1) |
| Total space | — | O(N contacts + Σ name chars for Trie) |

Where L = name length, M = matching contacts, N = total contacts

---

## 📋 Interview Checklist (L63)

- [ ] **Dual structure**: HashMap for O(1) exact + Trie for O(L) prefix
- [ ] **Why both?** HashMap can't do prefix; Trie can't do O(1) by phone
- [ ] **Contact refs in Trie**: List<Contact> at terminal nodes
- [ ] **Multiple contacts per name**: List in byName HashMap
- [ ] **Unique phone constraint**: byPhone.containsKey() check
- [ ] **Case insensitive**: Lowercase all Trie/HashMap keys
- [ ] **DFS for prefix**: Recursive collection from prefix node
- [ ] **Production**: Elasticsearch, fuzzy search, T9, phonetic

See `PhoneDirectorySystem.java` for full implementation with autocomplete demo.
