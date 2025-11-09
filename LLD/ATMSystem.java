import java.util.*;

// ==================== Enums ====================
// Why Enums? Type safety, prevents invalid values, readable code, compile-time checking
// Better than using Strings or integers which can have invalid values

enum TransactionType {
    WITHDRAWAL,
    DEPOSIT,
    BALANCE_INQUIRY,
    TRANSFER
    // Enums provide: 1) Type safety 2) Predefined set of values 3) Easy to extend
}

enum ATMState {
    IDLE,
    CARD_INSERTED,
    PIN_ENTERED,
    TRANSACTION_SELECTION,
    TRANSACTION_IN_PROGRESS,
    CASH_DISPENSING
    // Not currently used in State Pattern classes, but useful for logging/monitoring
}

enum TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING
    // Better than boolean - allows for more states (PENDING, CANCELLED, etc.)
}

// ==================== Models ====================
// Why separate Model classes? Single Responsibility Principle (SRP)
// Each class represents one domain entity with related data and behavior

class Card {
    // Why private fields? Encapsulation - hide internal state, control access via methods
    private String cardNumber;
    private String cvv;
    private Date expiryDate;
    private String pin;
    private String accountNumber;  // Links card to account - one-to-one relationship

    // Constructor enforces object creation with all required fields
    // Prevents objects in invalid state (like Card with no cardNumber)
    public Card(String cardNumber, String cvv, Date expiryDate, String pin, String accountNumber) {
        this.cardNumber = cardNumber;
        this.cvv = cvv;
        this.expiryDate = expiryDate;
        this.pin = pin;
        this.accountNumber = accountNumber;
    }

    // Getters provide read-only access - no setters to prevent modification after creation
    // Immutability increases security (card details shouldn't change after creation)
    public String getCardNumber() { return cardNumber; }
    public String getPin() { return pin; }
    public String getAccountNumber() { return accountNumber; }
    public Date getExpiryDate() { return expiryDate; }
    
    // Business logic belongs in the entity it operates on (Tell, Don't Ask principle)
    public boolean isExpired() {
        return new Date().after(expiryDate);
    }
}

class Account {
    private String accountNumber;
    private String accountHolderName;
    private double balance;  // Could use BigDecimal for financial precision in production
    private List<Transaction> transactionHistory;  // One-to-many relationship with Transaction

    public Account(String accountNumber, String accountHolderName, double balance) {
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
        this.balance = balance;
        this.transactionHistory = new ArrayList<>();  // Initialize to avoid NullPointerException
    }

    public String getAccountNumber() { return accountNumber; }
    public String getAccountHolderName() { return accountHolderName; }
    public double getBalance() { return balance; }
    public List<Transaction> getTransactionHistory() { return transactionHistory; }

    // Why synchronized? Thread safety - multiple ATMs might access same account
    // Prevents race conditions when multiple transactions occur simultaneously
    public synchronized boolean debit(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;  // Transaction allowed
        }
        return false;  // Insufficient funds
    }

    // Synchronized to prevent race conditions during deposits
    public synchronized void credit(double amount) {
        balance += amount;
    }

    // Maintains transaction history for audit trail and customer reference
    public void addTransaction(Transaction transaction) {
        transactionHistory.add(transaction);
    }
}

class Customer {
    private String customerId;
    private String name;
    private String email;
    private String phone;
    private List<Account> accounts;  // One customer can have multiple accounts
    private List<Card> cards;  // One customer can have multiple cards

    public Customer(String customerId, String name, String email, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.accounts = new ArrayList<>();
        this.cards = new ArrayList<>();
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public List<Account> getAccounts() { return accounts; }
    public List<Card> getCards() { return cards; }

    public void addAccount(Account account) {
        accounts.add(account);
    }

    public void addCard(Card card) {
        cards.add(card);
    }
}

// ==================== Transaction Classes ====================
// Why Abstract Class? Share common implementation across all transaction types
// Alternative: Interface - but then we'd duplicate fields/logic in each subclass
// Abstract class provides: 1) Code reuse 2) Template method pattern 3) Enforced structure

abstract class Transaction {
    // Common fields for all transactions - prevents duplication
    private String transactionId;
    private TransactionType type;
    private Date timestamp;
    private double amount;
    private TransactionStatus status;
    private String sourceAccount;
    private String destinationAccount;  // Only used for transfers

