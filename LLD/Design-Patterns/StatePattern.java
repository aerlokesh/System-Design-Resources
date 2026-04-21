import java.util.*;

// ==================== STATE PATTERN ====================
// Definition: Allows an object to alter its behavior when its internal state changes.
// The object will appear to change its class.
//
// REAL System Design Interview Examples:
// 1. Order Lifecycle - Placed→Paid→Shipped→Delivered→Returned
// 2. Circuit Breaker - Closed→Open→HalfOpen
// 3. TCP Connection - Listen→SynSent→Established→Closing→Closed
// 4. Document Workflow - Draft→Review→Approved→Published
// 5. Player/Media State - Stopped→Playing→Paused→Buffering
//
// Interview Use Cases:
// - Design E-Commerce: Order state machine
// - Design Circuit Breaker: State transitions for fault tolerance
// - Design Workflow Engine: Document approval states
// - Design Streaming Service: Media player state management
// - Design Ticket System: Ticket lifecycle management

// ==================== EXAMPLE 1: ORDER LIFECYCLE ====================
// Used in: Amazon, Shopify, any e-commerce
// Interview Question: Design an order management system

interface OrderState {
    void next(OrderContext order);
    void previous(OrderContext order);
    void cancel(OrderContext order);
    String getStateName();
}

class OrderContext {
    private OrderState state;
    private String orderId;
    private List<String> stateHistory = new ArrayList<>();

    public OrderContext(String orderId) {
        this.orderId = orderId;
        this.state = new PlacedState();
        stateHistory.add(state.getStateName());
    }

    public void setState(OrderState state) {
        this.state = state;
        stateHistory.add(state.getStateName());
        System.out.printf("   📋 Order %s → %s%n", orderId, state.getStateName());
    }

    public void next() { state.next(this); }
    public void previous() { state.previous(this); }
    public void cancel() { state.cancel(this); }
    public String getStateName() { return state.getStateName(); }
    public String getOrderId() { return orderId; }
    public List<String> getHistory() { return stateHistory; }
}

class PlacedState implements OrderState {
    @Override
    public void next(OrderContext order) {
        System.out.println("   💳 Payment processing...");
        order.setState(new PaidState());
    }
    @Override
    public void previous(OrderContext order) {
        System.out.println("   ⚠️  Cannot go back from Placed state");
    }
    @Override
    public void cancel(OrderContext order) {
        System.out.println("   ❌ Order cancelled before payment");
        order.setState(new CancelledState());
    }
    @Override
    public String getStateName() { return "PLACED"; }
}

class PaidState implements OrderState {
    @Override
    public void next(OrderContext order) {
        System.out.println("   📦 Preparing shipment...");
        order.setState(new ShippedState());
    }
    @Override
    public void previous(OrderContext order) {
        System.out.println("   💰 Refunding payment...");
        order.setState(new PlacedState());
    }
    @Override
    public void cancel(OrderContext order) {
        System.out.println("   ❌ Order cancelled, initiating refund...");
        order.setState(new CancelledState());
    }
    @Override
    public String getStateName() { return "PAID"; }
}

class ShippedState implements OrderState {
    @Override
    public void next(OrderContext order) {
        System.out.println("   ✅ Package delivered!");
        order.setState(new DeliveredState());
    }
    @Override
    public void previous(OrderContext order) {
        System.out.println("   ⚠️  Cannot revert shipment");
    }
    @Override
    public void cancel(OrderContext order) {
        System.out.println("   ⚠️  Cannot cancel shipped order, please return after delivery");
    }
    @Override
    public String getStateName() { return "SHIPPED"; }
}

class DeliveredState implements OrderState {
    @Override
    public void next(OrderContext order) {
        System.out.println("   📝 Order completed and archived");
        order.setState(new CompletedState());
    }
    @Override
    public void previous(OrderContext order) {
        System.out.println("   ↩️  Initiating return...");
        order.setState(new ReturnedState());
    }
    @Override
    public void cancel(OrderContext order) {
        System.out.println("   ↩️  Too late to cancel, initiating return instead...");
        order.setState(new ReturnedState());
    }
    @Override
    public String getStateName() { return "DELIVERED"; }
}

