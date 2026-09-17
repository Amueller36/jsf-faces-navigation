package de.andre.jsfnavigation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public final class JsfNavigationSupport {

    private JsfNavigationSupport() {
    }

    public static List<NavigationTarget> resolve(
            JsfCursorReference reference,
            IFile currentFile) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (reference == null || currentFile == null) {
            return result;
        }

        String project =
                currentFile.getProject().getName();

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index == null) {
            return result;
        }

        switch (reference.getKind()) {
        case JsfCursorReference.COMPONENT:
            addSymbols(
                    result,
                    index.find(
                            ViewSymbol.COMPONENT_ID,
                            reference.getName(),
                            project),
                    currentFile,
                    "component");
            break;

        case JsfCursorReference.WIDGET:
            addSymbols(
                    result,
                    index.find(
                            ViewSymbol.WIDGET_VAR,
                            reference.getName(),
                            project),
                    currentFile,
                    "widgetVar");
            break;

        case JsfCursorReference.FILE:
            IFile target =
                    resolveViewPath(
                            currentFile,
                            reference.getName());

            if (target != null) {
                result.add(
                        new WebNavigationTarget(
                                target,
                                0,
                                target.getProjectRelativePath()
                                        .toPortableString()));
            }
            break;

        case JsfCursorReference.COMPOSITE:
            IFile composite =
                    resolveComposite(
                            currentFile.getProject(),
                            reference.getExtra());

            if (composite != null) {
                result.add(
                        new WebNavigationTarget(
                                composite,
                                0,
                                composite.getProjectRelativePath()
                                        .toPortableString()));
            }
            break;

        case JsfCursorReference.BUNDLE_KEY:
            IFile properties =
                    resolveBundleProperties(
                            currentFile,
                            reference.getExtra());

            if (properties != null) {
                int offset =
                        findPropertyKeyOffset(
                                properties,
                                reference.getName());

                if (offset >= 0) {
                    result.add(
                            new WebNavigationTarget(
                                    properties,
                                    offset,
                                    properties.getProjectRelativePath()
                                            .toPortableString()
                                    + " — "
                                    + reference.getName()));
                }
            }
            break;

        case JsfCursorReference.COMPOSITE_ATTRIBUTE:
            IFile compositeFile =
                    resolveComposite(
                            currentFile.getProject(),
                            reference.getExtra());

            if (compositeFile != null) {
                int attributeOffset =
                        findCompositeAttributeOffset(
                                compositeFile,
                                reference.getName());

                if (attributeOffset >= 0) {
                    result.add(
                            new WebNavigationTarget(
                                    compositeFile,
                                    attributeOffset,
                                    compositeFile.getProjectRelativePath()
                                        .toPortableString()
                                    + " — cc:attribute "
                                    + reference.getName()));
                }
            }
            break;
        case JsfCursorReference.ROLE:
            result.addAll(
                    ProjectTextSearch.find(
                            currentFile.getProject(),
                            reference.getName(),
                            new String[] {
                                    ".java",
                                    ".xhtml",
                                    ".xml",
                                    ".properties"
                            },
                            "role "
                                    + reference.getName()));
            break;

        default:
            break;
        }

        return unique(result);
    }

    public static List<NavigationTarget> reverseComponentReferences(
            String componentId,
            IFile currentFile) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index == null || currentFile == null) {
            return result;
        }

        List<ViewSymbol> refs =
                index.referencesToComponent(
                        componentId,
                        currentFile.getProject().getName());

        addSymbols(
                result,
                refs,
                currentFile,
                "reference");

        result.addAll(
                JavaUiReferenceSearch.componentReferences(
                        currentFile.getProject(),
                        componentId));

        return unique(result);
    }

    public static List<NavigationTarget> reverseWidgetReferences(
            String widgetVar,
            IFile currentFile) {

        List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index == null || currentFile == null) {
            return result;
        }

        addSymbols(
                result,
                index.referencesToWidget(
                        widgetVar,
                        currentFile.getProject().getName()),
                currentFile,
                "PF reference");

        result.addAll(
                JavaUiReferenceSearch.widgetReferences(
                        currentFile.getProject(),
                        widgetVar));

        return unique(result);
    }

    private static void addSymbols(
            List<NavigationTarget> result,
            List<ViewSymbol> symbols,
            IFile preferredFile,
            String description) {

        /*
         * Same-file targets come first. This matches JSF view-local IDs and
         * avoids unrelated IDs from other pages unless there is ambiguity.
         */
        for (int pass = 0; pass < 2; pass++) {
            for (ViewSymbol symbol : symbols) {
                IFile file = symbol.getFile();

                if (!file.exists()) {
                    continue;
                }

                boolean same =
                        preferredFile != null
                        && preferredFile.getFullPath()
                                .equals(file.getFullPath());

                if ((pass == 0 && !same)
                        || (pass == 1 && same)) {
                    continue;
                }

                result.add(
                        new WebNavigationTarget(
                                file,
                                symbol.getOffset(),
                                file.getProjectRelativePath()
                                    .toPortableString()
                                + " — "
                                + description
                                + " "
                                + symbol.getName()));
            }
        }
    }

    public static IFile resolveViewPath(
            IFile currentFile,
            String value) {

        if (currentFile == null
                || value == null
                || value.isEmpty()
                || value.indexOf("#{") >= 0
                || value.indexOf("${") >= 0) {

            return null;
        }

        IProject project =
                currentFile.getProject();

        if (value.startsWith("/")) {
            return findProjectWebFile(
                    project,
                    value.substring(1));
        }

        IContainer parent =
                currentFile.getParent();

        IResource relative =
                parent.findMember(
                        new Path(value));

        if (relative instanceof IFile
                && relative.exists()) {

            return (IFile) relative;
        }

        return findProjectWebFile(
                project,
                value);
    }

    public static IFile resolveComposite(
            IProject project,
            String prefixAndName) {

        if (project == null
                || prefixAndName == null) {

            return null;
        }

        return findProjectWebFile(
                project,
                "resources/"
                + prefixAndName
                + ".xhtml");
    }

    private static IFile findProjectWebFile(
            final IProject project,
            final String webRelativePath) {

        if (project == null || !project.isOpen()) {
            return null;
        }

        String normalized =
                webRelativePath.replace('\\', '/');

        String[] commonRoots = new String[] {
                "",
                "WebContent/",
                "web/",
                "src/main/webapp/",
                "src/main/resources/META-INF/resources/"
        };

        for (String root : commonRoots) {
            IResource member =
                    project.findMember(
                            new Path(root + normalized));

            if (member instanceof IFile
                    && member.exists()) {

                return (IFile) member;
            }
        }

        final IFile[] found = new IFile[1];

        try {
            project.accept(
                    new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(
                                IResourceProxy proxy)
                                throws CoreException {

                            if (found[0] != null) {
                                return false;
                            }

                            if (proxy.getType()
                                    == IResource.FOLDER) {

                                String name =
                                        proxy.getName();

                                if ("target".equals(name)
                                        || "build".equals(name)
                                        || "bin".equals(name)
                                        || ".git".equals(name)) {

                                    return false;
                                }

                                return true;
                            }

                            if (proxy.getType()
                                    != IResource.FILE) {

                                return true;
                            }

                            IResource resource =
                                    proxy.requestResource();

                            if (resource instanceof IFile) {
                                String rel =
                                        resource.getProjectRelativePath()
                                                .toPortableString();

                                if (rel.endsWith(
                                        "/" + webRelativePath)
                                        || rel.equals(
                                                webRelativePath)) {

                                    found[0] =
                                            (IFile) resource;

                                    return false;
                                }
                            }

                            return true;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            return null;
        }

        return found[0];
    }

    public static IFile resolveBundleProperties(
            IFile currentFile,
            String bundleVar) {

        JsfViewIndexService index =
                Activator.getJsfViewIndexService();

        if (index == null) {
            return null;
        }

        List<ViewSymbol> vars =
                index.symbolsInFile(
                        currentFile,
                        ViewSymbol.BUNDLE_VAR);

        String basename = null;

        for (ViewSymbol symbol : vars) {
            if (bundleVar.equals(
                    symbol.getName())) {

                basename = symbol.getExtra();
                break;
            }
        }

        if (basename == null) {
            basename =
                    findFacesConfigBundle(
                            currentFile.getProject(),
                            bundleVar);
        }

        if (basename == null) {
            return null;
        }

        String relative =
                basename.replace('.', '/')
                + ".properties";

        return findProjectWebFile(
                currentFile.getProject(),
                relative);
    }


    private static String findFacesConfigBundle(
            final IProject project,
            final String bundleVar) {

        if (project == null || bundleVar == null) {
            return null;
        }

        final String[] result = new String[1];

        try {
            project.accept(
                    new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(
                                IResourceProxy proxy)
                                throws CoreException {

                            if (result[0] != null) {
                                return false;
                            }

                            if (proxy.getType()
                                    == IResource.FOLDER) {

                                String name = proxy.getName();

                                if ("target".equals(name)
                                        || "build".equals(name)
                                        || "bin".equals(name)
                                        || ".git".equals(name)) {

                                    return false;
                                }

                                return true;
                            }

                            if (proxy.getType() != IResource.FILE
                                    || !"faces-config.xml".equals(
                                            proxy.getName())) {

                                return true;
                            }

                            IFile file =
                                    (IFile) proxy.requestResource();

                            String source =
                                    JsfPageInspector.read(file);

                            if (source == null) {
                                return false;
                            }

                            Pattern block = Pattern.compile(
                                    "<resource-bundle\\b[^>]*>(.*?)</resource-bundle>",
                                    Pattern.DOTALL);

                            Matcher matcher = block.matcher(source);

                            while (matcher.find()) {
                                String body = matcher.group(1);

                                Matcher var = Pattern.compile(
                                        "<var>\\s*"
                                        + Pattern.quote(bundleVar)
                                        + "\\s*</var>",
                                        Pattern.DOTALL)
                                        .matcher(body);

                                if (!var.find()) {
                                    continue;
                                }

                                Matcher base = Pattern.compile(
                                        "<base-name>\\s*([^<]+?)\\s*</base-name>",
                                        Pattern.DOTALL)
                                        .matcher(body);

                                if (base.find()) {
                                    result[0] = base.group(1).trim();
                                    return false;
                                }
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            return null;
        }

        return result[0];
    }

    public static int findPropertyKeyOffset(
            IFile file,
            String key) {

        BufferedReader reader = null;

        try {
            String charset =
                    file.getCharset();

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    file.getContents(),
                                    Charset.forName(
                                            charset)));

            int offset = 0;
            String line;

            Pattern pattern =
                    Pattern.compile(
                            "^\\s*"
                            + Pattern.quote(key)
                            + "\\s*[:=]");

            while ((line = reader.readLine()) != null) {
                Matcher matcher =
                        pattern.matcher(line);

                if (matcher.find()) {
                    int keyPos =
                            line.indexOf(key);

                    return offset
                            + Math.max(0, keyPos);
                }

                offset += line.length() + 1;
            }

        } catch (Exception e) {
            return -1;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }

        return -1;
    }


    private static int findCompositeAttributeOffset(
            IFile file,
            String attributeName) {

        BufferedReader reader = null;

        try {
            String charset = file.getCharset();

            reader = new BufferedReader(
                    new InputStreamReader(
                            file.getContents(),
                            Charset.forName(charset)));

            StringBuilder source = new StringBuilder();
            char[] buffer = new char[8192];
            int read;

            while ((read = reader.read(buffer)) >= 0) {
                source.append(buffer, 0, read);
            }

            Pattern pattern = Pattern.compile(
                    "<\\s*(?:cc|composite):attribute\\b[^>]*\\bname\\s*=\\s*(['\"])"
                    + Pattern.quote(attributeName)
                    + "\\1",
                    Pattern.DOTALL);

            Matcher matcher = pattern.matcher(source);

            if (matcher.find()) {
                int pos = source.indexOf(attributeName, matcher.start());
                return pos;
            }

        } catch (Exception e) {
            return -1;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }

        return -1;
    }

    private static List<NavigationTarget> unique(
            List<NavigationTarget> input) {

        Map<String, NavigationTarget> unique =
                new LinkedHashMap<String, NavigationTarget>();

        for (NavigationTarget target : input) {
            unique.put(
                    target.getIdentity(),
                    target);
        }

        return new ArrayList<NavigationTarget>(
                unique.values());
    }
}
