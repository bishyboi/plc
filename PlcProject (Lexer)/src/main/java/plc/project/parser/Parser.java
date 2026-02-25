package plc.project.parser;

import com.google.common.base.Preconditions;
import plc.project.lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        if (tokens.peek("LET")) {
            return parseLetStmt();
        } else if (tokens.peek("DEF")) {
            return parseDefStmt();
        } else if (tokens.peek("IF")) {
            return parseIfStmt();
        } else if (tokens.peek("FOR")) {
            return parseForStmt();
        } else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        } else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt parseLetStmt() throws ParseException {
        tokens.match("LET");

        if(!tokens.match(Token.Type.IDENTIFIER)){
            throw error("Expected Identifier")
        }

        String name = tokens.get(-1).literal()

        Optional<Ast.Expr> value = Optional.empty();
        
        if (tokens.match("=")) {
            value = Optional.of(parseExpr());
        }

        if (!tokens.match(";")) {
            throw error("Expected ';'");
        }

        return new Ast.Stmt.Let(name, value);
    }

    private Ast.Stmt parseDefStmt() throws ParseException {
        tokens.match("DEF");

        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw error("Expected function name");
        }
        String name = tokens.get(-1).literal();

        if (!tokens.match("(")) {
            throw error("Expected '('");
        }

        List<String> parameters = new ArrayList<>();

        if (!tokens.peek(")")) {
            do {
                if (!tokens.match(Token.Type.IDENTIFIER)) {
                    throw error("Expected parameter name");
                }
                parameters.add(tokens.get(-1).literal());
            } while (tokens.match(","));
        }

        if (!tokens.match(")")) {
            throw error("Expected ')'");
        }

        if (!tokens.match("{")) {
            throw error("Expected '{'");
        }

        List<Ast.Stmt> body = new ArrayList<>();
        while (!tokens.peek("}")) {
            body.add(parseStmt());
        }

        tokens.match("}");

        return new Ast.Stmt.Def(name, parameters, body);
    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        tokens.match("IF");

        tokens.match("(");
        Ast.Expr condition = parseExpr();
        tokens.match(")");

        tokens.match("{");
        List<Ast.Stmt> thenStmts = new ArrayList<>();
        while (!tokens.peek("}")) {
            thenStmts.add(parseStmt());
        }
        tokens.match("}");

        List<Ast.Stmt> elseStmts = new ArrayList<>();

        if (tokens.match("ELSE")) {
            tokens.match("{");
            while (!tokens.peek("}")) {
                elseStmts.add(parseStmt());
            }
            tokens.match("}");
        }

        return new Ast.Stmt.If(condition, thenStmts, elseStmts);
    }

    private Ast.Stmt parseForStmt() throws ParseException {
        if (!tokens.match("FOR")) {
            throw new ParseException("Expected 'FOR'.", tokens.getNext());
        }
        if (!tokens.match("(")) {
            throw new ParseException("Expected '('.", tokens.getNext());
        }
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected loop variable name.", tokens.getNext());
        }
        String name = tokens.get(-1).literal();

        if (!tokens.match("IN")) {
            throw new ParseException("Expected 'IN'.", tokens.getNext());
        }

        Ast.Expr iterable = parseExpr();

        if (!tokens.match(")")) {
            throw new ParseException("Expected ')'.", tokens.getNext());
        }
        if (!tokens.match("{")) {
            throw new ParseException("Expected '{'.", tokens.getNext());
        }

        List<Ast.Stmt> body = new ArrayList<>();
        while (!tokens.peek("}")) {
            body.add(parseStmt());
        }

        if (!tokens.match("}")) {
            throw new ParseException("Expected '}'.", tokens.getNext());
        }

        return new Ast.Stmt.For(name, iterable, body);
    }

    private Ast.Stmt parseReturnStmt() throws ParseException {
        if (!tokens.match("RETURN")) {
            throw new ParseException("Expected 'RETURN'.", tokens.getNext());
        }

        Optional<Ast.Expr> value = Optional.empty();

        if (!tokens.peek(";")) {
            value = Optional.of(parseExpr());
        }

        if (!tokens.match(";")) {
            throw new ParseException("Expected ';'.", tokens.getNext());
        }

        return new Ast.Stmt.Return(value);
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr left = parseExpr();

        if (tokens.match("=")) {
            Ast.Expr value = parseExpr();

            if (!tokens.match(";")) {
                throw new ParseException("Expected ';'.", tokens.getNext());
            }

            return new Ast.Stmt.Assignment(left, value);
        }

        if (!tokens.match(";")) {
            throw new ParseException("Expected ';'.", tokens.getNext());
        }

        return new Ast.Stmt.Expression(left);
    }

    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();~
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseLiteralExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseGroupExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseObjectExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