class ReturnedState implements OrderState {
    @Override
    public void next(OrderContext order) {
        System.out.println("   💰 Return processed, refund issued");
        order.setState(new CompletedState());
    }
    @Override
    public void previous(OrderContext order) {
        System.out.println("   ⚠️  Cannot revert return");
    }
    @Override
    public void cancel(OrderContext order) {
        System.out.println("   ⚠️  Return already in progress");
    }
    @Override
    public String getStateName() { return "RETURNED"; }
}

class CancelledState implements OrderState {
    @Override
    public void next(OrderContext order) { System.out.println("   ⚠️  Order is cancelled, no further actions"); }
    @Override
    public void previous(OrderContext order) { System.out.println("   ⚠️  Order is cancelled, cannot revert"); }
    @Override
    public void cancel(OrderContext order) { System.out.println("   ⚠️  Already cancelled"); }
    @Override
    public String getStateName() { return "CANCELLED"; }
}

class CompletedState implements OrderState {
    @Override
    public void next(OrderContext order) { System.out.println("   ⚠️  Order is complete"); }
    @Override
    public void previous(OrderContext order) { System.out.println("   ⚠️  Order is complete"); }
    @Override
    public void cancel(OrderContext order) { System.out.println("   ⚠️  Cannot cancel completed order"); }
    @Override
    public String getStateName() { return "COMPLETED"; }
}

// ==================== EXAMPLE 2: CIRCUIT BREAKER ====================
// Used in: Netflix Hystrix, Resilience4j, any microservice
// Interview Question: Design a circuit breaker for microservices

interface CircuitState {
    boolean allowRequest(CircuitBreakerContext cb);
    void onSuccess(CircuitBreakerContext cb);
    void onFailure(CircuitBreakerContext cb);
    String getStateName();
}

class CircuitBreakerContext {
    private CircuitState state;
    private int failureCount = 0;
    private int successCount = 0;
    private int failureThreshold;
    private int successThreshold;
    private long lastFailureTime = 0;
    private long cooldownPeriodMs;

    public CircuitBreakerContext(int failureThreshold, int successThreshold, long cooldownPeriodMs) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.cooldownPeriodMs = cooldownPeriodMs;
        this.state = new ClosedState();
    }

    public void setState(CircuitState state) {
        System.out.printf("   🔌 Circuit Breaker → %s%n", state.getStateName());
        this.state = state;
    }

    public boolean allowRequest() { return state.allowRequest(this); }
    public void recordSuccess() { state.onSuccess(this); }
    public void recordFailure() { state.onFailure(this); }

    public int getFailureCount() { return failureCount; }
    public void incrementFailure() { failureCount++; lastFailureTime = System.currentTimeMillis(); }
    public void resetFailures() { failureCount = 0; }
    public int getSuccessCount() { return successCount; }
    public void incrementSuccess() { successCount++; }
    public void resetSuccesses() { successCount = 0; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public boolean isCooldownElapsed() {
        return System.currentTimeMillis() - lastFailureTime > cooldownPeriodMs;
    }
    public String getStateName() { return state.getStateName(); }
}

class ClosedState implements CircuitState {
    @Override
    public boolean allowRequest(CircuitBreakerContext cb) { return true; }

    @Override
    public void onSuccess(CircuitBreakerContext cb) {
        cb.resetFailures();
        System.out.println("   ✅ [CLOSED] Request succeeded, failure count reset");
    }

    @Override
    public void onFailure(CircuitBreakerContext cb) {
        cb.incrementFailure();
        System.out.printf("   ❌ [CLOSED] Failure %d/%d%n", cb.getFailureCount(), cb.getFailureThreshold());
        if (cb.getFailureCount() >= cb.getFailureThreshold()) {
            System.out.println("   🚨 Failure threshold reached! Opening circuit...");
            cb.setState(new OpenState());
        }
    }

    @Override
    public String getStateName() { return "CLOSED"; }
}

class OpenState implements CircuitState {
    @Override
    public boolean allowRequest(CircuitBreakerContext cb) {
        if (cb.isCooldownElapsed()) {
            System.out.println("   ⏱️  Cooldown elapsed, transitioning to HALF-OPEN...");
            cb.setState(new HalfOpenState());
            return true;
        }
        System.out.println("   🚫 [OPEN] Request rejected, circuit is open");
        return false;
    }

