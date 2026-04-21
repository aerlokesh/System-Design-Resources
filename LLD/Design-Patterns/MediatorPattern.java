import java.util.*;

// ==================== MEDIATOR PATTERN ====================
// Definition: Defines an object that encapsulates how a set of objects interact.
// Promotes loose coupling by keeping objects from referring to each other explicitly.
//
// REAL System Design Interview Examples:
// 1. Chat Room / Group Chat - Mediates messages between users
// 2. Air Traffic Control - Coordinates planes without direct communication
// 3. Microservice Orchestrator - Central coordinator for service calls
// 4. UI Event Manager - Components communicate through mediator
// 5. Auction System - Mediates bids between buyers and seller
//
// Interview Use Cases:
// - Design Chat System: Chat room mediates messages
// - Design Ride Sharing: Matching drivers and riders
// - Design Auction Platform: Bid management and notifications
// - Design Workflow Orchestrator: Coordinate microservice steps
// - Design Trading System: Order matching engine

// ==================== EXAMPLE 1: CHAT ROOM ====================
// Used in: Slack, Discord, WhatsApp groups
// Interview Question: Design a chat/messaging system

interface ChatMediator {
    void sendMessage(String message, ChatUser sender, String roomId);
    void addUser(ChatUser user, String roomId);
    void removeUser(ChatUser user, String roomId);
}

class ChatUser {
    private String userId;
    private String name;
    private ChatMediator mediator;
    private List<String> receivedMessages = new ArrayList<>();

    public ChatUser(String userId, String name, ChatMediator mediator) {
        this.userId = userId;
        this.name = name;
        this.mediator = mediator;
    }

    public void send(String message, String roomId) {
        System.out.printf("   📤 %s sends to [%s]: \"%s\"%n", name, roomId, message);
        mediator.sendMessage(message, this, roomId);
    }

    public void receive(String message, String senderName, String roomId) {
        String formatted = String.format("[%s] %s: %s", roomId, senderName, message);
        receivedMessages.add(formatted);
        System.out.printf("   📥 %s received: \"%s\" from %s in [%s]%n", name, message, senderName, roomId);
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public int getMessageCount() { return receivedMessages.size(); }
}

class ChatRoomMediator implements ChatMediator {
    private Map<String, List<ChatUser>> rooms = new HashMap<>();

    @Override
    public void sendMessage(String message, ChatUser sender, String roomId) {
        List<ChatUser> users = rooms.get(roomId);
        if (users == null) return;

        for (ChatUser user : users) {
            if (!user.getUserId().equals(sender.getUserId())) {
                user.receive(message, sender.getName(), roomId);
            }
        }
    }

    @Override
    public void addUser(ChatUser user, String roomId) {
        rooms.computeIfAbsent(roomId, k -> new ArrayList<>()).add(user);
        System.out.printf("   ✅ %s joined room [%s]%n", user.getName(), roomId);
    }

    @Override
    public void removeUser(ChatUser user, String roomId) {
        List<ChatUser> users = rooms.get(roomId);
        if (users != null) {
            users.remove(user);
            System.out.printf("   ❌ %s left room [%s]%n", user.getName(), roomId);
        }
    }

    public int getRoomSize(String roomId) {
        return rooms.getOrDefault(roomId, Collections.emptyList()).size();
    }
}

// ==================== EXAMPLE 2: RIDE SHARING MATCHER ====================
// Used in: Uber, Lyft, Ola
// Interview Question: Design a ride-sharing/matching system

interface RideMediator {
    void requestRide(Rider rider, String pickup, String destination);
    void registerDriver(Driver driver);
    void acceptRide(Driver driver, String rideId);
}

class Rider {
    private String riderId;
    private String name;
    private RideMediator mediator;

    public Rider(String riderId, String name, RideMediator mediator) {
        this.riderId = riderId;
        this.name = name;
        this.mediator = mediator;
    }

    public void requestRide(String pickup, String destination) {
        System.out.printf("   🧑 %s requesting ride: %s → %s%n", name, pickup, destination);
        mediator.requestRide(this, pickup, destination);
    }

    public void notifyDriverAssigned(String driverName, String eta) {
        System.out.printf("   📱 %s notified: %s is arriving in %s%n", name, driverName, eta);
    }

    public String getRiderId() { return riderId; }
    public String getName() { return name; }
}

class Driver {
    private String driverId;
    private String name;
    private String location;
    private boolean available = true;
    private RideMediator mediator;

    public Driver(String driverId, String name, String location, RideMediator mediator) {
        this.driverId = driverId;
        this.name = name;
        this.location = location;
        this.mediator = mediator;
    }

