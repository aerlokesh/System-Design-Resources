import java.time.*;
import java.util.*;

/**
 * INTERVIEW-READY Bank Account System
 * Time to complete: 40-50 minutes
 * Focus: Account types, transactions, balance management
 */

// ==================== Account Type ====================
enum AccountType {
    SAVINGS, CHECKING, CREDIT
}

enum TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER
}

enum TransactionStatus {
    SUCCESS, FAILED, PENDING
}

// ==================== Transaction ====================
class Transaction {
    private final String transactionId;
    private final TransactionType type;
    private final double amount;
    private final LocalDateTime timestamp;
    private final TransactionStatus status;
    private final String fromAccount;
    private final String toAccount;

    public Transaction(String transactionId, TransactionType type, double amount,
                      String fromAccount, String toAccount, TransactionStatus status) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public String getTransactionId() { return transactionId; }
    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }

    @Override
    public String toString() {
        return transactionId + ": " + type + " $" + amount + 
               " [" + status + "] at " + timestamp.toLocalTime();
    }
}

// ==================== Bank Account (Abstract) ====================
abstract class BankAccount {
    protected final String accountNumber;
    protected final String accountHolder;
    protected final AccountType type;
    protected double balance;
    protected final List<Transaction> transactions;

    public BankAccount(String accountNumber, String accountHolder, 
                      AccountType type, double initialBalance) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.type = type;
        this.balance = initialBalance;
        this.transactions = new ArrayList<>();
    }

    public synchronized boolean deposit(double amount, String transactionId) {
        if (amount <= 0) {
            return false;
        }

        balance += amount;
        recordTransaction(new Transaction(transactionId, TransactionType.DEPOSIT,
            amount, null, accountNumber, TransactionStatus.SUCCESS));
        
        System.out.println("✓ Deposited $" + amount + " to " + accountNumber + 
                         " | Balance: $" + balance);
        return true;
    }

    public synchronized boolean withdraw(double amount, String transactionId) {
        if (amount <= 0 || !canWithdraw(amount)) {
            recordTransaction(new Transaction(transactionId, TransactionType.WITHDRAWAL,
                amount, accountNumber, null, TransactionStatus.FAILED));
            System.out.println("✗ Withdrawal failed for " + accountNumber);
            return false;
        }

        balance -= amount;
        recordTransaction(new Transaction(transactionId, TransactionType.WITHDRAWAL,
            amount, accountNumber, null, TransactionStatus.SUCCESS));
        
        System.out.println("✓ Withdrew $" + amount + " from " + accountNumber + 
                         " | Balance: $" + balance);
        return true;
    }

    protected abstract boolean canWithdraw(double amount);

    protected void recordTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public double getBalance() { return balance; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountHolder() { return accountHolder; }
    public AccountType getType() { return type; }
    public List<Transaction> getTransactions() { return new ArrayList<>(transactions); }

    @Override
    public String toString() {
        return type + " Account[" + accountNumber + "] - " + accountHolder + 
               " | Balance: $" + String.format("%.2f", balance);
    }
}

// ==================== Savings Account ====================
class SavingsAccount extends BankAccount {
    private static final double MIN_BALANCE = 100.0;

    public SavingsAccount(String accountNumber, String accountHolder, double initialBalance) {
        super(accountNumber, accountHolder, AccountType.SAVINGS, initialBalance);
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return (balance - amount) >= MIN_BALANCE;
    }
}

// ==================== Checking Account ====================
class CheckingAccount extends BankAccount {
    private static final double OVERDRAFT_LIMIT = 500.0;

    public CheckingAccount(String accountNumber, String accountHolder, double initialBalance) {
        super(accountNumber, accountHolder, AccountType.CHECKING, initialBalance);
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return (balance - amount) >= -OVERDRAFT_LIMIT;
    }
}

// ==================== Credit Account ====================
class CreditAccount extends BankAccount {
    private final double creditLimit;

