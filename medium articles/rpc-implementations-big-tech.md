# The RPC Problem Big Tech Solved Differently: Inside Amazon Coral, Google gRPC, and Meta Thrift

*Why companies building distributed systems at scale can't just use off-the-shelf RPC frameworks*

---

RPC (Remote Procedure Call) seems straightforward until you need it to handle billions of requests per day. Make a function call that executes on another machine. Return the result. How complex can it be? While debugging a timeout issue in a service dependency at Amazon, I discovered our internal RPC framework — Coral — has features I'd never seen documented anywhere. That made me curious: how do other tech giants handle service-to-service communication? What I found wasn't just different implementations. These companies fundamentally reconceived what RPC means when you're operating at their scale, and the architectural decisions reveal deep insights about distributed systems that nobody talks about.

## The Core Insight: RPC At Scale Isn't About Making Remote Calls

Here's what surprised me most: Amazon, Google, Meta, and Twitter didn't just build different RPC frameworks. They built different philosophies about how services should communicate. Amazon's Coral optimizes for operational safety — it's nearly impossible to build an unreliable service with it. Google's gRPC optimizes for efficiency and standardization — HTTP/2 multiplexing reduces connection overhead. Meta's Thrift optimizes for language diversity — 28+ language bindings enable true polyglot development. Twitter's Finagle optimizes for failure handling — circuit breakers and retry budgets are first-class concepts.

These different optimizations create entirely different architectures, and understanding why each company made their choices reveals lessons about distributed systems that apply even if you'll never work at these companies.

## Amazon Coral: When Safety Is Non-Negotiable

Amazon's internal RPC framework is called Coral, and most people outside Amazon have never heard of it. That's by design — it's not open source, and Amazon doesn't talk about it publicly. But Coral powers nearly every service at AWS and Amazon.com, handling trillions of calls per day.

The architectural philosophy behind Coral is this: good defaults should be mandatory, not optional. You don't get to disable retries. You don't get to skip timeouts. You can't bypass circuit breakers. These aren't features you enable — they're constraints you work within.

When I first encountered Coral, this seemed restrictive. Why can't I control retry behavior for my specific use case? But after several oncall rotations dealing with cascading failures caused by OTHER teams' services, I understood. The framework protects you from yourself and everyone else.

Consider what happens when a service starts failing. Without built-in protections, clients keep hammering the failing service, making recovery impossible. The load prevents the service from restarting or processing the backlog. This cascades — as clients time out, their clients time out, and suddenly the entire dependency graph is failing.

Coral prevents this through mandatory circuit breakers. When a service's error rate crosses a threshold, the circuit opens automatically. Clients stop sending requests, giving the service time to recover. The circuit closes gradually, testing if the service is healthy before allowing full traffic. You can't disable this. You can configure thresholds, but the protection is always active.

This architectural choice has profound implications. Service teams can't cause cascading failures even if they try. Outages stay localized. Recovery happens faster because failing services aren't being hammered by retry storms.

Another insight: Coral makes observability mandatory. Every RPC automatically emits metrics — latency percentiles, error rates, request counts. You don't set this up. You don't instrument your code. It happens automatically. This seems basic until you realize what it enables: Amazon has comprehensive visibility into every service dependency across the entire company without requiring individual teams to do anything.

The deeper lesson is about recognizing when flexibility is actually a liability. Engineers instinctively want control, but in a distributed system with thousands of services maintained by thousands of teams, uniformity is more valuable than flexibility. Coral works because it removes decisions that should never have been decisions.

## Google gRPC: The HTTP/2 Bet That Paid Off

Google's approach to RPC is fundamentally different from Amazon's. Instead of building a closed framework with mandatory protections, Google built an open framework on top of HTTP/2 and released it as gRPC.

The critical insight was that HTTP/2 solved problems that RPC frameworks were reinventing badly. Most custom RPC protocols handle multiplexing, flow control, and compression. HTTP/2 does this natively. Why reinvent these mechanisms when you can leverage years of protocol development and optimization?

But here's what most people miss about gRPC's architecture: the decision to use HTTP/2 wasn't primarily about performance. It was about ecosystem compatibility. By using HTTP/2, gRPC automatically works with existing load balancers, proxies, and CDNs. A custom binary protocol requires custom infrastructure. HTTP/2 just works.

This has cascading benefits. Want to add authentication? Use standard HTTP headers and existing auth middleware. Need to monitor traffic? Use existing HTTP debugging tools. Want to route traffic through a service mesh? Envoy and Istio understand HTTP/2 natively. The protocol choice eliminates an entire class of infrastructure problems.

The streaming support reveals another insight about Google's workloads. Unary RPC (single request, single response) is common, but Google frequently needs different patterns:

- **Server streaming**: Database queries returning millions of rows
- **Client streaming**: Log aggregation from thousands of sources  
- **Bidirectional streaming**: Real-time collaboration like Google Docs