    // Constructor shared by all subclasses - ensures consistent initialization
    public Transaction(String transactionId, TransactionType type, double amount, String sourceAccount) {
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.sourceAccount = sourceAccount;
        this.timestamp = new Date();  // Auto-capture creation time
        this.status = TransactionStatus.PENDING;  // All start as pending
    }

    // Public getters for read access
    public String getTransactionId() { return transactionId; }
    public TransactionType getType() { return type; }
    public Date getTimestamp() { return timestamp; }
    public double getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public String getSourceAccount() { return sourceAccount; }
    public String getDestinationAccount() { return destinationAccount; }

    // Protected setters - only subclasses can modify (encapsulation)
    protected void setStatus(TransactionStatus status) { this.status = status; }
    protected void setDestinationAccount(String account) { this.destinationAccount = account; }

    // Abstract method - Template Method Pattern
    // Forces each transaction type to implement its own execution logic
    // Polymorphism allows treating all transactions uniformly while having different behavior
    public abstract boolean execute(Account account);
}

// Concrete implementation for Withdrawal
// Why separate class? Open/Closed Principle - open for extension, closed for modification
class WithdrawalTransaction extends Transaction {
    public WithdrawalTransaction(String transactionId, double amount, String accountNumber) {
        super(transactionId, TransactionType.WITHDRAWAL, amount, accountNumber);
    }

    @Override
    public boolean execute(Account account) {
        // Business logic specific to withdrawal
        if (account.debit(getAmount())) {
            setStatus(TransactionStatus.SUCCESS);
            account.addTransaction(this);  // Audit trail
            return true;
        }
        setStatus(TransactionStatus.FAILED);
        return false;
    }
}

class DepositTransaction extends Transaction {
    public DepositTransaction(String transactionId, double amount, String accountNumber) {
        super(transactionId, TransactionType.DEPOSIT, amount, accountNumber);
    }

    @Override
    public boolean execute(Account account) {
        account.credit(getAmount());
        setStatus(TransactionStatus.SUCCESS);
        account.addTransaction(this);
        return true;  // Deposits always succeed (in this simplified model)
    }
}

class BalanceInquiryTransaction extends Transaction {
    public BalanceInquiryTransaction(String transactionId, String accountNumber) {
        super(transactionId, TransactionType.BALANCE_INQUIRY, 0, accountNumber);  // Amount is 0
    }

    @Override
    public boolean execute(Account account) {
        setStatus(TransactionStatus.SUCCESS);
        account.addTransaction(this);  // Record even inquiries for audit
        return true;
    }
}

class TransferTransaction extends Transaction {
    public TransferTransaction(String transactionId, double amount, String sourceAccount, String destAccount) {
        super(transactionId, TransactionType.TRANSFER, amount, sourceAccount);
        setDestinationAccount(destAccount);
    }

    @Override
    public boolean execute(Account account) {
        // Simplified: In production, this would need access to destination account
        // Would use BankService to coordinate both debit and credit atomically
        if (account.debit(getAmount())) {
            setStatus(TransactionStatus.SUCCESS);
            account.addTransaction(this);
            return true;
        }
        setStatus(TransactionStatus.FAILED);
        return false;
    }
}

// ==================== Transaction Factory ====================
// Why Factory Pattern? Centralizes object creation logic
// Benefits: 1) Client doesn't need to know concrete classes 2) Easy to add new types
// 3) Encapsulates complex creation logic 4) Provides unique IDs

class TransactionFactory {
    private static int transactionCounter = 0;  // Static counter for unique IDs

    // Static factory method - creates appropriate transaction type based on enum
    // Single point of creation ensures consistent transaction ID generation
    public static Transaction createTransaction(TransactionType type, double amount, 
                                               String sourceAccount, String destAccount) {
        // Generate unique ID using timestamp + counter (production would use UUID)
        String transactionId = "TXN" + System.currentTimeMillis() + "_" + (++transactionCounter);
        
        // Switch-case on enum - type-safe, compiler checks all cases
        switch (type) {
            case WITHDRAWAL:
                return new WithdrawalTransaction(transactionId, amount, sourceAccount);
            case DEPOSIT:
                return new DepositTransaction(transactionId, amount, sourceAccount);
            case BALANCE_INQUIRY:
                return new BalanceInquiryTransaction(transactionId, sourceAccount);
            case TRANSFER:
                return new TransferTransaction(transactionId, amount, sourceAccount, destAccount);
            default:
                throw new IllegalArgumentException("Invalid transaction type");
        }
    }
}

// ==================== ATM Components ====================
// Why separate component classes? Single Responsibility + real-world modeling
// Each represents a physical ATM component with specific responsibility

class CardReader {
    // Represents physical card reader hardware
    public Card readCard(String cardNumber) {
        System.out.println("Reading card: " + cardNumber);
        // In real implementation: read magnetic strip/chip, validate card
        return null;  // Placeholder - would return Card object after reading
    }

