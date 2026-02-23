import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.Instant;

/**
 * MESSAGE QUEUE - System Design Implementation
 * 
 * Concepts demonstrated:
 * - Point-to-point queue (one consumer per message)
 * - Pub/Sub with topics and subscribers
 * - Dead letter queue (DLQ) for failed messages
 * - Message acknowledgment and retry
 * - Priority queue
 * - Consumer groups (like Kafka)
 * 
 * Interview talking points:
 * - Used by: Kafka, RabbitMQ, SQS, SNS
 * - At-least-once vs At-most-once vs Exactly-once delivery
 * - Ordering guarantees: FIFO, partition-level ordering (Kafka)
 * - Backpressure: What happens when consumers are slow?
 * - Idempotency: Consumers must handle duplicate messages
 * - Persistence: Messages durably stored on disk (Kafka log)
 */
class MessageQueue {

    // ==================== MESSAGE ====================
    static class Message {
        final String id;
        final String topic;
        final String body;
        final Map<String, String> headers;
        final long timestamp;
        final int priority;
        int retryCount;
        long visibleAfter; // For delayed visibility after failed processing

        Message(String topic, String body, int priority) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.topic = topic;
            this.body = body;
            this.headers = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.retryCount = 0;
            this.visibleAfter = 0;
        }

        Message(String topic, String body) { this(topic, body, 0); }

