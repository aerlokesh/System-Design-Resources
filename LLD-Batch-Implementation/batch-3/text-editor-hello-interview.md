# Text Editor with Undo & Redo - HELLO Interview Framework

> **Companies**: Microsoft, Google, Amazon, Apple, Uber  
> **Difficulty**: Hard  
> **Primary Pattern**: Command  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is This About?

Every text editor (VS Code, Word, Notepad++) supports Undo (Ctrl+Z) and Redo (Ctrl+Y). The challenge: how do you **reverse any operation** and **replay it forward**?

**The Command pattern** solves this elegantly: every edit becomes an object with `execute()` and `undo()`. Two stacks manage the history.

**Real-world**: VS Code undo history stores ~1000 operations per file. Google Docs uses Operational Transform (different problem). Microsoft Word uses a Command-based undo system similar to what we'll build.

### For L63 Microsoft Interview

This is a **design pattern showcase** question:
1. **Command Pattern**: Interface with execute() + undo() — the primary pattern
2. **Memento aspect**: DeleteCommand saves deleted text for undo
3. **Invoker vs Receiver**: Editor (invoker) delegates to Document (receiver) via Commands
4. **Redo invalidation**: New action after undo clears redo stack — explain WHY
5. **Composite Command**: Macro = list of commands with batch execute/undo
6. **Extensibility**: Adding new operations (bold, indent) = new Command class, zero changes to Editor

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What operations?" → Insert, Delete, Replace, Backspace
- "Undo/Redo unlimited?" → Bounded (configurable max, e.g., 100)
- "Cursor tracking?" → Yes, operations update cursor position
- "New action after undo clears redo?" → Yes (standard behavior)

### Functional Requirements (P0)
1. **Insert** text at cursor position or specific position
2. **Delete** text at position with length
3. **Replace** text at position (delete old + insert new)
4. **Backspace** (delete character before cursor)
5. **Undo** last operation — restores previous state
6. **Redo** last undone operation — replays it
7. **New edit after undo clears redo stack** (standard behavior)
8. **Cursor tracking** — all operations update cursor

### Non-Functional
- O(1) undo/redo (stack push/pop)
- O(N) insert/delete where N = text length affected
- Bounded undo history (configurable max)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────────────┐
│              TextEditor (Invoker)                        │
│  - document: TextDocument                               │
│  - undoStack: Deque<EditorCommand>                      │
│  - redoStack: Deque<EditorCommand>                      │
│  - insert(text) / delete(pos, len) / replace(...)       │
│  - undo() / redo()                                      │
└────────────────────────┬────────────────────────────────┘
                         │ creates & executes
                         ▼
┌─────────────────────────────────────────────────────────┐
│            EditorCommand (interface)                     │
│  + execute(): void                                      │
│  + undo(): void                                         │
│  + getDescription(): String                             │
└────────┬───────────────┬────────────────┬───────────────┘
         │               │                │
   ┌─────▼──────┐ ┌─────▼──────────┐ ┌───▼──────────────┐
   │InsertText  │ │DeleteText      │ │ReplaceText       │
   │Command     │ │Command         │ │Command           │
   │            │ │                │ │                  │
   │- position  │ │- position      │ │- position        │
   │- text      │ │- length        │ │- oldText (saved) │
   │            │ │- deletedText   │ │- newText         │
   │            │ │  (SAVED!)      │ │                  │
   │            │ │                │ │                  │
   │execute:    │ │execute:        │ │execute:          │
   │ insert text│ │ save text,     │ │ save old,        │
   │            │ │ then delete    │ │ delete old,      │
   │undo:       │ │                │ │ insert new       │
   │ delete same│ │undo:           │ │                  │
   │ range      │ │ re-insert      │ │undo:             │
   │            │ │ saved text     │ │ delete new,      │
   │            │ │                │ │ insert old       │
   └────────────┘ └────────────────┘ └──────────────────┘
                         │ operates on
                         ▼
