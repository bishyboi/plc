package plc.project.evaluator;

public final class ReturnException extends RuntimeException {

    private final RuntimeValue value;

    public ReturnException(RuntimeValue value) {
        this.value = value;
    }

    public RuntimeValue getValue() {
        return value;
    }

}
