package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.IDocument;

public final class JpaQuerySupport {

    private JpaQuerySupport() {
    }

    public static JpaQueryReference detect(
            IDocument document,
            int offset) {

        if (document == null) {
            return null;
        }

        String source = document.get();

        if (offset < 0 || offset >= source.length()) {
            return null;
        }

        if (!isInsideJavaStringLiteral(source, offset)) {
            return null;
        }

        int left = offset;
        int right = offset;

        if (!isPathCharacter(source.charAt(offset))
                && offset > 0
                && isPathCharacter(source.charAt(offset - 1))) {
            left = offset - 1;
            right = offset - 1;
        }

        if (!isPathCharacter(source.charAt(left))) {
            return null;
        }

        while (left > 0 && isPathCharacter(source.charAt(left - 1))) {
            left--;
        }

        while (right + 1 < source.length()
                && isPathCharacter(source.charAt(right + 1))) {
            right++;
        }

        String chain = source.substring(left, right + 1);

        if (chain.indexOf('.') < 0) {
            return null;
        }

        List<String> segments = new ArrayList<String>();
        List<Integer> offsets = new ArrayList<Integer>();

        int partStart = 0;
        int selectedIndex = -1;

        for (int i = 0; i <= chain.length(); i++) {
            if (i == chain.length() || chain.charAt(i) == '.') {
                String part = chain.substring(partStart, i);

                if (!isIdentifier(part)) {
                    return null;
                }

                int absoluteStart = left + partStart;
                int absoluteEnd = absoluteStart + part.length();

                if (offset >= absoluteStart && offset <= absoluteEnd) {
                    selectedIndex = segments.size();
                }

                segments.add(part);
                offsets.add(Integer.valueOf(absoluteStart));
                partStart = i + 1;
            }
        }

        if (segments.size() < 2 || selectedIndex < 0) {
            return null;
        }

        int statementStart = findStatementStart(source, left);
        int statementEnd = findStatementEnd(source, right);

        return new JpaQueryReference(
                segments,
                offsets,
                selectedIndex,
                statementStart,
                statementEnd);
    }

    private static boolean isPathCharacter(char c) {
        return Character.isJavaIdentifierPart(c)
                || c == '.'
                || c == '$';
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }

        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isInsideJavaStringLiteral(
            String source,
            int offset) {

        int lineStart = source.lastIndexOf('\n', offset);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;

        boolean inString = false;
        boolean escaped = false;

        for (int i = lineStart; i <= offset; i++) {
            char c = source.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
            }
        }

        return inString;
    }

    private static int findStatementStart(String source, int offset) {
        int minimum = Math.max(0, offset - 6000);

        for (int i = offset; i >= minimum; i--) {
            char c = source.charAt(i);

            if (c == ';' || c == '{' || c == '}') {
                return i + 1;
            }
        }

        return minimum;
    }

    private static int findStatementEnd(String source, int offset) {
        int maximum = Math.min(source.length(), offset + 6000);

        for (int i = offset; i < maximum; i++) {
            if (source.charAt(i) == ';') {
                return i + 1;
            }
        }

        return maximum;
    }
}
