import java.util.*;
import java.time.*;

// ==================== SPLITWISE LLD ====================
// System Design Interview Question: Design an expense sharing application like Splitwise
//
// Requirements:
// 1. Users can create expenses and split them
// 2. Support different split types: Equal, Exact, Percentage
// 3. Track balances between users (who owes whom)
// 4. Settle up debts
// 5. Create groups for recurring expenses
// 6. Simplify debts (if A owes B and B owes C, optimize)
// 7. View expense history
//
// Design Patterns Used:
// - Strategy Pattern: Different split calculation strategies
// - Factory Pattern: Creating different split types
// - Singleton Pattern: SplitwiseService as central coordinator

// ==================== ENUMS ====================

enum SplitType {
    EQUAL,      // Split equally among all users
    EXACT,      // Exact amounts specified for each user
    PERCENTAGE  // Percentage-based split
}

enum ExpenseCategory {
    FOOD, ENTERTAINMENT, TRAVEL, SHOPPING, UTILITIES, RENT, OTHER
}

// ==================== USER ====================

class User {
    private String userId;
    private String name;
    private String email;
    private String phone;

    public User(String userId, String name, String email, String phone) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof User)) return false;
        User other = (User) obj;
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + userId + ")";
    }
}

// ==================== SPLIT STRATEGY (Strategy Pattern) ====================

interface SplitStrategy {
    Map<User, Double> calculateSplits(List<User> users, double totalAmount, Map<User, Double> metadata);
    SplitType getType();
}

class EqualSplitStrategy implements SplitStrategy {
    @Override
    public Map<User, Double> calculateSplits(List<User> users, double totalAmount, Map<User, Double> metadata) {
        Map<User, Double> splits = new HashMap<>();
        double amountPerUser = totalAmount / users.size();
        
        for (User user : users) {
            splits.put(user, amountPerUser);
        }
        
        return splits;
    }

    @Override
    public SplitType getType() {
        return SplitType.EQUAL;
    }
}

class ExactSplitStrategy implements SplitStrategy {
    @Override
    public Map<User, Double> calculateSplits(List<User> users, double totalAmount, Map<User, Double> metadata) {
        Map<User, Double> splits = new HashMap<>();
        double sum = 0;
        
        for (User user : users) {
            Double amount = metadata.get(user);
            if (amount == null) {
                throw new IllegalArgumentException("Exact amount not specified for user: " + user.getName());
            }
            splits.put(user, amount);
            sum += amount;
        }
        
        // Validate that sum equals total
        if (Math.abs(sum - totalAmount) > 0.01) {
            throw new IllegalArgumentException("Sum of exact splits doesn't match total amount");
        }
        
        return splits;
    }

    @Override
    public SplitType getType() {
        return SplitType.EXACT;
    }
}

class PercentageSplitStrategy implements SplitStrategy {
    @Override
    public Map<User, Double> calculateSplits(List<User> users, double totalAmount, Map<User, Double> metadata) {
        Map<User, Double> splits = new HashMap<>();
        double totalPercentage = 0;
        
        for (User user : users) {
            Double percentage = metadata.get(user);
            if (percentage == null) {
                throw new IllegalArgumentException("Percentage not specified for user: " + user.getName());
            }
            double amount = (totalAmount * percentage) / 100.0;
            splits.put(user, amount);
            totalPercentage += percentage;
        }
        
        // Validate percentages add up to 100
        if (Math.abs(totalPercentage - 100.0) > 0.01) {
            throw new IllegalArgumentException("Percentages must add up to 100");
        }
        
        return splits;
    }

    @Override
    public SplitType getType() {
        return SplitType.PERCENTAGE;
    }
}

// ==================== SPLIT FACTORY (Factory Pattern) ====================

class SplitFactory {
    public static SplitStrategy createSplitStrategy(SplitType type) {
        switch (type) {
            case EQUAL:
                return new EqualSplitStrategy();
            case EXACT:
                return new ExactSplitStrategy();
            case PERCENTAGE:
                return new PercentageSplitStrategy();
            default:
                throw new IllegalArgumentException("Unknown split type: " + type);
        }
    }
}

// ==================== EXPENSE ====================

class Expense {
    private String expenseId;
    private String description;
    private double amount;
    private User paidBy;
    private List<User> participants;
    private Map<User, Double> splits;
    private SplitType splitType;
    private ExpenseCategory category;
    private LocalDateTime createdAt;
    private String groupId; // null if not part of a group