    @Override
    public void onSuccess(CircuitBreakerContext cb) { /* shouldn't happen in open state */ }

    @Override
    public void onFailure(CircuitBreakerContext cb) { /* shouldn't happen in open state */ }

    @Override
    public String getStateName() { return "OPEN"; }
}

class HalfOpenState implements CircuitState {
    @Override
    public boolean allowRequest(CircuitBreakerContext cb) {
        System.out.println("   🔄 [HALF-OPEN] Allowing probe request...");
        return true;
    }

    @Override
    public void onSuccess(CircuitBreakerContext cb) {
        cb.incrementSuccess();
        System.out.printf("   ✅ [HALF-OPEN] Success %d/%d%n", cb.getSuccessCount(), cb.getSuccessThreshold());
        if (cb.getSuccessCount() >= cb.getSuccessThreshold()) {
            System.out.println("   ✅ Recovery confirmed! Closing circuit...");
            cb.resetFailures();
            cb.resetSuccesses();
            cb.setState(new ClosedState());
        }
    }

    @Override
    public void onFailure(CircuitBreakerContext cb) {
        System.out.println("   ❌ [HALF-OPEN] Probe failed! Re-opening circuit...");
        cb.resetSuccesses();
        cb.setState(new OpenState());
    }

    @Override
    public String getStateName() { return "HALF-OPEN"; }
}

// ==================== EXAMPLE 3: DOCUMENT WORKFLOW ====================
// Used in: CMS systems, JIRA, GitHub PR reviews
// Interview Question: Design a document management/workflow system

interface DocumentState {
    void edit(DocumentContext doc);
    void submitForReview(DocumentContext doc);
    void approve(DocumentContext doc);
    void reject(DocumentContext doc);
    void publish(DocumentContext doc);
    String getStateName();
}

class DocumentContext {
    private DocumentState state;
    private String documentId;
    private String title;

    public DocumentContext(String documentId, String title) {
        this.documentId = documentId;
        this.title = title;
        this.state = new DraftDocState();
    }

    public void setState(DocumentState state) {
        System.out.printf("   📄 Doc '%s' → %s%n", title, state.getStateName());
        this.state = state;
    }

    public void edit() { state.edit(this); }
    public void submitForReview() { state.submitForReview(this); }
    public void approve() { state.approve(this); }
    public void reject() { state.reject(this); }
    public void publish() { state.publish(this); }
    public String getStateName() { return state.getStateName(); }
}

class DraftDocState implements DocumentState {
    @Override
    public void edit(DocumentContext doc) {
        System.out.println("   ✏️  Editing draft...");
    }
    @Override
    public void submitForReview(DocumentContext doc) {
        System.out.println("   📤 Submitting for review...");
        doc.setState(new ReviewDocState());
    }
    @Override
    public void approve(DocumentContext doc) { System.out.println("   ⚠️  Cannot approve a draft"); }
    @Override
    public void reject(DocumentContext doc) { System.out.println("   ⚠️  Cannot reject a draft"); }
    @Override
    public void publish(DocumentContext doc) { System.out.println("   ⚠️  Must be approved first"); }
    @Override
    public String getStateName() { return "DRAFT"; }
}

class ReviewDocState implements DocumentState {
    @Override
    public void edit(DocumentContext doc) { System.out.println("   ⚠️  Cannot edit during review"); }
    @Override
    public void submitForReview(DocumentContext doc) { System.out.println("   ⚠️  Already in review"); }
    @Override
    public void approve(DocumentContext doc) {
        System.out.println("   ✅ Document approved!");
        doc.setState(new ApprovedDocState());
    }
    @Override
    public void reject(DocumentContext doc) {
        System.out.println("   ❌ Document rejected, sending back to draft...");
        doc.setState(new DraftDocState());
    }
    @Override
    public void publish(DocumentContext doc) { System.out.println("   ⚠️  Must be approved first"); }
    @Override
    public String getStateName() { return "IN_REVIEW"; }
}

