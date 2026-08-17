package de.andre.jsfnavigation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.IDocument;

public final class JsfCursorReferenceDetector {

    private static final Pattern PF = Pattern.compile(
            "\\bPF\\s*\\(\\s*(['\"])([A-Za-z_$][A-Za-z0-9_$]*)\\1\\s*\\)");

    private static final Pattern BUNDLE = Pattern.compile(
            "[#\\$]\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\[\\s*(['\"])([^'\"]+)\\2\\s*\\]\\s*\\}");

    private static final Pattern ROLE = Pattern.compile(
            "\\bisUserInRole\\s*\\(\\s*(['\"])([^'\"]+)\\1\\s*\\)");

    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.DOTALL);

    private static final Pattern TAG_NAME = Pattern.compile(
            "<\\s*([A-Za-z_][A-Za-z0-9_.-]*):([A-Za-z_][A-Za-z0-9_.-]*)");

    private static final Set<String> COMPONENT_ATTRIBUTES =
            new HashSet<String>(Arrays.asList(
                    "update",
                    "process",
                    "render",
                    "reRender",
                    "rerender",
                    "execute",
                    "for"));

    private static final Set<String> STANDARD_PREFIXES =
            new HashSet<String>(Arrays.asList(
                    "h", "f", "p", "ui", "a4j", "rich", "c", "fn",
                    "cc", "composite", "fmt"));

    private JsfCursorReferenceDetector() {
    }

    public static JsfCursorReference find(
            IDocument document,
            int cursorOffset) {

        String text = document.get();

        JsfCursorReference result =
                findPattern(
                        PF,
                        text,
                        cursorOffset,
                        JsfCursorReference.WIDGET,
                        2,
                        null);

        if (result != null) {
            return result;
        }

        Matcher bundle = BUNDLE.matcher(text);

        while (bundle.find()) {
            if (inside(
                    cursorOffset,
                    bundle.start(3),
                    bundle.end(3))) {

                return new JsfCursorReference(
                        JsfCursorReference.BUNDLE_KEY,
                        bundle.group(3),
                        bundle.start(3),
                        bundle.group(3).length(),
                        bundle.group(1));
            }
        }

        Matcher role = ROLE.matcher(text);

        while (role.find()) {
            if (inside(
                    cursorOffset,
                    role.start(2),
                    role.end(2))) {

                return new JsfCursorReference(
                        JsfCursorReference.ROLE,
                        role.group(2),
                        role.start(2),
                        role.group(2).length(),
                        null);
            }
        }

        int tagStart =
                text.lastIndexOf('<', cursorOffset);

        int tagEnd =
                text.indexOf('>', cursorOffset);

        if (tagStart >= 0
                && tagEnd >= cursorOffset) {

            String tagText =
                    text.substring(
                            tagStart,
                            tagEnd + 1);

            Matcher tagName =
                    TAG_NAME.matcher(tagText);

            if (tagName.find()) {
                int prefixStart =
                        tagStart + tagName.start(1);

                int localEnd =
                        tagStart + tagName.end(2);

                if (cursorOffset >= prefixStart
                        && cursorOffset <= localEnd) {

                    String prefix =
                            tagName.group(1);

                    String local =
                            tagName.group(2);

                    if (!STANDARD_PREFIXES.contains(prefix)) {
                        String name =
                                prefix + ":" + local;

                        return new JsfCursorReference(
                                JsfCursorReference.COMPOSITE,
                                name,
                                prefixStart,
                                name.length(),
                                prefix + "/" + local);
                    }
                }
            }

            String currentTagPrefix =
                    tagName.find(0)
                            ? tagName.group(1)
                            : null;

            String currentLocalName =
                    tagName.find(0)
                            ? tagName.group(2)
                            : null;

            Matcher attr =
                    ATTRIBUTE.matcher(tagText);

            while (attr.find()) {
                String attrName =
                        attr.group(1);

                String value =
                        attr.group(3) != null
                                ? attr.group(3)
                                : attr.group(4);

                int localValueStart =
                        attr.group(3) != null
                                ? attr.start(3)
                                : attr.start(4);

                int valueStart =
                        tagStart + localValueStart;

                int valueEnd =
                        valueStart + value.length();

                int attrNameStart =
                        tagStart + attr.start(1);

                int attrNameEnd =
                        tagStart + attr.end(1);

                if (currentTagPrefix != null
                        && currentLocalName != null
                        && !STANDARD_PREFIXES.contains(currentTagPrefix)
                        && inside(
                                cursorOffset,
                                attrNameStart,
                                attrNameEnd)) {

                    return new JsfCursorReference(
                            JsfCursorReference.COMPOSITE_ATTRIBUTE,
                            attrName,
                            attrNameStart,
                            attrName.length(),
                            currentTagPrefix + "/" + currentLocalName);
                }

                if (!inside(
                        cursorOffset,
                        valueStart,
                        valueEnd)) {

                    continue;
                }

                if (COMPONENT_ATTRIBUTES.contains(
                        attrName)) {

                    return componentToken(
                            value,
                            valueStart,
                            cursorOffset,
                            attrName);
                }

                String tagPrefix = currentTagPrefix;
                String localName = currentLocalName;

                if ("ui".equals(tagPrefix)
                        && "include".equals(localName)
                        && "src".equals(attrName)
                        && value.indexOf("#{") < 0
                        && value.indexOf("${") < 0) {

                    return new JsfCursorReference(
                            JsfCursorReference.FILE,
                            value,
                            valueStart,
                            value.length(),
                            "include");
                }

                if ("ui".equals(tagPrefix)
                        && ("composition".equals(localName)
                                || "decorate".equals(localName))
                        && "template".equals(attrName)
                        && value.indexOf("#{") < 0
                        && value.indexOf("${") < 0) {

                    return new JsfCursorReference(
                            JsfCursorReference.FILE,
                            value,
                            valueStart,
                            value.length(),
                            "template");
                }

                if (("outcome".equals(attrName)
                        || "action".equals(attrName))
                        && value.indexOf("#{") < 0
                        && value.indexOf("${") < 0
                        && !value.trim().isEmpty()) {

                    String outcomeFile =
                            value.endsWith(".xhtml")
                                    ? value
                                    : value + ".xhtml";

                    return new JsfCursorReference(
                            JsfCursorReference.FILE,
                            outcomeFile,
                            valueStart,
                            value.length(),
                            "outcome");
                }
            }
        }

        return null;
    }

    private static JsfCursorReference componentToken(
            String value,
            int valueStart,
            int cursorOffset,
            String attributeName) {

        int relative =
                cursorOffset - valueStart;

        int start = relative;

        while (start > 0
                && !Character.isWhitespace(
                        value.charAt(start - 1))) {
            start--;
        }

        int end = Math.min(
                relative,
                value.length());

        while (end < value.length()
                && !Character.isWhitespace(
                        value.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        String token =
                value.substring(start, end);

        if (token.startsWith("@")
                || token.indexOf("#{") >= 0
                || token.indexOf("${") >= 0) {

            return null;
        }

        String normalized =
                JsfViewParser.normalizeClientId(token);

        if (normalized.isEmpty()) {
            return null;
        }

        int tokenNameIndex =
                token.lastIndexOf(normalized);

        return new JsfCursorReference(
                JsfCursorReference.COMPONENT,
                normalized,
                valueStart + start + tokenNameIndex,
                normalized.length(),
                attributeName);
    }

    private static JsfCursorReference findPattern(
            Pattern pattern,
            String text,
            int cursorOffset,
            int kind,
            int group,
            String extra) {

        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            if (inside(
                    cursorOffset,
                    matcher.start(group),
                    matcher.end(group))) {

                return new JsfCursorReference(
                        kind,
                        matcher.group(group),
                        matcher.start(group),
                        matcher.group(group).length(),
                        extra);
            }
        }

        return null;
    }

    private static boolean inside(
            int offset,
            int start,
            int endExclusive) {

        return offset >= start
                && offset < endExclusive;
    }
}
