package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jdt.core.IType;

public final class XsdHyperlinkDetector
        implements IHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(
            ITextViewer textViewer,
            IRegion region,
            boolean canShowMultipleHyperlinks) {

        IFile current =
                EditorContext.currentFile();

        if (current == null
                || !"xsd".equalsIgnoreCase(
                        current.getFileExtension())) {

            return null;
        }

        IDocument document =
                textViewer.getDocument();

        XsdAttributeReference reference =
                XsdReferenceDetector.find(
                        document,
                        region.getOffset());

        if (reference == null) {
            return null;
        }

        String attribute =
                localName(
                        reference.getAttributeName());

        if ("schemaLocation".equals(
                attribute)) {

            String location =
                    reference.getValue();

            IFile target =
                    schemaLocation(
                            current,
                            location);

            return target != null
                    && target.exists()
                            ? new IHyperlink[] {
                                    new WorkspaceFileHyperlink(
                                            reference.getRegion(),
                                            target,
                                            "Open imported/included schema "
                                                    + target.getName())
                            }
                            : null;
        }

        if ("type".equals(attribute)
                || "base".equals(attribute)
                || "ref".equals(attribute)) {

            QName qname =
                    qname(
                            reference.getValue());

            XsdIndexService service =
                    Activator.getXsdIndexService();

            if (service == null) {
                return null;
            }

            String namespace =
                    qname.prefix.isEmpty()
                            ? ""
                            : XsdReferenceDetector
                                    .namespaceForPrefix(
                                            document,
                                            qname.prefix);

            if ("http://www.w3.org/2001/XMLSchema"
                    .equals(
                            namespace)) {

                return null;
            }

            List<XsdDefinition> definitions =
                    filterByReferenceKind(
                            service.resolve(
                                    namespace,
                                    qname.local),
                            attribute);

            return definitions.isEmpty()
                    ? null
                    : new IHyperlink[] {
                            new XsdDefinitionHyperlink(
                                    reference.getRegion(),
                                    definitions)
                    };
        }

        if ("name".equals(attribute)
                && isDefinitionTag(
                        reference.getTagName())) {

            XsdIndexService service =
                    Activator.getXsdIndexService();

            if (service == null) {
                return null;
            }

            String namespace =
                    service.targetNamespace(
                            current);

            List<XsdDefinition> definitions =
                    service.resolve(
                            namespace,
                            reference.getValue());

            if (definitions.isEmpty()) {
                return null;
            }

            XsdDefinition currentDefinition =
                    nearestCurrentDefinition(
                            definitions,
                            current,
                            reference.getRegion()
                                    .getOffset());

            if (currentDefinition == null) {
                return null;
            }

            List<IType> types =
                    JaxbTypeResolver
                            .findForDefinition(
                                    currentDefinition,
                                    null);

            return types.isEmpty()
                    ? null
                    : new IHyperlink[] {
                            new JaxbTypeHyperlink(
                                    reference.getRegion(),
                                    types)
                    };
        }

        return null;
    }


    private static IFile schemaLocation(
            IFile current,
            String location) {

        if (current == null
                || location == null
                || location.trim()
                        .isEmpty()) {

            return null;
        }

        String value =
                location.trim();

        if (value.startsWith(
                "platform:/resource/")) {

            value =
                    "/"
                    + value.substring(
                            "platform:/resource/"
                                    .length());
        }

        if (value.indexOf(
                "://") >= 0) {

            return null;
        }

        Path path =
                new Path(
                        value);

        if (path.isAbsolute()) {
            IFile absolute =
                    org.eclipse.core.resources
                            .ResourcesPlugin
                            .getWorkspace()
                            .getRoot()
                            .getFile(
                                    path);

            return absolute.exists()
                    ? absolute
                    : null;
        }

        IContainer parent =
                current.getParent();

        if (parent == null) {
            return null;
        }

        IFile relative =
                parent.getFile(
                        path);

        return relative.exists()
                ? relative
                : null;
    }


    private static List<XsdDefinition> filterByReferenceKind(
            List<XsdDefinition> definitions,
            String attribute) {

        if (definitions == null
                || definitions.isEmpty()) {

            return definitions;
        }

        List<XsdDefinition> preferred =
                new ArrayList<XsdDefinition>();

        for (XsdDefinition definition :
                definitions) {

            String kind =
                    definition.getKind();

            if (("type".equals(attribute)
                    || "base".equals(attribute))
                    && ("complexType".equalsIgnoreCase(
                            kind)
                            || "simpleType".equalsIgnoreCase(
                                    kind))) {

                preferred.add(
                        definition);

            } else if ("ref".equals(attribute)
                    && ("element".equalsIgnoreCase(
                            kind)
                            || "attribute".equalsIgnoreCase(
                                    kind)
                            || "group".equalsIgnoreCase(
                                    kind)
                            || "attributeGroup".equalsIgnoreCase(
                                    kind))) {

                preferred.add(
                        definition);
            }
        }

        return preferred.isEmpty()
                ? definitions
                : preferred;
    }

    private static XsdDefinition nearestCurrentDefinition(
            List<XsdDefinition> definitions,
            IFile current,
            int offset) {

        String path =
                current.getFullPath()
                        .toPortableString();

        XsdDefinition best = null;
        int bestDistance =
                Integer.MAX_VALUE;

        for (XsdDefinition definition :
                definitions) {

            if (!path.equals(
                    definition.getResourcePath())) {

                continue;
            }

            int distance =
                    Math.abs(
                            definition.getOffset()
                            - offset);

            if (distance < bestDistance) {
                best = definition;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static boolean isDefinitionTag(
            String tagName) {

        String local =
                localName(
                        tagName);

        return "complexType".equals(
                local)
                || "simpleType".equals(
                        local)
                || "element".equals(
                        local)
                || "attribute".equals(
                        local)
                || "group".equals(
                        local)
                || "attributeGroup".equals(
                        local);
    }

    private static QName qname(
            String value) {

        String text =
                value == null
                        ? ""
                        : value.trim();

        int colon =
                text.indexOf(':');

        return colon > 0
                ? new QName(
                        text.substring(
                                0,
                                colon),
                        text.substring(
                                colon + 1))
                : new QName(
                        "",
                        text);
    }

    private static String localName(
            String value) {

        if (value == null) {
            return "";
        }

        int colon =
                value.indexOf(':');

        return colon >= 0
                ? value.substring(
                        colon + 1)
                : value;
    }

    private static final class QName {

        final String prefix;
        final String local;

        QName(
                String prefix,
                String local) {

            this.prefix = prefix;
            this.local = local;
        }
    }
}