These patterns are awkward to implement over traditional request-response RPC. HTTP/2's native streaming support makes them natural. Google designed gRPC around their actual communication patterns rather than forcing those patterns into a simple request-response model.

Another architectural detail: Protocol Buffers as the serialization format wasn't just about efficiency. It was about enabling schema evolution. Services at Google run different versions simultaneously. Protocol Buffers' forward and backward compatibility rules ensure old clients can talk to new servers and vice versa. This seems basic until you're coordinating deployments across thousands of services and realize coordinated updates are impossible.

The lesson from gRPC is about identifying which problems are genuinely unique to your domain versus which problems are already solved by existing standards. Google could have built yet another custom RPC protocol, but they recognized that most of what they needed was already specified in HTTP/2.

## Meta Thrift: The Original RPC Framework

Thrift predates both Coral and gRPC. Facebook (now Meta) open-sourced it in 2008 when they realized their microservices architecture needed better than HTTP+JSON.

The architectural insight that drove Thrift was language diversity. Facebook was primarily PHP at the time but needed services in C++ for performance, Python for machine learning, Java for infrastructure. They needed these services to communicate efficiently without the overhead of parsing JSON or dealing with language-specific serialization quirks.

Thrift's solution was to separate protocol from transport from serialization. This seems academic until you understand what it enables. The same service can simultaneously support:

- Binary protocol over TCP for high performance
- Compact protocol over HTTP for efficiency  
- JSON protocol over HTTP for debugging

This flexibility meant Facebook could optimize different communication paths independently. Services in the same datacenter could use binary over TCP for maximum performance. Cross-datacenter traffic could use compression. Debugging could use human-readable JSON without changing service code.

But here's what's interesting: Thrift's architecture reveals something about how Meta thinks about failure. Unlike Coral's mandatory protections or gRPC's built-in retry logic, Thrift is minimalist. It provides the mechanism for RPC but doesn't enforce policy. Each service team decides their own retry logic, timeout behavior, and circuit breaking.

This seems risky compared to Coral's opinionated approach, but it reflects Meta's engineering culture. They trust individual teams to make appropriate decisions for their context. A service handling Facebook posts has different reliability requirements than a service generating thumbnail images. Enforcing uniform policies would be suboptimal.

The trade-off is that building a reliable service on Thrift requires more expertise than building on Coral. You need to understand distributed systems failures, implement retry logic correctly, set appropriate timeouts. Coral makes it hard to build an unreliable service. Thrift makes it possible to build a highly optimized service if you know what you're doing.

Another insight from Thrift's architecture: the multiplicity of language support (28+ languages) isn't just about enabling polyglot development. It's about enabling teams to use the right tool for each job. Meta's search infrastructure uses C++ for performance. Their data science tools use Python. Their internal tools use PHP. Thrift enables these components to work together seamlessly.

## Twitter Finagle: When Failure Is The Default

Twitter's Finagle framework takes a completely different approach to RPC. While Coral prevents failures, and Thrift enables handling failures, Finagle assumes failure is constant and builds the entire architecture around that assumption.

The architectural insight is in the name: Finagle means to achieve something by deviousness or trickery. The framework is designed to be clever about getting results from unreliable services.

Consider Twitter's "fail whale" era when the site frequently went down. The core problem wasn't that individual services were unreliable — it was that services couldn't gracefully degrade when dependencies failed. If the user profile service was slow, the entire timeline would time out. If the tweet composer had issues, nothing worked.

Finagle's architecture solves this through composition. Every service is wrapped in filters that implement various failure handling strategies:

**Request hedging**: Send the same request to multiple backend instances, use whichever responds first. This seems wasteful, but when the alternative is waiting for a slow instance to timeout, using 2x resources to halve latency can be optimal.

**Adaptive load balancing**: Track response times and error rates per instance. Route new requests to the fastest, most reliable instances. This automatically handles uneven load distribution and degraded hosts without manual intervention.

**Budget-based retries**: Don't retry indefinitely. Set a retry budget — maybe 20% extra requests are acceptable, maybe 50%. Once the budget is exhausted, fail fast. This prevents retry storms where retries cause more load than original requests.

The deeper insight is about making failure handling composable. Instead of implementing retry logic, circuit breaking, and load balancing in every service, Finagle implements these as filters that compose. A service can stack multiple strategies:

```
Authentication → Rate Limiting → Retries → Circuit Breaking → Load Balancing → Actual Service
```

Each filter wraps the next, transforming requests and responses. This architecture means reliability features are separate from business logic, making them easier to test, modify, and reason about.

Another architectural choice: Finagle is built on Scala's Future abstraction. This seems like a language implementation detail, but it fundamentally changes how services compose. Traditional RPC is synchronous — you call a service and block waiting for the response. Finagle is async — you get a Future immediately that will eventually contain the response.

