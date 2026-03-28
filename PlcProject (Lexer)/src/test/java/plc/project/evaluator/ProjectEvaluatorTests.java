package plc.project.evaluator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ProjectEvaluatorTests {

    @Test
    void testLiteral() throws EvaluateException {
        test(new Ast.Expr.Literal(BigInteger.ONE), new RuntimeValue.Primitive(BigInteger.ONE));
        test(new Ast.Expr.Literal("string"), new RuntimeValue.Primitive("string"));
        test(new Ast.Expr.Literal(true), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Literal(null), new RuntimeValue.Primitive(null));
    }

    @Test
    void testBinaryArithmeticBigInteger() throws EvaluateException {
        test(new Ast.Expr.Binary("+", new Ast.Expr.Literal(BigInteger.valueOf(1)), new Ast.Expr.Literal(BigInteger.valueOf(2))), new RuntimeValue.Primitive(BigInteger.valueOf(3)));
        test(new Ast.Expr.Binary("-", new Ast.Expr.Literal(BigInteger.valueOf(5)), new Ast.Expr.Literal(BigInteger.valueOf(3))), new RuntimeValue.Primitive(BigInteger.valueOf(2)));
        test(new Ast.Expr.Binary("*", new Ast.Expr.Literal(BigInteger.valueOf(4)), new Ast.Expr.Literal(BigInteger.valueOf(2))), new RuntimeValue.Primitive(BigInteger.valueOf(8)));
        test(new Ast.Expr.Binary("/", new Ast.Expr.Literal(BigInteger.valueOf(10)), new Ast.Expr.Literal(BigInteger.valueOf(3))), new RuntimeValue.Primitive(BigInteger.valueOf(3)));
    }

    @Test
    void testBinaryArithmeticBigDecimal() throws EvaluateException {
        test(new Ast.Expr.Binary("+", new Ast.Expr.Literal(new BigDecimal("1.1")), new Ast.Expr.Literal(new BigDecimal("2.2"))), new RuntimeValue.Primitive(new BigDecimal("3.3")));
        test(new Ast.Expr.Binary("-", new Ast.Expr.Literal(new BigDecimal("5.5")), new Ast.Expr.Literal(new BigDecimal("3.3"))), new RuntimeValue.Primitive(new BigDecimal("2.2")));
        test(new Ast.Expr.Binary("*", new Ast.Expr.Literal(new BigDecimal("4.4")), new Ast.Expr.Literal(new BigDecimal("2.0"))), new RuntimeValue.Primitive(new BigDecimal("8.80")));
        test(new Ast.Expr.Binary("/", new Ast.Expr.Literal(new BigDecimal("1.0")), new Ast.Expr.Literal(new BigDecimal("3.0"))), new RuntimeValue.Primitive(new BigDecimal("0.3333333333333333")));
    }

    @Test
    void testBinaryStringConcatenation() throws EvaluateException {
        test(new Ast.Expr.Binary("+", new Ast.Expr.Literal("Hello, "), new Ast.Expr.Literal("World!")), new RuntimeValue.Primitive("Hello, World!"));
        test(new Ast.Expr.Binary("+", new Ast.Expr.Literal("Value: "), new Ast.Expr.Literal(BigInteger.TEN)), new RuntimeValue.Primitive("Value: 10"));
        test(new Ast.Expr.Binary("+", new Ast.Expr.Literal(true), new Ast.Expr.Literal(" is true")), new RuntimeValue.Primitive("TRUE is true"));
    }

    @Test
    void testBinaryComparison() throws EvaluateException {
        test(new Ast.Expr.Binary("<", new Ast.Expr.Literal(BigInteger.ONE), new Ast.Expr.Literal(BigInteger.TWO)), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Binary("<=", new Ast.Expr.Literal(BigInteger.ONE), new Ast.Expr.Literal(BigInteger.ONE)), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Binary(">", new Ast.Expr.Literal(BigInteger.TWO), new Ast.Expr.Literal(BigInteger.ONE)), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Binary(">=", new Ast.Expr.Literal(BigInteger.ONE), new Ast.Expr.Literal(BigInteger.ONE)), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Binary("==", new Ast.Expr.Literal(BigInteger.ONE), new Ast.Expr.Literal(BigInteger.ONE)), new RuntimeValue.Primitive(true));
        test(new Ast.Expr.Binary("!=", new Ast.Expr.Literal(BigInteger.ONE), new Ast.Expr.Literal(BigInteger.TWO)), new RuntimeValue.Primitive(true));
    }

    @Test
    void testBinaryLogical() throws EvaluateException {
        test(new Ast.Expr.Binary("AND", new Ast.Expr.Literal(true), new Ast.Expr.Literal(false)), new RuntimeValue.Primitive(false));
        test(new Ast.Expr.Binary("OR", new Ast.Expr.Literal(true), new Ast.Expr.Literal(false)), new RuntimeValue.Primitive(true));
        // Short-circuiting
        test(new Ast.Expr.Binary("AND", new Ast.Expr.Literal(false), new Ast.Expr.Variable("undefined")), new RuntimeValue.Primitive(false));
        test(new Ast.Expr.Binary("OR", new Ast.Expr.Literal(true), new Ast.Expr.Variable("undefined")), new RuntimeValue.Primitive(true));
    }

    @Test
    void testLetAndVariable() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        evaluator.visit(new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(BigInteger.TEN))));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.TEN), evaluator.visit(new Ast.Expr.Variable("x")));
    }

    @Test
    void testAssignment() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        evaluator.visit(new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(BigInteger.TEN))));
        evaluator.visit(new Ast.Stmt.Assignment(new Ast.Expr.Variable("x"), new Ast.Expr.Literal(BigInteger.valueOf(20))));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.valueOf(20)), evaluator.visit(new Ast.Expr.Variable("x")));
    }

    @Test
    void testIfStatement() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        evaluator.visit(new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(BigInteger.ZERO))));
        evaluator.visit(new Ast.Stmt.If(new Ast.Expr.Literal(true), 
            List.of(new Ast.Stmt.Assignment(new Ast.Expr.Variable("x"), new Ast.Expr.Literal(BigInteger.ONE))), 
            Collections.emptyList()));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.ONE), evaluator.visit(new Ast.Expr.Variable("x")));
    }

    @Test
    void testDefAndFunction() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        // DEF add(a, b) DO RETURN a + b; END
        evaluator.visit(new Ast.Stmt.Def("add", List.of("a", "b"), List.of(
            new Ast.Stmt.Return(Optional.of(new Ast.Expr.Binary("+", new Ast.Expr.Variable("a"), new Ast.Expr.Variable("b"))))
        )));
        // add(1, 2)
        RuntimeValue result = evaluator.visit(new Ast.Expr.Function("add", List.of(
            new Ast.Expr.Literal(BigInteger.valueOf(1)), 
            new Ast.Expr.Literal(BigInteger.valueOf(2))
        )));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.valueOf(3)), result);
    }

    @Test
    void testObjectExprAndMethod() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        // OBJECT MyObj DO LET x = 5; DEF getX() DO RETURN this.x; END END
        Ast.Expr.ObjectExpr objectExpr = new Ast.Expr.ObjectExpr(Optional.of("MyObj"), 
            List.of(new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(BigInteger.valueOf(5))))),
            List.of(new Ast.Stmt.Def("getX", Collections.emptyList(), List.of(
                new Ast.Stmt.Return(Optional.of(new Ast.Expr.Property(new Ast.Expr.Variable("this"), "x")))
            )))
        );
        evaluator.visit(new Ast.Stmt.Let("obj", Optional.of(objectExpr)));
        // obj.getX()
        RuntimeValue result = evaluator.visit(new Ast.Expr.Method(new Ast.Expr.Variable("obj"), "getX", Collections.emptyList()));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.valueOf(5)), result);
    }

    @Test
    void testInheritance() throws EvaluateException {
        Scope scope = Environment.scope();
        Evaluator evaluator = new Evaluator(scope);
        // Prototype: OBJECT Proto DO LET y = 10; END END
        Ast.Expr.ObjectExpr protoExpr = new Ast.Expr.ObjectExpr(Optional.of("Proto"), 
            List.of(new Ast.Stmt.Let("y", Optional.of(new Ast.Expr.Literal(BigInteger.valueOf(10))))),
            Collections.emptyList()
        );
        evaluator.visit(new Ast.Stmt.Let("proto", Optional.of(protoExpr)));
        // Child: OBJECT Child DO LET prototype = proto; END END
        Ast.Expr.ObjectExpr childExpr = new Ast.Expr.ObjectExpr(Optional.of("Child"), 
            List.of(new Ast.Stmt.Let("prototype", Optional.of(new Ast.Expr.Variable("proto")))),
            Collections.emptyList()
        );
        evaluator.visit(new Ast.Stmt.Let("child", Optional.of(childExpr)));
        // child.y
        RuntimeValue result = evaluator.visit(new Ast.Expr.Property(new Ast.Expr.Variable("child"), "y"));
        Assertions.assertEquals(new RuntimeValue.Primitive(BigInteger.valueOf(10)), result);
    }

    @Test
    void testSqrt() throws EvaluateException {
        test(new Ast.Expr.Function("sqrt", List.of(new Ast.Expr.Literal(BigInteger.valueOf(16)))), new RuntimeValue.Primitive(BigInteger.valueOf(4)));
        test(new Ast.Expr.Function("sqrt", List.of(new Ast.Expr.Literal(new BigDecimal("16.0")))), new RuntimeValue.Primitive(new BigDecimal("4.000000000000000")));
    }

    @Test
    void testRange() throws EvaluateException {
        RuntimeValue result = new Evaluator(Environment.scope()).visit(new Ast.Expr.Function("range", List.of(
            new Ast.Expr.Literal(BigInteger.valueOf(1)), 
            new Ast.Expr.Literal(BigInteger.valueOf(4))
        )));
        Assertions.assertTrue(result instanceof RuntimeValue.Primitive);
        Assertions.assertEquals(Arrays.asList(
            new RuntimeValue.Primitive(BigInteger.valueOf(1)),
            new RuntimeValue.Primitive(BigInteger.valueOf(2)),
            new RuntimeValue.Primitive(BigInteger.valueOf(3))
        ), ((RuntimeValue.Primitive) result).value());
    }

    private static void test(Ast ast, RuntimeValue expected) throws EvaluateException {
        Evaluator evaluator = new Evaluator(Environment.scope());
        RuntimeValue actual = ast instanceof Ast.Expr ? evaluator.visit((Ast.Expr) ast) : evaluator.visit((Ast.Source) ast);
        Assertions.assertEquals(expected, actual);
    }
}
