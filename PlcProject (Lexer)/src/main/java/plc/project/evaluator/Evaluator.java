package plc.project.evaluator;

import plc.project.parser.Ast;

import java.util.List;
import java.util.Optional;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        // CHANGE: Evaluate each statement, handling ReturnException for global returns.
        RuntimeValue value = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        } catch (ReturnException e) {
            value = e.getValue();
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        // CHANGE: Define a variable in the current scope with an optional initial value.
        var value = ast.value().isPresent() ? visit(ast.value().get()) : new RuntimeValue.Primitive(null);
        scope.define(ast.name(), value);
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        // CHANGE: Defines a function in the current scope with static scoping and parameter handling.
        Scope definitionScope = scope;
        RuntimeValue.Function function = new RuntimeValue.Function(ast.name(), arguments -> {
            if (arguments.size() != ast.parameters().size()) {
                throw new EvaluateException("Expected " + ast.parameters().size() + " arguments, but got " + arguments.size() + ".");
            }
            Scope parentScope = scope;
            scope = new Scope(definitionScope);
            for (int i = 0; i < arguments.size(); i++) {
                scope.define(ast.parameters().get(i), arguments.get(i));
            }
            scope = new Scope(scope);
            try {
                for (var stmt : ast.body()) visit(stmt);
            } catch (ReturnException e) {
                return e.getValue();
            } finally {
                scope = parentScope;
            }
            return new RuntimeValue.Primitive(null);
        });
        scope.define(ast.name(), function);
        return function;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        // CHANGE: Evaluate a conditional statement within its own scope for either branch.
        var condition = requireType(visit(ast.condition()), Boolean.class)
            .orElseThrow(() -> new EvaluateException("Condition must be a Boolean.", ast.condition()));
        RuntimeValue value = new RuntimeValue.Primitive(null);
        scope = new Scope(scope);
        try {
            if (condition) {
                for (var stmt : ast.thenBody()) value = visit(stmt);
            } else {
                for (var stmt : ast.elseBody()) value = visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        // CHANGE: Evaluates a for loop over an iterable expression, utilizing nested scopes.
        var iterable = requireType(visit(ast.expression()), Iterable.class)
            .orElseThrow(() -> new EvaluateException("Expression must be an iterable.", ast.expression()));
        for (Object element : iterable) {
            Scope parentScope = scope;
            scope = new Scope(scope);
            scope.define(ast.name(), (RuntimeValue) element);
            scope = new Scope(scope);
            try {
                for (var stmt : ast.body()) visit(stmt);
            } finally {
                scope = parentScope;
            }
        }
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        // CHANGE: Signals a function return by throwing a ReturnException with the value.
        throw new ReturnException(visit(ast.value()));
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        // CHANGE: Assigns a value to either a variable or a property of an object.
        var value = visit(ast.value());
        if (ast.receiver() instanceof Ast.Expr.Variable variable) {
            scope.assign(variable.name(), value);
        } else if (ast.receiver() instanceof Ast.Expr.Property property) {
            var receiver = requireType(visit(property.receiver()), RuntimeValue.ObjectValue.class)
                .orElseThrow(() -> new EvaluateException("Receiver must be an object.", ast.receiver()));
            receiver.scope().assign(property.name(), value);
        } else {
            throw new EvaluateException("Invalid assignment receiver.", ast.receiver());
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        // CHANGE: Returns the value of the expression within the group.
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        // CHANGE: Evaluates binary operations including arithmetic, comparison, and logical.
        if (ast.operator().equals("AND") || ast.operator().equals("OR")) {
            var left = requireType(visit(ast.left()), Boolean.class)
                .orElseThrow(() -> new EvaluateException("Left operand must be Boolean.", ast.left()));
            if (ast.operator().equals("AND")) {
                return new RuntimeValue.Primitive(left && requireType(visit(ast.right()), Boolean.class)
                    .orElseThrow(() -> new EvaluateException("Right operand must be Boolean.", ast.right())));
            } else {
                return new RuntimeValue.Primitive(left || requireType(visit(ast.right()), Boolean.class)
                    .orElseThrow(() -> new EvaluateException("Right operand must be Boolean.", ast.right())));
            }
        }

        var leftVal = visit(ast.left());
        var rightVal = visit(ast.right());

        switch (ast.operator()) {
            case "+":
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof String ||
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof String) {
                    return new RuntimeValue.Primitive(leftVal.print() + rightVal.print());
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigInteger b1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigInteger b2) {
                    return new RuntimeValue.Primitive(b1.add(b2));
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigDecimal d1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigDecimal d2) {
                    return new RuntimeValue.Primitive(d1.add(d2));
                }
                throw new EvaluateException("Invalid operands for +.", ast);
            case "-":
            case "*":
            case "/":
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigInteger b1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigInteger b2) {
                    if (ast.operator().equals("/") && b2.equals(java.math.BigInteger.ZERO)) throw new EvaluateException("Divide by zero.", ast);
                    return new RuntimeValue.Primitive(ast.operator().equals("-") ? b1.subtract(b2) : ast.operator().equals("*") ? b1.multiply(b2) : b1.divide(b2));
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigDecimal d1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigDecimal d2) {
                    if (ast.operator().equals("/") && d2.compareTo(java.math.BigDecimal.ZERO) == 0) throw new EvaluateException("Divide by zero.", ast);
                    return new RuntimeValue.Primitive(ast.operator().equals("-") ? d1.subtract(d2) : ast.operator().equals("*") ? d1.multiply(d2) : d1.divide(d2, java.math.MathContext.DECIMAL64));
                }
                throw new EvaluateException("Invalid operands for arithmetic.", ast);
            case "==":
                return new RuntimeValue.Primitive(java.util.Objects.equals(
                    leftVal instanceof RuntimeValue.Primitive p1 ? p1.value() : leftVal,
                    rightVal instanceof RuntimeValue.Primitive p2 ? p2.value() : rightVal));
            case "!=":
                return new RuntimeValue.Primitive(!java.util.Objects.equals(
                    leftVal instanceof RuntimeValue.Primitive p1 ? p1.value() : leftVal,
                    rightVal instanceof RuntimeValue.Primitive p2 ? p2.value() : rightVal));
            case "<":
            case "<=":
            case ">":
            case ">=":
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof Comparable c1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() != null && p2.value().getClass().isInstance(c1)) {
                    int cmp = c1.compareTo(p2.value());
                    return new RuntimeValue.Primitive(ast.operator().equals("<") ? cmp < 0 : ast.operator().equals("<=") ? cmp <= 0 : ast.operator().equals(">") ? cmp > 0 : cmp >= 0);
                }
                throw new EvaluateException("Invalid operands for comparison.", ast);
            default:
                throw new EvaluateException("Unsupported operator: " + ast.operator(), ast);
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        // CHANGE: Retrieves the value of a variable from the scope or throws an exception.
        return scope.resolve(ast.name())
            .orElseThrow(() -> new EvaluateException("Variable " + ast.name() + " is not defined.", ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        // CHANGE: Evaluates a property access on an object, supporting prototypal inheritance.
        var receiver = requireType(visit(ast.receiver()), RuntimeValue.ObjectValue.class)
            .orElseThrow(() -> new EvaluateException("Receiver must be an object.", ast.receiver()));
        return receiver.scope().resolve(ast.name())
            .orElseThrow(() -> new EvaluateException("Property " + ast.name() + " is not defined.", ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        // CHANGE: Evaluates a function call by resolving the function and invoking it with arguments.
        var function = requireType(scope.resolve(ast.name())
            .orElseThrow(() -> new EvaluateException("Function " + ast.name() + " is not defined.", ast)), RuntimeValue.Function.class)
            .orElseThrow(() -> new EvaluateException("Identifier " + ast.name() + " is not a function.", ast));
        var arguments = new java.util.ArrayList<RuntimeValue>();
        for (var arg : ast.arguments()) arguments.add(visit(arg));
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        // CHANGE: Evaluates a method call on an object, passing the object as the first 'this' argument.
        var receiver = requireType(visit(ast.receiver()), RuntimeValue.ObjectValue.class)
            .orElseThrow(() -> new EvaluateException("Receiver must be an object.", ast.receiver()));
        var function = requireType(receiver.scope().resolve(ast.name())
            .orElseThrow(() -> new EvaluateException("Method " + ast.name() + " is not defined.", ast)), RuntimeValue.Function.class)
            .orElseThrow(() -> new EvaluateException("Property " + ast.name() + " is not a method.", ast));
        var arguments = new java.util.ArrayList<RuntimeValue>();
        arguments.add(receiver);
        for (var arg : ast.arguments()) arguments.add(visit(arg));
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        // CHANGE: Creates a new object value with fields and methods, supporting 'this' keyword.
        RuntimeValue.ObjectValue object = new RuntimeValue.ObjectValue(ast.name(), new Scope(scope));
        Scope parentScope = scope;
        scope = object.scope();
        try {
            for (var field : ast.fields()) {
                var value = field.value().isPresent() ? visit(field.value().get()) : new RuntimeValue.Primitive(null);
                scope.define(field.name(), value);
            }
            for (var method : ast.methods()) {
                RuntimeValue.Function function = new RuntimeValue.Function(method.name(), arguments -> {
                    if (arguments.size() != method.parameters().size() + 1) {
                        throw new EvaluateException("Expected " + method.parameters().size() + " arguments, but got " + (arguments.size() - 1) + ".");
                    }
                    Scope pScope = scope;
                    scope = new Scope(object.scope());
                    scope.define("this", arguments.get(0));
                    for (int i = 0; i < method.parameters().size(); i++) {
                        scope.define(method.parameters().get(i), arguments.get(i + 1));
                    }
                    scope = new Scope(scope);
                    try {
                        for (var stmt : method.body()) visit(stmt);
                    } catch (ReturnException e) {
                        return e.getValue();
                    } finally {
                        scope = pScope;
                    }
                    return new RuntimeValue.Primitive(null);
                });
                scope.define(method.name(), function);
            }
        } finally {
            scope = parentScope;
        }
        return object;
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If type
     * is a subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value must be a {@link RuntimeValue.Primitive} and
     * the check applies to the primitive value.
     */
    private static <T> Optional<T> requireType(RuntimeValue value, Class<T> type) {
        //To be discussed in lecture
        Optional<Object> unwrapped = RuntimeValue.class.isAssignableFrom(type)
            ? Optional.of(value)
            : requireType(value, RuntimeValue.Primitive.class).map(RuntimeValue.Primitive::value);
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

    public static class Environment {

        public static RuntimeValue sqrt(List<RuntimeValue> arguments) throws EvaluateException {
            // CHANGE: Calculates the square root of a BigInteger or BigDecimal.
            if (arguments.size() != 1) throw new EvaluateException("Expected 1 argument for sqrt.");
            var value = arguments.get(0);
            if (value instanceof RuntimeValue.Primitive p && p.value() instanceof java.math.BigInteger b) {
                return new RuntimeValue.Primitive(b.sqrt());
            } else if (value instanceof RuntimeValue.Primitive p && p.value() instanceof java.math.BigDecimal d) {
                return new RuntimeValue.Primitive(d.sqrt(java.math.MathContext.DECIMAL64));
            }
            throw new EvaluateException("Invalid argument for sqrt.");
        }

        public static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException {
            // CHANGE: Generates a list of BigIntegers from start (inclusive) to end (exclusive).
            if (arguments.size() != 2) throw new EvaluateException("Expected 2 arguments for range.");
            var start = requireType(arguments.get(0), java.math.BigInteger.class)
                .orElseThrow(() -> new EvaluateException("Start must be an Integer."));
            var end = requireType(arguments.get(1), java.math.BigInteger.class)
                .orElseThrow(() -> new EvaluateException("End must be an Integer."));
            if (start.compareTo(end) > 0) throw new EvaluateException("Start must be <= end.");
            var list = new java.util.ArrayList<RuntimeValue>();
            for (var i = start; i.compareTo(end) < 0; i = i.add(java.math.BigInteger.ONE)) {
                list.add(new RuntimeValue.Primitive(i));
            }
            return new RuntimeValue.Primitive(list);
        }

    }

}
