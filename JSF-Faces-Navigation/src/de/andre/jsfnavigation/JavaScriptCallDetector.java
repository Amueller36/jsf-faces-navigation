package de.andre.jsfnavigation;

import org.eclipse.jface.text.IDocument;

public final class JavaScriptCallDetector {

    private JavaScriptCallDetector() {
    }

    public static JavaScriptCall find(IDocument document, int cursorOffset) {
        try {
            String text = document.get();
            return find(text, cursorOffset);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static JavaScriptCall find(String text, int cursorOffset) {
        if (text == null || text.isEmpty()
                || cursorOffset < 0 || cursorOffset > text.length()) {
            return null;
        }

        int probe = cursorOffset;

        if (probe == text.length() && probe > 0) {
            probe--;
        }

        if (probe < text.length() && !isIdentifierPart(text.charAt(probe))) {
            if (probe > 0 && isIdentifierPart(text.charAt(probe - 1))) {
                probe--;
            } else {
                return null;
            }
        }

        int start = probe;
        int end = probe + 1;

        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }

        if (start >= end || !isIdentifierStart(text.charAt(start))) {
            return null;
        }

        int after = end;
        while (after < text.length() && Character.isWhitespace(text.charAt(after))) {
            after++;
        }

        if (after >= text.length() || text.charAt(after) != '(') {
            return null;
        }

        String name = text.substring(start, end);

        if (isJavaScriptKeyword(name)) {
            return null;
        }

        return new JavaScriptCall(name, start);
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isJavaScriptKeyword(String value) {
        return "if".equals(value)
                || "for".equals(value)
                || "while".equals(value)
                || "switch".equals(value)
                || "catch".equals(value)
                || "function".equals(value)
                || "return".equals(value)
                || "typeof".equals(value)
                || "new".equals(value);
    }
}