    public Expense(String expenseId, String description, double amount, User paidBy,
                  List<User> participants, SplitType splitType, ExpenseCategory category) {
        this.expenseId = expenseId;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.participants = new ArrayList<>(participants);
        this.splitType = splitType;
        this.category = category;
        this.createdAt = LocalDateTime.now();
        this.splits = new HashMap<>();
    }

    public void calculateSplits(Map<User, Double> metadata) {
        SplitStrategy strategy = SplitFactory.createSplitStrategy(splitType);
        this.splits = strategy.calculateSplits(participants, amount, metadata);
    }

    // Getters
    public String getExpenseId() { return expenseId; }
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public User getPaidBy() { return paidBy; }
    public List<User> getParticipants() { return participants; }
    public Map<User, Double> getSplits() { return splits; }
    public SplitType getSplitType() { return splitType; }
    public ExpenseCategory getCategory() { return category; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    @Override
    public String toString() {
        return String.format("Expense[%s]: %s - $%.2f paid by %s (%s split)",
            expenseId, description, amount, paidBy.getName(), splitType);
    }
}

// ==================== GROUP ====================

class Group {
    private String groupId;
    private String name;
    private List<User> members;
    private List<Expense> expenses;
    private LocalDateTime createdAt;

    public Group(String groupId, String name) {
        this.groupId = groupId;
        this.name = name;
        this.members = new ArrayList<>();
        this.expenses = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }

    public void addMember(User user) {
        if (!members.contains(user)) {
            members.add(user);
        }
    }

    public void removeMember(User user) {
        members.remove(user);
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
        expense.setGroupId(groupId);
    }

    public String getGroupId() { return groupId; }
    public String getName() { return name; }
    public List<User> getMembers() { return members; }
    public List<Expense> getExpenses() { return expenses; }

    @Override
    public String toString() {
        return String.format("Group[%s]: %s (%d members, %d expenses)",
            groupId, name, members.size(), expenses.size());
    }
}

// ==================== BALANCE SHEET ====================

class BalanceSheet {
    // Map of: User -> (Other User -> Amount they owe)
    private Map<User, Map<User, Double>> balances;

    public BalanceSheet() {
        this.balances = new HashMap<>();
    }

    public void addBalance(User user1, User user2, double amount) {
        if (amount == 0) return;

        balances.computeIfAbsent(user1, k -> new HashMap<>())
                .merge(user2, amount, Double::sum);
    }

    public void updateBalanceForExpense(Expense expense) {
        User paidBy = expense.getPaidBy();
        Map<User, Double> splits = expense.getSplits();

        for (Map.Entry<User, Double> entry : splits.entrySet()) {
            User user = entry.getKey();
            double amount = entry.getValue();

            if (!user.equals(paidBy)) {
                // User owes paidBy the amount
                addBalance(user, paidBy, amount);
            }
        }
    }

    public Map<User, Double> getBalanceForUser(User user) {
        Map<User, Double> userBalances = new HashMap<>();

        // Amount user owes to others
        Map<User, Double> owes = balances.getOrDefault(user, new HashMap<>());
        for (Map.Entry<User, Double> entry : owes.entrySet()) {
            userBalances.put(entry.getKey(), entry.getValue());
        }

        // Amount others owe to user (negative)
        for (Map.Entry<User, Map<User, Double>> entry : balances.entrySet()) {
            User otherUser = entry.getKey();
            if (!otherUser.equals(user)) {
                Double amount = entry.getValue().get(user);
                if (amount != null) {
                    userBalances.merge(otherUser, -amount, Double::sum);
                }
            }
        }

        return userBalances;
    }

    public void settleBalance(User user1, User user2, double amount) {
        // User1 pays user2
        Map<User, Double> user1Owes = balances.get(user1);
        if (user1Owes != null && user1Owes.containsKey(user2)) {
            double currentOwes = user1Owes.get(user2);
            if (amount >= currentOwes) {
                user1Owes.remove(user2);
                System.out.printf("✅ Settled: %s paid %s $%.2f (fully settled)%n",
                    user1.getName(), user2.getName(), currentOwes);
            } else {
                user1Owes.put(user2, currentOwes - amount);
                System.out.printf("✅ Partial settlement: %s paid %s $%.2f (remaining: $%.2f)%n",
                    user1.getName(), user2.getName(), amount, currentOwes - amount);
            }
        }
    }

