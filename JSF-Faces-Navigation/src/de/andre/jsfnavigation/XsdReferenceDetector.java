package de.andre.jsfnavigation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;

public final class XsdReferenceDetector {

    private static final Pattern ATTRIBUTE =
            Pattern.compile(
                    "([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(['\"])(.*?)\\2",
                    Pattern.DOTALL);

    private XsdReferenceDetector() {
    }

    public static XsdAttributeReference find(
            IDocument document,
            int offset) {

        if (document == null
                || document.getLength() == 0
                || offset < 0
                || offset > document.getLength()) {

            return null;
        }

        try {
            int left =
                    findLeft(
                            document,
                            offset,
                            '<',
                            4096);

            int right =
                    findRight(
                            document,
                            offset,
                            '>',
                            4096);

            if (left < 0
                    || right <= left) {

                return null;
            }

            String tag =
                    document.get(
                            left,
                            right - left + 1);

            String tagName =
                    tagName(
                            tag);

            Matcher matcher =
                    ATTRIBUTE.matcher(
                            tag);

            while (matcher.find()) {
                int valueStart =
                        left
                        + matcher.start(3);

                int valueEnd =
                        left
                        + matcher.end(3);

                if (offset >= valueStart
                        && offset <= valueEnd) {

                    return new XsdAttributeReference(
                            tagName,
                            matcher.group(1),
                            matcher.group(3),
                            new Region(
                                    valueStart,
                                    Math.max(
                                            1,
                                            matcher.group(3)
                                                    .length())));
                }
            }

        } catch (BadLocationException e) {
            return null;
        }

        return null;
    }

    public static String namespaceForPrefix(
            IDocument document,
            String prefix) {

        if (document == null
                || prefix == null
                || prefix.isEmpty()) {

            return "";
        }

        try {
            int length =
                    Math.min(
                            document.getLength(),
                            32768);

            String head =
                    document.get(
                            0,
                            length);

            Pattern pattern =
                    Pattern.compile(
                            "\\bxmlns:"
                            + Pattern.quote(
                                    prefix)
                            + "\\s*=\\s*(['\"])(.*?)\\1",
                            Pattern.CASE_INSENSITIVE
                                    | Pattern.DOTALL);

            Matcher matcher =
                    pattern.matcher(
                            head);

            return matcher.find()
                    ? matcher.group(2)
                            .trim()
                    : "";

        } catch (BadLocationException e) {
            return "";
        }
    }

    private static String tagName(
            String tag) {

        Matcher matcher =
                Pattern.compile(
                        "<\\s*([A-Za-z_:][A-Za-z0-9_:.-]*)")
                        .matcher(
                                tag);

        return matcher.find()
                ? matcher.group(1)
                : "";
    }

    private static int findLeft(
            IDocument document,
            int offset,
            char wanted,
            int maxDistance)
            throws BadLocationException {

        int start =
                Math.min(
                        offset,
                        document.getLength() - 1);

        int min =
                Math.max(
                        0,
                        start - maxDistance);

        for (int i = start;
                i >= min;
                i--) {

            char c =
                    document.getChar(i);

            if (c == wanted) {
                return i;
            }

            if (c == '>') {
                return -1;
            }
        }

        return -1;
    }

    private static int findRight(
            IDocument document,
            int offset,
            char wanted,
            int maxDistance)
            throws BadLocationException {

        int end =
                Math.min(
                        document.getLength(),
                        offset + maxDistance);

        for (int i =
                Math.max(
                        0,
                        offset);
                i < end;
                i++) {

            char c =
                    document.getChar(i);

            if (c == wanted) {
                return i;
            }

            if (c == '<'
                    && i != offset) {

                return -1;
            }
        }

        return -1;
    }
}
