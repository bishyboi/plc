# PlcProject Codebase Summary (In-Depth)

This document provides a comprehensive overview of the `PlcProject` codebase. This codebase implements a custom programming language interpreter (a tree-walk interpreter) through three primary phases: Lexical Analysis, Parsing, and Evaluation.

## 1. Overview and Cross-Functionality

The interpreter is structured as a pipeline where data flows sequentially from text to execution.

**Cross-Functionality Flow:**
1. **Lexer**: Takes the raw source code string and converts it into a sequence of meaningful syntactic chunks called `Token`s.
2. **Parser**: Takes the list of `Token`s and organizes them into a hierarchical tree structure based on grammar rules, creating an **Abstract Syntax Tree (`Ast`)**.
3. **Evaluator**: Traverses the resulting `Ast` and executes the logic described by the tree, maintaining memory state using `Scope` and returning values using `RuntimeValue`s.

**Pipeline Example:**
```java
String sourceCode = "let x = 10 + 5;";

// 1. Lexing phase
Lexer lexer = new Lexer(sourceCode);
List<Token> tokens = lexer.lex(); 
// Result: [LET, IDENTIFIER("x"), OPERATOR("="), INTEGER("10"), OPERATOR("+"), INTEGER("5"), OPERATOR(";")]

// 2. Parsing phase
Parser parser = new Parser(tokens);
Ast sourceAst = parser.parse("source");
// Result: Ast.Source -> [ Ast.Stmt.Let("x", Ast.Expr.Binary("+", ...)) ]

// 3. Evaluation phase
Scope globalScope = new Scope(null);
Evaluator evaluator = new Evaluator(globalScope);
RuntimeValue result = evaluator.visit(sourceAst); // Kicks off AST traversal
```

---

## 2. The Abstract Syntax Tree (AST)

### What it means and its use case
An Abstract Syntax Tree (AST) is a structured, hierarchical representation of source code. While the lexer flattens code into a 1-dimensional array of tokens, the parser groups tokens into branches showing relationships, precedence, and code blocks.
Its use case is taking away the confusing semantics of punctuation and formatting; an AST plainly states *"This is a block of statements"* or *"This is an addition operation with a left side and right side."* 

It acts as the single source of truth for the Evaluator, preventing the Evaluator from having to care about syntax rules or string splitting.

### Interfacing with ASTs and `Ast.Visitor / visit()`
The `Ast` file in this codebase defines nodes using Java `record` implementations within sealed interfaces. It defines two main branches:
- **`Stmt` (Statement)**: Operations that perform an action (like assignments, definitions, `If`, `For`). Usually do not yield inline primitive values.
- **`Expr` (Expression)**: Operations that compute a value (like literals, binary math, variables).

To properly interact with an AST, you must use the **Visitor Pattern** implemented by `Ast.Visitor<T, E>`. The problem with AST nodes is that they form a massive, recursive tree, making it messy to use huge `if-else` blocks or `instanceof` to find out what node you are looking at. 

The Visitor Pattern solves this:
1. `Ast.Visitor` declares a unique `visit()` method for every single type of `Ast` node (e.g. `visit(Ast.Stmt.Let ast)`, `visit(Ast.Expr.Binary ast)`).
2. A generic `visit(Ast ast)` uses Java 17's pattern-matching `switch` expression to map an arbitrary node object to its specific overloaded `visit()` method.
3. The `Evaluator` implements this `Visitor`, so it naturally overrides all these methods, containing specific execution logic. **When you process a node, you simply call `visit(childNode)`, which handles redirecting and typecasting cleanly and safely.**

---

## 3. The Evaluator and Scope

The Evaluator executes the AST nodes. A major part of this process is environment management, which determines what variables exist in memory at an exact moment. This is handled by the `Scope` class.

### How Scope Works
`Scope` creates localized "bubbles" of memory holding mappings of Variable Names to `RuntimeValue`s. Since variables should only exist in specific parts of the code (like inside a `for` loop or `function` definition), Scopes are **chained** where a child scope retains a pointer to its `parent` Scope.

### Difference between `.get()` and `.resolve()`
There are two ways the scope fetches data. Understanding the difference is vital for object properties versus normal variables:
- **`get(String name)`**: Looks for the variable explicitly **only in the current, immediate scope**. It does *not* ask the parent scopes if it doesn't find the variable. 
  - *Use Case*: Reading exact properties dynamically held in an object (so you don't accidentally grab a parent variable named the same thing).
- **`resolve(String name)`**: Look for the variable in the current scope. If it is NOT found locally, it travels upwards asking the `parent.resolve(name)`. It repeats recursively up the chain until reaching the global scope. 
  - *Use Case*: Regular variables and function scoping. Allows an inner function to read variables made outside of it.

---

## 4. RuntimeValues and Optional<T> Wrapper

Because you are writing a custom programming language, you cannot just return standard Java `int`, `String`, or objects easily within the interpreter's type system. To standardize everything in the `Evaluator`, you wrap and return `RuntimeValue` interfaces.

### Using `RuntimeValue`s
There are three main implementations of `RuntimeValue` defined as records:
1. **`RuntimeValue.Primitive(Object value)`**: Used to wrap standard types like numeric `BigDecimal`, `BigInteger`, Java `String`, and `Boolean`, or custom literals like `null`. 
   - *How to Return*: `return new RuntimeValue.Primitive(true);` or `return new RuntimeValue.Primitive(new BigInteger("10"));`
2. **`RuntimeValue.Function`**: Encapsulates a closure and method signature. Returns a `Definition` which invokes executable code within the tree.
3. **`RuntimeValue.ObjectValue`**: Represents a custom interpreted object housing its own specific `Scope` instance mapping fields and properties.

### The Role of `Optional<T>`
You'll see `Optional<T>` used heavily when fetching from Scope, accessing AST node properties that might not exist, or unboxing RuntimeValues in the Evaluator helper `requireType()`.

Instead of explicitly returning `null` when a variable does not exist, `Scope.resolve()` intentionally returns an `Optional<RuntimeValue>`. This forces the compiler to warn developers to handle both scenarios:
```java
// Properly grabbing from scope:
Optional<RuntimeValue> valueOpt = scope.resolve(variableName);

if (!valueOpt.isPresent()) {
    // Gracefully handle missing variable!
    throw new EvaluateException("Variable " + variableName + " is not defined.");
} 

// The variable exists! Extract it natively via .get()
RuntimeValue value = valueOpt.get();
```

In `Evaluator.java`, `requireType(RuntimeValue value, Class<T> type)` provides a safe way to type-check and unbox Java primitives from inside a `RuntimeValue`. It behaves similarly returning `Optional`:
```java
// Expecting a user's input parameter to be a bool
Optional<Boolean> conditionOpt = requireType(conditionValue, Boolean.class);
if (!conditionOpt.isPresent()) {
    throw new EvaluateException("Condition must be a Boolean!");
}
boolean condition = conditionOpt.get();
```
