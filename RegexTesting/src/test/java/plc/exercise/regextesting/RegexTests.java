package plc.exercise.regextesting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RegexTests {

    @ParameterizedTest
    @MethodSource
    public void testEmail(String test, String input, boolean matches) {
        test(input, Regex.EMAIL, matches);
    }

    public static Stream<Arguments> testEmail() {
        return Stream.of(
                Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
                Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
                Arguments.of("Subdomain", "otherdomain@cise.ufl.edu", false),
                Arguments.of("Another Subdomain", "otherdomain@eng.ufl.edu", false),
                Arguments.of("Another Another Subdomain", "otherdomain@admin.ufl.edu", false),
                Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
                Arguments.of("Symbols", "symbols#$%@gmail.com", false),
                //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

                Arguments.of("Bad Domain", "bishoy@ufl.e", false),
                Arguments.of("Missing @ Sign", "bishoy$gmail.com", false),
                Arguments.of("Symbols in email", "bishoy@gmai$l.com", false),
                Arguments.of("Dots in name and email domain", "bishoy.pramanik@gmail.co.edu.gov", false),
                Arguments.of("Double dot", "bishoy@gmail..com", false),

                Arguments.of("My email", "bishoy.pramanik@ufl.edu", true),
                Arguments.of("Email with numbers", "bishoy48@gmail.com", true),
                Arguments.of("Email with symbols", "bishoy_pramanik@gmail.com", true),
                Arguments.of("Double dot in name", "sean.o.connery@gmail.com", true),
                Arguments.of("Underscore + Numbers", "love_rosie92@gmail.com", true)

        );
    }

    @ParameterizedTest
    @MethodSource
    public void testSearchTerm(String test, String input, boolean matches) {
        test(input, Regex.SEARCH_TERM, matches);
    }

    public static Stream<Arguments> testSearchTerm() {
        return Stream.of(
                Arguments.of("Equal", "search", true),
                Arguments.of("Substring", "google search \"lmgtfy\"", true),
                Arguments.of("Spaces", "use arch", false),
                //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

                Arguments.of("Sentence Search", "search for steve harvey m&m", true),
                Arguments.of("Search end of Sentence", "give me steve harvey m&m search", true),
                Arguments.of("Search inside numbers", "1234567890search123", true),
                Arguments.of("Search inside letters", "europesearchaustralia", true),
                Arguments.of("Search with leading space", " search", true),

                Arguments.of("Search uppercase", "toSEARCH me", false),
                Arguments.of("Redherring", "research", false),
                Arguments.of("Typo", "serach", false),
                Arguments.of("Another typo", "searh", false),
                Arguments.of("Spaces between letter", "s e a rch", false),
                Arguments.of("Symbol in-between", "se-arch", false)

        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDiscountCsv(String test, String input, boolean matches) {
        test(input, Regex.DISCOUNT_CSV, matches);
    }

    public static Stream<Arguments> testDiscountCsv() {
        return Stream.of(
                Arguments.of("Single", "single", true),
                Arguments.of("Multiple", "one,two,three", true),
                Arguments.of("Spaces", "first , second", true),
                Arguments.of("Missing Value", "first,,second", false),
                //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

                Arguments.of("Underscore in data", "foo_bar, one-two", true),
                Arguments.of("Inconsistent spaces", "entry1,entry2 , entry3 ,entry4", true),
                Arguments.of("Single value", "entry", true),
                Arguments.of("Lots of spaces", "yes   ,   no", true),
                Arguments.of("Numbers and Letters", "123, yes, blue", true),

                Arguments.of("Trailing comma", "entry1, ", false),
                Arguments.of("Spaces inside entry", "entry 1, entry2", false),
                Arguments.of("Leading comma", ", entry1", false),
                Arguments.of("Missing value", "first,, third", false),
                Arguments.of("Space for value", "first, , third", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDateNotation(String test, String input, boolean matches, Map<String, String> groups) {
        var matcher = Regex.DATE_NOTATION.matcher(input);
        Assertions.assertEquals(matches, matcher.matches());
        if (matches) {
            Assertions.assertEquals(groups, Regex.getGroups(matcher));
        }
    }

    public static Stream<Arguments> testDateNotation() {
        return Stream.of(
                Arguments.of("Month/Day", "3/14", true, Map.of(
                        "month", "3", "day", "14"
                )),
                Arguments.of("Month/Day/Year", "3/14/2026", true, Map.of(
                        "month", "3", "day", "14", "year", "2026"
                )),
                Arguments.of("Month Leading Zero", "03/14", false, Map.of()),
                Arguments.of("Missing Year", "3/14/", false, Map.of()),
                //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
                Arguments.of("Month Notation", "1/1", true, Map.of("month", "1", "day", "1")),
                Arguments.of("Leap Year", "2/29/2024", true, Map.of("month", "2", "day", "29", "year", "2024")),
                Arguments.of("Full Date", "4/30/2004", true, Map.of("month", "4", "day", "30", "year", "2004")),
                Arguments.of("Far back dates", "8/14/1947", true, Map.of("month", "8", "day", "14", "year", "1947")),
                Arguments.of("Halloween!!", "10/31/2023", true, Map.of("month", "10", "day", "31", "year", "2023")),

                Arguments.of("Month Leading Zero", "03/14", false, Map.of()),
                Arguments.of("Missing Year", "3/14/", false, Map.of()),
                Arguments.of("Trailing Year Slash", "10/31/", false, Map.of()),
                Arguments.of("Negative Year", "10/31/-2023", false, Map.of()),
                Arguments.of("Not a Leap Year", "2/29/2023", false, Map.of()),
                Arguments.of("0 Month", "0/10/2023", false, Map.of()),
                Arguments.of("0 Day", "1/0/2020", false, Map.of()),
                Arguments.of("0 Year", "1/1/0", false, Map.of()),

                //Note: The bonus is deceptively straightforward at first glance.
                //Passing this test case indicates you are attempting the bonus.
                Arguments.of("March 32nd (Bonus)", "3/32", false, Map.of())
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumber(String test, String input, boolean matches) {
        test(input, Regex.NUMBER, matches);
    }

    public static Stream<Arguments> testNumber() {
        //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
        return Stream.of(
                Arguments.of("Zero", "0", true),
                Arguments.of("Sequence", "123", true),
                Arguments.of("Negative Numbers", "-42", true),
                Arguments.of("Decimal Check", "5.31", true),
                Arguments.of("Scientific Notation", "5e4", true),

                Arguments.of("Multiple Decimal Points", "3..33", false),
                Arguments.of("Two signs", "--3", false),
                Arguments.of("Bad Scientific Notation with not an integer exponent", "2e2.3", false),
                Arguments.of("Multiple Exponents", "2e2e2", false),
                Arguments.of("Trailing Decimal", "5.", false)
        );
//        throw new UnsupportedOperationException("TODO");
    }

    @ParameterizedTest
    @MethodSource
    public void testString(String test, String input, boolean matches) {
        test(input, Regex.STRING, matches);
    }

    public static Stream<Arguments> testString() {
        //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
//        throw new UnsupportedOperationException("TODO");
        return Stream.of(
                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Normal String", "\"hello\"", true),
                Arguments.of("Spaces", "\"hello spaces\"", true),
                Arguments.of("Internal Escaped Quotes", "\"he said \\\"hi!\\\"\"", true),
                // Use \\t to represent the literal characters \ and t
                Arguments.of("Literal Escape Tab", "\"tabs:\\t\"", true),

                Arguments.of("No terminating quote", "\"hello", false),
                Arguments.of("Odd number of unescaped quotes", "\"odd\" quotes\"", false),
                Arguments.of("Bad escape sequence", "\"\\a\"", false),
                Arguments.of("Single Quotes", "'hi'", false),
                Arguments.of("No leading quote", "hello\"", false)
        );
    }

    private static void test(String input, Pattern pattern, boolean matches) {
        Assertions.assertEquals(matches, pattern.matcher(input).matches());
    }

}
