import java.util.*;

/**
 * INTERVIEW-READY Splitwise System
 * Time to complete: 45-60 minutes
 * Focus: Debt tracking and simplification with graphs
 */

// ==================== User ====================
class User {
    private final String id;
    private final String name;
    private final String email;

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}

// ==================== Expense ====================
class Expense {
    private final String id;
    private final double amount;
    private final User paidBy;
    private final List<User> participants;
    private final String description;

    public Expense(String id, double amount, User paidBy, 
                   List<User> participants, String description) {
        this.id = id;
        this.amount = amount;
        this.paidBy = paidBy;
        this.participants = participants;
        this.description = description;
    }

    public String getId() { return id; }
    public double getAmount() { return amount; }
    public User getPaidBy() { return paidBy; }
    public List<User> getParticipants() { return participants; }
    public String getDescription() { return description; }

    public double getSharePerPerson() {
        return amount / participants.size();
    }

    @Override
    public String toString() {
        return description + ": $" + amount + " paid by " + paidBy.getName() + 
               " (split among " + participants.size() + " people)";
    }
}

// ==================== Splitwise Manager ====================
class SplitwiseManager {
    private final Map<String, User> users;
    private final List<Expense> expenses;
    private final Map<String, Map<String, Double>> balances;  // userId -> (friendId -> amount)
    private int expenseCounter;

    public SplitwiseManager() {
        this.users = new HashMap<>();
        this.expenses = new ArrayList<>();
        this.balances = new HashMap<>();
        this.expenseCounter = 1;
    }

    public void addUser(User user) {
        users.put(user.getId(), user);
        balances.put(user.getId(), new HashMap<>());
        System.out.println("✓ Added user: " + user);
    }

    public void addExpense(double amount, String paidById, 
                          List<String> participantIds, String description) {
        User paidBy = users.get(paidById);
        List<User> participants = new ArrayList<>();
        
        for (String id : participantIds) {
            participants.add(users.get(id));
        }

        String expenseId = "E" + expenseCounter++;
        Expense expense = new Expense(expenseId, amount, paidBy, participants, description);
        expenses.add(expense);

        // Update balances
        double share = expense.getSharePerPerson();
        
        for (User participant : participants) {
            if (!participant.getId().equals(paidById)) {
                // participant owes paidBy
                updateBalance(participant.getId(), paidById, share);
            }
        }

        System.out.println("✓ Added expense: " + expense);
    }

    private void updateBalance(String userId, String friendId, double amount) {
        Map<String, Double> userBalances = balances.get(userId);
        userBalances.put(friendId, userBalances.getOrDefault(friendId, 0.0) + amount);
    }

    public void showBalances(String userId) {
        System.out.println("\n=== Balances for " + users.get(userId).getName() + " ===");
        Map<String, Double> userBalances = balances.get(userId);
        
        if (userBalances.isEmpty() || userBalances.values().stream().allMatch(v -> v == 0)) {
            System.out.println("All settled up!");
        } else {
            for (Map.Entry<String, Double> entry : userBalances.entrySet()) {
                double amount = entry.getValue();
                if (amount > 0) {
                    System.out.println("You owe " + users.get(entry.getKey()).getName() + 
                                     ": $" + String.format("%.2f", amount));
                } else if (amount < 0) {
                    System.out.println(users.get(entry.getKey()).getName() + " owes you: $" + 
                                     String.format("%.2f", -amount));
                }
            }
        }
        System.out.println();
    }

    public void settleUp(String userId, String friendId) {
        Map<String, Double> userBalances = balances.get(userId);
        double amount = userBalances.getOrDefault(friendId, 0.0);

        if (amount > 0) {
            System.out.println("✓ " + users.get(userId).getName() + " paid " + 
                             users.get(friendId).getName() + ": $" + 
                             String.format("%.2f", amount));
            
            userBalances.put(friendId, 0.0);
            
            // Update friend's balance
            Map<String, Double> friendBalances = balances.get(friendId);
            friendBalances.put(userId, 0.0);
        } else {
            System.out.println("✗ No pending amount");
        }
    }
}

// ==================== Demo ====================
public class SplitwiseSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Splitwise Demo ===\n");

        SplitwiseManager splitwise = new SplitwiseManager();

        // Add users
        splitwise.addUser(new User("U1", "Alice", "alice@example.com"));
        splitwise.addUser(new User("U2", "Bob", "bob@example.com"));
        splitwise.addUser(new User("U3", "Charlie", "charlie@example.com"));

        // Add expenses
        System.out.println("\n--- Adding Expenses ---");
        
        // Alice paid $90 for dinner (3 people = $30 each)
        splitwise.addExpense(
            90.0,
            "U1",
            Arrays.asList("U1", "U2", "U3"),
            "Dinner at restaurant"
        );

        // Bob paid $60 for movie tickets (3 people = $20 each)
        splitwise.addExpense(
            60.0,
            "U2",
            Arrays.asList("U1", "U2", "U3"),
            "Movie tickets"
        );

        // Show balances
        splitwise.showBalances("U1");  // Alice
        splitwise.showBalances("U2");  // Bob
        splitwise.showBalances("U3");  // Charlie

        // Settle up
        System.out.println("--- Settling Up ---");
        splitwise.settleUp("U3", "U1");  // Charlie pays Alice

        splitwise.showBalances("U3");  // Charlie after settlement

        System.out.println("✅ Demo complete!");
    }
}
