package de.andre.jsfnavigation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;

public final class JaxbJavaHyperlinkDetector
        implements IHyperlinkDetector {

    private static final Pattern JAXB_ANNOTATION =
            Pattern.compile(
                    "@(?:[A-Za-z0-9_$.]+\\.)?"
                    + "(XmlType|XmlRootElement|XmlElement|XmlElementDecl)"
                    + "\\s*\\(",
                    Pattern.MULTILINE);

    private static final Pattern NAMESPACE =
            Pattern.compile(
                    "\\bnamespace\\s*=\\s*(['\"])(.*?)\\1",
                    Pattern.DOTALL);

    private JaxbJavaHyperlinkDetector() {
    }

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IFile file =
                EditorContext.currentFile();

        if (file == null
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return null;
        }

        IDocument document =
                textViewer.getDocument();

        StringLiteral literal =
                stringLiteral(
                        document,
                        region.getOffset());

        if (literal == null) {
            return null;
        }

        AnnotationWindow annotation =
                annotationWindow(
                        document,
                        literal.start);

        if (annotation == null
                || !isNameMember(
                        annotation.text,
                        literal.start
                                - annotation.start)) {

            return null;
        }

        String namespace =
                namespace(
                        annotation.text);

        XsdIndexService service =
                Activator.getXsdIndexService();

        if (service == null) {
            return null;
        }

        List<XsdDefinition> definitions =
                service.resolve(
                        namespace,
                        literal.value);

        return definitions.isEmpty()
                ? null
                : new IHyperlink[] {
                        new XsdDefinitionHyperlink(
                                new Region(
                                        literal.start,
                                        Math.max(
                                                1,
                                                literal.value
                                                        .length())),
                                definitions)
                };
    }

    private static boolean isNameMember(
            String annotationText,
            int localStringStart) {

        int equals =
                annotationText
                        .lastIndexOf(
                                '=',
                                localStringStart);

        if (equals < 0) {
            return false;
        }

        int i =
                equals - 1;

        while (i >= 0
                && Character.isWhitespace(
                        annotationText
                                .charAt(i))) {

            i--;
        }

        int end =
                i + 1;

        while (i >= 0
                && Character.isJavaIdentifierPart(
                        annotationText
                                .charAt(i))) {

            i--;
        }

        String member =
                annotationText.substring(
                        i + 1,
                        end);

        return "name".equals(
                member);
    }

    private static String namespace(
            String annotationText) {

        Matcher matcher =
                NAMESPACE.matcher(
                        annotationText);

        if (!matcher.find()) {
            return "";
        }

        String value =
                matcher.group(2)
                        .trim();

        return "##default".equals(
                value)
                ? ""
                : value;
    }

    private static AnnotationWindow annotationWindow(
            IDocument document,
            int offset) {

        try {
            int from =
                    Math.max(
                            0,
                            offset - 1600);

            int to =
                    Math.min(
                            document.getLength(),
                            offset + 1200);

            String window =
                    document.get(
                            from,
                            to - from);

            Matcher matcher =
                    JAXB_ANNOTATION.matcher(
                            window);

            int bestStart = -1;

            while (matcher.find()) {
                int absolute =
                        from
                        + matcher.start();

                if (absolute <= offset
                        && absolute > bestStart) {

                    bestStart =
                            absolute;
                }
            }

            if (bestStart < 0) {
                return null;
            }

            int close =
                    findClosingParen(
                            document,
                            bestStart,
                            Math.min(
                                    document.getLength(),
                                    bestStart + 3000));

            if (close < offset) {
                return null;
            }

            return new AnnotationWindow(
                    bestStart,
                    document.get(
                            bestStart,
                            close - bestStart + 1));

        } catch (BadLocationException e) {
            return null;
        }
    }

    private static int findClosingParen(
            IDocument document,
            int start,
            int max)
            throws BadLocationException {

        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = start;
                i < max;
                i++) {

            char c =
                    document.getChar(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }

                if (c == '\\') {
                    escaped = true;
                    continue;
                }

                if (c == quote) {
                    inString = false;
                }

                continue;
            }

            if (c == '"'
                    || c == '\'') {

                inString = true;
                quote = c;
                continue;
            }

            if (c == '(') {
                depth++;

            } else if (c == ')') {
                depth--;

                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static StringLiteral stringLiteral(
            IDocument document,
            int offset) {

        if (document == null
                || document.getLength()
                        == 0) {

            return null;
        }

        try {
            int left =
                    Math.min(
                            offset,
                            document.getLength() - 1);

            while (left >= 0
                    && offset - left < 1024) {

                char c =
                        document.getChar(
                                left);

                if (c == '"') {
                    break;
                }

                if (c == '\n'
                        || c == '\r') {

                    return null;
                }

                left--;
            }

            if (left < 0
                    || document.getChar(
                            left)
                            != '"') {

                return null;
            }

            int right =
                    left + 1;

            boolean escaped = false;

            while (right
                    < document.getLength()
                    && right - left < 2048) {

                char c =
                        document.getChar(
                                right);

                if (escaped) {
                    escaped = false;

                } else if (c == '\\') {
                    escaped = true;

                } else if (c == '"') {
                    break;
                }

                right++;
            }

            if (right
                    >= document.getLength()
                    || document.getChar(
                            right)
                            != '"'
                    || offset < left
                    || offset > right) {

                return null;
            }

            String value =
                    document.get(
                            left + 1,
                            right - left - 1);

            return new StringLiteral(
                    left + 1,
                    value);

        } catch (BadLocationException e) {
            return null;
        }
    }

    private static final class AnnotationWindow {

        final int start;
        final String text;

        AnnotationWindow(
                int start,
                String text) {

            this.start = start;
            this.text = text;
        }
    }

    private static final class StringLiteral {

        final int start;
        final String value;

        StringLiteral(
                int start,
                String value) {

            this.start = start;
            this.value = value;
        }
    }
}
