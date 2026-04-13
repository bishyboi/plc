# PlcProject Codebase Summary

This document provides a comprehensive overview of the `PlcProject` codebase. This codebase implements a custom programming language interpreter (a tree-walk interpreter) through three primary phases: Lexical Analysis, Parsing, and Evaluation.

## 1. Overview and Cross-Functionality

The interpreter is structured as a pipeline where data flows sequentially from text to execution.

**Cross-Functionality Flow:**
1. **Lexer**: Takes the raw source code string and converts it into a sequence of meaningful chunks called `Token`s.
2. **Parser**: Takes the list of `Token`s and organizes them into a hierarchical tree structure based on grammar rules, creating an Abstract Syntax Tree (`Ast`).
3. **Evaluator**: Traverses the resulting `Ast` and executes the logic described by the tree, maintaining memory state in a `Scope`.

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
// Result: Ast.Source containing Ast.Stmt.Let("x", Ast.Expr.Binary("+", Ast.Expr.Literal(10), Ast.Expr.Literal(5)))

// 3. Evaluation phase
Scope globalScope = new Scope(null);
Evaluator evaluator = new Evaluator(globalScope);
RuntimeValue result = evaluator.visit(sourceAst);
// Result: `x` is bound to RuntimeValue.Primitive(15) inside `globalScope`.
```

---

## 2. Core Components

### 2.1. Lexer (`plc.project.lexer.Lexer`)

The Lexer is responsible for tokenization. It iterates character by character to group them into valid syntactic words.

- **`CharStream` class**: A helper nested inside `Lexer` that manages the input string state. It allows checking subsequent characters (`peek()`), consuming matched characters (`match()`), and building a token literal string (`emit()`). 
- **`lex()`**: The main loop that categorizes input. It ignores whitespace and comments, and delegates to specific literal matchers.
- **Specific token matched methods**: `lexIdentifier()`, `lexNumber()`, `lexString()`, `lexOperator()`.

**Example:**
When `lexToken()` encouters a quote `""`, it calls `lexString()`. `lexString()` reads until the next unescaped quote `""` and creates a `new Token(Token.Type.STRING, literal)`.

### 2.2 Abstract Syntax Tree (`plc.project.parser.Ast`)

The `Ast` component defines the structural nodes using Java `record` types.

- **`Source`**: The root of the tree, holding a list of statements.
- **`Stmt`**: Interfaces for Statements indicating commands (e.g., `Let`, `Def`, `If`, `For`, `Return`, `Assignment`).
- **`Expr`**: Interfaces for Expressions indicating values or operations that yield values (e.g., `Literal`, `Variable`, `Binary`, `Function`, `Method`, `Property`, `ObjectExpr`).
- **`Visitor<T, E>` interface**: Uses the Visitor design pattern. Defining this interface allows decoupling execution (in the Evaluator) from tree construction (in the Parser).

**Example:**
An AST node representing `1 + 2` is stored as:
`new Ast.Expr.Binary("+", new Ast.Expr.Literal(1), new Ast.Expr.Literal(2))`

### 2.3 Parser (`plc.project.parser.Parser`)

The parser ensures the code conforms to the expected grammar layout using *Recursive Descent* parsing. It linearly traverses a `TokenStream`.

- **`parseSource()` / `parseStmt()` / `parseExpr()`**: Main branches of the parser. They recursively parse parts of the syntax tree based on what token is seen.
- **Precedence Parsing**: Expressions are split into priority levels (`parseAdditiveExpr`, `parseMultiplicativeExpr`, `parseSecondaryExpr`, `parsePrimaryExpr`). This guarantees operations like `1 + 2 * 3` parse with `*` deeper in the tree, executing first.

**Example:**
In `parseLetStmt()`, it `match("LET")` to consume the token, then expects `match(Token.Type.IDENTIFIER)`. It optionally consumes an `=` to parse an expression (`parseExpr()`). Lastly, it forces matching a `;`. The result is an `Ast.Stmt.Let` object.

### 2.4 Evaluator (`plc.project.evaluator.Evaluator`)

The Evaluator executes the constructed tree. It implements the `Ast.Visitor` interface, defining execution semantics on different nodes while managing the call stack and variable definitions in `Scope`.

- **`visit(Ast.Stmt.Let)`**: Defines a new variable in the current `scope`.
- **`visit(Ast.Expr.Binary)`**: Retrieves the evaluated values of the `left` and `right` branches and performs arithmetic (+, -, *, /) or logical operations. It supports Java's `BigDecimal` and `BigInteger` for accurate large scale operations.
- **`visit(Ast.Stmt.Def)` & `Ast.Expr.Function`**: `Stmt.Def` stores a closure/custom `RuntimeValue.Function` in scope. When an `Ast.Expr.Function` triggers, it creates a new localized child `Scope` filled with passed arguments, executes the body, and resolves a returned value.
- **`Environment` class**: Serves as a standard library built-in toolkit, defining methods like `sqrt()` and `range()` directly implemented in standard Java, instead of interpreted Plc Language.

**Scope Example**:
```
{
    let x = 5;
    if true do
        let x = 10; 
        print(x); // Inner scope, accesses 10
    end
    print(x); // Parent scope, accesses 5
}
```
The evaluator uses `new Scope(parentScope)` inside block statements (`If`, `For`, function bodies) to ensure variables like `x` stay within bounds. When `visit(Ast.Expr.Variable)` executes, it looks for the variable matching the node's name traversing sequentially up the `Scope` chain.
