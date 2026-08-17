package de.andre.jsfnavigation;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;

public final class ProjectTextSearch {

    private ProjectTextSearch() {
    }

    public static List<NavigationTarget> find(
            final IProject project,
            final String needle,
            final String[] extensions,
            final String labelPrefix) {

        final List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (project == null
                || !project.isOpen()
                || needle == null
                || needle.isEmpty()) {

            return result;
        }

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
                                    != IResource.FILE
                                    || !matchesExtension(
                                            proxy.getName(),
                                            extensions)) {

                                return true;
                            }

                            IFile file =
                                    (IFile) proxy.requestResource();

                            String source =
                                    read(file);

                            if (source == null) {
                                return false;
                            }

                            int from = 0;

                            while (from < source.length()) {
                                int offset =
                                        source.indexOf(
                                                needle,
                                                from);

                                if (offset < 0) {
                                    break;
                                }

                                result.add(
                                        new WebNavigationTarget(
                                                file,
                                                offset,
                                                file.getProjectRelativePath()
                                                    .toPortableString()
                                                + " — "
                                                + labelPrefix));

                                from =
                                        offset
                                        + Math.max(
                                                1,
                                                needle.length());
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            // Return whatever has already been found.
        }

        return result;
    }


    public static List<NavigationTarget> findFilesByName(
            final IProject project,
            final String fileName,
            final String labelPrefix) {

        final List<NavigationTarget> result =
                new ArrayList<NavigationTarget>();

        if (project == null
                || !project.isOpen()
                || fileName == null
                || fileName.isEmpty()) {

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

                            if (proxy.getType() == IResource.FILE
                                    && fileName.equals(proxy.getName())) {

                                IFile file =
                                        (IFile) proxy.requestResource();

                                result.add(
                                        new WebNavigationTarget(
                                                file,
                                                0,
                                                file.getProjectRelativePath()
                                                    .toPortableString()
                                                + " — "
                                                + labelPrefix));
                            }

                            return false;
                        }
                    },
                    IResource.NONE);

        } catch (CoreException e) {
            // Return accumulated results.
        }

        return result;
    }

    private static boolean matchesExtension(
            String fileName,
            String[] extensions) {

        String lower =
                fileName.toLowerCase();

        for (String extension : extensions) {
            if (lower.endsWith(
                    extension.toLowerCase())) {

                return true;
            }
        }

        return false;
    }

    private static String read(IFile file) {
        InputStream in = null;

        try {
            in = file.getContents();

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[8192];

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
                }
            }
        }
    }
}
