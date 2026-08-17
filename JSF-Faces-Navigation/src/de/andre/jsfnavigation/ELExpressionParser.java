package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.IDocument;

public final class ELExpressionParser {

    private ELExpressionParser() {
    }

    public static ELExpression find(
            IDocument document,
            int cursorOffset) {

        try {
            String text = document.get();

            int hashStart =
                    text.lastIndexOf("#{", cursorOffset);

            int dollarStart =
                    text.lastIndexOf("${", cursorOffset);

            int start =
                    Math.max(hashStart, dollarStart);

            if (start < 0) {
                return null;
            }

            int end = text.indexOf('}', start + 2);

            if (end < 0 || cursorOffset > end) {
                return null;
            }

            String rawBody =
                    text.substring(start + 2, end);

            int leadingWhitespace = 0;

            while (leadingWhitespace < rawBody.length()
                    && Character.isWhitespace(
                            rawBody.charAt(
                                    leadingWhitespace))) {

                leadingWhitespace++;
            }

            String body =
                    rawBody.substring(
                            leadingWhitespace)
                            .trim();

            if (body.isEmpty()) {
                return null;
            }

            List<String> parts =
                    splitSimplePropertyChain(body);

            if (parts.isEmpty()) {
                return null;
            }

            /*
             * expressionStart remains the offset of '#{'/'${', adjusted
             * backwards so that "+ 2" in the selection code lands on the
             * first identifier even when the expression begins with spaces.
             */
            return new ELExpression(
                    body,
                    parts,
                    start + leadingWhitespace);

        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> splitSimplePropertyChain(
            String body) {

        List<String> result =
                new ArrayList<String>();

        int start = 0;

        for (int i = 0; i <= body.length(); i++) {
            if (i == body.length()
                    || body.charAt(i) == '.') {

                String part =
                        body.substring(start, i)
                                .trim();

                if (!isIdentifier(part)) {
                    return new ArrayList<String>();
                }

                result.add(part);
                start = i + 1;
            }
        }

        return result;
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(
                value.charAt(0))) {

            return false;
        }

        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(
                    value.charAt(i))) {

                return false;
            }
        }

        return true;
    }
}