    public void ejectCard() {
        System.out.println("Ejecting card...");
        // In real implementation: mechanical eject operation
    }
}

class CashDispenser {
    // Represents physical cash dispenser with limited denominations
    private int totalCash;  // Total cash available in ATM
    private Map<Integer, Integer> denominations;  // denomination value -> count

    public CashDispenser(int totalCash) {
        this.totalCash = totalCash;
        this.denominations = new HashMap<>();
        // Initialize with realistic denominations and counts
        denominations.put(100, 100);  // 100 notes of $100
        denominations.put(50, 100);
        denominations.put(20, 100);
        denominations.put(10, 100);
    }

    public boolean canDispense(double amount) {
        return amount <= totalCash && amount > 0;
    }

    // Why synchronized? Multiple transactions shouldn't dispense cash simultaneously
    // Prevents race condition where two transactions try to use same bills
    public synchronized boolean dispenseCash(double amount) {
        if (!canDispense(amount)) {
            System.out.println("Insufficient cash in ATM");
            return false;
        }

        // Calculate which bills to dispense - greedy algorithm
        Map<Integer, Integer> requiredNotes = calculateDenominations((int) amount);
        if (requiredNotes == null) {
            System.out.println("Cannot dispense exact amount");
            return false;  // Can't make exact change with available denominations
        }

        // Deduct dispensed bills from inventory
        for (Map.Entry<Integer, Integer> entry : requiredNotes.entrySet()) {
            int denom = entry.getKey();
            int count = entry.getValue();
            denominations.put(denom, denominations.get(denom) - count);
        }

        totalCash -= (int) amount;
        System.out.println("Dispensing cash: $" + amount);
        return true;
    }

    // Greedy algorithm for denomination calculation
    // Starts with largest bills to minimize number of bills dispensed
    private Map<Integer, Integer> calculateDenominations(int amount) {
        Map<Integer, Integer> result = new HashMap<>();
        List<Integer> denoms = new ArrayList<>(denominations.keySet());
        Collections.sort(denoms, Collections.reverseOrder());  // Sort descending

        for (int denom : denoms) {
            if (amount >= denom && denominations.get(denom) > 0) {
                int count = Math.min(amount / denom, denominations.get(denom));
                result.put(denom, count);
                amount -= count * denom;
            }
        }

        return amount == 0 ? result : null;  // null if exact change can't be made
    }

    public int getTotalCash() {
        return totalCash;
    }
}

class Screen {
    // Represents display screen - abstraction over UI output
    public void displayMessage(String message) {
        System.out.println("SCREEN: " + message);
    }

    public void displayBalance(double balance) {
        System.out.println("SCREEN: Your balance is: $" + String.format("%.2f", balance));
    }

    public void displayTransactionStatus(boolean success, String message) {
        System.out.println("SCREEN: Transaction " + (success ? "SUCCESSFUL" : "FAILED") + " - " + message);
    }
}

class Keypad {
    // Represents physical keypad - abstraction over input
    private Scanner scanner;

    public Keypad() {
        this.scanner = new Scanner(System.in);
    }

    public String getInput() {
        return scanner.nextLine();
    }

    public int getNumericInput() {
        return scanner.nextInt();
    }
}

// ==================== Bank Service ====================
// Why separate service? Separation of Concerns - centralizes banking operations
// In production: This would be a remote service/API call to actual bank backend

class BankService {
    // In-memory storage for demo - production would use database
    private Map<String, Account> accounts;
    private Map<String, Card> cards;
    private Map<String, Customer> customers;

    public BankService() {
        this.accounts = new HashMap<>();
        this.cards = new HashMap<>();
        this.customers = new HashMap<>();
    }

    // Registers customer and indexes their accounts/cards for quick lookup
    public void addCustomer(Customer customer) {
        customers.put(customer.getCustomerId(), customer);
        for (Account account : customer.getAccounts()) {
            accounts.put(account.getAccountNumber(), account);
        }
        for (Card card : customer.getCards()) {
            cards.put(card.getCardNumber(), card);
        }
    }

