package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Analyzer implements Ast.Visitor<Type, AnalyzeException> {

    private Context context;

    public Analyzer(Scope scope) {
        this.context = new Context(scope, Optional.empty(), new HashSet<>(), false);
    }

    public Context getContext() {
        return context;
    }

    @Override
    public Type visit(Ast.Source ast) throws AnalyzeException {
        Type type = Type.NIL;
        for (var statement : ast.statements()) {
            type = visit(statement);
        }
        return type;
    }

    @Override
    public Type visit(Ast.Stmt.Let ast) throws AnalyzeException {
        Type expected = null;
        if (ast.type().isPresent()) {
            expected = Environment.TYPES.get(ast.type().get());
            if (expected == null) throw new AnalyzeException(ast);
        }
        Type valType = ast.value().isPresent() ? visit(ast.value().get()) : null;
        if (expected != null && valType != null && !valType.isSubtypeOf(expected)) throw new AnalyzeException(ast);
        if (valType == null) context.uninitialized().add(ast.name());
        try {
            context.scope().declare(ast.name(), expected != null ? expected : (valType != null ? valType : Type.DYNAMIC));
        } catch (IllegalStateException e) {
            throw new AnalyzeException(ast);
        }
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.Def ast) throws AnalyzeException {
        List<Type> pTypes = new ArrayList<>();
        for (Optional<String> pt : ast.parameterTypes()) {
            Type t = pt.map(Environment.TYPES::get).orElse(Type.DYNAMIC);
            if (pt.isPresent() && t == null) throw new AnalyzeException(ast);
            pTypes.add(t);
        }
        Type retType = ast.returnType().map(Environment.TYPES::get).orElse(Type.DYNAMIC);
        if (ast.returnType().isPresent() && retType == null) throw new AnalyzeException(ast);
        Type.Function fnType = new Type.Function(pTypes, retType);
        try { context.scope().declare(ast.name(), fnType); } catch (IllegalStateException e) { throw new AnalyzeException(ast); }
        
        Context prev = context;
        context = new Context(new Scope(prev.scope()), Optional.of(fnType), new HashSet<>(prev.uninitialized()), false);
        for (int i = 0; i < ast.parameters().size(); i++) context.scope().declare(ast.parameters().get(i), pTypes.get(i));
        for (Ast.Stmt stmt : ast.body()) visit(stmt);
        if (!context.returns() && !retType.equals(Type.NIL) && !retType.equals(Type.DYNAMIC)) throw new AnalyzeException(ast);
        
        context = prev;
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.If ast) throws AnalyzeException {
        Type condType = visit(ast.condition());
        if (!condType.isSubtypeOf(Type.BOOLEAN)) throw new AnalyzeException(ast);
        
        Context prev = context;
        Context thenContext = new Context(prev);
        context = thenContext;
        ast.thenBody().forEach(this::visit);
        
        Context elseContext = new Context(prev);
        context = elseContext;
        ast.elseBody().forEach(this::visit);
        
        context = prev;
        context.merge(List.of(thenContext, elseContext));
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.For ast) throws AnalyzeException {
        Type exprType = visit(ast.expression());
        if (!exprType.isSubtypeOf(Type.ITERABLE)) throw new AnalyzeException(ast);
        
        Context prev = context;
        context = new Context(prev);
        try {
            context.scope().declare(ast.name(), Type.DYNAMIC);
            ast.body().forEach(this::visit);
        } catch (IllegalStateException e) {
            throw new AnalyzeException(ast);
        } finally {
            context = prev;
        }
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.Return ast) throws AnalyzeException {
        if (context.function().isEmpty()) throw new AnalyzeException(ast);
        Type retType = ast.value().isPresent() ? visit(ast.value().get()) : Type.NIL;
        if (!retType.isSubtypeOf(context.function().get().returns())) throw new AnalyzeException(ast);
        context.returns(true);
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        return visit(ast.expression());
    }

    @Override
    public Type visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        Type varType;
        if (ast.expression() instanceof Ast.Expr.Variable varExpr) {
            varType = context.scope().resolve(varExpr.name()).orElseThrow(() -> new AnalyzeException(ast));
            Type valType = visit(ast.value());
            if (!valType.isSubtypeOf(varType)) throw new AnalyzeException(ast);
            context.uninitialized().remove(varExpr.name());
        } else if (ast.expression() instanceof Ast.Expr.Property propExpr) {
            Type recType = visit(propExpr.receiver());
            if (recType instanceof Type.ObjectType obj) {
                varType = obj.scope().resolve(propExpr.name()).orElseThrow(() -> new AnalyzeException(ast));
            } else if (recType.equals(Type.DYNAMIC) || recType.equals(Type.ANY)) {
                varType = Type.DYNAMIC;
            } else {
                throw new AnalyzeException(ast);
            }
            Type valType = visit(ast.value());
            if (!valType.isSubtypeOf(varType)) throw new AnalyzeException(ast);
        } else {
            throw new AnalyzeException(ast);
        }
        return Type.NIL;
    }

    @Override
    public Type visit(Ast.Expr.Literal ast) throws AnalyzeException {
        return switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean b -> Type.BOOLEAN;
            case BigInteger i -> Type.INTEGER;
            case BigDecimal d -> Type.DECIMAL;
            case Character c -> Type.CHARACTER;
            case String s -> Type.STRING;
            default -> throw new AssertionError(ast.value().getClass());
        };
    }

    @Override
    public Type visit(Ast.Expr.Group ast) throws AnalyzeException {
        return visit(ast.expression());
    }

    @Override
    public Type visit(Ast.Expr.Binary ast) throws AnalyzeException {
        Type left = visit(ast.left());
        Type right = visit(ast.right());
        return switch (ast.operator()) {
            case "AND", "OR" -> {
                if (left.isSubtypeOf(Type.BOOLEAN) && right.isSubtypeOf(Type.BOOLEAN)) yield Type.BOOLEAN;
                throw new AnalyzeException(ast);
            }
            case "<", "<=", ">", ">=", "==", "!=" -> {
                if (ast.operator().equals("==") || ast.operator().equals("!=")) {
                    yield Type.BOOLEAN;
                }
                if (left.isSubtypeOf(Type.COMPARABLE) && right.isSubtypeOf(Type.COMPARABLE)) {
                    if (left.equals(Type.DYNAMIC) || right.equals(Type.DYNAMIC) || left.equals(right)) yield Type.BOOLEAN;
                }
                throw new AnalyzeException(ast);
            }
            case "+", "-", "*", "/", "^" -> {
                if (ast.operator().equals("+") && (left.equals(Type.STRING) || right.equals(Type.STRING))) yield Type.STRING;
                if (ast.operator().equals("+") && (left.equals(Type.DYNAMIC) && right.equals(Type.STRING) || left.equals(Type.STRING) && right.equals(Type.DYNAMIC))) yield Type.STRING;
                
                if (left.equals(Type.DYNAMIC) && right.equals(Type.DYNAMIC)) yield Type.DYNAMIC;
                if (left.equals(Type.DYNAMIC) && right.isSubtypeOf(Type.INTEGER)) yield Type.INTEGER;
                if (left.equals(Type.DYNAMIC) && right.isSubtypeOf(Type.DECIMAL)) yield Type.DECIMAL;
                if (right.equals(Type.DYNAMIC) && left.isSubtypeOf(Type.INTEGER)) yield Type.INTEGER;
                if (right.equals(Type.DYNAMIC) && left.isSubtypeOf(Type.DECIMAL)) yield Type.DECIMAL;

                if (left.isSubtypeOf(Type.INTEGER) && right.isSubtypeOf(Type.INTEGER) && left.equals(right)) yield Type.INTEGER;
                if (left.isSubtypeOf(Type.DECIMAL) && right.isSubtypeOf(Type.DECIMAL) && left.equals(right)) yield Type.DECIMAL;
                throw new AnalyzeException(ast);
            }
            default -> throw new AnalyzeException(ast);
        };
    }

    @Override
    public Type visit(Ast.Expr.Variable ast) throws AnalyzeException {
        if (context.uninitialized().contains(ast.name())) throw new AnalyzeException(ast);
        return context.scope().resolve(ast.name()).orElseThrow(() -> new AnalyzeException(ast));
    }

    @Override
    public Type visit(Ast.Expr.Property ast) throws AnalyzeException {
        Type receiverType = visit(ast.receiver());
        if (receiverType instanceof Type.ObjectType obj) {
            return obj.scope().resolve(ast.name()).orElseThrow(() -> new AnalyzeException(ast));
        } else if (receiverType.equals(Type.DYNAMIC) || receiverType.equals(Type.ANY)) {
            return Type.DYNAMIC;
        } else {
            throw new AnalyzeException(ast);
        }
    }

    @Override
    public Type visit(Ast.Expr.Function ast) throws AnalyzeException {
        Type funcType = context.scope().resolve(ast.name()).orElseThrow(() -> new AnalyzeException(ast));
        if (funcType instanceof Type.Function f) {
            if (ast.arguments().size() != f.parameters().size()) throw new AnalyzeException(ast);
            for (int i = 0; i < ast.arguments().size(); i++) {
                Type argType = visit(ast.arguments().get(i));
                if (!argType.isSubtypeOf(f.parameters().get(i))) throw new AnalyzeException(ast);
            }
            return f.returns();
        } else if (funcType.equals(Type.DYNAMIC) || funcType.equals(Type.ANY)) {
            return Type.DYNAMIC;
        } else {
            throw new AnalyzeException(ast);
        }
    }

    @Override
    public Type visit(Ast.Expr.Method ast) throws AnalyzeException {
        Type receiverType = visit(ast.receiver());
        if (receiverType instanceof Type.ObjectType obj) {
            Type funcType = obj.scope().resolve(ast.name()).orElseThrow(() -> new AnalyzeException(ast));
            if (funcType instanceof Type.Function f) {
                if (ast.arguments().size() != f.parameters().size()) throw new AnalyzeException(ast);
                for (int i = 0; i < ast.arguments().size(); i++) {
                    Type argType = visit(ast.arguments().get(i));
                    if (!argType.isSubtypeOf(f.parameters().get(i))) throw new AnalyzeException(ast);
                }
                return f.returns();
            } else {
                throw new AnalyzeException(ast);
            }
        } else if (receiverType.equals(Type.DYNAMIC) || receiverType.equals(Type.ANY)) {
            return Type.DYNAMIC;
        } else {
            throw new AnalyzeException(ast);
        }
    }

    @Override
    public Type visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        Scope objScope = new Scope(null);
        for (Ast.Stmt.Let field : ast.fields()) {
            Type expected = null;
            if (field.type().isPresent()) {
                expected = Environment.TYPES.get(field.type().get());
                if (expected == null) throw new AnalyzeException(ast);
            }
            Type valType = field.value().isPresent() ? visit(field.value().get()) : null;
            if (expected != null && valType != null && !valType.isSubtypeOf(expected)) throw new AnalyzeException(ast);
            Type finalType = expected != null ? expected : (valType != null ? valType : Type.DYNAMIC);
            objScope.declare(field.name(), finalType);
        }
        for (Ast.Stmt.Def method : ast.methods()) {
            List<Type> paramTypes = new ArrayList<>();
            for (Optional<String> pt : method.parameterTypes()) {
                Type t = pt.map(Environment.TYPES::get).orElse(Type.DYNAMIC);
                if (pt.isPresent() && t == null) throw new AnalyzeException(ast);
                paramTypes.add(t);
            }
            Type returnType = method.returnType().map(Environment.TYPES::get).orElse(Type.DYNAMIC);
            if (method.returnType().isPresent() && returnType == null) throw new AnalyzeException(ast);
            Type.Function funcType = new Type.Function(paramTypes, returnType);
            objScope.declare(method.name(), funcType);
            
            Context prev = context;
            context = new Context(new Scope(context.scope()), Optional.of(funcType), new HashSet<>(prev.uninitialized()), false);
            for (int i = 0; i < method.parameters().size(); i++) {
                context.scope().declare(method.parameters().get(i), paramTypes.get(i));
            }
            for (Ast.Stmt stmt : method.body()) visit(stmt);
            if (!context.returns() && !returnType.equals(Type.NIL) && !returnType.equals(Type.DYNAMIC)) throw new AnalyzeException(ast);
            context = prev;
        }
        return new Type.ObjectType(ast.name(), objScope);
    }

    public static final class ContextHooks {
        public static Set<String> mergeUninitialized(List<Set<String>> children) {
            Set<String> merged = new HashSet<>();
            for (Set<String> child : children) merged.addAll(child);
            return merged;
        }

        public static boolean mergeReturns(List<Boolean> children) {
            return !children.isEmpty() && children.stream().allMatch(b -> b);
        }
    }

    public static final class EnvironmentHooks {
        public static final Type SQRT = new Type.Function(List.of(Type.DECIMAL), Type.DECIMAL);
        public static final Type RANGE = new Type.Function(List.of(Type.INTEGER, Type.INTEGER), Type.ITERABLE);
    }

    public static final class TypeHooks {
        public static boolean isSubtypeOf(Type subtype, Type supertype) {
            if (subtype.equals(supertype)) return true;
            if (supertype.equals(Type.ANY)) return true;
            if (subtype.equals(Type.DYNAMIC) || supertype.equals(Type.DYNAMIC)) return true;
            if (supertype.equals(Type.EQUATABLE) && (subtype.equals(Type.BOOLEAN) || subtype.equals(Type.INTEGER) || subtype.equals(Type.DECIMAL) || subtype.equals(Type.CHARACTER) || subtype.equals(Type.STRING))) return true;
            if (supertype.equals(Type.COMPARABLE) && (subtype.equals(Type.INTEGER) || subtype.equals(Type.DECIMAL) || subtype.equals(Type.CHARACTER) || subtype.equals(Type.STRING))) return true;
            return false;
        }
    }

}
