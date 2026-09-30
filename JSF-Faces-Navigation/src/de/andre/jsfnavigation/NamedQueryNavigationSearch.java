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

public final class NamedQueryNavigationSearch {

    private NamedQueryNavigationSearch() {
    }

    public static List<NavigationTarget> findDefinitions(
            final IProject project,
            final String queryName) {

        final List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (project == null
                || !project.isOpen()
                || queryName == null
                || queryName.isEmpty()) {

            return result;
        }

        final Pattern javaPattern =
                Pattern.compile(
                        "@NamedQuery\\s*\\([^)]*?\\bname\\s*=\\s*(['\"])"
                        + Pattern.quote(queryName)
                        + "\\1",
                        Pattern.DOTALL);

        final Pattern xmlPattern =
                Pattern.compile(
                        "<named-query\\b[^>]*\\bname\\s*=\\s*(['\"])"
                        + Pattern.quote(queryName)
                        + "\\1",
                        Pattern.DOTALL);

        try {
            project.accept(
                    new IResourceProxyVisitor() {
                        @Override
                        public boolean visit(
                                IResourceProxy proxy)
                                throws CoreException {

                            if (proxy.getType()
                                    == IResource.FOLDER) {

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

                            if (proxy.getType()
                                    != IResource.FILE) {

                                return true;
                            }

                            String lower =
                                    proxy.getName().toLowerCase();

                            if (!lower.endsWith(".java")
                                    && !lower.endsWith(".xml")) {

                                return true;
                            }

                            IFile file =
                                    (IFile) proxy.requestResource();

                            String source = read(file);

                            if (source == null) {
                                return false;
                            }

                            Pattern pattern =
                                    lower.endsWith(".java")
                                            ? javaPattern
                                            : xmlPattern;

                            Matcher matcher =
                                    pattern.matcher(source);

                            while (matcher.find()) {
                                int offset =
                                        source.indexOf(
                                                queryName,
                                                matcher.start());

                                result.add(
                                        new WebNavigationTarget(
                                                file,
                                                Math.max(
                                                        matcher.start(),
                                                        offset),
                                                file.getProjectRelativePath()
                                                    .toPortableString()
                                                + " — named query "
                                                + queryName));
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            // Return any already collected declarations.
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
                    Charset.forName(
                            file.getCharset()));

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
