# Phone Directory - HELLO Interview Framework

> **Companies**: Google, Microsoft  
> **Difficulty**: Medium  
> **Pattern**: Trie (name autocomplete) + HashMap (O(1) phone lookup)  
> **Time**: 35 minutes

## Understanding the Problem

A phone directory stores contacts (name, phone, email). Must support O(1) lookup by phone number AND prefix-based name autocomplete (type "al" → shows "Alice Johnson", "Alice Smith").

### For L63 Microsoft
1. **Dual data structure**: HashMap for O(1) exact lookup + Trie for O(L) prefix search
2. **Multiple contacts per name**: Same name, different phones → List<Contact>
3. **Unique phone constraint**: No two contacts share a phone number
4. **Trie stores Contact references** at leaf nodes (not just strings)

---

## Key Design

```
Contact — name, phoneNumber, email (immutable value object)

PhoneDirectory
  - byPhone: HashMap<String, Contact>          ← O(1) exact phone lookup
  - byName: HashMap<String, List<Contact>>     ← O(1) exact name lookup
  - trieRoot: PhoneDirTrieNode                 ← O(L) prefix search by name
  
  addContact(name, phone, email):
    1. Check unique phone (byPhone.containsKey)
    2. Create Contact
    3. byPhone.put(phone, contact)
    4. byName.computeIfAbsent(name.lower, ArrayList::new).add(contact)
    5. Insert name into Trie with Contact reference at terminal node
  
  searchByPrefix("al"):
    1. Navigate Trie: root → 'a' → 'l'
    2. DFS from 'l' node → collect all Contact references
    3. Return List<Contact>
```

### Why Trie Stores Contact Objects (Not Just Strings)?

```java
class PhoneDirTrieNode {
    Map<Character, PhoneDirTrieNode> children;
    List<Contact> contacts;  // contacts whose name ends at this node
    // NOT just String word — we need the full Contact object!
}

// When DFS finds a terminal node, we have the Contact ready
// No need for a second lookup in the HashMap
```

### Key Validations
- Duplicate phone → rejected ("already exists")
- Same name, different phones → both stored (List<Contact>)
- Case-insensitive Trie search (lowercased)
- Delete by phone → removes from byPhone + byName list

### Deep Dives
- **Production**: Elasticsearch for fuzzy name matching, phonetic search (Soundex)
- **Contacts sync**: Google Contacts API, Exchange ActiveSync for Microsoft
- **T9 keyboard search**: Map 2=ABC, 3=DEF → navigate Trie with digit-to-char mapping
- **Favorites/Recent**: Separate quick-access list with LRU ordering

See `PhoneDirectorySystem.java` for full implementation with autocomplete demo.
