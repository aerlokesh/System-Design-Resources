import java.util.*;

/**
 * Text Editor with Undo/Redo - HELLO Interview Framework
 * 
 * Companies: Microsoft, Google, Amazon, Apple, Uber
 * Pattern: Command Pattern (primary) + Memento for state
 * Difficulty: Hard
 * 
 * Key Design Decisions:
 * 1. Command Pattern — each edit is an object with execute() and undo()
 * 2. Two stacks — undoStack and redoStack for O(1) undo/redo
 * 3. StringBuilder — efficient mutable string for document content
 * 4. Cursor tracking — insert/delete relative to cursor position
 */

// ==================== Command Interface ====================

interface EditorCommand {
    void execute();
    void undo();
    String getDescription();
}

// ==================== Concrete Commands ====================

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

    @Override
    public String getDescription() {
        return "Insert \"" + (text.length() > 20 ? text.substring(0, 20) + "..." : text) + "\" at " + position;
    }
}

class DeleteTextCommand implements EditorCommand {
    private final TextDocument doc;
    private final int position;
    private final int length;
    private String deletedText; // saved for undo

    DeleteTextCommand(TextDocument doc, int position, int length) {
        this.doc = doc; this.position = position; this.length = length;
    }

    @Override
    public void execute() {
        deletedText = doc.getSubstring(position, position + length);
        doc.deleteRaw(position, length);
    }

    @Override
    public void undo() { doc.insertRaw(position, deletedText); }

    @Override
    public String getDescription() {
        String preview = deletedText != null ? deletedText : "?";
        if (preview.length() > 20) preview = preview.substring(0, 20) + "...";
        return "Delete \"" + preview + "\" at " + position;
    }
}

class ReplaceTextCommand implements EditorCommand {
    private final TextDocument doc;
    private final int position;
    private final int length;
    private final String newText;
    private String oldText; // saved for undo

    ReplaceTextCommand(TextDocument doc, int position, int length, String newText) {
        this.doc = doc; this.position = position;
        this.length = length; this.newText = newText;
    }

    @Override
    public void execute() {
        oldText = doc.getSubstring(position, position + length);
        doc.deleteRaw(position, length);
        doc.insertRaw(position, newText);
    }

    @Override
    public void undo() {
        doc.deleteRaw(position, newText.length());
        doc.insertRaw(position, oldText);
    }

    @Override
    public String getDescription() {
        return "Replace \"" + (oldText != null ? oldText : "?") + "\" with \"" + newText + "\"";
    }
}

// ==================== Document (Receiver) ====================

class TextDocument {
    private final StringBuilder content;
    private int cursor; // current cursor position

    TextDocument() { this.content = new StringBuilder(); this.cursor = 0; }

    // Raw operations (called by commands, no undo tracking)
    void insertRaw(int pos, String text) {
        content.insert(pos, text);
        cursor = pos + text.length();
    }

    void deleteRaw(int pos, int length) {
        int end = Math.min(pos + length, content.length());
        content.delete(pos, end);
        cursor = pos;
    }

    String getSubstring(int start, int end) {
        return content.substring(start, Math.min(end, content.length()));
    }

    String getContent() { return content.toString(); }
    int length() { return content.length(); }
    int getCursor() { return cursor; }
    void setCursor(int pos) { cursor = Math.max(0, Math.min(pos, content.length())); }

    String display() {
        if (content.isEmpty()) return "  |  (empty)";
        // Show content with cursor marker
        String text = content.toString();
        String before = text.substring(0, cursor);
        String after = text.substring(cursor);
        return "  \"" + before + "|" + after + "\"  (len=" + content.length() + ", cursor=" + cursor + ")";
    }
}

// ==================== Text Editor (Invoker) ====================

class TextEditor {
    private final TextDocument document;
    private final Deque<EditorCommand> undoStack;
    private final Deque<EditorCommand> redoStack;
    private final int maxUndoSize;

    TextEditor() { this(100); }

    TextEditor(int maxUndoSize) {
        this.document = new TextDocument();
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
        this.maxUndoSize = maxUndoSize;
    }

    // ─── Edit Operations ───

    /** Insert text at current cursor position */
    void insert(String text) {
        if (text == null || text.isEmpty()) return;
        EditorCommand cmd = new InsertTextCommand(document, document.getCursor(), text);
        executeCommand(cmd);
    }

    /** Insert text at specific position */
    void insertAt(int position, String text) {
        if (text == null || text.isEmpty()) return;
        if (position < 0 || position > document.length()) {
            System.out.println("  ✗ Position out of bounds");
            return;
        }
        EditorCommand cmd = new InsertTextCommand(document, position, text);
        executeCommand(cmd);
    }

    /** Delete 'length' characters from position */
    void delete(int position, int length) {
        if (position < 0 || position >= document.length() || length <= 0) {
            System.out.println("  ✗ Invalid delete range");
            return;
        }
        EditorCommand cmd = new DeleteTextCommand(document, position,
            Math.min(length, document.length() - position));
        executeCommand(cmd);
    }

    /** Delete character before cursor (backspace) */
    void backspace() {
        if (document.getCursor() == 0) return;
        delete(document.getCursor() - 1, 1);
    }

    /** Replace text at position */
    void replace(int position, int length, String newText) {
        if (position < 0 || position + length > document.length()) {
            System.out.println("  ✗ Invalid replace range");
            return;
        }
        EditorCommand cmd = new ReplaceTextCommand(document, position, length, newText);
        executeCommand(cmd);
    }