class ApprovedDocState implements DocumentState {
    @Override
    public void edit(DocumentContext doc) {
        System.out.println("   ✏️  Editing approved doc, reverting to draft...");
        doc.setState(new DraftDocState());
    }
    @Override
    public void submitForReview(DocumentContext doc) { System.out.println("   ⚠️  Already approved"); }
    @Override
    public void approve(DocumentContext doc) { System.out.println("   ⚠️  Already approved"); }
    @Override
    public void reject(DocumentContext doc) {
        System.out.println("   ❌ Approval revoked, back to draft...");
        doc.setState(new DraftDocState());
    }
    @Override
    public void publish(DocumentContext doc) {
        System.out.println("   🌐 Publishing document!");
        doc.setState(new PublishedDocState());
    }
    @Override
    public String getStateName() { return "APPROVED"; }
}

class PublishedDocState implements DocumentState {
    @Override
    public void edit(DocumentContext doc) {
        System.out.println("   ✏️  Creating new draft version...");
        doc.setState(new DraftDocState());
    }
    @Override
    public void submitForReview(DocumentContext doc) { System.out.println("   ⚠️  Already published"); }
    @Override
    public void approve(DocumentContext doc) { System.out.println("   ⚠️  Already published"); }
    @Override
    public void reject(DocumentContext doc) {
        System.out.println("   ❌ Unpublishing document...");
        doc.setState(new DraftDocState());
    }
    @Override
    public void publish(DocumentContext doc) { System.out.println("   ⚠️  Already published"); }
    @Override
    public String getStateName() { return "PUBLISHED"; }
}

// ==================== EXAMPLE 4: MEDIA PLAYER ====================
// Used in: Spotify, Netflix player, any media app
// Interview Question: Design a music/video streaming player

interface PlayerState {
    void play(MediaPlayerContext player);
    void pause(MediaPlayerContext player);
    void stop(MediaPlayerContext player);
    void buffer(MediaPlayerContext player);
    String getStateName();
}

class MediaPlayerContext {
    private PlayerState state;
    private String currentTrack;

    public MediaPlayerContext() {
        this.state = new StoppedPlayerState();
    }

    public void setState(PlayerState state) {
        System.out.printf("   🎵 Player → %s%n", state.getStateName());
        this.state = state;
    }

    public void loadTrack(String track) { this.currentTrack = track; }
    public void play() { state.play(this); }
    public void pause() { state.pause(this); }
    public void stop() { state.stop(this); }
    public void buffer() { state.buffer(this); }
    public String getCurrentTrack() { return currentTrack; }
    public String getStateName() { return state.getStateName(); }
}

class StoppedPlayerState implements PlayerState {
    @Override
    public void play(MediaPlayerContext player) {
        System.out.printf("   ▶️  Loading '%s'...%n", player.getCurrentTrack());
        player.setState(new BufferingPlayerState());
    }
    @Override
    public void pause(MediaPlayerContext player) { System.out.println("   ⚠️  Already stopped"); }
    @Override
    public void stop(MediaPlayerContext player) { System.out.println("   ⚠️  Already stopped"); }
    @Override
    public void buffer(MediaPlayerContext player) { System.out.println("   ⚠️  Nothing to buffer"); }
    @Override
    public String getStateName() { return "STOPPED"; }
}

class PlayingPlayerState implements PlayerState {
    @Override
    public void play(MediaPlayerContext player) { System.out.println("   ⚠️  Already playing"); }
    @Override
    public void pause(MediaPlayerContext player) {
        System.out.println("   ⏸️  Paused");
        player.setState(new PausedPlayerState());
    }
    @Override
    public void stop(MediaPlayerContext player) {
        System.out.println("   ⏹️  Stopped playback");
        player.setState(new StoppedPlayerState());
    }
    @Override
    public void buffer(MediaPlayerContext player) {
        System.out.println("   ⏳ Network slow, buffering...");
        player.setState(new BufferingPlayerState());
    }
    @Override
    public String getStateName() { return "PLAYING"; }
}

class PausedPlayerState implements PlayerState {
    @Override
    public void play(MediaPlayerContext player) {
        System.out.println("   ▶️  Resumed playback");
        player.setState(new PlayingPlayerState());
    }
    @Override
    public void pause(MediaPlayerContext player) { System.out.println("   ⚠️  Already paused"); }
    @Override
    public void stop(MediaPlayerContext player) {
        System.out.println("   ⏹️  Stopped");
        player.setState(new StoppedPlayerState());
    }
    @Override
    public void buffer(MediaPlayerContext player) { System.out.println("   ⚠️  Paused, not buffering"); }
    @Override
    public String getStateName() { return "PAUSED"; }
}

