# 🎯 LLD Topic 6: Class Diagrams & UML Basics

> **Low-Level Design Interview — Deep Dive**
> A comprehensive guide covering UML class diagrams — relationships (association, aggregation, composition, inheritance, dependency), multiplicity, access modifiers, and how to sketch clean class diagrams in LLD interviews.

---

## Table of Contents

- [🎯 LLD Topic 6: Class Diagrams \& UML Basics](#-lld-topic-6-class-diagrams--uml-basics)
  - [Table of Contents](#table-of-contents)
  - [Core Concept](#core-concept)
  - [Class Notation](#class-notation)
    - [Example: User Class](#example-user-class)
  - [Relationships](#relationships)
  - [Association](#association)
  - [Aggregation](#aggregation)
  - [Composition](#composition)
    - [Aggregation vs Composition](#aggregation-vs-composition)
  - [Inheritance](#inheritance)
  - [Interface Implementation](#interface-implementation)
  - [Dependency](#dependency)
  - [Multiplicity](#multiplicity)
  - [Access Modifiers](#access-modifiers)
  - [UML for Common LLD Problems](#uml-for-common-lld-problems)
    - [Parking Lot](#parking-lot)
  - [Interview Tips for Drawing UML](#interview-tips-for-drawing-uml)
  - [Common Interview Mistakes](#common-interview-mistakes)
  - [Summary Cheat Sheet](#summary-cheat-sheet)

---

## Core Concept

UML class diagrams are the **visual language** of LLD interviews. They show the **static structure** of your system — what classes exist, what they contain, and how they relate to each other.

```
In LLD interviews, you DON'T need full UML:
  ✅ Class boxes with key fields and methods
  ✅ Relationships with arrows (inheritance, composition, association)
  ✅ Multiplicity (1-to-1, 1-to-many)
  ❌ Sequence diagrams, activity diagrams (unless asked)
  ❌ Every getter/setter/constructor
```

---

## Class Notation

```
┌──────────────────────────┐
│       ClassName          │   ← Class name (bold or centered)
├──────────────────────────┤
│ - privateField: Type     │   ← Fields (attributes)
│ # protectedField: Type   │
│ + publicField: Type      │
├──────────────────────────┤
│ + publicMethod(): RetType│   ← Methods (operations)
│ - privateHelper(): void  │
│ # doSomething(x: int)    │
└──────────────────────────┘

Access Modifiers:
  + public
  - private
  # protected
  ~ package-private (default)

Static members: underlined
Abstract class: <<abstract>> or italicized name
Interface: <<interface>>
```

### Example: User Class

```
┌──────────────────────────────────┐
│           User                    │
├──────────────────────────────────┤
│ - id: String                      │
│ - name: String                    │
│ - email: String                   │
│ - createdAt: LocalDateTime        │
├──────────────────────────────────┤
│ + getId(): String                 │
│ + getName(): String               │
│ + updateEmail(email: String): void│
│ + isActive(): boolean             │
└──────────────────────────────────┘
```

---

## Relationships

```
Relationship Arrows Summary:

  A ──────> B     Association (A uses/knows B)
  A ◇─────> B     Aggregation (A has B, but B can exist alone)
  A ◆─────> B     Composition (A owns B, B can't exist without A)
  A ───────▷ B    Inheritance (A extends B)
  A - - - -▷ B    Implementation (A implements B interface)
  A - - - -> B    Dependency (A temporarily uses B)
```

---

## Association

> **One class has a reference to another class.**

```java
// A Student is associated with a University
class Student {
    private University university;  // Association: Student knows about University
}

class University {
    private List<Student> students;  // Bidirectional association
}
```

```
┌──────────┐         ┌──────────────┐
│ Student  │────────>│  University   │
└──────────┘    *    └──────────────┘
  "A student is enrolled at a university"
```

---

## Aggregation

> **A "HAS-A" relationship where the child can exist independently of the parent.**

```java
// A Department has Employees, but Employees exist without the Department
class Department {
    private List<Employee> employees;  // Aggregation
    
    void addEmployee(Employee emp) { employees.add(emp); }
    void removeEmployee(Employee emp) { employees.remove(emp); }
    // If Department is dissolved, Employees still exist
}
```

```
┌──────────────┐         ┌──────────────┐
│  Department  │◇───────>│   Employee    │
└──────────────┘    *    └──────────────┘
  "Department has employees, but employees can exist without department"
```

---

## Composition

> **A "HAS-A" relationship where the child cannot exist without the parent. Parent controls the lifecycle.**

```java
// An Order owns its LineItems. Delete the Order, LineItems are gone.
class Order {
    private final List<LineItem> items = new ArrayList<>();  // Composition
    
    void addItem(String product, int qty, double price) {
        items.add(new LineItem(product, qty, price));  // Order CREATES the items
    }
    // When Order is garbage collected, LineItems go too
}

class LineItem {
    // Cannot exist without an Order
    private String productName;
    private int quantity;
    private double price;
}
```

```
┌──────────────┐         ┌──────────────┐
│    Order     │◆───────>│   LineItem    │
└──────────────┘   1..*  └──────────────┘
  "Order owns line items. No order = no line items."
```

### Aggregation vs Composition

```
Aggregation (◇ open diamond):
  - Child CAN exist independently
  - Parent holds a reference, doesn't control lifecycle
  - Example: Team ◇→ Player (Player can exist without Team)

Composition (◆ filled diamond):
  - Child CANNOT exist independently
  - Parent creates and destroys children
  - Example: House ◆→ Room (Room can't exist without House)
```

---

## Inheritance

> **IS-A relationship. Subclass inherits from superclass.**

```
┌──────────────────┐
│   <<abstract>>   │
│     Vehicle      │
├──────────────────┤
│ - licensePlate   │
│ - fuelLevel      │
├──────────────────┤
│ + refuel()       │
│ + getRange()*    │  ← abstract method
└────────▲─────────┘
         │
    ┌────┴────┐
    │         │
┌───┴──┐  ┌──┴───┐
│ Car  │  │Truck │
├──────┤  ├──────┤
│      │  │-cargo│
├──────┤  ├──────┤
│+getR │  │+getR │
│ange()│  │ange()│
└──────┘  └──────┘
```

---

## Interface Implementation

```
┌──────────────────┐
│  <<interface>>   │
│   Sortable       │
├──────────────────┤
│ + compareTo(): int│
└────────▲─────────┘
         ┊ (dashed line)
    ┌────┴────┐
    │         │
┌───┴──┐  ┌──┴───┐
│ User │  │Product│
└──────┘  └──────┘
```

---

## Dependency

> **A temporary, weak relationship. Class A uses Class B as a method parameter, local variable, or return type.**

```java
class ReportGenerator {
    // Dependency: uses Formatter temporarily, doesn't store it
    String generate(Data data, Formatter formatter) {
        return formatter.format(data);
    }
}
```

```
┌──────────────────┐         ┌──────────────┐
│ ReportGenerator  │- - - - >│  Formatter   │
└──────────────────┘         └──────────────┘
  "ReportGenerator uses Formatter but doesn't hold a reference"
```

---

## Multiplicity

```
Notation    Meaning
─────────────────────────────
1           Exactly one
0..1        Zero or one (optional)
*           Zero or more
1..*        One or more
0..*        Zero or more (same as *)
3..5        Between 3 and 5
n           Fixed number

Examples:
  Order 1 ◆────── 1..* LineItem     (Order has at least one item)
  User  1 ────── 0..* Order          (User may have zero or more orders)
  Person 1 ────── 0..1 Passport      (Person may have zero or one passport)
  Student * ────── * Course           (Many-to-many)
```

---

## Access Modifiers

```
Symbol  Java Keyword    Visibility
────────────────────────────────────
+       public          Everyone
-       private         Same class only
#       protected       Same class + subclasses + same package
~       (default)       Same package only

Rule of thumb for LLD interviews:
  - Fields: ALWAYS private (-)
  - Methods: public (+) for API, private (-) for internal
  - Use protected (#) only for template method hooks
```

---

## UML for Common LLD Problems

### Parking Lot

```
┌───────────────────┐     ┌─────────────────┐
│   ParkingLot      │◆───>│   ParkingFloor  │
├───────────────────┤1   *├─────────────────┤
│ - floors          │     │ - spots         │
│ - entrances       │     │ - floorNumber   │
├───────────────────┤     ├─────────────────┤
│ + findSpot()      │     │ + getAvailable()│
└───────────────────┘     └───────┬─────────┘
                                  │◆ 1..*
                          ┌───────┴─────────┐
                          │   ParkingSpot   │
                          ├─────────────────┤
                          │ - spotNumber    │
                          │ - type: SpotType│
                          │ - isOccupied    │
                          ├─────────────────┤
                          │ + park(Vehicle) │
                          │ + unpark()      │
                          └─────────────────┘
                                  │ 0..1
                          ┌───────┴─────────┐
                          │ <<abstract>>    │
                          │    Vehicle      │
                          ├─────────────────┤
                          │ - licensePlate  │
                          │ - type          │
                          └───────▲─────────┘
                             ┌────┼────┐
                          ┌──┴─┐┌┴──┐┌┴──┐
                          │Car ││Bike││Truck│
                          └────┘└───┘└────┘
```

---

## Interview Tips for Drawing UML

```
1. START with entities (nouns from requirements)
   "Design a parking lot" → ParkingLot, Floor, Spot, Vehicle, Ticket

2. ADD key fields and methods (not ALL of them)
   Show important business methods, skip getters/setters

3. DRAW relationships
   - Identify IS-A (inheritance) vs HAS-A (composition/aggregation)
   - Mark multiplicity (1, *, 1..*)

4. USE interfaces for behavior variation
   - PaymentStrategy, NotificationChannel, VehicleType

5. KEEP IT CLEAN
   - Don't crowd the diagram
   - Group related classes
   - Use boxes and arrows, not paragraphs
```

---

## Common Interview Mistakes

```
❌ Mistake 1: Drawing every field and method
   Fix: Show only IMPORTANT fields and PUBLIC methods.

❌ Mistake 2: Confusing aggregation and composition
   Fix: "Can the child exist without the parent?"
   Yes → Aggregation (◇). No → Composition (◆).

❌ Mistake 3: Making all relationships associations
   Fix: Distinguish between owns (◆), has (◇), uses (- - ->), and is-a (▷).

❌ Mistake 4: Skipping multiplicity
   Fix: Always mark 1, *, 0..1 on relationship lines.

❌ Mistake 5: Not showing interfaces
   Fix: Use <<interface>> boxes for Strategy, Observer patterns.
```

---

## Summary Cheat Sheet

```
┌──────────────────────────────────────────────────────────────┐
│            CLASS DIAGRAM & UML CHEAT SHEET                    │
├──────────────┬───────────────────────────────────────────────┤
│ Class Box    │ Name | Fields (- private) | Methods (+ public)│
├──────────────┼───────────────────────────────────────────────┤
│ Association  │ ────> "uses/knows" (A has reference to B)      │
│ Aggregation  │ ◇──> "has" (child can exist alone)            │
│ Composition  │ ◆──> "owns" (child dies with parent)          │
│ Inheritance  │ ──▷ "is-a" (solid line, hollow arrow)         │
│ Implements   │ --▷ "implements" (dashed line, hollow arrow)  │
│ Dependency   │ --> "uses temporarily" (dashed line)           │
├──────────────┼───────────────────────────────────────────────┤
│ Multiplicity │ 1, 0..1, *, 1..*, 0..*                       │
├──────────────┼───────────────────────────────────────────────┤
│ Access       │ + public, - private, # protected              │
├──────────────┴───────────────────────────────────────────────┤
│ Key Interview Line:                                           │
│ "Let me sketch the class diagram to show the key entities,    │
│  their relationships, and the interfaces where behavior       │
│  varies — this will guide my implementation."                 │
└──────────────────────────────────────────────────────────────┘
```