    public void simplifyDebts() {
        // Simplification algorithm: if A owes B and B owes A, net them out
        System.out.println("\n🔄 Simplifying debts...");
        
        for (User user1 : new HashSet<>(balances.keySet())) {
            Map<User, Double> user1Owes = balances.get(user1);
            if (user1Owes == null) continue;

            for (User user2 : new HashSet<>(user1Owes.keySet())) {
                double amount1to2 = user1Owes.get(user2);
                
                Map<User, Double> user2Owes = balances.get(user2);
                if (user2Owes != null && user2Owes.containsKey(user1)) {
                    double amount2to1 = user2Owes.get(user1);
                    
                    // Net out the debts
                    if (amount1to2 > amount2to1) {
                        user1Owes.put(user2, amount1to2 - amount2to1);
                        user2Owes.remove(user1);
                        System.out.printf("   Simplified: %s owes %s $%.2f (netted from $%.2f and $%.2f)%n",
                            user1.getName(), user2.getName(), amount1to2 - amount2to1, amount1to2, amount2to1);
                    } else if (amount2to1 > amount1to2) {
                        user2Owes.put(user1, amount2to1 - amount1to2);
                        user1Owes.remove(user2);
                        System.out.printf("   Simplified: %s owes %s $%.2f (netted from $%.2f and $%.2f)%n",
                            user2.getName(), user1.getName(), amount2to1 - amount1to2, amount2to1, amount1to2);
                    } else {
                        // Equal debts, cancel both
                        user1Owes.remove(user2);
                        user2Owes.remove(user1);
                        System.out.printf("   Cancelled: %s and %s had equal debts of $%.2f%n",
                            user1.getName(), user2.getName(), amount1to2);
                    }
                }
            }
        }
    }

    public void displayBalances() {
        System.out.println("\n💰 Current Balances:");
        boolean hasBalances = false;
        
        for (Map.Entry<User, Map<User, Double>> entry : balances.entrySet()) {
            User user = entry.getKey();
            Map<User, Double> owes = entry.getValue();
            
            for (Map.Entry<User, Double> debt : owes.entrySet()) {
                if (debt.getValue() > 0.01) { // Ignore tiny amounts
                    hasBalances = true;
                    System.out.printf("   %s owes %s: $%.2f%n",
                        user.getName(), debt.getKey().getName(), debt.getValue());
                }
            }
        }
        
        if (!hasBalances) {
            System.out.println("   All settled up! 🎉");
        }
    }
}

// ==================== SPLITWISE SERVICE (Singleton Pattern) ====================

class SplitwiseService {
    private static SplitwiseService instance;
    private Map<String, User> users;
    private Map<String, Group> groups;
    private List<Expense> expenses;
    private BalanceSheet balanceSheet;
    private int expenseCounter;

    private SplitwiseService() {
        users = new HashMap<>();
        groups = new HashMap<>();
        expenses = new ArrayList<>();
        balanceSheet = new BalanceSheet();
        expenseCounter = 0;
        System.out.println("💰 Splitwise Service initialized");
    }

    public static synchronized SplitwiseService getInstance() {
        if (instance == null) {
            instance = new SplitwiseService();
        }
        return instance;
    }

    // User Management
    public User registerUser(String name, String email, String phone) {
        String userId = "USER-" + System.currentTimeMillis();
        User user = new User(userId, name, email, phone);
        users.put(userId, user);
        System.out.println("✅ User registered: " + user);
        return user;
    }

    public User getUser(String userId) {
        return users.get(userId);
    }

    // Group Management
    public Group createGroup(String name, List<User> members) {
        String groupId = "GROUP-" + System.currentTimeMillis();
        Group group = new Group(groupId, name);
        
        for (User member : members) {
            group.addMember(member);
        }
        
        groups.put(groupId, group);
        System.out.println("✅ Group created: " + group);
        return group;
    }

    public Group getGroup(String groupId) {
        return groups.get(groupId);
    }

