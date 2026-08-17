package de.andre.jsfnavigation;

import org.eclipse.jface.text.IDocument;

public final class JavaStringReferenceDetector {

    private JavaStringReferenceDetector() {
    }

    public static JavaStringReference find(
            IDocument document,
            int cursorOffset) {

        String source = document.get();
        StringLiteral literal =
                literalAt(source, cursorOffset);

        if (literal == null) {
            return null;
        }

        int prefixStart =
                Math.max(0, literal.quoteStart - 350);

        String prefix =
                source.substring(
                        prefixStart,
                        literal.quoteStart);

        if (hasRecent(prefix,
                ".ajax().update")
                || hasRecent(prefix, ".update")
                || hasRecent(prefix, "addPartialUpdateTarget")
                || hasRecent(prefix, "addComponentToAjaxRender")) {

            String component =
                    JsfViewParser.normalizeClientId(
                            literal.value);

            if (!component.isEmpty()) {
                int valueIndex =
                        literal.value.lastIndexOf(
                                component);

                return new JavaStringReference(
                        JavaStringReference.COMPONENT_ID,
                        component,
                        literal.valueStart + valueIndex,
                        component.length());
            }
        }

        if (hasRecent(prefix, "createNamedQuery")
                || hasRecent(prefix, "getNamedQuery")
                || hasRecent(prefix, "NamedQuery")) {

            return new JavaStringReference(
                    JavaStringReference.NAMED_QUERY,
                    literal.value,
                    literal.valueStart,
                    literal.value.length());
        }

        if (hasRecent(prefix, "isUserInRole")
                || hasRecent(prefix, "hasRole")
                || hasRecent(prefix, "RolesAllowed")
                || hasRecent(prefix, "DeclareRoles")) {

            return new JavaStringReference(
                    JavaStringReference.ROLE,
                    literal.value,
                    literal.valueStart,
                    literal.value.length());
        }

        String trimmedPrefix =
                prefix.trim();

        if (trimmedPrefix.endsWith("return")
                || trimmedPrefix.endsWith("return(")) {

            if (looksLikeOutcome(literal.value)) {
                return new JavaStringReference(
                        JavaStringReference.OUTCOME,
                        literal.value,
                        literal.valueStart,
                        literal.value.length());
            }
        }

        return null;
    }

    private static boolean looksLikeOutcome(
            String value) {

        return value != null
                && !value.isEmpty()
                && value.indexOf(' ') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('=') < 0
                && value.length() < 160;
    }

    private static boolean hasRecent(
            String prefix,
            String token) {

        int index = prefix.lastIndexOf(token);

        if (index < 0) {
            return false;
        }

        int open =
                prefix.indexOf('(', index);

        return open >= 0;
    }

    private static StringLiteral literalAt(
            String source,
            int offset) {

        int start = -1;
        char quote = 0;

        for (int i = offset; i >= 0; i--) {
            char c = source.charAt(i);

            if ((c == '"' || c == '\'')
                    && !escaped(source, i)) {

                start = i;
                quote = c;
                break;
            }

            if (c == '\n' || c == '\r') {
                break;
            }
        }

        if (start < 0) {
            return null;
        }

        int end = -1;

        for (int i = start + 1;
                i < source.length();
                i++) {

            char c = source.charAt(i);

            if (c == quote
                    && !escaped(source, i)) {

                end = i;
                break;
            }
        }

        if (end < offset || end < 0) {
            return null;
        }

        return new StringLiteral(
                start,
                start + 1,
                source.substring(
                        start + 1,
                        end));
    }

    private static boolean escaped(
            String source,
            int index) {

        int slashes = 0;

        for (int i = index - 1;
                i >= 0
                && source.charAt(i) == '\\';
                i--) {

            slashes++;
        }

        return (slashes % 2) == 1;
    }

    private static final class StringLiteral {
        final int quoteStart;
        final int valueStart;
        final String value;

        StringLiteral(
                int quoteStart,
                int valueStart,
                String value) {

            this.quoteStart = quoteStart;
            this.valueStart = valueStart;
            this.value = value;
        }
    }
}
