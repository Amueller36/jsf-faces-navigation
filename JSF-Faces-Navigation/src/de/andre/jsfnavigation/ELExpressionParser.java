package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.IDocument;

public final class ELExpressionParser {

    private static final String IDENT = "[A-Za-z_$][A-Za-z0-9_$]*";

    /*
     * Matches chains inside a larger EL expression without requiring the whole
     * expression to be a simple property chain. This means operators such as
     * !, and/or, method parentheses and comparisons no longer disable Ctrl+Click.
     * A simple bracket/index section between properties is tolerated too.
     */
    private static final Pattern CHAIN = Pattern.compile(
            IDENT
            + "(?:\\s*\\[[^\\]]*\\])?"
            + "(?:\\s*\\.\\s*" + IDENT
            + "(?:\\s*\\[[^\\]]*\\])?)*");

    private static final Pattern CHAIN_PART = Pattern.compile(
            "(?:^|\\.)\\s*(" + IDENT + ")");

    private ELExpressionParser() {
    }

    public static ELExpression find(IDocument document, int cursorOffset) {
        try {
            String text = document.get();
            int start = enclosingExpressionStart(text, cursorOffset);

            if (start < 0) {
                return null;
            }

            int end = text.indexOf('}', start + 2);

            if (end < 0 || cursorOffset > end) {
                return null;
            }

            int bodyStart = start + 2;
            String body = text.substring(bodyStart, end);
            int relativeCursor = cursorOffset - bodyStart;

            Matcher chainMatcher = CHAIN.matcher(body);

            while (chainMatcher.find()) {
                if (relativeCursor < chainMatcher.start()
                        || relativeCursor >= chainMatcher.end()) {
                    continue;
                }

                String chain = chainMatcher.group();
                int chainAbsoluteStart = bodyStart + chainMatcher.start();
                Matcher partMatcher = CHAIN_PART.matcher(chain);

                List<String> parts = new ArrayList<String>();
                List<Integer> offsets = new ArrayList<Integer>();

                while (partMatcher.find()) {
                    String part = partMatcher.group(1);
                    int offset = chainAbsoluteStart + partMatcher.start(1);
                    parts.add(part);
                    offsets.add(Integer.valueOf(offset));
                }

                if (parts.isEmpty() || isELKeyword(parts.get(0))) {
                    return null;
                }

                return new ELExpression(parts, offsets);
            }

            return null;

        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isELKeyword(String value) {
        return "and".equals(value) || "or".equals(value)
                || "not".equals(value) || "empty".equals(value)
                || "true".equals(value) || "false".equals(value)
                || "null".equals(value) || "eq".equals(value)
                || "ne".equals(value) || "lt".equals(value)
                || "gt".equals(value) || "le".equals(value)
                || "ge".equals(value) || "div".equals(value)
                || "mod".equals(value);
    }

    private static int enclosingExpressionStart(String text, int cursorOffset) {
        int hashStart = text.lastIndexOf("#{", cursorOffset);
        int dollarStart = text.lastIndexOf("${", cursorOffset);
        int start = Math.max(hashStart, dollarStart);

        if (start < 0) {
            return -1;
        }

        int previousClose = text.lastIndexOf('}', cursorOffset);
        return previousClose > start ? -1 : start;
    }
}