    public void notifyNewRide(String rideId, String pickup, String destination) {
        System.out.printf("   🚗 %s notified of ride request: %s → %s (ride: %s)%n",
            name, pickup, destination, rideId);
        // Auto-accept for demo
        mediator.acceptRide(this, rideId);
    }

    public String getDriverId() { return driverId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}

class RideMatchingMediator implements RideMediator {
    private List<Driver> availableDrivers = new ArrayList<>();
    private Map<String, Rider> pendingRides = new HashMap<>();

    @Override
    public void registerDriver(Driver driver) {
        availableDrivers.add(driver);
        System.out.printf("   ✅ Driver %s registered at %s%n", driver.getName(), driver.getLocation());
    }

    @Override
    public void requestRide(Rider rider, String pickup, String destination) {
        String rideId = "RIDE-" + UUID.randomUUID().toString().substring(0, 6);
        pendingRides.put(rideId, rider);

        // Find nearest available driver
        Driver nearestDriver = findNearestDriver(pickup);
        if (nearestDriver != null) {
            System.out.printf("   🔍 Matched %s with driver %s%n", rider.getName(), nearestDriver.getName());
            nearestDriver.notifyNewRide(rideId, pickup, destination);
        } else {
            System.out.printf("   ⚠️  No drivers available for %s%n", rider.getName());
        }
    }

    @Override
    public void acceptRide(Driver driver, String rideId) {
        Rider rider = pendingRides.remove(rideId);
        if (rider != null) {
            driver.setAvailable(false);
            System.out.printf("   ✅ %s accepted ride %s%n", driver.getName(), rideId);
            rider.notifyDriverAssigned(driver.getName(), "5 min");
        }
    }

    private Driver findNearestDriver(String pickup) {
        for (Driver driver : availableDrivers) {
            if (driver.isAvailable()) return driver;
        }
        return null;
    }
}

// ==================== EXAMPLE 3: AUCTION SYSTEM ====================
// Used in: eBay, Sotheby's, Ad exchanges
// Interview Question: Design an online auction system

interface AuctionMediator {
    void placeBid(Bidder bidder, String auctionId, double amount);
    String createAuction(String itemName, double startingPrice, String sellerId);
    void closeAuction(String auctionId);
}

class Bidder {
    private String bidderId;
    private String name;

    public Bidder(String bidderId, String name) {
        this.bidderId = bidderId;
        this.name = name;
    }

    public void notifyOutbid(String auctionId, double currentBid) {
        System.out.printf("   📢 %s: You've been outbid on %s! Current bid: $%.2f%n",
            name, auctionId, currentBid);
    }

    public void notifyWon(String auctionId, double winningBid) {
        System.out.printf("   🏆 %s: Congratulations! You won %s at $%.2f%n",
            name, auctionId, winningBid);
    }

    public void notifyLost(String auctionId) {
        System.out.printf("   😞 %s: Auction %s ended, you didn't win%n", name, auctionId);
    }

    public String getBidderId() { return bidderId; }
    public String getName() { return name; }
}

class AuctionHouseMediator implements AuctionMediator {
    private Map<String, Double> currentBids = new HashMap<>();
    private Map<String, Bidder> highestBidders = new HashMap<>();
    private Map<String, Set<Bidder>> auctionParticipants = new HashMap<>();
    private Map<String, String> auctionItems = new HashMap<>();

    @Override
    public String createAuction(String itemName, double startingPrice, String sellerId) {
        String auctionId = "AUC-" + UUID.randomUUID().toString().substring(0, 6);
        currentBids.put(auctionId, startingPrice);
        auctionItems.put(auctionId, itemName);
        auctionParticipants.put(auctionId, new HashSet<>());
        System.out.printf("   🔨 Auction created: %s for '%s' starting at $%.2f%n",
            auctionId, itemName, startingPrice);
        return auctionId;
    }

    @Override
    public void placeBid(Bidder bidder, String auctionId, double amount) {
        double currentBid = currentBids.getOrDefault(auctionId, 0.0);

        if (amount <= currentBid) {
            System.out.printf("   ❌ %s's bid of $%.2f rejected (current: $%.2f)%n",
                bidder.getName(), amount, currentBid);
            return;
        }

        // Notify previous highest bidder
        Bidder previousHighest = highestBidders.get(auctionId);
        if (previousHighest != null && !previousHighest.getBidderId().equals(bidder.getBidderId())) {
            previousHighest.notifyOutbid(auctionId, amount);
        }

        currentBids.put(auctionId, amount);
        highestBidders.put(auctionId, bidder);
        auctionParticipants.get(auctionId).add(bidder);

        System.out.printf("   💰 %s bid $%.2f on %s (%s) — NEW HIGHEST%n",
            bidder.getName(), amount, auctionId, auctionItems.get(auctionId));
    }

