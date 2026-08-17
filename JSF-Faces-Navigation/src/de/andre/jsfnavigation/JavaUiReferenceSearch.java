package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;

public final class JavaUiReferenceSearch {

    private JavaUiReferenceSearch() {
    }

    public static List<NavigationTarget> componentReferences(
            IProject project,
            String componentId) {

        String quoted = Pattern.quote(componentId);

        Pattern pattern = Pattern.compile(
                "(?:\\.ajax\\(\\)\\.update|\\.update|addPartialUpdateTarget|addComponentToAjaxRender)"
                + "\\s*\\([^;]{0,500}?(['\"])(?:[^'\"]*:)?"
                + quoted
                + "\\1",
                Pattern.DOTALL);

        return search(
                project,
                pattern,
                componentId,
                "Java Ajax component reference");
    }

    public static List<NavigationTarget> widgetReferences(
            IProject project,
            String widgetVar) {

        Pattern pattern = Pattern.compile(
                "PF\\s*\\(\\s*(['\"])"
                + Pattern.quote(widgetVar)
                + "\\1\\s*\\)");

        return search(
                project,
                pattern,
                widgetVar,
                "Java widget reference");
    }

    private static List<NavigationTarget> search(
            final IProject project,
            final Pattern pattern,
            final String token,
            final String label) {

        final List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (project == null || !project.isOpen()) {
            return result;
        }

        try {
            project.accept(
                    new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(
                                IResourceProxy proxy)
                                throws CoreException {

                            if (proxy.getType() == IResource.FOLDER) {
                                String name = proxy.getName();

                                if ("target".equals(name)
                                        || "build".equals(name)
                                        || "bin".equals(name)
                                        || ".git".equals(name)
                                        || ".svn".equals(name)) {

                                    return false;
                                }

                                return true;
                            }

                            if (proxy.getType() != IResource.FILE
                                    || !proxy.getName()
                                            .toLowerCase()
                                            .endsWith(".java")) {

                                return true;
                            }

                            IFile file =
                                    (IFile) proxy.requestResource();

                            String source = read(file);

                            if (source == null) {
                                return false;
                            }

                            Matcher matcher =
                                    pattern.matcher(source);

                            while (matcher.find()) {
                                int offset =
                                        source.indexOf(
                                                token,
                                                matcher.start());

                                if (offset >= 0
                                        && offset < matcher.end()) {

                                    result.add(
                                            new WebNavigationTarget(
                                                    file,
                                                    offset,
                                                    file.getProjectRelativePath()
                                                        .toPortableString()
                                                    + " — "
                                                    + label));
                                }
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            // Return accumulated matches.
        }

        return result;
    }

    private static String read(IFile file) {
        InputStream in = null;

        try {
            in = file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
            }

            return new String(
                    out.toByteArray(),
                    Charset.forName(file.getCharset()));

        } catch (Exception e) {
            return null;

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
