package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;

public final class JsfViewParser {

    private static final Pattern TAG = Pattern.compile(
            "<\\s*([A-Za-z_][A-Za-z0-9_.-]*):([A-Za-z_][A-Za-z0-9_.-]*)\\b([^>]*)>",
            Pattern.DOTALL);

    private static final Pattern ATTR = Pattern.compile(
            "([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.DOTALL);

    private static final Pattern PF_CALL = Pattern.compile(
            "\\bPF\\s*\\(\\s*(['\"])([A-Za-z_$][A-Za-z0-9_$]*)\\1\\s*\\)");

    private static final Pattern BUNDLE_KEY = Pattern.compile(
            "[#\\$]\\{\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\[\\s*(['\"])([^'\"]+)\\2\\s*\\]\\s*\\}");

    private static final Set<String> COMPONENT_REFERENCE_ATTRIBUTES =
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

    private JsfViewParser() {
    }

    public static IndexedViewFile parse(IFile file) {
        if (file == null || !file.exists()) {
            return null;
        }

        String source = read(file);

        if (source == null) {
            return null;
        }

        String path =
                file.getFullPath().toPortableString();

        long stamp =
                file.getModificationStamp();

        List<ViewSymbol> symbols =
                new ArrayList<ViewSymbol>();

        Matcher tagMatcher = TAG.matcher(source);

        while (tagMatcher.find()) {
            String prefix = tagMatcher.group(1);
            String localName = tagMatcher.group(2);
            String attributes = tagMatcher.group(3);
            int attributesOffset = tagMatcher.start(3);

            Matcher attrMatcher = ATTR.matcher(attributes);

            while (attrMatcher.find()) {
                String attrName = attrMatcher.group(1);
                String value = attrMatcher.group(3) != null
                        ? attrMatcher.group(3)
                        : attrMatcher.group(4);

                int quoteOffset = attrMatcher.group(3) != null
                        ? attrMatcher.start(3)
                        : attrMatcher.start(4);

                int valueOffset =
                        attributesOffset + quoteOffset;

                if ("id".equals(attrName) && isStatic(value)) {
                    symbols.add(new ViewSymbol(
                            ViewSymbol.COMPONENT_ID,
                            value,
                            path,
                            valueOffset,
                            attrName,
                            prefix + ":" + localName,
                            stamp));
                }

                if ("widgetVar".equals(attrName) && isStatic(value)) {
                    symbols.add(new ViewSymbol(
                            ViewSymbol.WIDGET_VAR,
                            value,
                            path,
                            valueOffset,
                            attrName,
                            prefix + ":" + localName,
                            stamp));
                }

                if (COMPONENT_REFERENCE_ATTRIBUTES.contains(attrName)) {
                    addComponentReferences(
                            symbols,
                            value,
                            path,
                            valueOffset,
                            attrName,
                            stamp);
                }

                if ("ui".equals(prefix)
                        && "include".equals(localName)
                        && "src".equals(attrName)
                        && isStatic(value)) {

                    symbols.add(new ViewSymbol(
                            ViewSymbol.INCLUDE,
                            value,
                            path,
                            valueOffset,
                            attrName,
                            null,
                            stamp));
                }

                if ("ui".equals(prefix)
                        && ("composition".equals(localName)
                                || "decorate".equals(localName))
                        && "template".equals(attrName)
                        && isStatic(value)) {

                    symbols.add(new ViewSymbol(
                            ViewSymbol.TEMPLATE,
                            value,
                            path,
                            valueOffset,
                            attrName,
                            null,
                            stamp));
                }

                if ("f".equals(prefix)
                        && "loadBundle".equals(localName)) {

                    // Bundle var/basename are associated later by scanning
                    // the complete tag attributes.
                }
            }

            addBundleDeclaration(
                    symbols,
                    prefix,
                    localName,
                    attributes,
                    attributesOffset,
                    path,
                    stamp);

            if (!STANDARD_PREFIXES.contains(prefix)) {
                symbols.add(new ViewSymbol(
                        ViewSymbol.COMPOSITE_TAG,
                        prefix + ":" + localName,
                        path,
                        tagMatcher.start(1),
                        null,
                        prefix + "/" + localName,
                        stamp));
            }
        }

        Matcher pf = PF_CALL.matcher(source);

        while (pf.find()) {
            symbols.add(new ViewSymbol(
                    ViewSymbol.WIDGET_REFERENCE,
                    pf.group(2),
                    path,
                    pf.start(2),
                    "PF",
                    null,
                    stamp));
        }

        Matcher bundleKey = BUNDLE_KEY.matcher(source);

        while (bundleKey.find()) {
            symbols.add(new ViewSymbol(
                    ViewSymbol.BUNDLE_KEY,
                    bundleKey.group(3),
                    path,
                    bundleKey.start(3),
                    bundleKey.group(1),
                    null,
                    stamp));
        }

        return new IndexedViewFile(
                path,
                stamp,
                symbols);
    }

    private static void addBundleDeclaration(
            List<ViewSymbol> symbols,
            String prefix,
            String localName,
            String attributes,
            int attributesOffset,
            String path,
            long stamp) {

        if (!"f".equals(prefix)
                || !"loadBundle".equals(localName)) {

            return;
        }

        String basename = null;
        String var = null;
        int varOffset = -1;

        Matcher matcher = ATTR.matcher(attributes);

        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(3) != null
                    ? matcher.group(3)
                    : matcher.group(4);

            int valueStart = matcher.group(3) != null
                    ? matcher.start(3)
                    : matcher.start(4);

            if ("basename".equals(name)) {
                basename = value;
            } else if ("var".equals(name)) {
                var = value;
                varOffset = attributesOffset + valueStart;
            }
        }

        if (basename != null
                && var != null
                && isStatic(basename)
                && isStatic(var)) {

            symbols.add(new ViewSymbol(
                    ViewSymbol.BUNDLE_VAR,
                    var,
                    path,
                    varOffset,
                    "f:loadBundle",
                    basename,
                    stamp));
        }
    }