        public String toString() {
            return String.format("Msg[%s|%s|p%d]", id, body.substring(0, Math.min(20, body.length())), priority);
        }
    }

    // ==================== 1. SIMPLE FIFO QUEUE ====================
    static class SimpleQueue {
        private final BlockingQueue<Message> queue;
        private final String name;
        private final AtomicLong produced = new AtomicLong();
        private final AtomicLong consumed = new AtomicLong();

        SimpleQueue(String name, int capacity) {
            this.name = name;
            this.queue = new LinkedBlockingQueue<>(capacity);
        }

        boolean send(Message msg) {
            boolean added = queue.offer(msg);
            if (added) produced.incrementAndGet();
            return added;
        }

        Message receive() {
            Message msg = queue.poll();
            if (msg != null) consumed.incrementAndGet();
            return msg;
        }

        Message receive(long timeoutMs) throws InterruptedException {
            Message msg = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (msg != null) consumed.incrementAndGet();
            return msg;
        }

        int size() { return queue.size(); }
        long getProduced() { return produced.get(); }
        long getConsumed() { return consumed.get(); }
    }

    // ==================== 2. PUB/SUB SYSTEM ====================
    static class PubSubSystem {
        private final Map<String, List<Subscriber>> topicSubscribers = new ConcurrentHashMap<>();
        private final AtomicLong totalPublished = new AtomicLong();

        interface Subscriber {
            String getName();
            void onMessage(Message msg);
        }

        void subscribe(String topic, Subscriber subscriber) {
            topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        }

        void unsubscribe(String topic, Subscriber subscriber) {
            List<Subscriber> subs = topicSubscribers.get(topic);
            if (subs != null) subs.remove(subscriber);
        }

        void publish(Message msg) {
            totalPublished.incrementAndGet();
            List<Subscriber> subs = topicSubscribers.get(msg.topic);
            if (subs != null) {
                for (Subscriber sub : subs) {
                    try { sub.onMessage(msg); }
                    catch (Exception e) { System.err.println("Subscriber error: " + e.getMessage()); }
                }
            }
        }

        int getSubscriberCount(String topic) {
            List<Subscriber> subs = topicSubscribers.get(topic);
            return subs != null ? subs.size() : 0;
        }
    }

    // ==================== 3. QUEUE WITH DLQ AND RETRY ====================
    static class ReliableQueue {
        private final BlockingQueue<Message> mainQueue;
        private final BlockingQueue<Message> deadLetterQueue;
        private final int maxRetries;
        private final long retryDelayMs;
        private final String name;

        ReliableQueue(String name, int capacity, int maxRetries, long retryDelayMs) {
            this.name = name;
            this.mainQueue = new LinkedBlockingQueue<>(capacity);
            this.deadLetterQueue = new LinkedBlockingQueue<>();
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        boolean send(Message msg) { return mainQueue.offer(msg); }

        Message receive() { return mainQueue.poll(); }

        void acknowledge(Message msg) {
            // Message processed successfully, nothing to do
        }

        void nack(Message msg) {
            msg.retryCount++;
            if (msg.retryCount > maxRetries) {
                deadLetterQueue.offer(msg);
            } else {
                msg.visibleAfter = System.currentTimeMillis() + retryDelayMs;
                mainQueue.offer(msg);
            }
        }

        int mainQueueSize() { return mainQueue.size(); }
        int dlqSize() { return deadLetterQueue.size(); }

        List<Message> getDLQMessages() {
            return new ArrayList<>(deadLetterQueue);
        }
    }

    // ==================== 4. PRIORITY QUEUE ====================
    static class PriorityMessageQueue {
        private final PriorityBlockingQueue<Message> queue;

        PriorityMessageQueue() {
            this.queue = new PriorityBlockingQueue<>(100,
                    (a, b) -> Integer.compare(b.priority, a.priority)); // Higher priority first
        }

        void send(Message msg) { queue.offer(msg); }

        Message receive() { return queue.poll(); }

        int size() { return queue.size(); }
    }

    // ==================== 5. CONSUMER GROUP (KAFKA-STYLE) ====================
    static class ConsumerGroupQueue {
        private final String topic;
        private final List<List<Message>> partitions;
        private final Map<String, int[]> consumerOffsets; // group -> [partition offsets]
        private final int numPartitions;

        ConsumerGroupQueue(String topic, int numPartitions) {
            this.topic = topic;
            this.numPartitions = numPartitions;
            this.partitions = new ArrayList<>();
            for (int i = 0; i < numPartitions; i++) {
                partitions.add(new CopyOnWriteArrayList<>());
            }
            this.consumerOffsets = new ConcurrentHashMap<>();
        }

        void produce(String key, Message msg) {
            int partition = Math.abs(key.hashCode()) % numPartitions;
            partitions.get(partition).add(msg);
        }

        void registerConsumerGroup(String group) {
            consumerOffsets.put(group, new int[numPartitions]);
        }

        List<Message> consume(String group, int partition, int maxMessages) {
            int[] offsets = consumerOffsets.get(group);
            if (offsets == null) return Collections.emptyList();

            List<Message> partData = partitions.get(partition);
            int currentOffset = offsets[partition];
            int endOffset = Math.min(currentOffset + maxMessages, partData.size());

            List<Message> result = new ArrayList<>();
            for (int i = currentOffset; i < endOffset; i++) {
                result.add(partData.get(i));
            }
            offsets[partition] = endOffset;
            return result;
        }

        int getPartitionSize(int partition) { return partitions.get(partition).size(); }
        int getOffset(String group, int partition) {
            int[] offsets = consumerOffsets.get(group);
            return offsets != null ? offsets[partition] : 0;
        }
    }

    // ==================== DEMO ====================
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== MESSAGE QUEUE - System Design Demo ===\n");

        // 1. Simple FIFO Queue
        System.out.println("--- 1. Simple FIFO Queue ---");
        SimpleQueue sq = new SimpleQueue("orders", 100);
        sq.send(new Message("orders", "Order #1001"));
        sq.send(new Message("orders", "Order #1002"));
        sq.send(new Message("orders", "Order #1003"));
        System.out.printf("  Queue size: %d%n", sq.size());
        Message m;
        while ((m = sq.receive()) != null) {
            System.out.printf("  Consumed: %s%n", m);
        }
        System.out.printf("  Produced: %d, Consumed: %d%n", sq.getProduced(), sq.getConsumed());

        // 2. Producer-Consumer with multiple threads
        System.out.println("\n--- 2. Multi-threaded Producer/Consumer ---");
        SimpleQueue mtQueue = new SimpleQueue("tasks", 1000);
        int numProducers = 3, numConsumers = 2, msgsPerProducer = 100;
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicInteger totalConsumed = new AtomicInteger();

        for (int p = 0; p < numProducers; p++) {
            final int pid = p;
            new Thread(() -> {
                for (int i = 0; i < msgsPerProducer; i++)
                    mtQueue.send(new Message("tasks", "P" + pid + "-Task" + i));
                producersDone.countDown();
            }).start();
        }
        producersDone.await();

        CountDownLatch consumersDone = new CountDownLatch(numConsumers);
        for (int c = 0; c < numConsumers; c++) {
            new Thread(() -> {
                Message msg;
                while ((msg = mtQueue.receive()) != null) totalConsumed.incrementAndGet();
                consumersDone.countDown();
            }).start();
        }
        consumersDone.await();
        System.out.printf("  %d producers x %d msgs = %d total. Consumed: %d%n",
                numProducers, msgsPerProducer, numProducers * msgsPerProducer, totalConsumed.get());

        // 3. Pub/Sub
        System.out.println("\n--- 3. Pub/Sub System ---");
        PubSubSystem pubsub = new PubSubSystem();
        List<String> emailLog = new CopyOnWriteArrayList<>();
        List<String> smsLog = new CopyOnWriteArrayList<>();

        pubsub.subscribe("user.signup", new PubSubSystem.Subscriber() {
            public String getName() { return "EmailService"; }
            public void onMessage(Message msg) { emailLog.add("Welcome email for: " + msg.body); }
        });
        pubsub.subscribe("user.signup", new PubSubSystem.Subscriber() {
            public String getName() { return "SMSService"; }
            public void onMessage(Message msg) { smsLog.add("SMS sent for: " + msg.body); }
        });
        pubsub.subscribe("user.signup", new PubSubSystem.Subscriber() {
            public String getName() { return "AnalyticsService"; }
            public void onMessage(Message msg) { /* track silently */ }
        });

        pubsub.publish(new Message("user.signup", "alice@example.com"));
        pubsub.publish(new Message("user.signup", "bob@example.com"));

        System.out.printf("  Subscribers on 'user.signup': %d%n", pubsub.getSubscriberCount("user.signup"));
        System.out.printf("  Email log: %s%n", emailLog);
        System.out.printf("  SMS log: %s%n", smsLog);

        // 4. Reliable Queue with DLQ
        System.out.println("\n--- 4. Reliable Queue with Dead Letter Queue ---");
        ReliableQueue rq = new ReliableQueue("payments", 100, 3, 100);
        Message payMsg = new Message("payments", "Pay $99.99");
        rq.send(payMsg);

        Message received = rq.receive();
        System.out.printf("  Received: %s%n", received);

        // Simulate failures
        for (int i = 0; i < 4; i++) {
            rq.nack(received);
            System.out.printf("  NACK #%d: retry=%d, mainQ=%d, DLQ=%d%n",
                    i + 1, received.retryCount, rq.mainQueueSize(), rq.dlqSize());
            if (rq.mainQueueSize() > 0) received = rq.receive();
        }
        System.out.printf("  Final: mainQ=%d, DLQ=%d (message moved to DLQ after %d retries)%n",
                rq.mainQueueSize(), rq.dlqSize(), 3);

        // 5. Priority Queue
        System.out.println("\n--- 5. Priority Message Queue ---");
        PriorityMessageQueue pq = new PriorityMessageQueue();
        pq.send(new Message("alerts", "Low priority alert", 1));
        pq.send(new Message("alerts", "CRITICAL alert", 10));
        pq.send(new Message("alerts", "Medium priority alert", 5));
        pq.send(new Message("alerts", "High priority alert", 8));

        System.out.println("  Consuming in priority order:");
        Message pm;
        while ((pm = pq.receive()) != null) {
            System.out.printf("    Priority %d: %s%n", pm.priority, pm.body);
        }

        // 6. Consumer Groups (Kafka-style)
        System.out.println("\n--- 6. Consumer Groups (Kafka-style) ---");
        ConsumerGroupQueue cgq = new ConsumerGroupQueue("events", 3);
        cgq.registerConsumerGroup("group-A");
        cgq.registerConsumerGroup("group-B");

        // Produce messages with keys (keys determine partition)
        String[] eventKeys = {"user-1", "user-2", "user-3", "user-1", "user-2"};
        for (int i = 0; i < eventKeys.length; i++) {
            cgq.produce(eventKeys[i], new Message("events", "Event-" + i));
        }

        System.out.printf("  Partitions: [%d, %d, %d] messages%n",
                cgq.getPartitionSize(0), cgq.getPartitionSize(1), cgq.getPartitionSize(2));

        // Both groups can consume independently
        for (String group : new String[]{"group-A", "group-B"}) {
            int totalMsgs = 0;
            for (int p = 0; p < 3; p++) {
                List<Message> msgs = cgq.consume(group, p, 10);
                totalMsgs += msgs.size();
            }
            System.out.printf("  %s consumed %d messages (independent of other groups)%n", group, totalMsgs);
        }

        // Summary
        System.out.println("\n--- Message Queue Patterns ---");
        System.out.println("  Point-to-Point: One consumer per message (work queue)");
        System.out.println("  Pub/Sub:        All subscribers get every message (fanout)");
        System.out.println("  Consumer Group: Partitioned parallel consumption (Kafka)");
        System.out.println("  Priority:       Higher priority messages processed first");
        System.out.println("  DLQ:            Failed messages stored for investigation");
    }
}