    // Validates card and PIN - security check
    public boolean authenticateCard(String cardNumber, String pin) {
        Card card = cards.get(cardNumber);
        if (card == null || card.isExpired()) {
            return false;  // Card not found or expired
        }
        return card.getPin().equals(pin);  // Simple comparison - production would hash
    }

    public Account getAccount(String accountNumber) {
        return accounts.get(accountNumber);
    }

    public Card getCard(String cardNumber) {
        return cards.get(cardNumber);
    }

    // Delegates to transaction's execute method - polymorphism in action
    public boolean processTransaction(Transaction transaction, Account account) {
        return transaction.execute(account);
    }
}

// ==================== ATM State Pattern ====================
// Why State Pattern? ATM behavior changes based on current state
// Benefits: 1) Eliminates complex if-else chains 2) Each state encapsulates its logic
// 3) Easy to add new states 4) Adheres to Open/Closed Principle

// Interface defines contract for all states - what operations ATM can perform
interface IATMState {
    void insertCard(ATM atm, Card card);
    void enterPin(ATM atm, String pin);
    void selectTransaction(ATM atm, TransactionType type);
    void executeTransaction(ATM atm, double amount);
    void ejectCard(ATM atm);
}

// Idle State - waiting for card insertion
class IdleState implements IATMState {
    @Override
    public void insertCard(ATM atm, Card card) {
        atm.getScreen().displayMessage("Card inserted. Please enter PIN.");
        atm.setCurrentCard(card);
        atm.setState(new CardInsertedState());  // Transition to next state
    }

    @Override
    public void enterPin(ATM atm, String pin) {
        atm.getScreen().displayMessage("Please insert card first.");
        // Invalid operation in this state - user must insert card first
    }

    @Override
    public void selectTransaction(ATM atm, TransactionType type) {
        atm.getScreen().displayMessage("Please insert card first.");
    }

    @Override
    public void executeTransaction(ATM atm, double amount) {
        atm.getScreen().displayMessage("Please insert card first.");
    }

    @Override
    public void ejectCard(ATM atm) {
        atm.getScreen().displayMessage("No card to eject.");
    }
}

// Card Inserted State - waiting for PIN entry
class CardInsertedState implements IATMState {
    private int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 3;  // Security measure

    @Override
    public void insertCard(ATM atm, Card card) {
        atm.getScreen().displayMessage("Card already inserted.");
        // Can't insert another card
    }

    @Override
    public void enterPin(ATM atm, String pin) {
        // Validate PIN against bank service
        if (atm.getBankService().authenticateCard(atm.getCurrentCard().getCardNumber(), pin)) {
            atm.getScreen().displayMessage("PIN verified successfully.");
            String accountNumber = atm.getCurrentCard().getAccountNumber();
            Account account = atm.getBankService().getAccount(accountNumber);
            atm.setCurrentAccount(account);
            atm.setState(new PinEnteredState());  // Move to authenticated state
        } else {
            attemptCount++;
            if (attemptCount >= MAX_ATTEMPTS) {
                // Security: Block card after max attempts
                atm.getScreen().displayMessage("Maximum attempts exceeded. Card blocked.");
                atm.ejectCard();
            } else {
                atm.getScreen().displayMessage("Invalid PIN. Attempts remaining: " + (MAX_ATTEMPTS - attemptCount));
            }
        }
    }

    @Override
    public void selectTransaction(ATM atm, TransactionType type) {
        atm.getScreen().displayMessage("Please enter PIN first.");
    }

    @Override
    public void executeTransaction(ATM atm, double amount) {
        atm.getScreen().displayMessage("Please enter PIN first.");
    }

    @Override
    public void ejectCard(ATM atm) {
        atm.getCardReader().ejectCard();
        atm.reset();  // Return to idle state
    }
}

// PIN Entered State - authenticated, waiting for transaction selection
class PinEnteredState implements IATMState {
    @Override
    public void insertCard(ATM atm, Card card) {
        atm.getScreen().displayMessage("Card already inserted.");
    }

    @Override
    public void enterPin(ATM atm, String pin) {
        atm.getScreen().displayMessage("PIN already verified.");
    }