    public CreditAccount(String accountNumber, String accountHolder, double creditLimit) {
        super(accountNumber, accountHolder, AccountType.CREDIT, 0);
        this.creditLimit = creditLimit;
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return Math.abs(balance - amount) <= creditLimit;
    }

    public double getAvailableCredit() {
        return creditLimit + balance; // balance is negative for credit
    }
}

// ==================== Bank ====================
class Bank {
    private final String bankName;
    private final Map<String, BankAccount> accounts;
    private int transactionCounter;

    public Bank(String bankName) {
        this.bankName = bankName;
        this.accounts = new HashMap<>();
        this.transactionCounter = 1;
    }

    public void addAccount(BankAccount account) {
        accounts.put(account.getAccountNumber(), account);
        System.out.println("✓ Added account: " + account);
    }

    public boolean deposit(String accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            System.out.println("✗ Account not found: " + accountNumber);
            return false;
        }

        String txnId = "TXN" + transactionCounter++;
        return account.deposit(amount, txnId);
    }

    public boolean withdraw(String accountNumber, double amount) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            System.out.println("✗ Account not found: " + accountNumber);
            return false;
        }

        String txnId = "TXN" + transactionCounter++;
        return account.withdraw(amount, txnId);
    }

    public boolean transfer(String fromAccountNumber, String toAccountNumber, double amount) {
        BankAccount fromAccount = accounts.get(fromAccountNumber);
        BankAccount toAccount = accounts.get(toAccountNumber);

        if (fromAccount == null || toAccount == null) {
            System.out.println("✗ Invalid account(s)");
            return false;
        }

        String txnId = "TXN" + transactionCounter++;
        
        if (fromAccount.withdraw(amount, txnId + "-W")) {
            toAccount.deposit(amount, txnId + "-D");
            System.out.println("✓ Transferred $" + amount + " from " + 
                             fromAccountNumber + " to " + toAccountNumber);
            return true;
        }

        System.out.println("✗ Transfer failed");
        return false;
    }

    public void displayAccount(String accountNumber) {
        BankAccount account = accounts.get(accountNumber);
        if (account == null) {
            System.out.println("✗ Account not found");
            return;
        }

        System.out.println("\n=== Account Details ===");
        System.out.println(account);
        System.out.println("\nRecent Transactions:");
        
        List<Transaction> txns = account.getTransactions();
        int start = Math.max(0, txns.size() - 5);
        for (int i = start; i < txns.size(); i++) {
            System.out.println("  " + txns.get(i));
        }
        System.out.println();
    }

    public void displayAllAccounts() {
        System.out.println("\n=== " + bankName + " - All Accounts ===");
        for (BankAccount account : accounts.values()) {
            System.out.println("  " + account);
        }
        System.out.println();
    }
}

// ==================== Demo ====================
public class BankAccountSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== Bank Account System Demo ===\n");

        Bank bank = new Bank("ABC Bank");

        // Create accounts
        System.out.println("--- Creating Accounts ---");
        bank.addAccount(new SavingsAccount("SA001", "Alice", 1000));
        bank.addAccount(new CheckingAccount("CA001", "Bob", 500));
        bank.addAccount(new CreditAccount("CR001", "Charlie", 5000));

        bank.displayAllAccounts();

        // Deposit
        System.out.println("--- Deposits ---");
        bank.deposit("SA001", 500);
        bank.deposit("CA001", 200);

        // Withdraw
        System.out.println("\n--- Withdrawals ---");
        bank.withdraw("SA001", 300);
        bank.withdraw("CA001", 600);  // Uses overdraft

        // Transfer
        System.out.println("\n--- Transfers ---");
        bank.transfer("SA001", "CA001", 200);

        // Display account details
        bank.displayAccount("SA001");
        bank.displayAccount("CA001");

        // Try invalid operations
        System.out.println("--- Invalid Operations ---");
        bank.withdraw("SA001", 2000);  // Exceeds min balance
        bank.withdraw("CA001", 2000);  // Exceeds overdraft

        bank.displayAllAccounts();

        System.out.println("✅ Demo complete!");
    }
}