    private static void addComponentReferences(
            List<ViewSymbol> symbols,
            String value,
            String path,
            int valueOffset,
            String attrName,
            long stamp) {

        if (value == null || value.indexOf("#{") >= 0
                || value.indexOf("${") >= 0) {

            return;
        }

        int i = 0;

        while (i < value.length()) {
            while (i < value.length()
                    && isReferenceSeparator(
                            value.charAt(i))) {
                i++;
            }

            int start = i;

            while (i < value.length()
                    && !isReferenceSeparator(
                            value.charAt(i))) {
                i++;
            }

            if (start >= i) {
                continue;
            }

            String token =
                    value.substring(start, i);

            if (token.startsWith("@")
                    || token.indexOf('(') >= 0
                    || token.indexOf(')') >= 0) {
                continue;
            }

            String normalized =
                    normalizeClientId(token);

            if (normalized.isEmpty()) {
                continue;
            }

            int nameOffset =
                    valueOffset + start
                    + token.lastIndexOf(normalized);

            symbols.add(new ViewSymbol(
                    ViewSymbol.COMPONENT_REFERENCE,
                    normalized,
                    path,
                    nameOffset,
                    attrName,
                    token,
                    stamp));
        }
    }

    private static boolean isReferenceSeparator(
            char c) {

        /*
         * PrimeFaces/RichFaces search expressions are commonly written as
         * either whitespace-separated or comma-separated lists, e.g.
         * process=":form,@form,panel".
         */
        return Character.isWhitespace(c)
                || c == ',';
    }

    public static String normalizeClientId(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim();

        while (result.startsWith(":")) {
            result = result.substring(1);
        }

        int colon = result.lastIndexOf(':');

        if (colon >= 0 && colon + 1 < result.length()) {
            result = result.substring(colon + 1);
        }

        return result;
    }

    private static boolean isStatic(String value) {
        return value != null
                && value.indexOf("#{") < 0
                && value.indexOf("${") < 0;
    }

    private static String read(IFile file) {
        InputStream in = null;

        try {
            in = file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(
                            file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }
}
