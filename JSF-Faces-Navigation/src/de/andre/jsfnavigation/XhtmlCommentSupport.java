package de.andre.jsfnavigation;

public final class XhtmlCommentSupport {

    private XhtmlCommentSupport() {
    }

    public static String toggle(
            String original,
            String delimiter) {

        if (original == null) {
            return null;
        }

        int first =
                firstNonWhitespace(
                        original);

        int last =
                lastNonWhitespace(
                        original);

        if (first < 0
                || last < first) {

            return original;
        }

        String core =
                original.substring(
                        first,
                        last + 1);

        String prefix =
                original.substring(
                        0,
                        first);

        String suffix =
                original.substring(
                        last + 1);

        if (core.startsWith("<!--")
                && core.endsWith("-->")) {

            String inner =
                    core.substring(
                            4,
                            core.length() - 3);

            boolean multilineWrapper =
                    inner.startsWith(
                            delimiter)
                    || inner.startsWith("\r\n")
                    || inner.startsWith("\n")
                    || inner.startsWith("\r");

            inner =
                    trimWrapperDelimiters(
                            inner,
                            delimiter,
                            prefix);

            return (multilineWrapper
                    ? ""
                    : prefix)
                    + inner
                    + suffix;
        }

        /*
         * XML comments cannot be nested. Refuse instead of silently creating
         * malformed XHTML.
         */
        if (core.contains("<!--")
                || core.contains("-->")) {

            return null;
        }

        if (core.indexOf('\n') < 0
                && core.indexOf('\r') < 0) {

            return prefix
                    + "<!-- "
                    + core
                    + " -->"
                    + suffix;
        }

        String indent =
                leadingWhitespaceOfFirstLine(
                        original);

        return indent
                + "<!--"
                + delimiter
                + original
                + delimiter
                + indent
                + "-->";
    }

    private static String trimWrapperDelimiters(
            String value,
            String delimiter,
            String outerIndent) {

        String result = value;

        if (result.startsWith(
                delimiter)) {

            result =
                    result.substring(
                            delimiter.length());

        } else if (result.startsWith("\r\n")) {
            result =
                    result.substring(2);

        } else if (result.startsWith("\n")
                || result.startsWith("\r")) {

            result =
                    result.substring(1);

        } else if (result.startsWith(" ")) {
            /*
             * Single-line comments are created as: <!-- content -->
             */
            result =
                    result.substring(1);
        }

        String closingPadding =
                delimiter
                + (outerIndent == null
                        ? ""
                        : outerIndent);

        if (result.endsWith(
                closingPadding)) {

            result =
                    result.substring(
                            0,
                            result.length()
                                    - closingPadding.length());

        } else if (result.endsWith(
                delimiter)) {

            result =
                    result.substring(
                            0,
                            result.length()
                                    - delimiter.length());

        } else if (result.endsWith("\r\n")) {
            result =
                    result.substring(
                            0,
                            result.length() - 2);

        } else if (result.endsWith("\n")
                || result.endsWith("\r")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1);

        } else if (result.endsWith(" ")) {
            result =
                    result.substring(
                            0,
                            result.length() - 1);
        }

        return result;
    }

    private static String leadingWhitespaceOfFirstLine(
            String text) {

        int end = 0;

        while (end < text.length()) {
            char c =
                    text.charAt(end);

            if (c != ' '
                    && c != '\t') {

                break;
            }

            end++;
        }

        return text.substring(
                0,
                end);
    }

    private static int firstNonWhitespace(
            String value) {

        for (int i = 0;
                i < value.length();
                i++) {

            if (!Character.isWhitespace(
                    value.charAt(i))) {

                return i;
            }
        }

        return -1;
    }

    private static int lastNonWhitespace(
            String value) {

        for (int i =
                value.length() - 1;
                i >= 0;
                i--) {

            if (!Character.isWhitespace(
                    value.charAt(i))) {

                return i;
            }
        }

        return -1;
    }
}
