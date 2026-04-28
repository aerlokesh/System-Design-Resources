import java.util.*;

/**
 * Phone Directory - HELLO Interview Framework
 * 
 * Companies: Google, Microsoft
 * Pattern: Trie for prefix search + HashMap for O(1) lookup
 * Difficulty: Medium
 * 
 * Key: Trie for name-based autocomplete, HashMap for O(1) by phone number.
 * Support: add contact, search by name prefix, search by number, delete.
 */

class Contact {
    private final String name;
    private final String phoneNumber;
    private final String email;

    Contact(String name, String phone, String email) {
        this.name = name; this.phoneNumber = phone; this.email = email;
    }

    String getName() { return name; }
    String getPhoneNumber() { return phoneNumber; }
    String getEmail() { return email; }

    @Override
    public String toString() {
        return String.format("%-20s %-15s %s", name, phoneNumber, email != null ? email : "");
    }
}

class PhoneDirTrieNode {
    final Map<Character, PhoneDirTrieNode> children = new HashMap<>();
    final List<Contact> contacts = new ArrayList<>(); // contacts at this node
}

class PhoneDirectory {
    private final Map<String, Contact> byPhone = new LinkedHashMap<>();    // phone → contact
    private final Map<String, List<Contact>> byName = new LinkedHashMap<>(); // name → contacts
    private final PhoneDirTrieNode trieRoot = new PhoneDirTrieNode();

    /** Add a contact */
    void addContact(String name, String phone, String email) {
        if (byPhone.containsKey(phone)) {
            System.out.println("  ⚠️ Phone " + phone + " already exists");
            return;
        }
        Contact contact = new Contact(name, phone, email);
        byPhone.put(phone, contact);
        byName.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(contact);
        insertTrie(name.toLowerCase(), contact);
        System.out.println("  ✅ Added: " + contact);
    }

    /** Search by exact phone number — O(1) */
    Contact searchByPhone(String phone) { return byPhone.get(phone); }

    /** Search by exact name — O(1) */
    List<Contact> searchByName(String name) {
        return byName.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }

    /** Search by name prefix — O(L + M) via Trie */
    List<Contact> searchByPrefix(String prefix) {
        PhoneDirTrieNode node = trieRoot;
        for (char c : prefix.toLowerCase().toCharArray()) {
            node = node.children.get(c);
            if (node == null) return Collections.emptyList();
        }
        List<Contact> results = new ArrayList<>();
        collectContacts(node, results);
        return results;
    }

    /** Delete by phone number */
    boolean deleteContact(String phone) {
        Contact c = byPhone.remove(phone);
        if (c == null) { System.out.println("  ✗ Not found: " + phone); return false; }
        List<Contact> nameList = byName.get(c.getName().toLowerCase());
        if (nameList != null) nameList.remove(c);
        System.out.println("  🗑️ Deleted: " + c.getName() + " (" + phone + ")");
        return true;
    }

    int size() { return byPhone.size(); }

    // ─── Trie ───
    private void insertTrie(String name, Contact contact) {
        PhoneDirTrieNode node = trieRoot;
        for (char c : name.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new PhoneDirTrieNode());
        }
        node.contacts.add(contact);
    }

    private void collectContacts(PhoneDirTrieNode node, List<Contact> results) {
        results.addAll(node.contacts);
        for (PhoneDirTrieNode child : node.children.values()) {
            collectContacts(child, results);
        }
    }

    String displayAll() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-20s %-15s %s\n", "NAME", "PHONE", "EMAIL"));
        sb.append("  " + "─".repeat(50) + "\n");
        for (Contact c : byPhone.values()) sb.append("  ").append(c).append("\n");
        return sb.toString();
    }
}

public class PhoneDirectorySystem {
    public static void main(String[] args) {
        System.out.println("╔═════════════════════════════════════════════╗");
        System.out.println("║  Phone Directory - Trie + HashMap Lookup  ║");
        System.out.println("╚═════════════════════════════════════════════╝\n");

        PhoneDirectory dir = new PhoneDirectory();

        // ── Add contacts ──
        System.out.println("━━━ Adding contacts ━━━");
        dir.addContact("Alice Johnson", "555-0101", "alice@msft.com");
        dir.addContact("Alice Smith", "555-0102", "asmith@msft.com");
        dir.addContact("Bob Williams", "555-0201", "bob@msft.com");
        dir.addContact("Charlie Brown", "555-0301", "charlie@msft.com");
        dir.addContact("Chris Evans", "555-0302", "chris@msft.com");
        dir.addContact("David Lee", "555-0401", "david@msft.com");
        dir.addContact("Diana Prince", "555-0402", null);
        System.out.println("\n" + dir.displayAll());

        // ── Search by phone ──
        System.out.println("━━━ Search by phone ━━━");
        Contact c = dir.searchByPhone("555-0101");
        System.out.println("  555-0101: " + (c != null ? c : "not found"));
        c = dir.searchByPhone("555-9999");
        System.out.println("  555-9999: " + (c != null ? c : "not found"));
        System.out.println();

        // ── Search by name ──
        System.out.println("━━━ Search by exact name ━━━");
        System.out.println("  'alice johnson': " + dir.searchByName("alice johnson"));
        System.out.println();

        // ── Prefix search (autocomplete) ──
        System.out.println("━━━ Prefix search (autocomplete) ━━━");
        System.out.println("  Prefix 'al': ");
        for (Contact contact : dir.searchByPrefix("al"))
            System.out.println("    " + contact);

        System.out.println("  Prefix 'ch': ");
        for (Contact contact : dir.searchByPrefix("ch"))
            System.out.println("    " + contact);

        System.out.println("  Prefix 'd': ");
        for (Contact contact : dir.searchByPrefix("d"))
            System.out.println("    " + contact);

        System.out.println("  Prefix 'xyz': " + dir.searchByPrefix("xyz"));
        System.out.println();

        // ── Delete ──
        System.out.println("━━━ Delete ━━━");
        dir.deleteContact("555-0301");
        System.out.println("  Size: " + dir.size());
        System.out.println();

        // ── Duplicate phone ──
        System.out.println("━━━ Edge case: duplicate phone ━━━");
        dir.addContact("Eve", "555-0101", "eve@msft.com"); // already exists

        System.out.println("\n✅ All Phone Directory scenarios complete.");
    }
}
