# Spreadsheet (Microsoft Excel) + Sum Formula - HELLO Interview Framework

> **Companies**: Microsoft, Amazon  
> **Difficulty**: Medium/Hard  
> **Pattern**: Observer (formula dependency graph) + Expression Evaluation  
> **Covers**: "Design spreadsheet like Excel" + "Design Excel Sum Formula"

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

### 🎯 What is a Spreadsheet?

A spreadsheet is a **grid of cells** where each cell can contain:
- **Text**: "Revenue", "Q1 Sales"
- **Numbers**: 42, 3.14
- **Formulas**: `=SUM(A1:A5)`, `=B2*0.1`, `=A1+B2`

The critical challenge: when a cell value changes, **all formulas that depend on it must automatically recalculate** — cascading through the dependency graph.

**Real-world**: Microsoft Excel has ~750M users. Every cell change triggers a dependency graph traversal that can affect thousands of downstream formulas.

### For L63 Microsoft Interview

This is a **premium Microsoft question** — it tests:
1. **Expression parsing**: Parse `=SUM(A1:A5)` into a computable expression
2. **Dependency tracking**: Know which formulas to recalculate when a cell changes
3. **Circular reference detection**: `=A1` references `=B1` references `=A1` → cycle!
4. **Lazy evaluation**: Only evaluate cells that are needed
5. **Cell addressing**: Convert "B3" to row=3, col=2

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions to Ask
- "What formulas should we support?" → SUM, AVG, MIN, MAX + basic arithmetic (+, -, *, /)
- "Cell references?" → Yes: `=A1`, `=A1+B2`
- "Range references?" → Yes: `=SUM(A1:A5)` (column or row ranges)
- "Should formulas auto-recalculate?" → Yes, when any dependency changes
- "Circular references?" → Detect and report as error

### Functional Requirements

#### Must Have (P0)
1. **Cell Operations**
   - Set cell value: text, number, or formula
   - Get evaluated cell value
   - Display grid with computed values

2. **Formula Support**
   - Arithmetic: `=A1+B2`, `=B2*0.1`, `=A1-C3`, `=B5/2`
   - Range functions: `=SUM(A1:A5)`, `=AVG(B2:B6)`, `=MIN(...)`, `=MAX(...)`
   - Cell references: `=A1` returns A1's value

3. **Auto-Recalculation**
   - When a cell changes, all dependent formulas recalculate
   - Cascading: if B6=SUM(B2:B5) and C2=B2*0.1, changing B2 updates BOTH

4. **Error Handling**
   - Invalid formula → `#ERROR`
   - Division by zero → `NaN`
   - Circular reference → `#CIRCULAR` (P1)

#### Nice to Have (P1)
- Circular reference detection (topological sort)
- Cell formatting (currency, percentage)
- Undo/redo (Command pattern)
- Named ranges ("Revenue" instead of "B2:B6")
- Sheet tabs (multiple sheets)

### Non-Functional Requirements
- **Lazy initialization**: Only create cells that are used (sparse grid)
- **Recalculation**: O(D) where D = dependent cells (not all cells)
- **Memory**: O(C) where C = cells with data (not rows × cols)

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌────────────────────────────────────────────────────────┐
│                   Spreadsheet                          │
│  - cells: Map<String, Cell>  (lazy, sparse)            │
│  - setCellValue(cellId, value): void                   │
│  - getCellValue(cellId): Object                        │
│  - evaluate(cell): void                                │
│  - evaluateFormula(formula): double                    │
│  - recomputeDependents(): void                         │
└────────────────────────┬───────────────────────────────┘
                         │ contains
                         ▼
┌────────────────────────────────────────────────────────┐
│                      Cell                              │
│  - cellId: String ("A1", "B5")                         │
│  - rawValue: String ("42", "Hello", "=SUM(A1:A5)")     │
│  - computedValue: Object (42.0, "Hello", 7500.0)       │
│  - isFormula: boolean (true if starts with "=")         │
│  - getNumericValue(): double                           │
└────────────────────────────────────────────────────────┘
```

### Class: Cell
| Attribute | Type | Description |
|-----------|------|-------------|
| cellId | String | "A1", "B5" — column letter + row number |
| rawValue | String | What user typed: "42", "=SUM(A1:A5)" |
| computedValue | Object | Evaluated result: 42.0, 7500.0, "#ERROR" |
| isFormula | boolean | true if rawValue starts with "=" |

**Why `Object` for computedValue?** Can be Double (numbers/formulas), String (text), or error messages.

### Class: Spreadsheet
| Attribute | Type | Description |
|-----------|------|-------------|
| cells | Map<String, Cell> | Sparse grid — only populated cells stored |

**Why `Map<String, Cell>` not `Cell[][]`?**
- Sparse: typical spreadsheet has 1000 cells out of 17M possible (26 cols × 1M rows)
- Dynamic: no fixed size needed
- O(1) lookup by cellId

---

## 3️⃣ API Design

```java
class Spreadsheet {
    /** Set cell value — text, number, or formula (starting with "=") */
    void setCellValue(String cellId, String value);
    
