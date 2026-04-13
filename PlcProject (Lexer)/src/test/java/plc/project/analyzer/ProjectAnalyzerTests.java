package plc.project.analyzer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Parser;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. This contains many more extended test cases.
 */
final class ProjectAnalyzerTests {

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Multiple Statements",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("x", Optional.empty(), Optional.of(new Ast.Expr.Literal(new BigInteger("1")))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("x"))
                )),
                Type.INTEGER
            ),
            Arguments.of("Empty Source",
                new Ast.Source(List.of()),
                Type.NIL
            ),
            Arguments.of("Expression Followed By If",
                new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("ignored")),
                    new Ast.Stmt.If(new Ast.Expr.Literal(true), List.of(), List.of())
                )),
                Type.NIL
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Initialization Double Subtype",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("y", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("y"))
                )),
                Type.COMPARABLE
            ),
            Arguments.of("Initialization Dynamic Explicit",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("ydyn", Optional.of("Dynamic"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("ydyn"))
                )),
                Type.DYNAMIC
            ),
            Arguments.of("Initialization Type Mismatch Primitive",
                new Ast.Source(List.of(
                    new Ast.Stmt.Let("z", Optional.of("Integer"), Optional.of(new Ast.Expr.Literal("value")))
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Multi Parameter Invalid Invocation",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("mult", List.of("p1", "p2"), List.of(Optional.of("String"), Optional.of("Integer")), Optional.empty(), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("mult", List.of(new Ast.Expr.Literal("str"))))
                )),
                new AnalyzeException("unused")
            ),
            Arguments.of("Return Type Valid",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("rt", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("Valid")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("rt", List.of()))
                )),
                Type.STRING
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If Valid Condition Boolean Expr",
                new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Binary("==", new Ast.Expr.Literal("1"), new Ast.Expr.Literal("1")), List.of(), List.of())
                )),
                Type.NIL
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For Without Iterable",
                new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Literal("Not Iterable!"),
                        List.of()
                    )
                )),
                new AnalyzeException("unused")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Object input, Object expected) {
        test(input, expected, "source");
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            Arguments.of("Mixed Branch Return Valid",
                new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.If(
                            new Ast.Expr.Literal(true),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("true branch")))),
                            List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("else branch"))))
                        )
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                )),
                Type.STRING
            )
        );
    }


    private static void test(Object input, Object expected, String rule) {
        var ast = switch (input) {
            case Ast parsed -> parsed;
            case String program -> Assertions.assertDoesNotThrow(() -> new Parser(new Lexer(program).lex()).parse(rule));
            default -> throw new AssertionError(input);
        };
        var scope = Environment.scope();
        var analyzer = new Analyzer(new Scope(scope));
        switch (expected) {
            case Type type -> {
                var received = Assertions.assertDoesNotThrow(() -> analyzer.visit(ast), "Unexpected AnalyzeException");
                Assertions.assertEquals(type, received);
            }
            case AnalyzeException e -> {
                var received = Assertions.assertThrows(AnalyzeException.class, () -> analyzer.visit(ast), "Expected AnalyzeException");
                if (e.getAst().isPresent()) {
                    Assertions.assertEquals(e.getAst(), received.getAst(), "Unexpected AnalyzeException Ast");
                }
            }
            default -> throw new AssertionError(input);
        }
    }

}
