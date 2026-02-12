package plc.exercise.regextesting;

import java.io.File;
import java.io.FileFilter;
import java.util.Objects;
import java.util.regex.Pattern;


public final class RegexFileFilter implements FileFilter {
    private final Pattern regex;
    private final boolean filename;

    public RegexFileFilter(Pattern regex, boolean filename) {
        this.regex = regex;
        this.filename = filename;
    }

    @Override
    public boolean accept(File pathname) {
        String target = this.filename ? pathname.getName() : pathname.getPath();
        return regex.matcher(target).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegexFileFilter that = (RegexFileFilter) o;
        return filename == that.filename && Objects.equals(regex.pattern(), that.regex.pattern());
    }

    @Override
    public int hashCode() {
        return Objects.hash(regex.pattern(), filename);
    }

    @Override
    public String toString() {
        return "RegexFileFilter{" +
                "regex=" + regex.pattern() +
                ", filename=" + filename +
                '}';
    }
    public static File[] listJavaFiles(File directory) {
        // Using the regex from the quiz to search for files ending in .java
        return directory.listFiles(new RegexFileFilter(Pattern.compile(".*\\.java"), true));
    }
}