    // Expense Management
    public Expense addExpense(String description, double amount, User paidBy,
                             List<User> participants, SplitType splitType,
                             ExpenseCategory category, Map<User, Double> metadata) {
        
        String expenseId = "EXP-" + (++expenseCounter);
        Expense expense = new Expense(expenseId, description, amount, paidBy,
                                     participants, splitType, category);
        
        // Calculate splits
        expense.calculateSplits(metadata);
        
        // Update balance sheet
        balanceSheet.updateBalanceForExpense(expense);
        
        expenses.add(expense);
        
        System.out.println("\n💸 Expense Added: " + expense);
        System.out.println("   Splits:");
        expense.getSplits().forEach((user, amt) ->
            System.out.printf("      %s: $%.2f%n", user.getName(), amt));
        
        return expense;
    }

    public void addExpenseToGroup(Group group, String description, double amount,
                                  User paidBy, SplitType splitType,
                                  ExpenseCategory category, Map<User, Double> metadata) {
        
        Expense expense = addExpense(description, amount, paidBy,
                                     group.getMembers(), splitType, category, metadata);
        group.addExpense(expense);
    }

    // Balance Operations
    public void showBalanceForUser(User user) {
        System.out.println("\n💰 Balance for " + user.getName() + ":");
        Map<User, Double> balances = balanceSheet.getBalanceForUser(user);
        
        if (balances.isEmpty()) {
            System.out.println("   All settled up! ✅");
            return;
        }

        double totalOwes = 0;
        double totalOwed = 0;

        for (Map.Entry<User, Double> entry : balances.entrySet()) {
            if (entry.getValue() > 0) {
                System.out.printf("   You owe %s: $%.2f%n",
                    entry.getKey().getName(), entry.getValue());
                totalOwes += entry.getValue();
            } else if (entry.getValue() < 0) {
                System.out.printf("   %s owes you: $%.2f%n",
                    entry.getKey().getName(), -entry.getValue());
                totalOwed += -entry.getValue();
            }
        }

        System.out.printf("   Net: %s%.2f%n",
            totalOwes > totalOwed ? "You owe $" : "You are owed $",
            Math.abs(totalOwes - totalOwed));
    }

    public void settleUp(User user1, User user2, double amount) {
        System.out.println("\n💳 Settlement:");
        balanceSheet.settleBalance(user1, user2, amount);
    }

    public void simplifyDebts() {
        balanceSheet.simplifyDebts();
    }

    public void showAllBalances() {
        balanceSheet.displayBalances();
    }