class BufferingPlayerState implements PlayerState {
    @Override
    public void play(MediaPlayerContext player) {
        System.out.println("   ▶️  Buffer complete, playing!");
        player.setState(new PlayingPlayerState());
    }
    @Override
    public void pause(MediaPlayerContext player) { System.out.println("   ⚠️  Still buffering..."); }
    @Override
    public void stop(MediaPlayerContext player) {
        System.out.println("   ⏹️  Stopped buffering");
        player.setState(new StoppedPlayerState());
    }
    @Override
    public void buffer(MediaPlayerContext player) { System.out.println("   ⏳ Still buffering..."); }
    @Override
    public String getStateName() { return "BUFFERING"; }
}

// ==================== DEMO ====================

public class StatePattern {
    public static void main(String[] args) {
        System.out.println("========== STATE PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: ORDER LIFECYCLE =====
        System.out.println("===== EXAMPLE 1: ORDER LIFECYCLE (E-Commerce) =====\n");

        OrderContext order1 = new OrderContext("ORD-001");
        System.out.println("Happy path:");
        order1.next(); // Placed → Paid
        order1.next(); // Paid → Shipped
        order1.next(); // Shipped → Delivered
        order1.next(); // Delivered → Completed
        System.out.println("   History: " + order1.getHistory());

        System.out.println();
        OrderContext order2 = new OrderContext("ORD-002");
        System.out.println("Cancel path:");
        order2.next();   // Placed → Paid
        order2.cancel(); // Paid → Cancelled
        order2.next();   // Cannot proceed

        System.out.println();
        OrderContext order3 = new OrderContext("ORD-003");
        System.out.println("Return path:");
        order3.next();     // Placed → Paid
        order3.next();     // Paid → Shipped
        order3.next();     // Shipped → Delivered
        order3.previous(); // Delivered → Returned
        order3.next();     // Returned → Completed

        // ===== EXAMPLE 2: CIRCUIT BREAKER =====
        System.out.println("\n\n===== EXAMPLE 2: CIRCUIT BREAKER (Microservices) =====\n");

        CircuitBreakerContext cb = new CircuitBreakerContext(3, 2, 100); // 3 failures to open, 2 successes to close
        System.out.println("State: " + cb.getStateName());

        // Normal operations
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure(); // This should open the circuit

        // Try request while open
        cb.allowRequest();

        // Wait for cooldown and try again
        try { Thread.sleep(150); } catch (InterruptedException e) {}
        cb.allowRequest(); // Should transition to HALF-OPEN

        // Probe requests in half-open
        cb.recordSuccess();
        cb.recordSuccess(); // Should close the circuit

        // ===== EXAMPLE 3: DOCUMENT WORKFLOW =====
        System.out.println("\n\n===== EXAMPLE 3: DOCUMENT WORKFLOW (CMS/JIRA) =====\n");

        DocumentContext doc = new DocumentContext("DOC-001", "System Design Guide");
        doc.edit();
        doc.submitForReview();
        doc.reject();                // Back to draft
        doc.edit();
        doc.submitForReview();
        doc.approve();
        doc.publish();
        System.out.println("   Final state: " + doc.getStateName());

        // ===== EXAMPLE 4: MEDIA PLAYER =====
        System.out.println("\n\n===== EXAMPLE 4: MEDIA PLAYER (Spotify/Netflix) =====\n");

        MediaPlayerContext player = new MediaPlayerContext();
        player.loadTrack("Bohemian Rhapsody - Queen");
        player.play();   // Stopped → Buffering
        player.play();   // Buffering → Playing
        player.pause();  // Playing → Paused
        player.play();   // Paused → Playing
        player.buffer(); // Playing → Buffering (network slow)
        player.play();   // Buffering → Playing
        player.stop();   // Playing → Stopped

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• E-commerce order management (Amazon, Shopify)");
        System.out.println("• Circuit breaker (Netflix Hystrix, Resilience4j)");
        System.out.println("• Document workflow (JIRA, Confluence, GitHub PRs)");
        System.out.println("• Media players (Spotify, Netflix, YouTube)");
    }
}