┌─────────────────────────────────────────────────────────┐
│             TextDocument (Receiver)                      │
│  - content: StringBuilder                               │
│  - cursor: int                                          │
│  - insertRaw(pos, text): void   ← low-level, no undo   │
│  - deleteRaw(pos, len): void    ← low-level, no undo   │
│  - getSubstring(start, end): String                     │
│  - getCursor() / setCursor(): int                       │
└─────────────────────────────────────────────────────────┘
```

### Why Separate Document from Editor?
- **Document (Receiver)**: Raw string operations, no undo awareness
- **Commands**: Know how to execute AND undo themselves
- **Editor (Invoker)**: Manages undo/redo stacks, creates commands

This separation means Document doesn't know about undo. Commands don't know about stacks. Editor doesn't know about string manipulation. **Single Responsibility Principle**.

---

## 3️⃣ API Design

```java
interface EditorCommand {
    void execute();
    void undo();
    String getDescription();
}

class TextEditor {
    void insert(String text);                    // at cursor
    void insertAt(int position, String text);    // at position
    void delete(int position, int length);       // delete range
    void backspace();                            // delete before cursor
    void replace(int pos, int len, String text); // replace range
    void moveCursor(int position);
    boolean undo();                              // returns false if nothing to undo
    boolean redo();                              // returns false if nothing to redo
    String getContent();
    String display();                            // shows cursor position
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Insert "Hello" then Undo

```
insert("Hello")
  ├─ Create InsertTextCommand(doc, cursor=0, "Hello")
  ├─ cmd.execute() → doc.insertRaw(0, "Hello") → content="Hello", cursor=5
  ├─ undoStack.push(cmd)    → stack: [Insert"Hello"]
  └─ redoStack.clear()      → stack: []

undo()
  ├─ cmd = undoStack.pop()  → InsertTextCommand
  ├─ cmd.undo() → doc.deleteRaw(0, 5) → content="", cursor=0
  └─ redoStack.push(cmd)    → redo stack: [Insert"Hello"]

redo()
  ├─ cmd = redoStack.pop()  → InsertTextCommand
  ├─ cmd.execute() → doc.insertRaw(0, "Hello") → content="Hello", cursor=5
  └─ undoStack.push(cmd)    → undo stack: [Insert"Hello"]
```

### Scenario 2: Delete Saves Text (Critical!)

```
Content: "Hello World"

delete(5, 6)  ← delete " World"
  ├─ Create DeleteTextCommand(doc, pos=5, len=6)
  ├─ cmd.execute():
  │   ├─ deletedText = doc.getSubstring(5, 11) → " World"  ← SAVED!
  │   └─ doc.deleteRaw(5, 6) → content="Hello", cursor=5
  └─ undoStack.push(cmd)

undo()
  ├─ cmd.undo():
  │   └─ doc.insertRaw(5, " World")  ← uses SAVED text!
  │   → content="Hello World", cursor=11
  └─ ✓ Perfectly restored!
```

**Why `deletedText` must be saved**: The Command doesn't know what text existed before deletion. Saving it on execute() is the **Memento aspect** of Command pattern.

### Scenario 3: New Action Clears Redo

```
insert("A")     → undo: [A]     redo: []
insert("B")     → undo: [A,B]   redo: []
undo()           → undo: [A]     redo: [B]
insert("C")     → undo: [A,C]   redo: []  ← CLEARED!

// "B" is gone forever. Redo-ing "B" after "C" would create a paradox.
// This is how Word, VS Code, IntelliJ, and every editor works.
```

---

## 5️⃣ Design + Implementation

### Command Interface

```java
interface EditorCommand {
    void execute();
    void undo();
    String getDescription();
}
```

### InsertTextCommand

```java
class InsertTextCommand implements EditorCommand {
    private final TextDocument doc;
    private final int position;
    private final String text;

    InsertTextCommand(TextDocument doc, int position, String text) {
        this.doc = doc; this.position = position; this.text = text;
    }

    @Override
    public void execute() { doc.insertRaw(position, text); }
    
    @Override
    public void undo() { doc.deleteRaw(position, text.length()); }
    // Insert's undo = delete the EXACT same range. 
    // We know the text and position, so this is trivially reversible.
    
    @Override
    public String getDescription() {
        return "Insert \"" + text + "\" at " + position;
    }
}
```

### DeleteTextCommand (The Interesting One)

```java
class DeleteTextCommand implements EditorCommand {
    private final TextDocument doc;
    private final int position;
    private final int length;
    private String deletedText;  // SAVED on execute for undo!

    DeleteTextCommand(TextDocument doc, int position, int length) {
        this.doc = doc; this.position = position; this.length = length;
    }

    @Override
    public void execute() {
        // MUST save before deleting — can't undo without knowing what was there!
        deletedText = doc.getSubstring(position, position + length);
        doc.deleteRaw(position, length);
    }

    @Override
    public void undo() {
        doc.insertRaw(position, deletedText);  // Re-insert saved text
    }
}
```

### ReplaceTextCommand

```java
class ReplaceTextCommand implements EditorCommand {
    private final TextDocument doc;
    private final int position;
    private final int length;
    private final String newText;
    private String oldText;  // saved for undo

    @Override
    public void execute() {
        oldText = doc.getSubstring(position, position + length);  // save old
        doc.deleteRaw(position, length);                           // delete old
        doc.insertRaw(position, newText);                          // insert new
    }

    @Override
    public void undo() {
        doc.deleteRaw(position, newText.length());  // remove new
        doc.insertRaw(position, oldText);            // restore old
    }
}
```

### TextDocument (Receiver)

```java
class TextDocument {
    private final StringBuilder content;
    private int cursor;

    TextDocument() { this.content = new StringBuilder(); this.cursor = 0; }

    void insertRaw(int pos, String text) {
        content.insert(pos, text);
        cursor = pos + text.length();
    }

    void deleteRaw(int pos, int length) {
        content.delete(pos, Math.min(pos + length, content.length()));
        cursor = pos;
    }

    String getSubstring(int start, int end) {
        return content.substring(start, Math.min(end, content.length()));
    }

    int getCursor() { return cursor; }
    void setCursor(int pos) { cursor = Math.max(0, Math.min(pos, content.length())); }
    String getContent() { return content.toString(); }
    int length() { return content.length(); }
}
```

### TextEditor (Invoker)

```java
class TextEditor {
    private final TextDocument document;
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();
    private final int maxUndoSize;

    void insert(String text) {
        EditorCommand cmd = new InsertTextCommand(document, document.getCursor(), text);
        executeCommand(cmd);
    }

    void delete(int position, int length) {
        EditorCommand cmd = new DeleteTextCommand(document, position, length);
        executeCommand(cmd);
    }

    private void executeCommand(EditorCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();  // NEW ACTION INVALIDATES REDO!
    }

    boolean undo() {
        if (undoStack.isEmpty()) return false;
        EditorCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        return true;
    }

    boolean redo() {
        if (redoStack.isEmpty()) return false;
        EditorCommand cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
        return true;
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: Macro Commands (Composite Pattern)

Group multiple commands into one undo-able unit:

```java
class MacroCommand implements EditorCommand {
    private final List<EditorCommand> commands;
    private final String name;