    @Override
    public void selectTransaction(ATM atm, TransactionType type) {
        atm.setCurrentTransactionType(type);
        atm.setState(new TransactionSelectedState());  // Move to transaction state
        atm.getScreen().displayMessage("Transaction selected: " + type);
        
        // Balance inquiry doesn't need amount, execute immediately
        if (type == TransactionType.BALANCE_INQUIRY) {
            atm.executeTransaction(0);
        } else {
            atm.getScreen().displayMessage("Please enter amount:");
        }
    }

    @Override
    public void executeTransaction(ATM atm, double amount) {
        atm.getScreen().displayMessage("Please select transaction type first.");
    }

    @Override
    public void ejectCard(ATM atm) {
        atm.getCardReader().ejectCard();
        atm.reset();
    }
}

// Transaction Selected State - processing transaction
class TransactionSelectedState implements IATMState {
    @Override
    public void insertCard(ATM atm, Card card) {
        atm.getScreen().displayMessage("Transaction in progress.");
    }

    @Override
    public void enterPin(ATM atm, String pin) {
        atm.getScreen().displayMessage("Transaction in progress.");
    }

    @Override
    public void selectTransaction(ATM atm, TransactionType type) {
        atm.getScreen().displayMessage("Transaction already selected.");
    }

    @Override
    public void executeTransaction(ATM atm, double amount) {
        // Use Factory to create appropriate transaction type
        Transaction transaction = TransactionFactory.createTransaction(
            atm.getCurrentTransactionType(),
            amount,
            atm.getCurrentAccount().getAccountNumber(),
            null
        );

        // Process transaction through bank service
        boolean success = atm.getBankService().processTransaction(transaction, atm.getCurrentAccount());

        if (success) {
            if (atm.getCurrentTransactionType() == TransactionType.WITHDRAWAL) {
                // Additional step for withdrawal - dispense physical cash
                if (atm.getCashDispenser().dispenseCash(amount)) {
                    atm.getScreen().displayTransactionStatus(true, "Amount withdrawn: $" + amount);
                } else {
                    // Rollback if cash can't be dispensed (atomicity)
                    atm.getCurrentAccount().credit(amount);
                    atm.getScreen().displayTransactionStatus(false, "Unable to dispense cash");
                }
            } else if (atm.getCurrentTransactionType() == TransactionType.BALANCE_INQUIRY) {
                atm.getScreen().displayBalance(atm.getCurrentAccount().getBalance());
            } else {
                atm.getScreen().displayTransactionStatus(true, "Transaction completed");
            }
        } else {
            atm.getScreen().displayTransactionStatus(false, "Insufficient funds or invalid transaction");
        }

        // Return to authenticated state - allows multiple transactions
        atm.setState(new PinEnteredState());
    }

    @Override
    public void ejectCard(ATM atm) {
        atm.getCardReader().ejectCard();
        atm.reset();
    }
}

// ==================== ATM Main Class (Singleton) ====================
// Why Singleton Pattern? Only one ATM instance should exist per physical machine
// Benefits: 1) Controlled access 2) Global point of access 3) Lazy initialization

class ATM {
    private static ATM instance;  // Single instance stored here
    private String atmId;
    private String location;
    private BankService bankService;
    
    // ATM has-a relationship with hardware components (Composition)
    private CardReader cardReader;
    private CashDispenser cashDispenser;
    private Screen screen;
    private Keypad keypad;
    
    // State pattern components
    private IATMState currentState;
    private Card currentCard;
    private Account currentAccount;
    private TransactionType currentTransactionType;

    // Private constructor prevents direct instantiation (Singleton enforcement)
    private ATM(String atmId, String location, BankService bankService) {
        this.atmId = atmId;
        this.location = location;
        this.bankService = bankService;
        this.cardReader = new CardReader();
        this.cashDispenser = new CashDispenser(50000);  // Initialize with $50,000
        this.screen = new Screen();
        this.keypad = new Keypad();
        this.currentState = new IdleState();  // Start in idle state
    }

    // Thread-safe singleton getter (synchronized prevents multiple instances in concurrent access)
    public static synchronized ATM getInstance(String atmId, String location, BankService bankService) {
        if (instance == null) {
            instance = new ATM(atmId, location, bankService);
        }
        return instance;
    }

    // State management - allows dynamic state changes
    public void setState(IATMState state) {
        this.currentState = state;
    }

    public IATMState getCurrentState() {
        return currentState;
    }

