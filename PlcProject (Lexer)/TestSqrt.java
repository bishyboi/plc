import java.math.BigDecimal;
import java.math.MathContext;

public class TestSqrt {
    public static void main(String[] args) {
        System.out.println("sqrt(16.0): " + new BigDecimal("16.0").sqrt(MathContext.DECIMAL64));
        System.out.println("sqrt(4.0): " + new BigDecimal("4.0").sqrt(MathContext.DECIMAL64));
    }
}
