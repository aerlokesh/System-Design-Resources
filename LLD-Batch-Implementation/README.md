# LLD Batch Implementation - HelloInterview Delivery Framework

Batch implementations of Low-Level Design problems following the **HelloInterview Delivery Framework**.

Reference: [HelloInterview LLD Delivery Framework](https://www.hellointerview.com/learn/low-level-design/in-a-hurry/delivery)

## Framework Steps (35 min total)
```
1. Requirements    (3-5 min)  → Clarify scope, constraints, edge cases
2. Core Entities   (5 min)    → Identify classes, enums, relationships
3. API/Interface   (5 min)    → Public methods, contracts, signatures
4. Data Flow       (optional) → Sequence of operations
5. Design + Code   (15-20 min)→ Patterns, implementation, thread safety
6. Deep Dives      (5-10 min) → Extensibility, edge cases, trade-offs
```

## Batch 1 - Problems
| # | Problem | Companies | Pattern | Difficulty | Status |
|---|---------|-----------|---------|------------|--------|
| 1 | Elevator System (Single Lift) | Microsoft, Adobe, Oracle | State | Medium | ✅ |
| 2 | Movie Ticket Booking (BookMyShow) | Salesforce, Microsoft, Uber | Observer | Medium | ✅ |
| 3 | Chess Game | Microsoft, Salesforce, Adobe | Factory | Medium | ✅ |

## Batch 2 - Problems (Upcoming)
| # | Problem | Companies | Pattern | Difficulty | Status |
|---|---------|-----------|---------|------------|--------|
| 4 | Parking Lot (Multi-Threaded) | Amazon, Microsoft, Goldman Sachs | Strategy | Medium | 🔄 |
| 5 | Rate Limiter | Atlassian, Microsoft, Oracle | Strategy | Medium | 🔄 |
| 6 | Hit Counter | Google, Amazon, Adobe | Sliding Window | Medium | 🔄 |

## File Structure
```
LLD-Batch-Implementation/
├── README.md
├── batch-1/
│   ├── elevator-system-hello-interview.md      # HELLO framework doc
│   ├── ElevatorSystem.java                     # Java implementation
│   ├── movie-ticket-booking-hello-interview.md # HELLO framework doc
│   ├── MovieTicketBookingSystem.java           # Java implementation
│   ├── chess-game-hello-interview.md           # HELLO framework doc
│   └── ChessGame.java                         # Java implementation
└── batch-2/
    └── (upcoming)
```

## How to Run
```bash
cd batch-1/
javac ElevatorSystem.java && java ElevatorSystem
javac MovieTicketBookingSystem.java && java MovieTicketBookingSystem
javac ChessGame.java && java ChessGame
```