    // Public API - delegates to current state (State Pattern in action)
    // Same method name, different behavior based on state
    public void insertCard(Card card) {
        currentState.insertCard(this, card);
    }

    public void enterPin(String pin) {
        currentState.enterPin(this, pin);
    }

    public void selectTransaction(TransactionType type) {
        currentState.selectTransaction(this, type);
    }

    public void executeTransaction(double amount) {
        currentState.executeTransaction(this, amount);
    }

    public void ejectCard() {
        currentState.ejectCard(this);
    }

    // Resets ATM to initial state after transaction completion
    public void reset() {
        this.currentCard = null;
        this.currentAccount = null;
        this.currentTransactionType = null;
        this.currentState = new IdleState();
        screen.displayMessage("Thank you for using our ATM. Please take your card.");
    }

    // Getters for components - provides access without exposing implementation
    public String getAtmId() { return atmId; }
    public String getLocation() { return location; }
    public BankService getBankService() { return bankService; }
    public CardReader getCardReader() { return cardReader; }
    public CashDispenser getCashDispenser() { return cashDispenser; }
    public Screen getScreen() { return screen; }
    public Keypad getKeypad() { return keypad; }
    public Card getCurrentCard() { return currentCard; }
    public Account getCurrentAccount() { return currentAccount; }
    public TransactionType getCurrentTransactionType() { return currentTransactionType; }

    // Setters for state management
    public void setCurrentCard(Card card) { this.currentCard = card; }
    public void setCurrentAccount(Account account) { this.currentAccount = account; }
    public void setCurrentTransactionType(TransactionType type) { this.currentTransactionType = type; }
}

// ==================== Demo/Main Class ====================
// Demonstrates complete ATM workflow with all major operations

public class ATMSystem {
    public static void main(String[] args) {
        // Initialize Bank Service (would be remote service in production)
        BankService bankService = new BankService();

        // Create test customer with account and card
        Customer customer = new Customer("CUST001", "John Doe", "john@example.com", "1234567890");
        
        Account account = new Account("ACC001", "John Doe", 5000.00);
        customer.addAccount(account);
        
        // Create card with 2-year expiry
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 2);
        Card card = new Card("1234567890123456", "123", calendar.getTime(), "1234", "ACC001");
        customer.addCard(card);
        
        bankService.addCustomer(customer);

        // Initialize ATM (Singleton)
        ATM atm = ATM.getInstance("ATM001", "Downtown Branch", bankService);

        // ===== Simulate complete ATM session =====
        System.out.println("=== ATM System Demo ===\n");

        // 1. Insert card (Idle -> CardInserted state transition)
        atm.insertCard(card);

        // 2. Enter PIN (CardInserted -> PinEntered state transition)
        atm.enterPin("1234");

        // 3. Check balance (demonstrates BalanceInquiryTransaction)
        System.out.println("\n--- Balance Inquiry ---");
        atm.selectTransaction(TransactionType.BALANCE_INQUIRY);

        // 4. Withdraw money (demonstrates WithdrawalTransaction + cash dispensing)
        System.out.println("\n--- Withdrawal ---");
        atm.selectTransaction(TransactionType.WITHDRAWAL);
        atm.executeTransaction(500.00);

        // 5. Check balance again (should show reduced amount)
        System.out.println("\n--- Balance Inquiry After Withdrawal ---");
        atm.selectTransaction(TransactionType.BALANCE_INQUIRY);

        // 6. Deposit money (demonstrates DepositTransaction)
        System.out.println("\n--- Deposit ---");
        atm.selectTransaction(TransactionType.DEPOSIT);
        atm.executeTransaction(1000.00);

        // 7. Final balance check (should show increased amount)
        System.out.println("\n--- Final Balance ---");
        atm.selectTransaction(TransactionType.BALANCE_INQUIRY);

        // 8. Eject card (returns to Idle state)
        System.out.println("\n--- Ejecting Card ---");
        atm.ejectCard();

        // Print complete transaction history (demonstrates audit trail)
        System.out.println("\n=== Transaction History ===");
        for (Transaction txn : account.getTransactionHistory()) {
            System.out.println("Transaction ID: " + txn.getTransactionId() + 
                             ", Type: " + txn.getType() + 
                             ", Amount: $" + txn.getAmount() + 
                             ", Status: " + txn.getStatus() +
                             ", Time: " + txn.getTimestamp());
        }
    }
}
