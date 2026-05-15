# 🎯 Topic 11: Java I/O & NIO

> **Java Interview — Deep Dive**
> Covering traditional I/O, NIO (non-blocking I/O), Channels, Buffers, file handling, and interview-ready concepts.

---

## Traditional I/O (java.io)

### Stream-Based (Byte & Character)

```java
// Byte Streams — raw binary data
InputStream  → FileInputStream, BufferedInputStream, ByteArrayInputStream
OutputStream → FileOutputStream, BufferedOutputStream, ByteArrayOutputStream

// Character Streams — text data (with encoding)
Reader → FileReader, BufferedReader, InputStreamReader
Writer → FileWriter, BufferedWriter, OutputStreamWriter

// Bridge: Byte → Character
Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
```

### Modern File I/O (Java 7+ NIO.2)

```java
// Read entire file
String content = Files.readString(Path.of("file.txt"));           // Java 11+
List<String> lines = Files.readAllLines(Path.of("file.txt"));     // Java 7+
byte[] bytes = Files.readAllBytes(Path.of("file.bin"));           // Java 7+

// Write to file
Files.writeString(Path.of("file.txt"), "Hello World");            // Java 11+
Files.write(Path.of("file.txt"), lines);                          // Java 7+

// Stream lines (lazy — for large files)
try (Stream<String> lines = Files.lines(Path.of("huge.txt"))) {
    lines.filter(l -> l.contains("ERROR")).forEach(System.out::println);
}

// Copy, Move, Delete
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
Files.delete(Path.of("temp.txt"));

// Walk directory tree
try (Stream<Path> paths = Files.walk(Path.of("/home"))) {
    paths.filter(Files::isRegularFile)
         .filter(p -> p.toString().endsWith(".java"))
         .forEach(System.out::println);
}
```

---

## NIO (java.nio) — Non-Blocking I/O

### Key Concepts

| Concept | I/O (java.io) | NIO (java.nio) |
|---------|--------------|-----------------|
| Model | Stream-based | Buffer-based + Channel |
| Blocking | **Blocking** | **Non-blocking** possible |
| Direction | One-way (in OR out) | Bidirectional (channels) |
| Selectors | No | Yes (multiplex channels) |
| Use case | Simple file I/O | High-performance networking |

### Channels & Buffers

```java
// Channel = connection to I/O device (file, socket)
// Buffer = container for data

// Read file via channel
try (FileChannel channel = FileChannel.open(Path.of("data.txt"), StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    while (channel.read(buffer) > 0) {
        buffer.flip();  // Switch from write-mode to read-mode
        while (buffer.hasRemaining()) {
            System.out.print((char) buffer.get());
        }
        buffer.clear();  // Reset for next read
    }
}
```

### Buffer Lifecycle

```
allocate() → [write data] → flip() → [read data] → clear()/compact()
              WRITE MODE              READ MODE       WRITE MODE
```

### Selector (Multiplexing — Interview Key for Networking)

```java
Selector selector = Selector.open();
ServerSocketChannel server = ServerSocketChannel.open();
server.configureBlocking(false);
server.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();  // Blocks until events ready
    for (SelectionKey key : selector.selectedKeys()) {
        if (key.isAcceptable()) { /* new connection */ }
        if (key.isReadable()) { /* data available */ }
        if (key.isWritable()) { /* can write */ }
    }
}
// One thread handles THOUSANDS of connections!
```

---

## Interview Key Points

| Topic | Detail |
|-------|--------|
| BufferedReader vs Scanner | BufferedReader is faster; Scanner parses tokens |
| try-with-resources | Always use for I/O — auto-closes resources |
| Encoding | Always specify charset: `StandardCharsets.UTF_8` |
| Large files | Use `Files.lines()` (lazy Stream) not `readAllLines()` (loads all) |
| NIO advantage | Non-blocking + selectors = one thread, many connections |
| Path vs File | `Path` (NIO.2) preferred over legacy `File` |

---

## Summary Cheat Sheet

```
┌────────────────────────────────────────────────────────────┐
│ Traditional I/O: Streams (byte/char), blocking, simple      │
│ NIO: Channels + Buffers, non-blocking, selectors            │
│ NIO.2 (Java 7+): Files utility class, Path, walk/lines     │
│                                                            │
│ File Reading: Files.readString() (small), Files.lines() (big)│
│ Always: try-with-resources, specify charset, use Path       │
│ Networking: NIO Selector = 1 thread, 1000s of connections   │
└────────────────────────────────────────────────────────────┘
```

---

> **Next Topic**: [Enums, Annotations & Reflection →](./12-enums-annotations-and-reflection.md)