    /** Get evaluated value (computed result, not raw formula) */
    Object getCellValue(String cellId);
    
    /** Display grid as formatted table */
    String display(int rows, int cols);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Set a Number

```
setCellValue("B2", "1000")
  ├─ Cell.setRawValue("1000")
  ├─ Cell.isFormula = false (doesn't start with "=")
  ├─ evaluate(): try parse as Double → 1000.0
  ├─ computedValue = 1000.0
  └─ recomputeDependents() → re-evaluate all formula cells
```

### Scenario 2: Set a Formula =SUM(B2:B5)

```
setCellValue("B6", "=SUM(B2:B5)")
  ├─ Cell.setRawValue("=SUM(B2:B5)")
  ├─ Cell.isFormula = true
  ├─ evaluate():
  │   ├─ formula = "SUM(B2:B5)" (strip "=")
  │   ├─ Regex match: SUM\(([A-Z]\d+):([A-Z]\d+)\) → start="B2", end="B5"
  │   ├─ getCellsInRange("B2", "B5"):
  │   │   └─ iterate B2, B3, B4, B5 → get numeric values → [1000, 2000, 1500, 3000]
  │   ├─ sum = 7500.0
  │   └─ computedValue = 7500.0
  └─ Done
```

### Scenario 3: Update Cell → Cascade

```
setCellValue("B2", "5000")  ← was 1000
  ├─ B2.computedValue = 5000.0
  ├─ recomputeDependents():
  │   ├─ B6 = SUM(B2:B5) → recalculate → 5000+2000+1500+3000 = 11500
  │   ├─ C2 = B2*0.1 → recalculate → 5000*0.1 = 500
  │   └─ C6 = SUM(C2:C5) → recalculate → 500+200+150+300 = 1150
  └─ All formulas updated!
```

---

## 5️⃣ Design + Implementation

### Formula Parsing: Regex-Based

```java
private double evaluateFormula(String formula) {
    // 1. Range functions: SUM(A1:A5), AVG(B2:B6), MIN(...), MAX(...)
    Matcher sumMatch = Pattern.compile("SUM\\(([A-Z]\\d+):([A-Z]\\d+)\\)").matcher(formula);
    if (sumMatch.matches()) {
        return evaluateSum(sumMatch.group(1), sumMatch.group(2));
    }
    // ... AVG, MIN, MAX patterns similarly
    
    // 2. Binary arithmetic: A1+B2, B2*0.1, A1-C3, B5/2
    for (char op : new char[]{'+', '-', '*', '/'}) {
        int idx = formula.indexOf(op);
        if (idx > 0) {
            double left = resolveOperand(formula.substring(0, idx).trim());
            double right = resolveOperand(formula.substring(idx + 1).trim());
            return switch (op) {
                case '+' -> left + right;
                case '-' -> left - right;
                case '*' -> left * right;
                case '/' -> right != 0 ? left / right : Double.NaN;
                default -> 0;
            };
        }
    }
    
    // 3. Single cell reference: =A1
    return resolveOperand(formula);
}

private double resolveOperand(String operand) {
    operand = operand.trim();
    if (operand.matches("[A-Z]\\d+")) {  // Cell reference
        Cell ref = cells.get(operand);
        return ref != null ? ref.getNumericValue() : 0;
    }
    return Double.parseDouble(operand);  // Literal number
}
```

**Why regex for parsing?** For interview scope, regex is sufficient. Production would use a proper expression parser (recursive descent or Shunting-Yard algorithm for operator precedence).

### Range Evaluation

```java
private List<Cell> getCellsInRange(String start, String end) {
    List<Cell> result = new ArrayList<>();
    char startCol = start.charAt(0), endCol = end.charAt(0);
    int startRow = Integer.parseInt(start.substring(1));
    int endRow = Integer.parseInt(end.substring(1));
    
    for (char c = startCol; c <= endCol; c++) {
        for (int r = startRow; r <= endRow; r++) {
            String id = "" + c + r;
            Cell cell = cells.get(id);
            if (cell != null) result.add(cell);
        }
    }
    return result;
}

// SUM, AVG, MIN, MAX use Java streams:
private double evaluateSum(String start, String end) {
    return getCellsInRange(start, end).stream()
        .mapToDouble(Cell::getNumericValue).sum();
}
```

### Auto-Recalculation (Simple Approach)

```java
void setCellValue(String cellId, String value) {
    Cell cell = cells.computeIfAbsent(cellId, Cell::new);
    cell.setRawValue(value);
    evaluate(cell);
    recomputeDependents();  // re-evaluate ALL formulas
}

private void recomputeDependents() {
    for (Cell cell : cells.values()) {
        if (cell.isFormula()) evaluate(cell);
    }
}
```

**This is O(F) where F = formula cells.** Good enough for interview. Production would build a dependency graph and only recompute affected cells.

---

## 6️⃣ Deep Dives

### Deep Dive 1: Dependency Graph (Production Approach)

Instead of recomputing ALL formulas, track which cells depend on which:

```java
// Dependency graph: cellId → set of cells that depend on it
Map<String, Set<String>> dependents = new HashMap<>();

// When parsing =SUM(B2:B5), register:
//   dependents["B2"] = {"B6"}
//   dependents["B3"] = {"B6"}
//   dependents["B4"] = {"B6"}
//   dependents["B5"] = {"B6"}

// When B2 changes → only recalculate dependents["B2"] = {"B6"}
// Then cascade: if B6 has dependents, recalculate those too
// This is a BFS/DFS traversal of the dependency graph
```

**Complexity**: O(D) where D = dependent cells (typically << total formula cells)

### Deep Dive 2: Circular Reference Detection

```
=A1 → references B1
=B1 → references C1  
=C1 → references A1  ← CIRCULAR!
```

**Detection**: Build directed graph of cell references. Run DFS from the modified cell. If we revisit a cell in the current path → cycle detected.

```java
boolean hasCircularReference(String cellId, Set<String> visited, Set<String> path) {
    if (path.contains(cellId)) return true;  // Cycle!
    if (visited.contains(cellId)) return false;  // Already checked, no cycle
    
    visited.add(cellId);
    path.add(cellId);
    
    for (String dep : getDependencies(cellId)) {
        if (hasCircularReference(dep, visited, path)) return true;
    }
    
    path.remove(cellId);
    return false;
}
```

**Alternative**: Topological sort. If the dependency graph has no valid topological ordering → circular reference exists.

### Deep Dive 3: Cell Addressing

```
"B3" → column B (index 1), row 3
"AA1" → column AA (index 26), row 1

Column to index: 
  A=0, B=1, ..., Z=25, AA=26, AB=27, ...
  
int colIndex(String col) {
    int idx = 0;
    for (char c : col.toCharArray()) {
        idx = idx * 26 + (c - 'A' + 1);
    }
    return idx - 1;
}
```

### Deep Dive 4: Flyweight Pattern (Cell Formatting)

Multiple cells may share the same formatting (font, color, border). Instead of storing format per cell:

```java
class CellFormat {
    String font; int fontSize; String color; String backgroundColor;
}

// Flyweight: shared format objects
Map<String, CellFormat> formatCache = new HashMap<>(); // key = "Arial-12-black-white"

// 1000 cells with same format → 1 CellFormat object, 1000 references
// Without flyweight: 1000 CellFormat objects with identical data
```

### Deep Dive 5: Expression Parser (Production)

For complex formulas like `=A1+B2*C3-D4/2`:

**Shunting-Yard Algorithm** (operator precedence):
1. Output queue + operator stack
2. Numbers/cell refs → output queue
3. Operators → compare precedence with stack top
4. `*` and `/` have higher precedence than `+` and `-`
5. Result: Reverse Polish Notation → evaluate with stack

Our interview approach (single operator) is sufficient, but mention this for L63.

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Empty cell referenced | getNumericValue() returns 0 |
| Text cell in SUM | getNumericValue() returns 0 (non-numeric treated as 0) |
| Division by zero | Return Double.NaN |
| Invalid formula syntax | catch Exception → "#ERROR" |
| Circular reference | P1: detect with DFS, return "#CIRCULAR" |
| Very large range =SUM(A1:Z1000) | O(26,000) — acceptable for in-memory |
| Cell references itself =A1 in cell A1 | Circular → error |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| setCellValue | O(1) set + O(F) recompute | O(1) |
| getCellValue | O(1) | — |
| evaluate (number) | O(1) | O(1) |
| evaluate (SUM range) | O(R) where R = range size | O(R) |
| recomputeDependents | O(F) all formulas | O(1) |
| With dependency graph | O(D) affected only | O(E) edges |

Where F = formula cells, R = range size, D = dependent cells, E = dependency edges

---

## 📋 Interview Checklist (L63 Microsoft)

- [ ] **Cell model**: rawValue vs computedValue, isFormula detection
- [ ] **Formula parsing**: SUM(range), arithmetic, cell references
- [ ] **Range evaluation**: Iterate column + row range, stream operations
- [ ] **Auto-recalculation**: When cell changes, dependent formulas update
- [ ] **Dependency graph**: Explain production approach (BFS/DFS on graph)
- [ ] **Circular reference**: DFS cycle detection, topological sort
- [ ] **Sparse grid**: Map<String, Cell> not Cell[][] (memory efficient)
- [ ] **Flyweight**: Shared cell formatting (mention for bonus points)
- [ ] **Expression parser**: Mention Shunting-Yard for complex formulas

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Cell, Spreadsheet) | 5 min |
| Formula Parsing + Evaluation | 15 min (main coding effort) |
| Auto-recalculation + Deep Dives | 10 min |
| **Total** | **~35 min** |

See `SpreadsheetSystem.java` for full runnable implementation with SUM, AVG, MIN, MAX, arithmetic, and cascading update demos.
