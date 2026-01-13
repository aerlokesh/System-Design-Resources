# HELLO Interview Low-Level Design Framework

This folder contains Low-Level Design (LLD) problems reorganized according to the **HELLO Interview Framework** - a structured approach to object-oriented design interviews that ensures comprehensive coverage of all critical aspects.

## 📋 Framework Overview

The HELLO Interview framework for LLD follows 6 sequential steps:

```
1. Requirements → 2. Core Entities → 3. API/Interface → 4. Data Flow → 5. Design → 6. Deep Dives
```

### Step 1: Requirements ✅
**Primary Goal: Understand the Problem**
- **Functional Requirements**: What features the system must support
- **Non-Functional Requirements**: Performance, scalability, thread-safety
- **Constraints**: Memory limits, response time, concurrent users
- **Assumptions**: What can we assume about inputs, usage patterns

### Step 2: Core Entities 🔷
**Primary Goal: Identify Domain Objects**
- Identify main classes and interfaces
- Define relationships (composition, aggregation, inheritance)
- Determine responsibilities for each class
- Single Responsibility Principle

### Step 3: API or Interface 🔌
**Primary Goal: Define Public Contracts**
- Design public methods for each class
- Define method signatures (parameters, return types)
- Specify exceptions and error handling
- Document pre-conditions and post-conditions

### Step 4: Data Flow 🔄
**Primary Goal: Show Interactions**
- Sequence diagrams for key operations
- Method call flows between objects
- State transitions and lifecycle
- Optional: Can be integrated into Design step

### Step 5: Design 🏗️
**Primary Goal: Create Class Structure**
- Complete class diagrams
- Design patterns used (Strategy, Factory, Observer, etc.)
- Data structures and algorithms
- Thread-safety mechanisms
- Code implementation

### Step 6: Deep Dives 🔍
**Primary Goal: Demonstrate Expertise**
- Handle edge cases
- Extensibility and future enhancements
- Performance optimizations
- Trade-offs and alternatives
- Testing strategies

## 📂 Document Structure

Each LLD document follows this template:

```markdown
# [System Name] - HELLO Interview Framework

## 1️⃣ Requirements
### Functional Requirements
- Feature 1: Description
- Feature 2: Description

### Non-Functional Requirements
- Performance: Response time
- Scalability: Concurrent users
- Thread Safety: Required/Not required
- Memory: Constraints

### Constraints and Assumptions
- Input constraints
- Usage patterns
- Edge cases

## 2️⃣ Core Entities
### Class/Interface 1: [Name]
**Responsibility**: What this class does

**Key Attributes**:
- attribute1: type - description
- attribute2: type - description

**Key Methods**:
- method1(): Description
- method2(): Description

**Relationships**:
- Inherits from: ParentClass
- Implements: Interface
- Has-a: CompositionClass
- Uses: DependencyClass

### Class/Interface 2: [Name]
...

## 3️⃣ API Design
### Class: [ClassName]
```java
public interface ClassName {
    /**
     * Method description
     * @param param1 Description
     * @return Description
     * @throws Exception When...
     */
    ReturnType methodName(ParamType param1);
}
```

### Class: [AnotherClass]
...

## 4️⃣ Data Flow
### Operation 1: [Name]
**Sequence**:
1. Client calls method A
2. Object X validates input
3. Object Y processes request
4. Result returned to client

**Sequence Diagram** (optional):
```
Client → ObjectA: method()
ObjectA → ObjectB: validate()
ObjectB → ObjectC: process()
ObjectC → ObjectA: result
ObjectA → Client: response
```

### Operation 2: [Name]
...

## 5️⃣ Design
### Class Diagram
[ASCII diagram or description]

### Design Patterns
**Pattern 1: [Name]**
- Why: Reason for using this pattern
- Where: Which classes use it
- How: Implementation approach

**Pattern 2: [Name]**
...

### Data Structures
- Structure 1: Purpose and choice rationale
- Structure 2: Purpose and choice rationale

### Thread Safety
- Synchronization approach
- Lock strategies
- Atomic operations

### Complete Implementation
```java
// Full code implementation
```

## 6️⃣ Deep Dives
### Topic 1: Extensibility
- How to add new features
- Open/Closed Principle
- Code examples

### Topic 2: Edge Cases
- Corner case handling
- Error scenarios
- Validation strategies

### Topic 3: Performance
- Time complexity analysis
- Space complexity analysis
- Optimization techniques

### Topic 4: Testing
- Unit test strategies
- Mock objects
- Test coverage

### Topic 5: Trade-offs
- Design decisions
- Alternative approaches
- When to use what
```

## 🎯 Key Benefits

