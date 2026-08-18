package de.andre.jsfnavigation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class JsfTaglibCatalogService {

    private static final Pattern XMLNS =
            Pattern.compile(
                    "\\bxmlns:([A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*(['\"])(.*?)\\2",
                    Pattern.DOTALL);

    private static final Map<String, CachedCatalog> CACHE =
            new HashMap<String, CachedCatalog>();

    private JsfTaglibCatalogService() {
    }

    public static List<JsfComponentProposal> tagProposals(
            IFile file,
            IDocument document,
            JsfMarkupCompletionContext context) {

        Catalog catalog =
                catalog(file);

        String namespace =
                namespaceUri(
                        document,
                        context.getNamespacePrefix());

        Map<String, TagDef> tags =
                catalog.tags(
                        namespace,
                        context.getNamespacePrefix());

        List<JsfComponentProposal> result =
                new ArrayList<JsfComponentProposal>();

        String prefix =
                context.getPrefix()
                        .toLowerCase();

        for (TagDef tag : tags.values()) {
            if (!prefix.isEmpty()
                    && !tag.name.toLowerCase()
                            .startsWith(prefix)) {

                continue;
            }

            result.add(
                    new JsfComponentProposal(
                            tag.name,
                            tag.name,
                            tag.description,
                            false));
        }

        sort(result);
        return result;
    }

    public static List<JsfComponentProposal> attributeProposals(
            IFile file,
            IDocument document,
            JsfMarkupCompletionContext context) {

        Catalog catalog =
                catalog(file);

        String namespace =
                namespaceUri(
                        document,
                        context.getNamespacePrefix());

        Map<String, TagDef> tags =
                catalog.tags(
                        namespace,
                        context.getNamespacePrefix());

        TagDef tag =
                tags.get(
                        context.getTagName());

        if (tag == null) {
            return Collections.emptyList();
        }

        Set<String> existing =
                context.getExistingAttributes();

        String prefix =
                context.getPrefix()
                        .toLowerCase();

        List<JsfComponentProposal> result =
                new ArrayList<JsfComponentProposal>();

        for (AttributeDef attribute :
                tag.attributes.values()) {

            if (existing.contains(
                    attribute.name)) {

                continue;
            }

            if (!prefix.isEmpty()
                    && !attribute.name
                            .toLowerCase()
                            .startsWith(prefix)) {

                continue;
            }

            String detail =
                    attribute.detail();

            result.add(
                    new JsfComponentProposal(
                            attribute.name,
                            attribute.name + "=\"\"",
                            detail,
                            true));
        }

        sort(result);
        return result;
    }


    public static JsfComponentHelp help(
            IFile file,
            IDocument document,
            JsfHelpContext context) {

        if (context == null) {
            return null;
        }

        Catalog catalog =
                catalog(file);

        String namespace =
                namespaceUri(
                        document,
                        context.getNamespacePrefix());

        Map<String, TagDef> tags =
                catalog.tags(
                        namespace,
                        context.getNamespacePrefix());

        TagDef tag =
                tags.get(
                        context.getTagName());

        String qualifiedTag =
                "<"
                + context.getNamespacePrefix()
                + ":"
                + context.getTagName()
                + ">";

        String library =
                libraryName(
                        context.getNamespacePrefix());

        if (context.isAttribute()) {
            String attributeName =
                    context.getAttributeName();

            AttributeDef attribute =
                    tag == null
                            ? null
                            : tag.attributes.get(
                                    attributeName);

            String type =
                    attribute == null
                            ? null
                            : attribute.type;

            String curated =
                    JsfAttributeHelpKnowledge
                            .explanation(
                                    attributeName);

            String metadataDescription =
                    attribute == null
                            ? null
                            : attribute.description;

            String description =
                    curated != null
                            ? curated
                            : metadataDescription;

            if (description == null
                    || description.trim()
                            .isEmpty()) {

                description =
                        "Configures the '"
                        + attributeName
                        + "' attribute of "
                        + qualifiedTag
                        + ". The exact behavior is defined by the component library version used by this project.";
            }

            String exampleValue =
                    JsfAttributeHelpKnowledge
                            .exampleValue(
                                    attributeName,
                                    type);

            StringBuilder out =
                    new StringBuilder();

            out.append(library)
                    .append(' ')
                    .append(qualifiedTag)
                    .append('\n')
                    .append("Attribute: ")
                    .append(attributeName)
                    .append("\n\n");

            out.append("What it does\n")
                    .append(description)
                    .append("\n\n");

            if (metadataDescription != null
                    && curated != null
                    && !sameMeaning(
                            curated,
                            metadataDescription)) {

                out.append("Library metadata\n")
                        .append(
                                clean(
                                        metadataDescription))
                        .append("\n\n");
            }

            out.append("Type: ")
                    .append(
                            type == null
                                    || type.trim()
                                            .isEmpty()
                                            ? "not specified by taglib metadata"
                                            : clean(type))
                    .append('\n');

            out.append("Required: ")
                    .append(
                            attribute != null
                                    && attribute.required
                                            ? "yes"
                                            : "no / not marked required")
                    .append('\n');

            out.append("Metadata: ")
                    .append(
                            attribute != null
                                    && attribute.metadataBacked
                                            ? "project taglib metadata"
                                            : "built-in fallback / generic help")
                    .append("\n\n");

            out.append("Example\n")
                    .append('<')
                    .append(
                            context.getNamespacePrefix())
                    .append(':')
                    .append(
                            context.getTagName())
                    .append(' ')
                    .append(attributeName)
                    .append("=\"")
                    .append(exampleValue)
                    .append("\" />")
                    .append("\n\n");

            String related =
                    relatedAttributes(
                            tag,
                            attributeName);

            if (!related.isEmpty()) {
                out.append("Related attributes\n")
                        .append(related)
                        .append('\n');
            }

            return new JsfComponentHelp(
                    qualifiedTag
                    + " — "
                    + attributeName,
                    out.toString());
        }

        String description =
                tag == null
                        ? null
                        : tag.description;

        if (description == null
                || description.trim()
                        .isEmpty()) {

            description =
                    "JSF component/tag provided by "
                    + library
                    + ". Use attribute help on a specific attribute for its type, purpose and an example.";
        }

        StringBuilder out =
                new StringBuilder();

        out.append(library)
                .append(' ')
                .append(qualifiedTag)
                .append("\n\n")
                .append("What it does\n")
                .append(clean(description))
                .append("\n\n")
                .append("Metadata: ")
                .append(
                        tag != null
                                && tag.metadataBacked
                                        ? "project taglib metadata"
                                        : "built-in fallback / generic help")
                .append("\n\n")
                .append("Example\n")
                .append(componentExample(
                        context,
                        tag))
                .append("\n\n");

        if (tag != null
                && !tag.attributes.isEmpty()) {

            out.append("Available attributes\n")
                    .append(
                            attributeSummary(
                                    tag))
                    .append('\n');
        }

        return new JsfComponentHelp(
                qualifiedTag,
                out.toString());
    }

    private static String componentExample(
            JsfHelpContext context,
            TagDef tag) {

        StringBuilder out =
                new StringBuilder();

        out.append('<')
                .append(
                        context.getNamespacePrefix())
                .append(':')
                .append(
                        context.getTagName());

        if (tag != null) {
            AttributeDef value =
                    tag.attributes.get(
                            "value");

            if (value != null) {
                out.append(
                        " value=\"#{bean.value}\"");
            } else if (tag.attributes
                    .containsKey("id")) {

                out.append(
                        " id=\"componentId\"");
            }
        }

        out.append(" />");

        return out.toString();
    }

    private static String relatedAttributes(
            TagDef tag,
            String current) {

        if (tag == null
                || tag.attributes.isEmpty()) {

            return "";
        }

        StringBuilder out =
                new StringBuilder();

        int count = 0;

        for (String name :
                tag.attributes.keySet()) {

            if (name.equals(current)) {
                continue;
            }

            if (count > 0) {
                out.append(", ");
            }

            out.append(name);
            count++;

            if (count >= 10) {
                break;
            }
        }

        return out.toString();
    }

    private static String attributeSummary(
            TagDef tag) {

        StringBuilder out =
                new StringBuilder();

        int count = 0;

        for (AttributeDef attribute :
                tag.attributes.values()) {

            if (count > 0) {
                out.append(", ");
            }

            out.append(attribute.name);

            if (attribute.required) {
                out.append('*');
            }

            count++;

            if (count >= 30
                    && tag.attributes.size()
                            > count) {

                out.append(", …");
                break;
            }
        }

        return out.toString();
    }

    private static String libraryName(
            String prefix) {

        if ("p".equals(prefix)) {
            return "PrimeFaces";
        }

        if ("rich".equals(prefix)) {
            return "RichFaces";
        }

        if ("a4j".equals(prefix)) {
            return "RichFaces / A4J";
        }

        if ("h".equals(prefix)) {
            return "JSF HTML";
        }

        if ("f".equals(prefix)) {
            return "JSF Core";
        }

        if ("ui".equals(prefix)) {
            return "Facelets";
        }

        return "JSF tag library";
    }

    private static String clean(
            String text) {

        if (text == null) {
            return "";
        }

        String value =
                text.replace('\r', ' ')
                        .replace('\n', ' ')
                        .replace('\t', ' ')
                        .trim();

        while (value.contains("  ")) {
            value =
                    value.replace(
                            "  ",
                            " ");
        }

        return value;
    }

    private static boolean sameMeaning(
            String left,
            String right) {

        return clean(left)
                .equalsIgnoreCase(
                        clean(right));
    }

    private static void sort(
            List<JsfComponentProposal> result) {

        Collections.sort(
                result,
                new Comparator<JsfComponentProposal>() {
                    @Override
                    public int compare(
                            JsfComponentProposal left,
                            JsfComponentProposal right) {

                        return left.getName()
                                .compareToIgnoreCase(
                                        right.getName());
                    }
                });
    }

    private static synchronized Catalog catalog(
            IFile file) {

        if (file == null
                || file.getProject() == null) {

            Catalog fallback =
                    new Catalog();

            addFallbacks(fallback);
            return fallback;
        }

        IProject project =
                file.getProject();

        String signature =
                signature(project);

        CachedCatalog cached =
                CACHE.get(
                        project.getName());

        if (cached != null
                && cached.signature
                        .equals(signature)) {

            return cached.catalog;
        }

        Catalog catalog =
                new Catalog();

        scanResolvedClasspath(
                project,
                catalog);

        scanProjectTaglibs(
                project,
                catalog);

        /*
         * Built-in fallback definitions fill gaps only. Exact taglib metadata
         * from the project's own PrimeFaces/RichFaces/JSF version wins.
         */
        addFallbacks(catalog);

        CACHE.put(
                project.getName(),
                new CachedCatalog(
                        signature,
                        catalog));

        return catalog;
    }

    private static String signature(
            IProject project) {

        StringBuilder out =
                new StringBuilder(
                        project.getName());

        IJavaProject javaProject =
                JavaCore.create(project);

        if (javaProject == null
                || !javaProject.exists()) {

            return out.toString();
        }

        try {
            IClasspathEntry[] entries =
                    javaProject.getResolvedClasspath(
                            true);

            for (IClasspathEntry entry : entries) {
                if (entry.getEntryKind()
                        != IClasspathEntry.CPE_LIBRARY) {

                    continue;
                }

                File library =
                        resolveLibrary(
                                entry.getPath());

                if (library != null) {
                    out.append('|')
                            .append(
                                    library.getAbsolutePath())
                            .append(':')
                            .append(
                                    library.lastModified())
                            .append(':')
                            .append(
                                    library.length());
                }
            }

        } catch (JavaModelException e) {
            // A rebuilding JDT model should not break content assist.
        }

        return out.toString();
    }

    private static void scanResolvedClasspath(
            IProject project,
            Catalog catalog) {

        IJavaProject javaProject =
                JavaCore.create(project);

        if (javaProject == null
                || !javaProject.exists()) {

            return;
        }

        try {
            IClasspathEntry[] entries =
                    javaProject.getResolvedClasspath(
                            true);

            for (IClasspathEntry entry : entries) {
                if (entry.getEntryKind()
                        != IClasspathEntry.CPE_LIBRARY) {

                    continue;
                }

                File library =
                        resolveLibrary(
                                entry.getPath());

                if (library != null
                        && library.isFile()
                        && library.getName()
                                .toLowerCase()
                                .endsWith(".jar")
                        && likelyTaglibJar(
                                library.getName())) {

                    scanJar(
                            library,
                            catalog);
                }
            }

        } catch (JavaModelException e) {
            // Ignore while Eclipse is rebuilding the classpath.
        }
    }


    private static boolean likelyTaglibJar(
            String fileName) {

        String lower =
                fileName == null
                        ? ""
                        : fileName.toLowerCase();

        return lower.contains("primefaces")
                || lower.contains("richfaces")
                || lower.contains("faces")
                || lower.contains("jsf")
                || lower.contains("facelets")
                || lower.contains("jstl")
                || lower.contains("taglib");
    }

    private static File resolveLibrary(
            IPath path) {

        if (path == null) {
            return null;
        }

        File direct =
                path.toFile();

        if (direct.isFile()) {
            return direct;
        }

        IFile workspaceFile =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getFile(path);

        if (workspaceFile.exists()
                && workspaceFile.getLocation()
                        != null) {

            File resolved =
                    workspaceFile.getLocation()
                            .toFile();

            if (resolved.isFile()) {
                return resolved;
            }
        }

        return null;
    }

    private static void scanJar(
            File jar,
            Catalog catalog) {

        ZipFile zip = null;

        try {
            zip = new ZipFile(jar);

            Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry =
                        entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String lower =
                        entry.getName()
                                .toLowerCase();

                if (!lower.endsWith(
                        ".taglib.xml")
                        && !lower.endsWith(
                                ".tld")) {

                    continue;
                }

                InputStream input =
                        zip.getInputStream(
                                entry);

                try {
                    parseTaglib(
                            input,
                            catalog);

                } finally {
                    input.close();
                }
            }

        } catch (Exception e) {
            // One malformed/unreadable dependency must not break completion.

        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void scanProjectTaglibs(
            IProject project,
            final Catalog catalog) {

        try {
            project.accept(
                    new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(
                                IResourceProxy proxy)
                                throws CoreException {

                            if (proxy.getType()
                                    == IResource.FOLDER) {

                                String name =
                                        proxy.getName();

                                if (".git".equals(name)
                                        || "target".equals(name)
                                        || "build".equals(name)
                                        || "bin".equals(name)) {

                                    return false;
                                }

                                return true;
                            }

                            if (proxy.getType()
                                    != IResource.FILE) {

                                return false;
                            }

                            String lower =
                                    proxy.getName()
                                            .toLowerCase();

                            if (!lower.endsWith(
                                    ".taglib.xml")
                                    && !lower.endsWith(
                                            ".tld")) {

                                return false;
                            }

                            IFile file =
                                    (IFile)
                                            proxy.requestResource();

                            InputStream input =
                                    null;

                            try {
                                input =
                                        new BufferedInputStream(
                                                file.getContents());

                                parseTaglib(
                                        input,
                                        catalog);

                            } catch (Exception e) {
                                // Ignore malformed local metadata.

                            } finally {
                                if (input != null) {
                                    try {
                                        input.close();
                                    } catch (Exception ignored) {
                                    }
                                }
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            // Best-effort metadata discovery.
        }
    }

    private static void parseTaglib(
            InputStream input,
            Catalog catalog)
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory
                        .newInstance();

        factory.setNamespaceAware(true);

        safeFeature(
                factory,
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);

        safeFeature(
                factory,
                "http://xml.org/sax/features/external-general-entities",
                false);

        safeFeature(
                factory,
                "http://xml.org/sax/features/external-parameter-entities",
                false);

        try {
            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    "");

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    "");

        } catch (IllegalArgumentException ignored) {
            // Older Java 8 XML providers may not expose these attributes.
        }

        DocumentBuilder builder =
                factory.newDocumentBuilder();

        Element root =
                builder.parse(input)
                        .getDocumentElement();

        if (root == null) {
            return;
        }

        String namespace =
                firstDescendantText(
                        root,
                        "namespace");

        if (namespace == null
                || namespace.trim().isEmpty()) {

            namespace =
                    firstDescendantText(
                            root,
                            "uri");
        }

        if (namespace == null
                || namespace.trim().isEmpty()) {

            return;
        }

        namespace =
                namespace.trim();

        NodeList children =
                root.getChildNodes();

        for (int i = 0;
                i < children.getLength();
                i++) {

            Node node =
                    children.item(i);

            if (!(node instanceof Element)
                    || !"tag".equals(
                            localName(node))) {

                continue;
            }

            Element tagElement =
                    (Element) node;

            String tagName =
                    directChildText(
                            tagElement,
                            "tag-name");

            if (tagName == null) {
                tagName =
                        directChildText(
                                tagElement,
                                "name");
            }

            if (tagName == null
                    || tagName.trim()
                            .isEmpty()) {

                continue;
            }

            TagDef tag =
                    catalog.tag(
                            namespace,
                            tagName.trim());

            tag.metadataBacked = true;

            String description =
                    directChildText(
                            tagElement,
                            "description");

            if (description != null
                    && !description.trim()
                            .isEmpty()) {

                tag.setDescriptionIfMissing(
                        description.trim());
            }

            NodeList tagChildren =
                    tagElement.getChildNodes();

            for (int j = 0;
                    j < tagChildren.getLength();
                    j++) {

                Node attributeNode =
                        tagChildren.item(j);

                if (!(attributeNode
                        instanceof Element)
                        || !"attribute".equals(
                                localName(
                                        attributeNode))) {

                    continue;
                }

                Element attributeElement =
                        (Element) attributeNode;

                String attributeName =
                        directChildText(
                                attributeElement,
                                "name");

                if (attributeName == null
                        || attributeName.trim()
                                .isEmpty()) {

                    continue;
                }

                String required =
                        directChildText(
                                attributeElement,
                                "required");

                String type =
                        directChildText(
                                attributeElement,
                                "type");

                if (type == null) {
                    type =
                            directChildText(
                                    attributeElement,
                                    "method-signature");
                }

                String attributeDescription =
                        directChildText(
                                attributeElement,
                                "description");

                AttributeDef attribute =
                        tag.attribute(
                                attributeName.trim());

                attribute.metadataBacked = true;

                attribute.merge(
                        type,
                        "true".equalsIgnoreCase(
                                required),
                        attributeDescription);
            }
        }
    }

    private static void safeFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value) {

        try {
            factory.setFeature(
                    feature,
                    value);
        } catch (Exception ignored) {
        }
    }

    private static String firstDescendantText(
            Element element,
            String wanted) {

        NodeList all =
                element.getElementsByTagNameNS(
                        "*",
                        wanted);

        if (all.getLength() > 0) {
            return all.item(0)
                    .getTextContent();
        }

        NodeList fallback =
                element.getElementsByTagName(
                        wanted);

        return fallback.getLength() > 0
                ? fallback.item(0)
                        .getTextContent()
                : null;
    }

    private static String directChildText(
            Element element,
            String wanted) {

        NodeList children =
                element.getChildNodes();

        for (int i = 0;
                i < children.getLength();
                i++) {

            Node node =
                    children.item(i);

            if (node instanceof Element
                    && wanted.equals(
                            localName(node))) {

                return node.getTextContent();
            }
        }

        return null;
    }

    private static String localName(
            Node node) {

        String local =
                node.getLocalName();

        if (local != null) {
            return local;
        }

        String name =
                node.getNodeName();

        int colon =
                name.indexOf(':');

        return colon >= 0
                ? name.substring(
                        colon + 1)
                : name;
    }

    private static String namespaceUri(
            IDocument document,
            String prefix) {

        if (document != null) {
            Matcher matcher =
                    XMLNS.matcher(
                            document.get());

            while (matcher.find()) {
                if (prefix.equals(
                        matcher.group(1))) {

                    return matcher.group(3)
                            .trim();
                }
            }
        }

        return fallbackNamespace(
                prefix);
    }

    private static String fallbackNamespace(
            String prefix) {

        if ("p".equals(prefix)) {
            return "http://primefaces.org/ui";
        }

        if ("rich".equals(prefix)) {
            return "http://richfaces.org/rich";
        }

        if ("a4j".equals(prefix)) {
            return "http://richfaces.org/a4j";
        }

        if ("h".equals(prefix)) {
            return "http://java.sun.com/jsf/html";
        }

        if ("f".equals(prefix)) {
            return "http://java.sun.com/jsf/core";
        }

        if ("ui".equals(prefix)) {
            return "http://java.sun.com/jsf/facelets";
        }

        if ("c".equals(prefix)) {
            return "http://java.sun.com/jsp/jstl/core";
        }

        return null;
    }

    private static void addFallbacks(
            Catalog catalog) {

        addPrimeFaces(catalog);
        addJsfHtml(catalog);
        addJsfCore(catalog);
        addFacelets(catalog);
        addRichFaces(catalog);
        addA4j(catalog);
    }

    private static void addPrimeFaces(
            Catalog catalog) {

        String ns =
                "http://primefaces.org/ui";

        String[] tags = {
                "ajax",
                "autoComplete",
                "blockUI",
                "calendar",
                "column",
                "commandButton",
                "commandLink",
                "confirm",
                "confirmDialog",
                "dataTable",
                "dialog",
                "fileUpload",
                "graphicImage",
                "growl",
                "inputMask",
                "inputText",
                "inputTextarea",
                "menuButton",
                "menuitem",
                "message",
                "messages",
                "outputLabel",
                "outputPanel",
                "panel",
                "panelGrid",
                "poll",
                "remoteCommand",
                "selectBooleanCheckbox",
                "selectCheckboxMenu",
                "selectManyCheckbox",
                "selectOneMenu",
                "separator",
                "slider",
                "spacer",
                "spinner",
                "tab",
                "tabView",
                "tooltip",
                "tree",
                "treeNode"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "autoComplete",
                "completeMethod", "var", "itemLabel", "itemValue",
                "converter", "dropdown", "forceSelection", "scrollHeight",
                "minQueryLength", "queryDelay", "maxResults", "multiple",
                "cache", "cacheTimeout", "emptyMessage");

        attrs(catalog, ns, "commandButton",
                "action", "actionListener", "process", "update", "ajax",
                "immediate", "disabled", "onclick", "oncomplete",
                "onstart", "onsuccess", "onerror", "icon");

        attrs(catalog, ns, "commandLink",
                "action", "actionListener", "process", "update", "ajax",
                "immediate", "disabled", "onclick", "oncomplete");

        attrs(catalog, ns, "dataTable",
                "var", "rows", "paginator", "rowKey", "selection",
                "selectionMode", "filteredValue", "sortBy", "filterBy",
                "lazy", "emptyMessage", "widgetVar", "rowIndexVar");

        attrs(catalog, ns, "column",
                "headerText", "footerText", "sortBy", "filterBy",
                "filterMatchMode", "width", "exportable", "priority");

        attrs(catalog, ns, "dialog",
                "header", "modal", "visible", "closable", "draggable",
                "resizable", "appendTo", "dynamic", "cache", "width",
                "height", "onShow", "onHide");

        attrs(catalog, ns, "selectOneMenu",
                "converter", "filter", "filterMatchMode", "editable",
                "effect", "panelStyle", "appendTo", "disabled");

        attrs(catalog, ns, "ajax",
                "event", "listener", "process", "update", "immediate",
                "async", "global", "onstart", "oncomplete", "onsuccess",
                "onerror", "delay");

        attrs(catalog, ns, "message",
                "for", "display", "showDetail", "showSummary");

        attrs(catalog, ns, "messages",
                "for", "globalOnly", "showDetail", "showSummary",
                "closable", "autoUpdate");

        attrs(catalog, ns, "panelGrid",
                "columns", "layout", "columnClasses", "rowClasses");

        attrs(catalog, ns, "fileUpload",
                "mode", "fileUploadListener", "allowTypes", "sizeLimit",
                "fileLimit", "auto", "multiple", "update", "process");

        attrs(catalog, ns, "remoteCommand",
                "name", "action", "actionListener", "process", "update",
                "oncomplete", "async", "global");
    }

    private static void addJsfHtml(
            Catalog catalog) {

        String ns =
                "http://java.sun.com/jsf/html";

        String[] tags = {
                "body",
                "column",
                "commandButton",
                "commandLink",
                "dataTable",
                "form",
                "graphicImage",
                "head",
                "inputHidden",
                "inputSecret",
                "inputText",
                "inputTextarea",
                "message",
                "messages",
                "outputFormat",
                "outputLabel",
                "outputLink",
                "outputText",
                "panelGrid",
                "panelGroup",
                "selectBooleanCheckbox",
                "selectManyCheckbox",
                "selectManyListbox",
                "selectManyMenu",
                "selectOneListbox",
                "selectOneMenu",
                "selectOneRadio"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "form",
                "prependId", "acceptcharset", "enctype", "target");

        attrs(catalog, ns, "commandButton",
                "action", "actionListener", "immediate", "type", "disabled");

        attrs(catalog, ns, "commandLink",
                "action", "actionListener", "immediate", "disabled");

        attrs(catalog, ns, "dataTable",
                "var", "rows", "first", "rowClasses", "columnClasses",
                "headerClass", "footerClass");

        attrs(catalog, ns, "message",
                "for", "showDetail", "showSummary", "errorClass",
                "warnClass", "infoClass", "fatalClass");

        attrs(catalog, ns, "outputText",
                "escape", "converter");
    }

    private static void addJsfCore(
            Catalog catalog) {

        String ns =
                "http://java.sun.com/jsf/core";

        String[] tags = {
                "ajax",
                "attribute",
                "convertDateTime",
                "convertNumber",
                "converter",
                "facet",
                "loadBundle",
                "metadata",
                "param",
                "passThroughAttribute",
                "selectItem",
                "selectItems",
                "setPropertyActionListener",
                "validateBean",
                "validateDoubleRange",
                "validateLength",
                "validateLongRange",
                "validator",
                "view",
                "viewAction",
                "viewParam"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "ajax",
                "event", "execute", "render", "listener",
                "immediate", "onevent", "onerror", "disabled");

        attrs(catalog, ns, "selectItems",
                "var", "itemValue", "itemLabel", "itemDescription",
                "itemDisabled", "itemLabelEscaped");

        attrs(catalog, ns, "converter",
                "converterId", "binding");

        attrs(catalog, ns, "validator",
                "validatorId", "binding", "disabled");
    }

    private static void addFacelets(
            Catalog catalog) {

        String ns =
                "http://java.sun.com/jsf/facelets";

        String[] tags = {
                "component",
                "composition",
                "debug",
                "decorate",
                "define",
                "fragment",
                "include",
                "insert",
                "param",
                "remove",
                "repeat"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "composition",
                "template");

        attrs(catalog, ns, "decorate",
                "template");

        attrs(catalog, ns, "include",
                "src");

        attrs(catalog, ns, "param",
                "name", "value");

        attrs(catalog, ns, "repeat",
                "var", "varStatus", "begin", "end", "step", "offset", "size");
    }

    private static void addRichFaces(
            Catalog catalog) {

        String ns =
                "http://richfaces.org/rich";

        String[] tags = {
                "calendar",
                "column",
                "dataTable",
                "dropDownMenu",
                "extendedDataTable",
                "inplaceInput",
                "menuGroup",
                "menuItem",
                "message",
                "messages",
                "modalPanel",
                "panel",
                "panelBar",
                "panelBarItem",
                "popupPanel",
                "simpleTogglePanel",
                "tab",
                "tabPanel",
                "toolTip"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "dataTable",
                "var", "rows", "rowKeyVar", "ajaxKeys", "reRender");

        attrs(catalog, ns, "column",
                "sortBy", "filterBy", "filterExpression", "label");

        attrs(catalog, ns, "popupPanel",
                "header", "modal", "show", "width", "height",
                "autosized", "domElementAttachment");

        attrs(catalog, ns, "modalPanel",
                "showWhenRendered", "width", "height", "resizeable",
                "moveable");
    }

    private static void addA4j(
            Catalog catalog) {

        String ns =
                "http://richfaces.org/a4j";

        String[] tags = {
                "ajax",
                "commandButton",
                "commandLink",
                "form",
                "jsFunction",
                "keepAlive",
                "log",
                "outputPanel",
                "poll",
                "push",
                "region",
                "repeat",
                "status",
                "support"
        };

        for (String tag : tags) {
            commonAttributes(
                    catalog.tag(ns, tag));
        }

        attrs(catalog, ns, "ajax",
                "event", "execute", "render", "listener",
                "onbegin", "oncomplete", "onbeforedomupdate");

        attrs(catalog, ns, "support",
                "event", "action", "actionListener", "reRender",
                "ajaxSingle", "limitToList", "oncomplete");

        attrs(catalog, ns, "commandButton",
                "action", "actionListener", "execute", "render",
                "reRender", "ajaxSingle", "oncomplete");

        attrs(catalog, ns, "commandLink",
                "action", "actionListener", "execute", "render",
                "reRender", "ajaxSingle", "oncomplete");
    }

    private static void commonAttributes(
            TagDef tag) {

        attrs(tag,
                "id",
                "binding",
                "rendered",
                "value",
                "style",
                "styleClass",
                "title");
    }

    private static void attrs(
            Catalog catalog,
            String namespace,
            String tag,
            String... attributes) {

        attrs(
                catalog.tag(
                        namespace,
                        tag),
                attributes);
    }

    private static void attrs(
            TagDef tag,
            String... attributes) {

        for (String attribute :
                attributes) {

            tag.attribute(attribute);
        }
    }

    private static final class CachedCatalog {
        final String signature;
        final Catalog catalog;

        CachedCatalog(
                String signature,
                Catalog catalog) {

            this.signature = signature;
            this.catalog = catalog;
        }
    }

    private static final class Catalog {

        private final Map<String, Map<String, TagDef>> byNamespace =
                new LinkedHashMap<String, Map<String, TagDef>>();

        TagDef tag(
                String namespace,
                String tagName) {

            Map<String, TagDef> tags =
                    byNamespace.get(namespace);

            if (tags == null) {
                tags =
                        new LinkedHashMap<String, TagDef>();

                byNamespace.put(
                        namespace,
                        tags);
            }

            TagDef tag =
                    tags.get(tagName);

            if (tag == null) {
                tag =
                        new TagDef(tagName);

                tags.put(
                        tagName,
                        tag);
            }

            return tag;
        }

        Map<String, TagDef> tags(
                String namespace,
                String prefix) {

            Map<String, TagDef> tags =
                    namespace == null
                            ? null
                            : byNamespace.get(
                                    namespace);

            if (tags != null
                    && !tags.isEmpty()) {

                return tags;
            }

            String fallback =
                    fallbackNamespace(
                            prefix);

            tags =
                    fallback == null
                            ? null
                            : byNamespace.get(
                                    fallback);

            return tags == null
                    ? Collections
                            .<String, TagDef>emptyMap()
                    : tags;
        }
    }

    private static final class TagDef {
        final String name;
        String description;
        boolean metadataBacked;
        final Map<String, AttributeDef> attributes =
                new LinkedHashMap<String, AttributeDef>();

        TagDef(String name) {
            this.name = name;
        }

        void setDescriptionIfMissing(
                String value) {

            if ((description == null
                    || description.trim()
                            .isEmpty())
                    && value != null) {

                description = value;
            }
        }

        AttributeDef attribute(
                String name) {

            AttributeDef attribute =
                    attributes.get(name);

            if (attribute == null) {
                attribute =
                        new AttributeDef(name);

                attributes.put(
                        name,
                        attribute);
            }

            return attribute;
        }
    }

    private static final class AttributeDef {
        final String name;
        String type;
        String description;
        boolean required;
        boolean metadataBacked;

        AttributeDef(String name) {
            this.name = name;
        }

        void merge(
                String newType,
                boolean newRequired,
                String newDescription) {

            if (type == null
                    && newType != null
                    && !newType.trim()
                            .isEmpty()) {

                type = newType.trim();
            }

            if (description == null
                    && newDescription != null
                    && !newDescription.trim()
                            .isEmpty()) {

                description =
                        newDescription.trim();
            }

            required =
                    required || newRequired;
        }

        String detail() {
            StringBuilder out =
                    new StringBuilder();

            if (required) {
                out.append("required");
            }

            if (type != null
                    && !type.isEmpty()) {

                if (out.length() > 0) {
                    out.append(" • ");
                }

                out.append(type);
            }

            if (description != null
                    && !description.isEmpty()) {

                if (out.length() > 0) {
                    out.append(" • ");
                }

                out.append(description);
            }

            return out.toString();
        }
    }
}