    @Override
    public void closeAuction(String auctionId) {
        Bidder winner = highestBidders.get(auctionId);
        double winningBid = currentBids.get(auctionId);

        System.out.printf("%n   🔔 AUCTION %s CLOSED! '%s'%n", auctionId, auctionItems.get(auctionId));

        if (winner != null) {
            winner.notifyWon(auctionId, winningBid);
            // Notify all other participants
            for (Bidder bidder : auctionParticipants.get(auctionId)) {
                if (!bidder.getBidderId().equals(winner.getBidderId())) {
                    bidder.notifyLost(auctionId);
                }
            }
        } else {
            System.out.println("   ⚠️  No bids placed, auction ended without winner");
        }
    }
}

// ==================== EXAMPLE 4: ORDER MATCHING ENGINE ====================
// Used in: Stock exchanges (NYSE, NASDAQ), Crypto exchanges
// Interview Question: Design a stock trading/matching system

interface TradingMediator {
    void placeBuyOrder(TraderParticipant buyer, String symbol, double price, int quantity);
    void placeSellOrder(TraderParticipant seller, String symbol, double price, int quantity);
}

class TraderParticipant {
    private String traderId;
    private String name;

    public TraderParticipant(String traderId, String name) {
        this.traderId = traderId;
        this.name = name;
    }

    public void notifyTradeExecuted(String symbol, double price, int quantity, String side) {
        System.out.printf("   📊 %s: Trade executed — %s %d %s @ $%.2f%n",
            name, side, quantity, symbol, price);
    }

    public String getTraderId() { return traderId; }
    public String getName() { return name; }
}

class OrderMatchingEngine implements TradingMediator {
    // Simplified order book
    private Map<String, TreeMap<Double, List<TradeOrder>>> buyOrders = new HashMap<>();
    private Map<String, TreeMap<Double, List<TradeOrder>>> sellOrders = new HashMap<>();

    static class TradeOrder {
        TraderParticipant trader;
        double price;
        int quantity;
        TradeOrder(TraderParticipant trader, double price, int quantity) {
            this.trader = trader; this.price = price; this.quantity = quantity;
        }
    }

    @Override
    public void placeBuyOrder(TraderParticipant buyer, String symbol, double price, int quantity) {
        System.out.printf("   📈 BUY order: %s wants %d %s @ $%.2f%n", buyer.getName(), quantity, symbol, price);

        TreeMap<Double, List<TradeOrder>> asks = sellOrders.getOrDefault(symbol, new TreeMap<>());
        if (!asks.isEmpty() && asks.firstKey() <= price) {
            // Match with lowest sell order
            Map.Entry<Double, List<TradeOrder>> bestAsk = asks.firstEntry();
            TradeOrder sellOrder = bestAsk.getValue().remove(0);
            if (bestAsk.getValue().isEmpty()) asks.remove(bestAsk.getKey());

            int matched = Math.min(quantity, sellOrder.quantity);
            double matchPrice = sellOrder.price;

            System.out.printf("   ⚡ MATCHED: %d %s @ $%.2f%n", matched, symbol, matchPrice);
            buyer.notifyTradeExecuted(symbol, matchPrice, matched, "BUY");
            sellOrder.trader.notifyTradeExecuted(symbol, matchPrice, matched, "SELL");
        } else {
            // Add to order book
            buyOrders.computeIfAbsent(symbol, k -> new TreeMap<>(Collections.reverseOrder()))
                .computeIfAbsent(price, k -> new ArrayList<>())
                .add(new TradeOrder(buyer, price, quantity));
            System.out.printf("   📋 Added to order book (no match, waiting...)%n");
        }
    }