1. **Structured Approach**: Systematic progression through design phases
2. **Complete Coverage**: Ensures no critical aspect is missed
3. **Interview-Friendly**: Maps well to typical LLD interview flow
4. **Pattern-Focused**: Emphasizes design patterns and OOP principles
5. **Code-Ready**: Includes complete implementation

## 📚 LLD Problems Included

### Core Problems (Complete with HELLO Framework + Java Implementation)
- ✅ **Rate Limiter** - Token Bucket, Leaky Bucket, Sliding Window algorithms
  - 📄 Markdown: `rate-limiter-hello-interview.md`
  - ☕ Java: `../LLD/RateLimiterSystem.java`
- ✅ **LRU Cache** - HashMap + Doubly Linked List implementation
  - 📄 Markdown: `lru-cache-hello-interview.md`
  - ☕ Java: `../LLD/LRUCacheSystem.java`
- ✅ **Circuit Breaker** - State pattern, failure detection
  - 📄 Markdown: `circuit-breaker-hello-interview.md`
  - ☕ Java: `../LLD/CircuitBreakerSystem.java`
- ✅ **Parking Lot System** - Strategy pattern, polymorphism, multi-level support
  - 📄 Markdown: `parking-lot-hello-interview.md`
  - ☕ Java: `../LLD/ParkingLotSystem.java`
- ✅ **Load Balancer** - 5 algorithms (Round Robin, Least Connections, Weighted RR, IP Hash, Random)
  - 📄 Markdown: `load-balancer-hello-interview.md`
  - ☕ Java: `../LLD/LoadBalancerSystem.java` (NEW!)

### In Progress
- 🔄 **ATM System** - State machine, transaction management
- 🔄 **Chess System** - Command pattern, move validation
- 🔄 **Food Delivery System** - Observer pattern, route optimization
- 🔄 **Splitwise** - Graph algorithms, debt simplification
- 🔄 **Instagram** - Observer pattern, feed generation
- 🔄 **Twitter** - Timeline generation, follower graph

### Additional Problems
- Authentication System
- Bike Rental System
- Customer Support Chat
- DNS Cache System
- Email System
- Elevator System
- Google Docs (Operational Transform)
- Hit Counter
- ID Generator
- Inventory Management
- Job Scheduler
- Logging Framework
- Meeting Room Scheduler
- Metrics Collection
- Movie Ticket Booking
- Music Shuffle
- Notification System
- Order Management
- Proximity Service
- Reddit Comment System
- Voting/Like System

## 🔄 Comparison with Original Structure

| Original LLD Structure | HELLO Interview Framework |
|------------------------|---------------------------|
| Problem Statement | 1. Requirements |
| Functional Requirements | Covered in Step 1 |
| Classes & Objects | Step 2 (Core Entities) |
| Methods & Interfaces | Step 3 (API Design) |
| Implementation | Step 5 (Design + Code) |
| Design Patterns | Highlighted in Step 5 |
| Edge Cases | Step 6 (Deep Dives) |

## 💡 Interview Tips

### Time Management (45-60 minute interview)
1. **Requirements** (5-7 minutes)
   - Ask clarifying questions
   - Write down requirements
   - Confirm understanding

2. **Core Entities** (8-10 minutes)
   - Identify main classes
   - Define relationships
   - Draw simple diagram

3. **API Design** (8-10 minutes)
   - Define interfaces
   - Method signatures
   - Error handling

4. **Design & Implementation** (15-20 minutes)
   - Draw class diagram
   - Write key methods
   - Show design patterns

5. **Deep Dives** (10-15 minutes)
   - Extensibility
   - Edge cases
   - Trade-offs discussion

### Common Mistakes to Avoid

❌ **Don't jump to code immediately**
- Start with requirements and design
- Code is the last step, not the first

❌ **Don't over-engineer**
- Solve the stated problem first
- Add complexity only when asked

❌ **Don't ignore edge cases**
- Null checks
- Empty inputs
- Boundary conditions

❌ **Don't forget thread safety**
- Ask if multi-threading is needed
- Use appropriate synchronization

✅ **Do think out loud**
- Explain your reasoning
- Discuss trade-offs
- Ask for feedback

✅ **Do use design patterns**
- Know when to apply them
- Explain why you chose them
- Show pattern knowledge

✅ **Do write clean code**
- Clear naming conventions
- Proper encapsulation
- SOLID principles

## 🛠️ How to Use This Resource

### For Interview Preparation
1. **Study the framework** - Understand the 6 steps
2. **Pick a problem** - Start with familiar ones
3. **Practice with timer** - Simulate real interview
4. **Compare solutions** - See different approaches
5. **Review patterns** - Know when to use each

