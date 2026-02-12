package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>
 * Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        List<Token> tokens = new ArrayList<>();
        while (chars.has(0)) {
            if (chars.peek("[ \b\n\r\t]")) {
                lexWhitespace();
            } else if (chars.peek("/", "/")) {
                lexComment();
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    private void lexWhitespace() {
        while (chars.match("[ \b\n\r\t]"))
            ;
        chars.emit();
    }

    private void lexComment() {
        chars.match("/");
        chars.match("/");
        while (chars.match("[^\n\r]"))
            ;
        chars.emit();
    }

    private Token lexToken() {
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (chars.peek("[1-9]") || chars.peek("[+-]", "[1-9]")) {
            return lexNumber();
        } else if (chars.peek("'")) {
            return lexCharacter();
        } else if (chars.peek("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }
    }

    private Token lexIdentifier() {
        chars.match("[A-Za-z_]");
        while (chars.match("[A-Za-z0-9_-]"))
            ;
        return new Token(Token.Type.IDENTIFIER, chars.emit());
    }

    private Token lexNumber() {
        chars.match("[+-]");
        chars.match("[1-9]");
        while (chars.match("[1-9]"))
            ;

        boolean isDecimal = false;
        if (chars.peek("\\.", "[1-9]")) {
            isDecimal = true;
            chars.match("\\.");
            while (chars.match("[1-9]"))
                ;
        }

        if (chars.peek("[eE]", "[+-]", "[1-9]")) {
            chars.match("[eE]");
            chars.match("[+-]");
            while (chars.match("[1-9]"))
                ;
        } else if (chars.peek("[eE]", "[1-9]")) {
            chars.match("[eE]");
            while (chars.match("[1-9]"))
                ;
        }

        return new Token(isDecimal ? Token.Type.DECIMAL : Token.Type.INTEGER, chars.emit());
    }

    private Token lexCharacter() {
        chars.match("'");
        if (chars.peek("\\\\")) {
            lexEscape();
        } else if (chars.match("[^'\\n\\r]")) {
            // Matched regular character
        } else {
            throw new LexException("Invalid character literal", chars.index);
        }

        if (!chars.match("'")) {
            throw new LexException("Unterminated character literal", chars.index);
        }
        return new Token(Token.Type.CHARACTER, chars.emit());
    }

    private Token lexString() {
        chars.match("\"");
        while (!chars.peek("\"")) {
            if (chars.peek("\\n") || chars.peek("\\r") || !chars.has(0)) {
                throw new LexException("Unterminated string literal", chars.index);
            }
            if (chars.peek("\\\\")) {
                lexEscape();
            } else {
                chars.match(".");
            }
        }
        chars.match("\"");
        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() {
        chars.match("\\\\");
        if (!chars.match("[bnrt'\"\\\\]")) {
            throw new LexException("Invalid escape sequence", chars.index);
        }
    }

    // TODO: Double check that this function catches edge cases
    public Token lexOperator() {
        if (chars.match("[<>!=]", "=")) {

        } else {
            chars.match(".");
        }
        return new Token(Token.Type.OPERATOR, chars.emit());
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next character(s) match their corresponding
         * pattern(s). Each pattern is a regex matching ONE character, e.g.:
         * - peek("/") is valid and will match the next character
         * - peek("/", "/") is valid and will match the next two characters
         * - peek("/+") is conceptually invalid, but will match one character
         * - peek("//") is strictly invalid as it can never match one character
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
