# Interview-Ready Code Guide

## ⏱️ The Reality of Coding Interviews

**Interview Time**: 45-60 minutes
**What you CAN write**: 150-250 lines of focused code
**What you CANNOT write**: Complete production system with all features

## 📋 Two Versions for Each Problem

### 1. **Full System** (Learning & Reference)
- **Files**: `*System.java` (600-800 lines)
- **Purpose**: Understand complete design, all patterns, edge cases
- **Use for**: Study, review, understanding trade-offs
- **Time to write**: 3-4 hours

### 2. **Interview Version** (Actual Interview)
- **Files**: `*System-Interview-Version.java` (150-250 lines)
- **Purpose**: What you actually write in 1 hour
- **Use for**: Practice, mock interviews, timed coding
- **Time to write**: 45-60 minutes

## 🎯 What to Include in Interview Code

### ✅ Must Have (Core 80%)
1. **Main entities** (1-2 classes)
   - Essential properties only
   - Basic validation

2. **Core interface** (1 interface)
   - Key method signatures

3. **2 Implementations** (2 classes)
   - Simple algorithm #1
   - Simple algorithm #2

4. **Coordinator class** (1 class)
   - Uses strategy pattern
   - Basic operations

5. **Demo/Test** (1 method)
   - Show it works
   - Demonstrate both algorithms

### ⚠️ Skip in Interview (Can Discuss)
- Health checking (mention verbally)
- Metrics tracking (mention verbally)
- Multiple edge cases (mention 2-3)
- Complex error handling
- Production concerns
- Extensive demos

## 📝 Interview Timeline (60 minutes)

### Phase 1: Requirements (7 min)
- Ask clarifying questions
- Write down requirements
- Confirm scope

### Phase 2: Design (8 min)
- Draw class diagram (simple)
- Define interfaces
- Show relationships

### Phase 3: Code Core (25 min)
- Server class (5 min)
- Strategy interface (2 min)
- RoundRobin strategy (5 min)
- LeastConnections strategy (5 min)
- LoadBalancer coordinator (8 min)

### Phase 4: Demo (10 min)
- Write simple main()
- Show both algorithms work
- Test basic flow

### Phase 5: Discussion (10 min)
- Discuss what's missing
- Explain trade-offs
- Talk about production concerns
- Answer questions

## 🎓 Example: Load Balancer Interview Approach

### What You Write (200 lines):
```java
// 1. Server class (30 lines)
class Server {
    private String id;
    private AtomicInteger connections;
    // Basic methods
}

// 2. Strategy interface (3 lines)
interface LoadBalancingStrategy {
    Server selectServer(List<Server> servers);
}

// 3. Round Robin (10 lines)
class RoundRobinStrategy implements LoadBalancingStrategy {
    private AtomicInteger index;
    // selectServer implementation
}

// 4. Least Connections (12 lines)
class LeastConnectionsStrategy implements LoadBalancingStrategy {
    // Find min connections
}

// 5. ServerPool (40 lines)
class ServerPool {
    private List<Server> servers;
    // add, remove, getHealthy
}

// 6. LoadBalancer (50 lines)
class LoadBalancer {
    private ServerPool pool;
    private LoadBalancingStrategy strategy;
    // routeRequest, completeRequest
}

// 7. Demo (40 lines)
public class Demo {
    public static void main(String[] args) {
        // Test both strategies
    }
}
```

### What You Discuss (Don't Code):
- "For production, I'd add health checking..."
- "We'd need metrics for monitoring..."
- "Weighted round robin would require..."
- "Thread safety considerations are..."
- "For distributed systems, we'd need..."

## 💡 Interview Tips

### Do This:
✅ **Start with simplest version** - Get working code first
✅ **Use Strategy pattern** - Shows design pattern knowledge
✅ **Make it compilable** - Actually works, not pseudocode
✅ **Add 1-2 comments** - Show you're thinking about edge cases
✅ **Write clean code** - Good naming, proper structure
✅ **Test it** - Simple main() that demonstrates functionality

### Don't Do This:
❌ **Don't try to write everything** - You'll run out of time
❌ **Don't over-engineer** - YAGNI (You Aren't Gonna Need It)
❌ **Don't skip testing** - Must show it works
❌ **Don't ignore questions** - Interviewer may guide you
❌ **Don't panic if incomplete** - Discussion counts too

## 📊 Complexity Guide

### What to Implement:
| Feature | Interview | Full System |
|---------|-----------|-------------|
| Basic routing | ✅ Code | ✅ Code |
| 2 algorithms | ✅ Code | ✅ Code |
| Strategy pattern | ✅ Code | ✅ Code |
| Server pool | ✅ Code | ✅ Code |
| Health checks | 💬 Discuss | ✅ Code |
| Metrics | 💬 Discuss | ✅ Code |
| Weighted RR | 💬 Discuss | ✅ Code |
| IP Hash | 💬 Discuss | ✅ Code |
| 5+ algorithms | ❌ Skip | ✅ Code |

## 🚀 Practice Strategy

### Week 1-2: Learn
- Study full implementations
- Understand all patterns
- Read markdown documentation

### Week 3-4: Practice
- Use interview versions
- Time yourself (60 min)
- Write from scratch

### Week 5+: Master
- Do without looking
- Explain as you code
- Handle follow-up questions

## 📁 Files to Use

### For Learning:
- `*-hello-interview.md` - Complete documentation
- `*System.java` - Full implementation

### For Interview Practice (NEW! ⭐):
All located in `../LLD/` folder:

**Core 5 (Most Common):**
- **LoadBalancerSystem-Interview-Version.java** (200 lines, 45-60 min)
- **RateLimiterSystem-Interview-Version.java** (160 lines, 45-60 min)
- **LRUCacheSystem-Interview-Version.java** (150 lines, 30-45 min)
- **ParkingLotSystem-Interview-Version.java** (200 lines, 45-60 min)
- **CircuitBreakerSystem-Interview-Version.java** (180 lines, 40-50 min)

**Additional 5 (Popular):**
- **ElevatorSystem-Interview-Version.java** (180 lines, 45-60 min)
- **MeetingRoomScheduler-Interview-Version.java** (170 lines, 40-50 min)
- **HitCounterSystem-Interview-Version.java** (130 lines, 30-40 min)
- **NotificationSystem-Interview-Version.java** (160 lines, 40-50 min)
- **JobSchedulerSystem-Interview-Version.java** (170 lines, 45-60 min)

**How to practice:**
1. Set timer for specified duration
2. Write from scratch (don't look at solution)
3. Compare with interview version
4. Repeat until you can do it without looking

## 🎯 Success Criteria

After 60 minutes, you should have:

✅ **Compilable code** - No syntax errors
✅ **2 Algorithms working** - Both demonstrate correctly
✅ **Strategy pattern visible** - Interface + implementations
✅ **Simple test/demo** - Shows it works
✅ **Clean structure** - Professional naming and organization

**Remember**: It's okay if incomplete! Interviewers expect discussion of missing features. Show you know what's missing and why!