### For Learning
1. **Read the original LLD** - Understand the problem
2. **Read HELLO version** - See structured approach
3. **Note the differences** - Learn from comparison
4. **Try it yourself** - Practice the framework
5. **Get feedback** - Share with peers

### For Mock Interviews
1. **Give problem statement** - Start with requirements only
2. **Follow the framework** - Guide through 6 steps
3. **Take notes** - Track strengths and gaps
4. **Provide feedback** - Specific, actionable points
5. **Iterate** - Practice makes perfect

## 📖 Design Patterns Quick Reference

### Creational Patterns
- **Factory Method**: Creating objects without specifying exact class
- **Abstract Factory**: Creating families of related objects
- **Builder**: Constructing complex objects step by step
- **Singleton**: Ensuring single instance (use sparingly!)
- **Prototype**: Cloning objects

### Structural Patterns
- **Adapter**: Making incompatible interfaces work together
- **Decorator**: Adding behavior dynamically
- **Facade**: Simplifying complex subsystems
- **Composite**: Tree structures of objects
- **Proxy**: Controlling access to objects

### Behavioral Patterns
- **Strategy**: Encapsulating algorithms
- **Observer**: Publish-subscribe relationships
- **Command**: Encapsulating requests as objects
- **State**: Changing behavior based on state
- **Template Method**: Defining algorithm skeleton
- **Chain of Responsibility**: Passing requests along chain

## 🎓 SOLID Principles

1. **Single Responsibility Principle**
   - Each class should have one reason to change
   - One responsibility per class

2. **Open/Closed Principle**
   - Open for extension, closed for modification
   - Use interfaces and abstract classes

3. **Liskov Substitution Principle**
   - Subtypes must be substitutable for base types
   - Don't break parent class contracts

4. **Interface Segregation Principle**
   - Many specific interfaces better than one general
   - Clients shouldn't depend on unused methods

5. **Dependency Inversion Principle**
   - Depend on abstractions, not concretions
   - High-level modules shouldn't depend on low-level

## 📊 Complexity Analysis

### Time Complexity Goals
- Read operations: O(1) or O(log n)
- Write operations: O(1) or O(log n)
- Batch operations: O(n)

### Space Complexity Goals
- Per-entity storage: O(1)
- Total system: O(n) where n is entities
- Cache: Bounded size with eviction

## 🧪 Testing Strategies

### Unit Testing
- Test each method independently
- Mock dependencies
- Cover edge cases

### Integration Testing
- Test component interactions
- Verify workflows
- Check state transitions

### Concurrency Testing
- Test thread safety
- Race condition detection
- Stress testing

## 📈 Expected Proficiency Levels

### Junior Engineer
- Implement basic classes correctly
- Use simple data structures
- Handle basic edge cases
- Write working code

### Mid-Level Engineer
- Design complete class hierarchy
- Apply 2-3 design patterns
- Handle concurrency basics
- Discuss trade-offs
- Write production-quality code

### Senior Engineer
- Architect extensible solutions
- Apply multiple patterns appropriately
- Advanced concurrency handling
- Performance optimization
- System-level thinking
- Mentor others through design

## 🔗 Related Resources

- **Design Patterns**: `/LLD/Design-Patterns/`
- **Concurrency Guide**: `/LLD/CONCURRENCY_README.md`
- **HLD Framework**: `/HLD-Hello-Interview-Framework/`
- **System Design Resources**: `/System-Design-Resources/`

## 📝 Contributing

To add a new HELLO Interview LLD:

1. Choose an LLD problem from `/LLD/` folder
2. Follow the template structure exactly
3. Include all 6 steps comprehensively
4. Add code implementation
5. Explain design patterns used
6. Discuss trade-offs and alternatives
7. Name file: `[problem-name]-hello-interview.md`

## 🎯 Success Metrics

After using this framework, you should be able to:

✅ Structure any LLD interview systematically
✅ Identify and apply appropriate design patterns
✅ Write clean, extensible code quickly
✅ Handle edge cases confidently
✅ Discuss trade-offs articulately
✅ Complete designs in 45-60 minutes

---

**Created**: January 2026  
**Framework Version**: 1.0  
**Total LLD Problems**: 40+ (5 complete with HELLO framework)  
**Difficulty Range**: Easy to Hard  
**Interview Success Rate**: High with proper preparation

## 📞 Next Steps

1. **Start with Rate Limiter** - Well-documented, commonly asked
2. **Master one pattern at a time** - Strategy, Factory, Observer
3. **Practice under time pressure** - Set 45-minute timer
4. **Review and iterate** - Learn from each attempt
5. **Mock interview** - Practice with peers or mentors

**Good luck with your interviews! 🚀**
