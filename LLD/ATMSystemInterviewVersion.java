import java.util.*;

/**
 * INTERVIEW-READY ATM System
 * Time to complete: 45-60 minutes
 * Focus: State pattern, transaction management
 */

// ==================== ATM State ====================
enum ATMState {
    IDLE, CARD_INSERTED, PIN_VERIFIED, TRANSACTION
}

enum ATMTransactionType {
    WITHDRAW, DEPOSIT, BALANCE_INQUIRY
}

// ==================== Bank Account ====================
class ATMBankAccount {
    private final String accountNumber;
    private final String pin;
    private double balance;

    public ATMBankAccount(String accountNumber, String pin, double initialBalance) {
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.balance = initialBalance;
    }

    public boolean verifyPin(String inputPin) {
        return this.pin.equals(inputPin);
    }

    public boolean withdraw(double amount) {
        if (amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    public void deposit(double amount) {
        balance += amount;
    }

    public double getBalance() {
        return balance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}

// ==================== Transaction Record ====================
class TransactionRecord {
    private final String transactionId;
    private final ATMTransactionType type;
    private final double amount;
    private final String timestamp;

    public TransactionRecord(String transactionId, ATMTransactionType type, double amount) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.timestamp = java.time.LocalDateTime.now().toString();
    }

    @Override
    public String toString() {
        return transactionId + ": " + type + " $" + amount;
    }
}

// ==================== ATM ====================
class ATM {
    private final String atmId;
    private ATMState state;
    private ATMBankAccount currentAccount;
    private double cashAvailable;
    private final List<TransactionRecord> transactions;
    private int transactionCounter;

    public ATM(String atmId, double initialCash) {
        this.atmId = atmId;
        this.state = ATMState.IDLE;
        this.cashAvailable = initialCash;
        this.transactions = new ArrayList<>();
        this.transactionCounter = 1;
    }

    public void insertCard(ATMBankAccount account) {
        if (state != ATMState.IDLE) {
            System.out.println("✗ ATM busy");
            return;
        }

        this.currentAccount = account;
        this.state = ATMState.CARD_INSERTED;
        System.out.println("✓ Card inserted for account: " + account.getAccountNumber());
    }

    public boolean enterPin(String pin) {
        if (state != ATMState.CARD_INSERTED) {
            System.out.println("✗ Insert card first");
            return false;
        }

        if (currentAccount.verifyPin(pin)) {
            state = ATMState.PIN_VERIFIED;
            System.out.println("✓ PIN verified");
            return true;
        } else {
            System.out.println("✗ Invalid PIN");
            ejectCard();
            return false;
        }
    }

    public boolean withdraw(double amount) {
        if (state != ATMState.PIN_VERIFIED) {
            System.out.println("✗ Verify PIN first");
            return false;
        }

        // Check account balance first
        if (amount > currentAccount.getBalance()) {
            System.out.println("✗ Insufficient account balance");
            return false;
        }

        // Then check ATM cash availability
        if (amount > cashAvailable) {
            System.out.println("✗ Insufficient cash in ATM");
            return false;
        }

        // Perform the withdrawal
        if (currentAccount.withdraw(amount)) {
            cashAvailable -= amount;
            recordTransaction(ATMTransactionType.WITHDRAW, amount);
            System.out.println("✓ Withdrawn: $" + amount);
            System.out.println("  New balance: $" + currentAccount.getBalance());
            return true;
        } else {
            System.out.println("✗ Withdrawal failed");
            return false;
        }
    }

    public void deposit(double amount) {
        if (state != ATMState.PIN_VERIFIED) {
            System.out.println("✗ Verify PIN first");
            return;
        }

        currentAccount.deposit(amount);
        cashAvailable += amount;
        recordTransaction(ATMTransactionType.DEPOSIT, amount);
        System.out.println("✓ Deposited: $" + amount);
        System.out.println("  New balance: $" + currentAccount.getBalance());
    }

    public void checkBalance() {
        if (state != ATMState.PIN_VERIFIED) {
            System.out.println("✗ Verify PIN first");
            return;
        }

        recordTransaction(ATMTransactionType.BALANCE_INQUIRY, 0);
        System.out.println("✓ Current balance: $" + currentAccount.getBalance());
    }

    public void ejectCard() {
        System.out.println("✓ Card ejected");
        this.currentAccount = null;
        this.state = ATMState.IDLE;
    }

    private void recordTransaction(ATMTransactionType type, double amount) {
        String txnId = "TXN" + transactionCounter++;
        TransactionRecord record = new TransactionRecord(txnId, type, amount);
        transactions.add(record);
    }

    public void displayStatus() {
        System.out.println("\n=== ATM Status ===");
        System.out.println("ATM ID: " + atmId);
        System.out.println("State: " + state);
        System.out.println("Cash Available: $" + cashAvailable);
        System.out.println("Transactions: " + transactions.size());
        System.out.println();
    }
}

// ==================== Demo ====================
public class ATMSystemInterviewVersion {
    public static void main(String[] args) {
        System.out.println("=== ATM System Demo ===\n");

        // Create ATM with $10,000 cash
        ATM atm = new ATM("ATM-001", 10000);

        // Create bank accounts
        ATMBankAccount account1 = new ATMBankAccount("1234567890", "1234", 1000);
        ATMBankAccount account2 = new ATMBankAccount("0987654321", "5678", 500);

        atm.displayStatus();

        // Test 1: Successful transaction
        System.out.println("--- Test 1: Successful Withdrawal ---");
        atm.insertCard(account1);
        atm.enterPin("1234");
        atm.checkBalance();
        atm.withdraw(200);
        atm.ejectCard();

        // Test 2: Wrong PIN
        System.out.println("\n--- Test 2: Wrong PIN ---");
        atm.insertCard(account1);
        atm.enterPin("0000");  // Wrong PIN

        // Test 3: Multiple operations
        System.out.println("\n--- Test 3: Multiple Operations ---");
        atm.insertCard(account2);
        atm.enterPin("5678");
        atm.checkBalance();
        atm.deposit(100);
        atm.withdraw(50);
        atm.checkBalance();
        atm.ejectCard();

        // Test 4: Insufficient funds
        System.out.println("\n--- Test 4: Insufficient Funds ---");
        atm.insertCard(account2);
        atm.enterPin("5678");
        atm.withdraw(10000);  // More than balance
        atm.ejectCard();

        atm.displayStatus();

        System.out.println("✅ Demo complete!");
    }
}
