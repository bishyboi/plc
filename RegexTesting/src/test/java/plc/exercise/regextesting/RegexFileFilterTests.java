package plc.exercise.regextesting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import java.io.FileFilter;
import java.util.Objects;

public final class RegexFileFilterTests {


    @Test

    public void test() {

//        throw new UnsupportedOperationException("TODO (uncomment)");
        var first = new RegexFileFilter(Pattern.compile("first"), true);
        var second = new RegexFileFilter(Pattern.compile("second"), false);

        Assertions.assertNotEquals(first, second);
        Assertions.assertNotEquals(first.hashCode(), second.hashCode());
        Assertions.assertNotEquals(first.toString(), second.toString());

    }


}