    @Override
    public void placeSellOrder(TraderParticipant seller, String symbol, double price, int quantity) {
        System.out.printf("   📉 SELL order: %s offers %d %s @ $%.2f%n", seller.getName(), quantity, symbol, price);

        TreeMap<Double, List<TradeOrder>> bids = buyOrders.getOrDefault(symbol, new TreeMap<>(Collections.reverseOrder()));
        if (!bids.isEmpty() && bids.firstKey() >= price) {
            Map.Entry<Double, List<TradeOrder>> bestBid = bids.firstEntry();
            TradeOrder buyOrder = bestBid.getValue().remove(0);
            if (bestBid.getValue().isEmpty()) bids.remove(bestBid.getKey());

            int matched = Math.min(quantity, buyOrder.quantity);
            double matchPrice = buyOrder.price;

            System.out.printf("   ⚡ MATCHED: %d %s @ $%.2f%n", matched, symbol, matchPrice);
            seller.notifyTradeExecuted(symbol, matchPrice, matched, "SELL");
            buyOrder.trader.notifyTradeExecuted(symbol, matchPrice, matched, "BUY");
        } else {
            sellOrders.computeIfAbsent(symbol, k -> new TreeMap<>())
                .computeIfAbsent(price, k -> new ArrayList<>())
                .add(new TradeOrder(seller, price, quantity));
            System.out.printf("   📋 Added to order book (no match, waiting...)%n");
        }
    }
}

// ==================== DEMO ====================

public class MediatorPattern {
    public static void main(String[] args) {
        System.out.println("========== MEDIATOR PATTERN: REAL SYSTEM DESIGN EXAMPLES ==========\n");

        // ===== EXAMPLE 1: CHAT ROOM =====
        System.out.println("===== EXAMPLE 1: CHAT ROOM (Slack/Discord) =====\n");

        ChatRoomMediator chatMediator = new ChatRoomMediator();
        ChatUser alice = new ChatUser("u1", "Alice", chatMediator);
        ChatUser bob = new ChatUser("u2", "Bob", chatMediator);
        ChatUser charlie = new ChatUser("u3", "Charlie", chatMediator);

        chatMediator.addUser(alice, "general");
        chatMediator.addUser(bob, "general");
        chatMediator.addUser(charlie, "general");
        chatMediator.addUser(alice, "engineering");
        chatMediator.addUser(bob, "engineering");

        alice.send("Hello everyone!", "general");
        System.out.println();
        bob.send("Working on the API redesign", "engineering");

        // ===== EXAMPLE 2: RIDE SHARING =====
        System.out.println("\n\n===== EXAMPLE 2: RIDE SHARING MATCHER (Uber/Lyft) =====\n");

        RideMatchingMediator rideMediator = new RideMatchingMediator();
        Driver driver1 = new Driver("d1", "Dave", "Downtown", rideMediator);
        Driver driver2 = new Driver("d2", "Eve", "Airport", rideMediator);
        rideMediator.registerDriver(driver1);
        rideMediator.registerDriver(driver2);

        Rider rider1 = new Rider("r1", "Alice", rideMediator);
        rider1.requestRide("123 Main St", "Airport");

        // ===== EXAMPLE 3: AUCTION SYSTEM =====
        System.out.println("\n\n===== EXAMPLE 3: AUCTION SYSTEM (eBay) =====\n");

        AuctionHouseMediator auctionMediator = new AuctionHouseMediator();
        String auctionId = auctionMediator.createAuction("Vintage Guitar", 100.0, "seller1");

        Bidder bidder1 = new Bidder("b1", "Alice");
        Bidder bidder2 = new Bidder("b2", "Bob");
        Bidder bidder3 = new Bidder("b3", "Charlie");

        auctionMediator.placeBid(bidder1, auctionId, 150.0);
        auctionMediator.placeBid(bidder2, auctionId, 200.0);
        auctionMediator.placeBid(bidder3, auctionId, 175.0); // Rejected
        auctionMediator.placeBid(bidder1, auctionId, 250.0);
        auctionMediator.closeAuction(auctionId);

        // ===== EXAMPLE 4: ORDER MATCHING ENGINE =====
        System.out.println("\n\n===== EXAMPLE 4: ORDER MATCHING ENGINE (Stock Exchange) =====\n");

        OrderMatchingEngine exchange = new OrderMatchingEngine();
        TraderParticipant trader1 = new TraderParticipant("t1", "Alice");
        TraderParticipant trader2 = new TraderParticipant("t2", "Bob");
        TraderParticipant trader3 = new TraderParticipant("t3", "Charlie");

        exchange.placeBuyOrder(trader1, "AAPL", 150.0, 100);
        exchange.placeBuyOrder(trader2, "AAPL", 149.0, 50);
        exchange.placeSellOrder(trader3, "AAPL", 150.0, 100); // Matches Alice's buy

        System.out.println("\n========== DEMO COMPLETE ==========");
        System.out.println("\nThese are REAL examples used in:");
        System.out.println("• Chat systems (Slack, Discord, WhatsApp)");
        System.out.println("• Ride sharing (Uber, Lyft matching)");
        System.out.println("• Auction platforms (eBay, ad exchanges)");
        System.out.println("• Stock exchanges (NYSE, NASDAQ order matching)");
    }
}