    // Expense History
    public void showExpenseHistory(User user) {
        System.out.println("\n📜 Expense History for " + user.getName() + ":");
        
        List<Expense> userExpenses = new ArrayList<>();
        for (Expense expense : expenses) {
            if (expense.getParticipants().contains(user) || expense.getPaidBy().equals(user)) {
                userExpenses.add(expense);
            }
        }

        if (userExpenses.isEmpty()) {
            System.out.println("   No expenses yet");
            return;
        }

        for (Expense expense : userExpenses) {
            System.out.printf("   [%s] %s - $%.2f (%s)%n",
                expense.getCreatedAt().toLocalDate(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getPaidBy().equals(user) ? "You paid" : "Paid by " + expense.getPaidBy().getName());
            
            if (expense.getSplits().containsKey(user)) {
                System.out.printf("      Your share: $%.2f%n", expense.getSplits().get(user));
            }
        }
    }

    public void showGroupExpenses(String groupId) {
        Group group = groups.get(groupId);
        if (group == null) {
            System.out.println("Group not found");
            return;
        }

        System.out.println("\n📊 Group Expenses: " + group.getName());
        System.out.println("   Members: " + group.getMembers().stream()
            .map(User::getName)
            .reduce((a, b) -> a + ", " + b)
            .orElse("None"));
        
        System.out.println("   Expenses:");
        for (Expense expense : group.getExpenses()) {
            System.out.printf("      %s - $%.2f (paid by %s)%n",
                expense.getDescription(), expense.getAmount(), expense.getPaidBy().getName());
        }
    }
}

// ==================== DEMO ====================

public class SplitwiseSystem {
    public static void main(String[] args) {
        System.out.println("========== SPLITWISE SYSTEM - LOW LEVEL DESIGN ==========\n");

        SplitwiseService splitwise = SplitwiseService.getInstance();

        // ===== REGISTER USERS =====
        System.out.println("===== REGISTERING USERS =====");
        User alice = splitwise.registerUser("Alice", "alice@email.com", "+1-555-0101");
        User bob = splitwise.registerUser("Bob", "bob@email.com", "+1-555-0102");
        User charlie = splitwise.registerUser("Charlie", "charlie@email.com", "+1-555-0103");
        User david = splitwise.registerUser("David", "david@email.com", "+1-555-0104");

        // ===== CREATE GROUP =====
        System.out.println("\n===== CREATING GROUP =====");
        Group roommates = splitwise.createGroup("Apartment Roommates",
            Arrays.asList(alice, bob, charlie));

        // ===== EXAMPLE 1: EQUAL SPLIT =====
        System.out.println("\n===== EXAMPLE 1: EQUAL SPLIT =====");
        System.out.println("Scenario: Alice paid $90 for dinner, split equally among 3 people");
        
        splitwise.addExpenseToGroup(roommates, "Dinner at Restaurant",
            90.0, alice, SplitType.EQUAL, ExpenseCategory.FOOD, new HashMap<>());

        splitwise.showBalanceForUser(alice);
        splitwise.showBalanceForUser(bob);

        // ===== EXAMPLE 2: EXACT SPLIT =====
        System.out.println("\n\n===== EXAMPLE 2: EXACT SPLIT =====");
        System.out.println("Scenario: Bob paid $120 for groceries, exact amounts specified");
        
        Map<User, Double> exactAmounts = new HashMap<>();
        exactAmounts.put(alice, 40.0);  // Alice's groceries
        exactAmounts.put(bob, 50.0);    // Bob's groceries
        exactAmounts.put(charlie, 30.0); // Charlie's groceries
        
        splitwise.addExpenseToGroup(roommates, "Grocery Shopping",
            120.0, bob, SplitType.EXACT, ExpenseCategory.SHOPPING, exactAmounts);

        splitwise.showBalanceForUser(alice);
        splitwise.showBalanceForUser(bob);

        // ===== EXAMPLE 3: PERCENTAGE SPLIT =====
        System.out.println("\n\n===== EXAMPLE 3: PERCENTAGE SPLIT =====");
        System.out.println("Scenario: Charlie paid $100 for utilities, split by room size");
        
        Map<User, Double> percentages = new HashMap<>();
        percentages.put(alice, 40.0);   // 40% (larger room)
        percentages.put(bob, 35.0);     // 35% (medium room)
        percentages.put(charlie, 25.0); // 25% (smaller room)
        
        splitwise.addExpenseToGroup(roommates, "Monthly Utilities",
            100.0, charlie, SplitType.PERCENTAGE, ExpenseCategory.UTILITIES, percentages);

        splitwise.showBalanceForUser(charlie);

        // ===== SHOW ALL BALANCES =====
        splitwise.showAllBalances();

        // ===== SIMPLIFY DEBTS =====
        splitwise.simplifyDebts();
        splitwise.showAllBalances();

        // ===== EXAMPLE 4: NON-GROUP EXPENSE =====
        System.out.println("\n\n===== EXAMPLE 4: NON-GROUP EXPENSE =====");
        System.out.println("Scenario: David and Alice went to movies");
        
        splitwise.addExpense("Movie Tickets", 30.0, david,
            Arrays.asList(david, alice), SplitType.EQUAL,
            ExpenseCategory.ENTERTAINMENT, new HashMap<>());

        splitwise.showBalanceForUser(alice);
        splitwise.showBalanceForUser(david);

        // ===== SETTLE UP =====
        System.out.println("\n\n===== SETTLEMENT =====");
        System.out.println("Bob settles his debt with Alice");
        splitwise.settleUp(bob, alice, 30.0); // Bob pays Alice

        splitwise.showAllBalances();

        // ===== EXPENSE HISTORY =====
        splitwise.showExpenseHistory(alice);
        
        // ===== GROUP SUMMARY =====
        splitwise.showGroupExpenses(roommates.getGroupId());

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nKey Features Demonstrated:");
        System.out.println("✅ Multiple split types (Equal, Exact, Percentage)");
        System.out.println("✅ Balance tracking between users");
        System.out.println("✅ Group expenses");
        System.out.println("✅ Debt simplification");
        System.out.println("✅ Settlement functionality");
        System.out.println("✅ Expense history");
        System.out.println("\nDesign Patterns Used:");
        System.out.println("• Strategy Pattern - Different split calculation strategies");
        System.out.println("• Factory Pattern - Creating split strategies");
        System.out.println("• Singleton Pattern - SplitwiseService as central coordinator");
    }
}
