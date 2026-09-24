package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class WebSphereLogFilterRules {

    private static String cachedSource = null;

    private static List<Rule> cachedRules =
            Collections.emptyList();

    private WebSphereLogFilterRules() {
    }

    public static String apply(
            String source,
            String ruleText) {

        String value =
                source == null
                        ? ""
                        : source;

        List<Rule> rules =
                rules(
                        ruleText);

        if (rules.isEmpty()
                || value.isEmpty()) {

            return value;
        }

        StringBuilder out =
                new StringBuilder(
                        value.length());

        String[] lines =
                value.split(
                        "(?<=\\n)",
                        -1);

        boolean suppressContinuation =
                false;

        for (String line : lines) {
            if (suppressContinuation
                    && isContinuationLine(
                            line)) {

                continue;
            }

            suppressContinuation = false;

            if (matches(
                    line,
                    rules)) {

                suppressContinuation = true;
                continue;
            }

            out.append(
                    line);
        }

        return out.toString();
    }

    private static synchronized List<Rule> rules(
            String source) {

        String normalized =
                source == null
                        ? ""
                        : source;

        if (normalized.equals(
                cachedSource)) {

            return cachedRules;
        }

        List<Rule> result =
                new ArrayList<Rule>();

        String[] lines =
                normalized.split(
                        "\\r?\\n");

        for (String line : lines) {
            String rule =
                    line == null
                            ? ""
                            : line.trim();

            if (rule.isEmpty()
                    || rule.startsWith("#")) {

                continue;
            }

            if (rule.regionMatches(
                    true,
                    0,
                    "regex:",
                    0,
                    6)) {

                String expression =
                        rule.substring(6)
                                .trim();

                if (expression.isEmpty()) {
                    continue;
                }

                try {
                    result.add(
                            Rule.regex(
                                    Pattern.compile(
                                            expression,
                                            Pattern.CASE_INSENSITIVE)));

                } catch (PatternSyntaxException ignored) {
                    // Invalid rules are ignored instead of breaking the log view.
                }

                continue;
            }

            if (rule.regionMatches(
                    true,
                    0,
                    "contains:",
                    0,
                    9)) {

                rule =
                        rule.substring(9)
                                .trim();
            }

            if (!rule.isEmpty()) {
                result.add(
                        Rule.contains(
                                rule.toLowerCase(
                                        Locale.ENGLISH)));
            }
        }

        cachedSource = normalized;
        cachedRules =
                Collections.unmodifiableList(
                        result);

        return cachedRules;
    }

    private static boolean matches(
            String line,
            List<Rule> rules) {

        for (Rule rule : rules) {
            if (rule.matches(
                    line)) {

                return true;
            }
        }

        return false;
    }

    private static boolean isContinuationLine(
            String line) {

        if (line == null
                || line.isEmpty()) {

            return false;
        }

        if (Character.isWhitespace(
                line.charAt(0))) {

            return true;
        }

        String trimmed =
                line.trim();

        return trimmed.startsWith("at ")
                || trimmed.startsWith("Caused by:")
                || trimmed.startsWith("Suppressed:")
                || trimmed.startsWith("Wrapped by:")
                || trimmed.startsWith("... ");
    }

    private static final class Rule {

        final String contains;
        final Pattern pattern;

        private Rule(
                String contains,
                Pattern pattern) {

            this.contains = contains;
            this.pattern = pattern;
        }

        static Rule contains(
                String value) {

            return new Rule(
                    value,
                    null);
        }

        static Rule regex(
                Pattern pattern) {

            return new Rule(
                    null,
                    pattern);
        }

        boolean matches(
                String line) {

            String value =
                    line == null
                            ? ""
                            : line;

            if (pattern != null) {
                return pattern.matcher(
                        value)
                        .find();
            }

            return value.toLowerCase(
                    Locale.ENGLISH)
                    .contains(
                            contains);
        }
    }
}
