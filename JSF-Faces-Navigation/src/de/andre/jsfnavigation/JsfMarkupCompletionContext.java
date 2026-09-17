package de.andre.jsfnavigation;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.IDocument;

public final class JsfMarkupCompletionContext {

    public static final int TAG = 1;
    public static final int ATTRIBUTE = 2;

    private static final Pattern ATTRIBUTE_NAME =
            Pattern.compile(
                    "\\b([A-Za-z_:][A-Za-z0-9_:\\-.]*)\\s*=");

    private final int kind;
    private final String prefix;
    private final String namespacePrefix;
    private final String tagName;
    private final int replaceOffset;
    private final int replaceLength;
    private final Set<String> existingAttributes;

    private JsfMarkupCompletionContext(
            int kind,
            String prefix,
            String namespacePrefix,
            String tagName,
            int replaceOffset,
            int replaceLength,
            Set<String> existingAttributes) {

        this.kind = kind;
        this.prefix = prefix;
        this.namespacePrefix = namespacePrefix;
        this.tagName = tagName;
        this.replaceOffset = replaceOffset;
        this.replaceLength = replaceLength;
        this.existingAttributes = existingAttributes;
    }

    public int getKind() {
        return kind;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public String getTagName() {
        return tagName;
    }

    public int getReplaceOffset() {
        return replaceOffset;
    }

    public int getReplaceLength() {
        return replaceLength;
    }

    public Set<String> getExistingAttributes() {
        return existingAttributes;
    }

    public static JsfMarkupCompletionContext detect(
            IDocument document,
            int offset) {

        if (document == null
                || offset < 0
                || offset > document.getLength()) {

            return null;
        }

        String text = document.get();

        int open =
                text.lastIndexOf(
                        '<',
                        Math.max(0, offset - 1));

        if (open < 0) {
            return null;
        }

        int closeBefore =
                text.lastIndexOf(
                        '>',
                        Math.max(0, offset - 1));

        if (closeBefore > open) {
            return null;
        }

        String fragment =
                text.substring(
                        open + 1,
                        offset);

        if (fragment.startsWith("/")
                || fragment.startsWith("!")
                || fragment.startsWith("?")) {

            return null;
        }

        int whitespace =
                firstWhitespace(fragment);

        if (whitespace < 0) {
            return tagContext(
                    fragment,
                    open + 1);
        }

        String qName =
                fragment.substring(
                        0,
                        whitespace);

        int colon =
                qName.indexOf(':');

        if (colon <= 0
                || colon == qName.length() - 1) {

            return null;
        }

        String namespacePrefix =
                qName.substring(0, colon);

        String tagName =
                qName.substring(colon + 1);

        int tokenStart =
                attributeTokenStart(
                        fragment,
                        fragment.length());

        String token =
                fragment.substring(tokenStart);

        /*
         * Do not propose attributes while the caret is inside a quoted value.
         */
        if (insideQuotedValue(fragment)) {
            return null;
        }

        String attributePrefix =
                normalizeAttributePrefix(token);

        int replaceOffset =
                open + 1 + tokenStart
                + token.length()
                - attributePrefix.length();

        Set<String> existing =
                existingAttributes(fragment);

        return new JsfMarkupCompletionContext(
                ATTRIBUTE,
                attributePrefix,
                namespacePrefix,
                tagName,
                replaceOffset,
                attributePrefix.length(),
                existing);
    }

    private static JsfMarkupCompletionContext tagContext(
            String fragment,
            int absoluteStart) {

        String token =
                fragment.trim();

        if (token.isEmpty()) {
            return null;
        }

        int colon =
                token.indexOf(':');

        if (colon <= 0) {
            return null;
        }

        String namespacePrefix =
                token.substring(0, colon);

        String tagPrefix =
                token.substring(colon + 1);

        return new JsfMarkupCompletionContext(
                TAG,
                tagPrefix,
                namespacePrefix,
                null,
                absoluteStart + colon + 1,
                tagPrefix.length(),
                new HashSet<String>());
    }

    private static int firstWhitespace(
            String value) {

        for (int i = 0;
                i < value.length();
                i++) {

            if (Character.isWhitespace(
                    value.charAt(i))) {

                return i;
            }
        }

        return -1;
    }

    private static int attributeTokenStart(
            String fragment,
            int end) {

        for (int i = end - 1;
                i >= 0;
                i--) {

            char c = fragment.charAt(i);

            if (Character.isWhitespace(c)
                    || c == '<'
                    || c == '>') {

                return i + 1;
            }
        }

        return 0;
    }

    private static boolean insideQuotedValue(
            String fragment) {

        boolean single = false;
        boolean dbl = false;

        for (int i = 0;
                i < fragment.length();
                i++) {

            char c = fragment.charAt(i);

            if (c == '\''
                    && !dbl) {

                single = !single;

            } else if (c == '"'
                    && !single) {

                dbl = !dbl;
            }
        }

        return single || dbl;
    }

    private static String normalizeAttributePrefix(
            String token) {

        String value = token;

        int equals =
                value.lastIndexOf('=');

        if (equals >= 0) {
            return "";
        }

        int quote =
                Math.max(
                        value.lastIndexOf('"'),
                        value.lastIndexOf('\''));

        if (quote >= 0) {
            value =
                    value.substring(
                            quote + 1);
        }

        int start = 0;

        while (start < value.length()
                && !isAttributeNameChar(
                        value.charAt(start))) {

            start++;
        }

        return value.substring(start);
    }

    private static boolean isAttributeNameChar(
            char c) {

        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-'
                || c == ':'
                || c == '.';
    }

    private static Set<String> existingAttributes(
            String fragment) {

        Set<String> result =
                new HashSet<String>();

        Matcher matcher =
                ATTRIBUTE_NAME.matcher(
                        fragment);

        while (matcher.find()) {
            result.add(
                    matcher.group(1));
        }

        return result;
    }
}
