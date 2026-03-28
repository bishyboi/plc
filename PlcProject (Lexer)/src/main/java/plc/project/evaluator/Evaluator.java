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
        // evaluate each statement throw evaluateexception for unhandled returns
        RuntimeValue value = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        } catch (ReturnException e) {
            throw new EvaluateException("Return outside function.");
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        // define a variable in the current scope with an optional initial value
        RuntimeValue value;
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        } else {
            value = new RuntimeValue.Primitive(null);
        }

        try {
            scope.define(ast.name(), value);
        } catch (IllegalStateException e) {
            throw new EvaluateException("Variable " + ast.name() + " is already defined.", ast);
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        // defines a function in the current scope with static scoping and parameter handling
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
        // evaluate a conditional statement within its own scope for either branch
        RuntimeValue conditionValue = visit(ast.condition());
        Optional<Boolean> conditionOpt = requireType(conditionValue, Boolean.class);
        if (!conditionOpt.isPresent()) {
            throw new EvaluateException("Condition must be a Boolean.", ast.condition());
        }
        boolean condition = conditionOpt.get();
        Scope parentScope = scope;
        RuntimeValue value = new RuntimeValue.Primitive(null);
        scope = new Scope(scope);
        try {
            if (condition) {
                for (var stmt : ast.thenBody()) value = visit(stmt);
            } else {
                for (var stmt : ast.elseBody()) value = visit(stmt);
            }
        } finally {
            scope = parentScope;
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        // evaluates a for loop over an iterable expression utilizing nested scopes
        RuntimeValue expressionValue = visit(ast.expression());
        Optional<Iterable> iterableOpt = requireType(expressionValue, Iterable.class);
        if (!iterableOpt.isPresent()) {
            throw new EvaluateException("Expression must be an iterable.", ast.expression());
        }
        Iterable iterable = iterableOpt.get();
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
        // signals a function return by throwing a returnexception with the value
        RuntimeValue value;
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        } else {
            value = new RuntimeValue.Primitive(null);
        }
        throw new ReturnException(value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        // assigns a value to either a variable or a property of an object
        var value = visit(ast.value());
        if (ast.expression() instanceof Ast.Expr.Variable variable) {
            scope.assign(variable.name(), value);
        } else if (ast.expression() instanceof Ast.Expr.Property property) {
            RuntimeValue receiverValue = visit(property.receiver());
            Optional<RuntimeValue.ObjectValue> receiverOpt = requireType(receiverValue, RuntimeValue.ObjectValue.class);
            if (!receiverOpt.isPresent()) {
                throw new EvaluateException("Receiver must be an object.", ast.expression());
            }
            RuntimeValue.ObjectValue receiver = receiverOpt.get();
            receiver.scope().assign(property.name(), value);
        } else {
            throw new EvaluateException("Invalid assignment receiver.", ast.expression());
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        // returns the value of the expression within the group
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        // handles logical operations with short circuiting
        String operator = ast.operator();
        if (operator.equals("AND") || operator.equals("OR")) {
            RuntimeValue leftValue = visit(ast.left());
            Optional<Boolean> leftOpt = requireType(leftValue, Boolean.class);
            if (!leftOpt.isPresent()) {
                throw new EvaluateException("Left operand must be Boolean.", ast.left());
            }
            boolean left = leftOpt.get();

            if (operator.equals("AND")) {
                if (!left) {
                    return new RuntimeValue.Primitive(false);
                }
                RuntimeValue rightValue = visit(ast.right());
                Optional<Boolean> rightOpt = requireType(rightValue, Boolean.class);
                if (!rightOpt.isPresent()) {
                    throw new EvaluateException("Right operand must be Boolean.", ast.right());
                }
                return new RuntimeValue.Primitive(rightOpt.get());
            } else {
                if (left) {
                    return new RuntimeValue.Primitive(true);
                }
                RuntimeValue rightValue = visit(ast.right());
                Optional<Boolean> rightOpt = requireType(rightValue, Boolean.class);
                if (!rightOpt.isPresent()) {
                    throw new EvaluateException("Right operand must be Boolean.", ast.right());
                }
                return new RuntimeValue.Primitive(rightOpt.get());
            }
        }

        var leftVal = visit(ast.left());

        switch (ast.operator()) {
            case "+":
                // performs addition with string and decimal support
                var rightValForPlus = visit(ast.right());
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof String ||
                    rightValForPlus instanceof RuntimeValue.Primitive p2 && p2.value() instanceof String) {
                    return new RuntimeValue.Primitive(leftVal.print() + rightValForPlus.print());
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigInteger b1 &&
                    rightValForPlus instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigInteger b2) {
                    return new RuntimeValue.Primitive(b1.add(b2));
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigDecimal d1 &&
                    rightValForPlus instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigDecimal d2) {
                    return new RuntimeValue.Primitive(d1.add(d2));
                }
                throw new EvaluateException("Invalid operands for +.", ast);
            case "-":
            case "*":
            case "/":
                // executes arithmetic after validating numeric type
                boolean isLeftNumeric = leftVal instanceof RuntimeValue.Primitive p && (p.value() instanceof java.math.BigInteger || p.value() instanceof java.math.BigDecimal);
                if (!isLeftNumeric) {
                    throw new EvaluateException("Left operand must be numeric.", ast.left());
                }
                
                RuntimeValue rightVal = visit(ast.right());
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigInteger b1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigInteger b2) {
                    if (ast.operator().equals("/") && b2.equals(java.math.BigInteger.ZERO)) {
                        throw new EvaluateException("Divide by zero.", ast);
                    }
                    if (ast.operator().equals("-")) return new RuntimeValue.Primitive(b1.subtract(b2));
                    if (ast.operator().equals("*")) return new RuntimeValue.Primitive(b1.multiply(b2));
                    return new RuntimeValue.Primitive(b1.divide(b2));
                }
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof java.math.BigDecimal d1 &&
                    rightVal instanceof RuntimeValue.Primitive p2 && p2.value() instanceof java.math.BigDecimal d2) {
                    if (ast.operator().equals("/") && d2.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        throw new EvaluateException("Divide by zero.", ast);
                    }
                    if (ast.operator().equals("-")) return new RuntimeValue.Primitive(d1.subtract(d2));
                    if (ast.operator().equals("*")) return new RuntimeValue.Primitive(d1.multiply(d2));
                    if (d1.scale() == 0 && d2.scale() == 0) {
                        return new RuntimeValue.Primitive(d1.divide(d2, 0, java.math.RoundingMode.DOWN));
                    }
                    return new RuntimeValue.Primitive(d1.divide(d2, java.math.MathContext.DECIMAL64));
                }
                throw new EvaluateException("Invalid operands for arithmetic.", ast);
            case "==":
                // compares values using equality and relational operators
                var rightValForEq = visit(ast.right());
                return new RuntimeValue.Primitive(java.util.Objects.equals(
                    leftVal instanceof RuntimeValue.Primitive p1 ? p1.value() : leftVal,
                    rightValForEq instanceof RuntimeValue.Primitive p2 ? p2.value() : rightValForEq));
            case "!=":
                var rightValForNeq = visit(ast.right());
                return new RuntimeValue.Primitive(!java.util.Objects.equals(
                    leftVal instanceof RuntimeValue.Primitive p1 ? p1.value() : leftVal,
                    rightValForNeq instanceof RuntimeValue.Primitive p2 ? p2.value() : rightValForNeq));
            case "<":
            case "<=":
            case ">":
            case ">=":
                if (!(leftVal instanceof RuntimeValue.Primitive p && p.value() instanceof Comparable)) {
                    throw new EvaluateException("Left operand must be comparable.", ast.left());
                }
                var rightValForComp = visit(ast.right());
                if (leftVal instanceof RuntimeValue.Primitive p1 && p1.value() instanceof Comparable c1 &&
                    rightValForComp instanceof RuntimeValue.Primitive p2 && p2.value() != null && p2.value().getClass().isInstance(c1)) {
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
        // retrieves the value of a variable from the scope or throws an exception
        Optional<RuntimeValue> valueOpt = scope.resolve(ast.name());
        if (!valueOpt.isPresent()) {
            throw new EvaluateException("Variable " + ast.name() + " is not defined.", ast);
        }
        return valueOpt.get();
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        // evaluates a property access on an object supporting prototypal inheritance
        RuntimeValue receiverValue = visit(ast.receiver());
        Optional<RuntimeValue.ObjectValue> receiverOpt = requireType(receiverValue, RuntimeValue.ObjectValue.class);
        if (!receiverOpt.isPresent()) {
            throw new EvaluateException("Receiver must be an object.", ast.receiver());
        }
        RuntimeValue.ObjectValue receiver = receiverOpt.get();

        Optional<RuntimeValue> propertyOpt = resolveProperty(receiver, ast.name());
        if (!propertyOpt.isPresent()) {
            throw new EvaluateException("Property " + ast.name() + " is not defined.", ast);
        }
        return propertyOpt.get();
    }

    private Optional<RuntimeValue> resolveProperty(RuntimeValue.ObjectValue receiver, String name) {
        // check own variables
        var value = receiver.scope().get(name);
        if (value.isPresent()) return value;
        // check prototype
        var prototype = receiver.scope().get("prototype");
        if (prototype.isPresent() && prototype.get() instanceof RuntimeValue.ObjectValue protoObj) {
            return resolveProperty(protoObj, name);
        }
        // finally try resolving from the defining scope parent
        return receiver.scope().resolve(name);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        // evaluates a function call by resolving the function and invoking it with arguments
        Optional<RuntimeValue> resolvedOpt = scope.resolve(ast.name());
        if (!resolvedOpt.isPresent()) {
            throw new EvaluateException("Function " + ast.name() + " is not defined.", ast);
        }
        RuntimeValue resolvedValue = resolvedOpt.get();

        Optional<RuntimeValue.Function> functionOpt = requireType(resolvedValue, RuntimeValue.Function.class);
        if (!functionOpt.isPresent()) {
            throw new EvaluateException("Identifier " + ast.name() + " is not a function.", ast);
        }
        RuntimeValue.Function function = functionOpt.get();

        var arguments = new java.util.ArrayList<RuntimeValue>();
        for (var arg : ast.arguments()) {
            arguments.add(visit(arg));
        }
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        // evaluates a method call on an object passing the object as the first this argument
        RuntimeValue receiverValue = visit(ast.receiver());
        Optional<RuntimeValue.ObjectValue> receiverOpt = requireType(receiverValue, RuntimeValue.ObjectValue.class);
        if (!receiverOpt.isPresent()) {
            throw new EvaluateException("Receiver must be an object.", ast.receiver());
        }
        RuntimeValue.ObjectValue receiver = receiverOpt.get();

        Optional<RuntimeValue> propertyOpt = resolveProperty(receiver, ast.name());
        if (!propertyOpt.isPresent()) {
            throw new EvaluateException("Method " + ast.name() + " is not defined.", ast);
        }
        RuntimeValue propertyValue = propertyOpt.get();

        Optional<RuntimeValue.Function> functionOpt = requireType(propertyValue, RuntimeValue.Function.class);
        if (!functionOpt.isPresent()) {
            throw new EvaluateException("Property " + ast.name() + " is not a method.", ast);
        }
        RuntimeValue.Function function = functionOpt.get();

        var arguments = new java.util.ArrayList<RuntimeValue>();
        arguments.add(receiver);
        for (var arg : ast.arguments()) {
            arguments.add(visit(arg));
        }
        return function.definition().invoke(arguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        // creates a new object value with fields and methods supporting this keyword
        RuntimeValue.ObjectValue object = new RuntimeValue.ObjectValue(ast.name(), new Scope(scope));
        Scope parentScope = scope;
        scope = object.scope();
        try {
            // define all fields within the objects new scope
            for (var field : ast.fields()) {
                RuntimeValue value;
                if (field.value().isPresent()) {
                    value = visit(field.value().get());
                } else {
                    value = new RuntimeValue.Primitive(null);
                }
                scope.define(field.name(), value);
            }
            // define all methods with support for the this keyword
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
        // to be discussed in lecture
        Optional<Object> unwrapped;
        if (RuntimeValue.class.isAssignableFrom(type)) {
            unwrapped = Optional.of(value);
        } else {
            Optional<RuntimeValue.Primitive> primOpt = requireType(value, RuntimeValue.Primitive.class);
            unwrapped = primOpt.map(RuntimeValue.Primitive::value);
        }
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

    public static class Environment {

        public static RuntimeValue sqrt(List<RuntimeValue> arguments) throws EvaluateException {
            // calculates the square root of a biginteger or bigdecimal
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected 1 argument for sqrt.");
            }
            RuntimeValue argument = arguments.get(0);
            if (argument instanceof RuntimeValue.Primitive p) {
                Object value = p.value();
                if (value instanceof java.math.BigInteger b) {
                    return new RuntimeValue.Primitive(b.sqrt());
                }
                if (value instanceof java.math.BigDecimal d) {
                    return new RuntimeValue.Primitive(d.sqrt(java.math.MathContext.DECIMAL64));
                }
            }
            throw new EvaluateException("Invalid argument for sqrt.");
        }

        public static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException {
            // generates a list of bigintegers from start inclusive to end exclusive
            if (arguments.size() != 2) {
                throw new EvaluateException("Expected 2 arguments for range.");
            }

            Optional<java.math.BigInteger> startOpt = requireType(arguments.get(0), java.math.BigInteger.class);
            if (!startOpt.isPresent()) {
                throw new EvaluateException("Start must be an Integer.");
            }
            java.math.BigInteger start = startOpt.get();

            Optional<java.math.BigInteger> endOpt = requireType(arguments.get(1), java.math.BigInteger.class);
            if (!endOpt.isPresent()) {
                throw new EvaluateException("End must be an Integer.");
            }
            java.math.BigInteger end = endOpt.get();

            if (start.compareTo(end) > 0) {
                throw new EvaluateException("Start must be <= end.");
            }
            var list = new java.util.ArrayList<RuntimeValue>();
            for (var i = start; i.compareTo(end) < 0; i = i.add(java.math.BigInteger.ONE)) {
                list.add(new RuntimeValue.Primitive(i));
            }
            return new RuntimeValue.Primitive(list);
        }

    }

}