This enables efficient handling of dependent calls. If you need data from three services to build a timeline, you can issue all three requests simultaneously and wait for all to complete. With blocking RPC, you'd either make sequential calls (slow) or manage threads manually (complex). With Futures, the composition is natural.

The lesson from Finagle is about building systems that expect and handle failure rather than trying to prevent failure. In large distributed systems, something is always failing. The question isn't if services will fail but how your system behaves when they do.

## The Patterns That Connect Them All

After analyzing these four frameworks, several patterns emerged that weren't obvious from reading documentation:

**The Abstraction Trade-off**: Coral hides complexity, making it easy to build reliable services but harder to optimize for specific use cases. Thrift exposes mechanisms, making it possible to build highly optimized services but requiring deep expertise. gRPC splits the difference, providing good defaults while allowing customization. Finagle embraces composition, making complex behavior achievable through simple building blocks. There's no universally correct choice — it depends on your organization's expertise and values.

**The Observability Philosophy**: Every framework makes different observability assumptions. Coral assumes you can't trust teams to instrument properly, so it's automatic. gRPC provides hooks but requires setup. Thrift leaves it entirely to service owners. Finagle makes it composable through filters. These choices reflect different beliefs about where observability responsibility should live.

**The Failure Spectrum**: Different frameworks assume different base failure rates. Coral assumes most services are reliable most of the time, so it optimizes for handling occasional failures gracefully. Finagle assumes constant partial failures, so it optimizes for maintaining throughput despite failures. This isn't just about implementation — it reflects real differences in operational maturity and system reliability.

**The Evolution Problem**: How do you evolve RPC frameworks when thousands of services depend on them? Coral can make breaking changes because it's internal and Amazon can coordinate migrations. gRPC must maintain compatibility because it's open source with external users. Thrift achieves compatibility through protocol versioning. These constraints shape how frameworks evolve over time.

## What Nobody Tells You About Building RPC Systems

After this analysis, here's what I wish someone had told me before building my first distributed system:

**Pick your battles carefully**. Don't build a custom RPC framework unless you have truly unique constraints that existing solutions can't handle. Amazon, Google, and Meta built custom solutions because they were operating at scales where no existing solutions worked. Most companies aren't operating at that scale.

**Mandatory protections only work if you control both sides**. Coral's mandatory circuit breakers work because Amazon controls all services using Coral. If you're building an RPC framework for external users, you can't enforce reliability patterns — they'll just not use your framework.

**The difference between RPC and distributed systems is observability**. Making a remote call that works is easy. Making a remote call you can debug when it doesn't work is hard. Every successful RPC framework builds observability in from the start, not as an afterthought.

**Retries are harder than they seem**. Every framework struggles with retry semantics. Retrying reads is safe. Retrying writes requires idempotency. Automatic retries can cause retry storms. Budget-based retries prevent storms but complicate reasoning. There's no perfect solution.

**Protocol choices have second-order effects**. Using HTTP/2 means gRPC works with existing infrastructure. Using Thrift's binary protocol means better performance but custom tooling. These trade-offs aren't just about the protocol — they affect your entire operational model.

**Language diversity compounds complexity exponentially**. Supporting multiple languages sounds good until you realize every language has different idioms, different error handling, different async models. Thrift's 28+ languages is impressive but requires massive maintenance. Most companies are better off choosing fewer languages and doing them well.

## The Real Lesson

These companies didn't build different RPC frameworks because they wanted variety. They built different frameworks because they were solving fundamentally different problems under different constraints.

Amazon built Coral because when you're powering AWS, reliability isn't optional. Mandatory protections prevent single teams from causing company-wide outages. Google built gRPC because standardization enables their massive engineering organization to move faster. Meta built Thrift because language diversity enables teams to use optimal tools. Twitter built Finagle because their workload requires aggressive failure handling to maintain uptime.

The architectures that emerged aren't arbitrary. They're the logical result of optimizing for different goals under different constraints. Each framework reveals how technical decisions flow from understanding your actual requirements rather than copying what worked elsewhere.

What matters isn't which framework is "best" — that's the wrong question. What matters is understanding what problem each framework was designed to solve and why their solutions differ. Those insights apply whether you're building a startup or working at a tech giant.

The next time you're choosing how services should communicate, don't ask "what does Google use?" Ask "what problem am I actually solving?" The answer will reveal what architecture makes sense for your context.

---

## References

**General RPC:**
- https://grpc.io/docs/what-is-grpc/introduction/
- https://thrift.apache.org/
- https://twitter.github.io/finagle/

**Amazon:**
- https://aws.amazon.com/builders-library/
- https://www.allthingsdistributed.com/

**Google:**
- https://sre.google/books/
- https://research.google/pubs/

**Meta:**
- https://engineering.fb.com/
- https://research.facebook.com/publications/

**Twitter:**
- https://blog.twitter.com/engineering/en_us/topics/infrastructure
