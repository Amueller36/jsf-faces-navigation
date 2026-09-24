package de.andre.jsfnavigation;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.resources.IFile;

public final class FlowFilterMatcher {

    private final String original;
    private final boolean regex;
    private final String text;
    private final Pattern pattern;
    private final String error;

    private FlowFilterMatcher(
            String original,
            boolean regex,
            String text,
            Pattern pattern,
            String error) {

        this.original =
                original == null
                        ? ""
                        : original;

        this.regex = regex;
        this.text =
                text == null
                        ? ""
                        : text;

        this.pattern = pattern;
        this.error = error;
    }

    public static FlowFilterMatcher compile(
            String query) {

        String value =
                query == null
                        ? ""
                        : query.trim();

        if (value.isEmpty()) {
            return new FlowFilterMatcher(
                    "",
                    false,
                    "",
                    null,
                    null);
        }

        boolean regex =
                value.startsWith("re:");

        String expression =
                regex
                        ? value.substring(3)
                                .trim()
                        : value;

        if (!regex
                && expression.length() >= 2
                && expression.startsWith("/")
                && expression.endsWith("/")) {

            regex = true;
            expression =
                    expression.substring(
                            1,
                            expression.length() - 1);
        }

        if (!regex) {
            return new FlowFilterMatcher(
                    value,
                    false,
                    expression.toLowerCase(
                            Locale.ENGLISH),
                    null,
                    null);
        }

        try {
            return new FlowFilterMatcher(
                    value,
                    true,
                    expression,
                    Pattern.compile(
                            expression,
                            Pattern.CASE_INSENSITIVE),
                    null);

        } catch (PatternSyntaxException e) {
            return new FlowFilterMatcher(
                    value,
                    true,
                    expression,
                    null,
                    e.getDescription());
        }
    }

    public boolean isActive() {
        return !original.isEmpty();
    }

    public boolean isValid() {
        return error == null;
    }

    public String getError() {
        return error == null
                ? ""
                : error;
    }

    public String getOriginal() {
        return original;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean matches(
            FlowEntry entry,
            IFile file) {

        if (!isActive()) {
            return true;
        }

        if (!isValid()
                || entry == null) {

            return false;
        }

        StringBuilder searchable =
                new StringBuilder();

        searchable.append(
                entry.getCategory())
                .append('\n')
                .append(
                        entry.getResourcePath());

        if (file != null) {
            searchable.append('\n')
                    .append(
                            file.getName())
                    .append('\n')
                    .append(
                            file.getProjectRelativePath()
                                    .toPortableString());
        }

        String haystack =
                searchable.toString();

        if (regex) {
            return pattern.matcher(
                    haystack)
                    .find();
        }

        return haystack
                .toLowerCase(
                        Locale.ENGLISH)
                .indexOf(text) >= 0;
    }
}