    /** Move cursor */
    void moveCursor(int position) { document.setCursor(position); }

    // ─── Undo / Redo ───

    boolean undo() {
        if (undoStack.isEmpty()) {
            System.out.println("  ✗ Nothing to undo");
            return false;
        }
        EditorCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        System.out.println("  ↩ Undo: " + cmd.getDescription());
        return true;
    }

    boolean redo() {
        if (redoStack.isEmpty()) {
            System.out.println("  ✗ Nothing to redo");
            return false;
        }
        EditorCommand cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
        System.out.println("  ↪ Redo: " + cmd.getDescription());
        return true;
    }

    // ─── Helper ───

    private void executeCommand(EditorCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear(); // new action invalidates redo history
        if (undoStack.size() > maxUndoSize) {
            // Remove oldest (bottom of stack) — convert to list and remove
            // In production, use a bounded deque
        }
    }

    // ─── Getters ───

    String getContent() { return document.getContent(); }
    String display() { return document.display(); }
    int getUndoCount() { return undoStack.size(); }
    int getRedoCount() { return redoStack.size(); }

    String getStatus() {
        return String.format("  Undo: %d | Redo: %d", undoStack.size(), redoStack.size());
    }
}

// ==================== Main Demo ====================

public class TextEditorSystem {
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  Text Editor with Undo/Redo - Command Pattern        ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝\n");

        TextEditor editor = new TextEditor();

        // ── Scenario 1: Basic insert ──
        System.out.println("━━━ Scenario 1: Insert text ━━━");
        editor.insert("Hello");
        System.out.println(editor.display());
        editor.insert(", World!");
        System.out.println(editor.display());
        System.out.println(editor.getStatus());
        System.out.println();

        // ── Scenario 2: Undo ──
        System.out.println("━━━ Scenario 2: Undo ━━━");
        editor.undo(); // removes ", World!"
        System.out.println(editor.display());
        editor.undo(); // removes "Hello"
        System.out.println(editor.display());
        System.out.println(editor.getStatus());
        System.out.println();

        // ── Scenario 3: Redo ──
        System.out.println("━━━ Scenario 3: Redo ━━━");
        editor.redo(); // restores "Hello"
        System.out.println(editor.display());
        editor.redo(); // restores ", World!"
        System.out.println(editor.display());
        System.out.println(editor.getStatus());
        System.out.println();

        // ── Scenario 4: New action clears redo stack ──
        System.out.println("━━━ Scenario 4: New action clears redo ━━━");
        editor.undo(); // undo ", World!" → redo has 1
        System.out.println("  After undo: " + editor.getStatus());
        editor.insert(" there!"); // new action → redo cleared
        System.out.println(editor.display());
        System.out.println("  After new insert: " + editor.getStatus());
        editor.redo(); // should fail — redo stack cleared
        System.out.println();

        // ── Scenario 5: Delete ──
        System.out.println("━━━ Scenario 5: Delete ━━━");
        System.out.println("  Before: " + editor.display());
        editor.delete(5, 7); // delete " there!"
        System.out.println("  After delete: " + editor.display());
        editor.undo(); // undo delete
        System.out.println("  After undo: " + editor.display());
        System.out.println();

        // ── Scenario 6: Replace ──
        System.out.println("━━━ Scenario 6: Replace ━━━");
        System.out.println("  Before: " + editor.display());
        editor.replace(0, 5, "Hi"); // "Hello" → "Hi"
        System.out.println("  After replace: " + editor.display());
        editor.undo();
        System.out.println("  After undo: " + editor.display());
        System.out.println();

        // ── Scenario 7: Insert at specific position ──
        System.out.println("━━━ Scenario 7: Insert at position ━━━");
        editor.insertAt(5, " beautiful");
        System.out.println(editor.display());
        editor.undo();
        System.out.println("  After undo: " + editor.display());
        System.out.println();

        // ── Scenario 8: Backspace ──
        System.out.println("━━━ Scenario 8: Backspace ━━━");
        editor.moveCursor(5);
        System.out.println("  Cursor at 5: " + editor.display());
        editor.backspace();
        System.out.println("  After backspace: " + editor.display());
        editor.undo();
        System.out.println("  After undo: " + editor.display());
        System.out.println();

        // ── Scenario 9: Multiple undos and redos ──
        System.out.println("━━━ Scenario 9: Chain of undos/redos ━━━");
        TextEditor editor2 = new TextEditor();
        editor2.insert("A");
        editor2.insert("B");
        editor2.insert("C");
        editor2.insert("D");
        System.out.println("  Content: " + editor2.display());

        editor2.undo(); // remove D
        editor2.undo(); // remove C
        editor2.undo(); // remove B
        System.out.println("  After 3 undos: " + editor2.display());

        editor2.redo(); // restore B
        System.out.println("  After 1 redo: " + editor2.display());

        editor2.insert("X"); // clears redo (C, D gone)
        System.out.println("  After insert X: " + editor2.display());
        System.out.println("  " + editor2.getStatus());

        // ── Scenario 10: Edge cases ──
        System.out.println("\n━━━ Scenario 10: Edge cases ━━━");
        TextEditor editor3 = new TextEditor();
        editor3.undo(); // nothing to undo
        editor3.redo(); // nothing to redo
        editor3.delete(0, 5); // nothing to delete
        editor3.backspace(); // cursor at 0

        System.out.println("\n✅ All text editor scenarios complete.");
    }
}