    MacroCommand(String name, List<EditorCommand> commands) {
        this.name = name; this.commands = new ArrayList<>(commands);
    }

    @Override
    public void execute() {
        for (EditorCommand cmd : commands) cmd.execute();
    }

    @Override
    public void undo() {
        // REVERSE ORDER! Last command undone first.
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }
}

// Usage: "Replace All" = delete + insert for every occurrence
// One undo undoes the entire batch
```

### Deep Dive 2: Bounded Undo History

```java
private void executeCommand(EditorCommand cmd) {
    cmd.execute();
    undoStack.push(cmd);
    redoStack.clear();
    
    // Bounded: drop oldest if exceeding limit
    if (undoStack.size() > maxUndoSize) {
        // ArrayDeque: removeLast() for oldest (bottom of stack)
        ((ArrayDeque<EditorCommand>) undoStack).removeLast();
    }
}
```

**Why bounded?** Memory. Each command stores data (deleted text, positions). For a 100MB document with character-level undo, unbounded history = OOM.

### Deep Dive 3: Character-Level vs Operation-Level

| Approach | Granularity | Memory | UX |
|----------|------------|--------|-----|
| **Operation-level** (our approach) | "Hello" = 1 undo | Low | Undo whole word |
| **Character-level** | Each keystroke = 1 undo | High | Undo one letter |
| **Grouped character** (VS Code) | Characters within 300ms = 1 group | Medium | Natural undo |

VS Code approach: Start a group on first keystroke. Keep adding to group while keystrokes are < 300ms apart. When pause detected, close group. One undo = one group.

### Deep Dive 4: Rope Data Structure (Large Documents)

For documents > 100MB, `StringBuilder` is slow for insert/delete in the middle (O(N) shift):

```
Rope: balanced binary tree of string fragments
  - Insert at position K: split at K, add new node, O(log N)
  - Delete range: cut subtree, O(log N)
  - Concat: new root with two children, O(1)
  
StringBuilder: O(N) for mid-document insert/delete
Rope: O(log N) for all operations

VS Code uses a "piece table" — similar concept to Rope.
```

### Deep Dive 5: Google Docs — Operational Transform (NOT Command)

Google Docs doesn't use Command pattern because it's **collaborative**:
- User A inserts "X" at position 5
- User B inserts "Y" at position 3 (simultaneously)
- Conflict: after B's insert, A's position shifts to 6

**Operational Transform (OT)**: Transform operations against each other to resolve conflicts. Each operation has a `transform(op1, op2)` function. Much more complex than Command pattern.

**CRDTs** (Conflict-free Replicated Data Types) are the modern alternative: Yjs, Automerge.

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Undo with empty stack | Return false, no-op |
| Redo with empty stack | Return false, no-op |
| New action after undo | Clears redo stack |
| Delete at position 0 length 0 | No-op validation |
| Insert empty string | No-op validation |
| Backspace at cursor 0 | No-op (nothing before cursor) |
| Delete beyond document end | Clamp to document length |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| insert | O(N) StringBuilder.insert | O(T) where T = text length |
| delete | O(N) StringBuilder.delete | O(T) saved text |
| undo | O(1) stack pop + O(N) reverse op | O(1) |
| redo | O(1) stack pop + O(N) replay op | O(1) |
| Total undo memory | — | O(U × T̄) where U = undo limit, T̄ = avg text |

---

## 📋 Interview Checklist (L63 Microsoft)

- [ ] **Command Pattern**: Interface with execute() + undo()
- [ ] **Three Commands**: Insert (trivially reversible), Delete (saves text), Replace (saves old)
- [ ] **Two Stacks**: undoStack + redoStack, new action clears redo
- [ ] **Receiver vs Invoker**: Document (raw ops) vs Editor (stack management) vs Commands (reversible ops)
- [ ] **DeleteCommand saves text**: Memento aspect — can't undo without knowing what was deleted
- [ ] **Macro command**: Composite pattern for batch undo
- [ ] **Bounded history**: maxUndoSize prevents OOM
- [ ] **Production discussion**: Rope/piece table for large docs, OT for collaborative
- [ ] **Edge cases**: Empty stacks, cursor boundaries, empty strings

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Command interface + 3 commands) | 5 min |
| Implementation (Document + Editor + stacks) | 15 min |
| Deep Dives (Macro, bounded, Rope, OT) | 10 min |
| **Total** | **~35 min** |

See `TextEditorSystem.java` for full runnable implementation with 10 demo scenarios.
