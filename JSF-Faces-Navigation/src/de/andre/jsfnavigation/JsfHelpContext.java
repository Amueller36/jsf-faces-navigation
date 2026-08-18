package de.andre.jsfnavigation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.IDocument;

public final class JsfHelpContext {

    private static final Pattern OPENING_TAG =
            Pattern.compile(
                    "^\\s*([A-Za-z_][A-Za-z0-9_.-]*):([A-Za-z_][A-Za-z0-9_.-]*)");

    private static final Pattern ATTRIBUTE =
            Pattern.compile(
                    "\\b([A-Za-z_:][A-Za-z0-9_:\\-.]*)\\s*=\\s*(['\"])(.*?)\\2",
                    Pattern.DOTALL);

    private final String namespacePrefix;
    private final String tagName;
    private final String attributeName;

    private JsfHelpContext(
            String namespacePrefix,
            String tagName,
            String attributeName) {

        this.namespacePrefix = namespacePrefix;
        this.tagName = tagName;
        this.attributeName = attributeName;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public String getTagName() {
        return tagName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public boolean isAttribute() {
        return attributeName != null;
    }

    public static JsfHelpContext detect(
            IDocument document,
            int offset) {

        if (document == null
                || offset < 0
                || offset > document.getLength()) {

            return null;
        }

        String text =
                document.get();

        int open =
                text.lastIndexOf(
                        '<',
                        Math.max(
                                0,
                                offset - 1));

        if (open < 0) {
            return null;
        }

        int closeBefore =
                text.lastIndexOf(
                        '>',
                        Math.max(
                                0,
                                offset - 1));

        if (closeBefore > open) {
            return null;
        }

        int close =
                text.indexOf(
                        '>',
                        offset);

        if (close < 0) {
            close =
                    Math.min(
                            text.length(),
                            offset + 4000);
        }

        String fragment =
                text.substring(
                        open + 1,
                        close);

        if (fragment.startsWith("/")
                || fragment.startsWith("!")
                || fragment.startsWith("?")) {

            return null;
        }

        Matcher tag =
                OPENING_TAG.matcher(
                        fragment);

        if (!tag.find()) {
            return null;
        }

        String namespacePrefix =
                tag.group(1);

        String tagName =
                tag.group(2);

        int relativeOffset =
                offset - (open + 1);

        String attributeName = null;

        Matcher attributes =
                ATTRIBUTE.matcher(
                        fragment);

        while (attributes.find()) {
            int attributeStart =
                    attributes.start(1);

            int attributeEnd =
                    attributes.end();

            if (relativeOffset >= attributeStart
                    && relativeOffset <= attributeEnd) {

                attributeName =
                        attributes.group(1);

                break;
            }
        }

        return new JsfHelpContext(
                namespacePrefix,
                tagName,
                attributeName);
    }
}
