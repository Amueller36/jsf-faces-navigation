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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public final class CallerSearch {

    private CallerSearch() {
    }

    public static List<NavigationTarget> findDirectCallers(
            final IMethod target) {

        final Map<String, NavigationTarget> unique =
                new LinkedHashMap<String, NavigationTarget>();

        findJavaCallers(target, unique);
        findJsfCallers(target, unique);

        return new ArrayList<NavigationTarget>(
                unique.values());
    }

    private static void findJavaCallers(
            final IMethod target,
            final Map<String, NavigationTarget> unique) {

        SearchPattern pattern =
                SearchPattern.createPattern(
                        target,
                        IJavaSearchConstants.REFERENCES);

        if (pattern == null) {
            return;
        }

        SearchRequestor requestor =
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(
                            SearchMatch match)
                            throws CoreException {

                        if (match.getOffset() < 0) {
                            return;
                        }

                        IMethod containing =
                                containingMethod(
                                        match.getElement());

                        if (containing == null) {
                            return;
                        }

                        IResource resource =
                                match.getResource();

                        IFile file =
                                resource instanceof IFile
                                        ? (IFile) resource
                                        : containing.getResource()
                                                instanceof IFile
                                                ? (IFile) containing
                                                        .getResource()
                                                : null;

                        if (file == null) {
                            return;
                        }

                        JavaNavigationTarget result =
                                JavaNavigationTarget.callSite(
                                        containing,
                                        file,
                                        match.getOffset());

                        unique.put(
                                result.getIdentity(),
                                result);
                    }
                };

        try {
            new SearchEngine().search(
                    pattern,
                    new SearchParticipant[] {
                            SearchEngine
                                    .getDefaultSearchParticipant()
                    },
                    SearchEngine.createWorkspaceScope(),
                    requestor,
                    new NullProgressMonitor());

        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

    private static void findJsfCallers(
            IMethod target,
            Map<String, NavigationTarget> unique) {

        IType declaringType =
                target.getDeclaringType();

        if (declaringType == null) {
            return;
        }

        String beanName;

        try {
            beanName =
                    BeanIntrospector.beanNameOf(
                            declaringType);

        } catch (JavaModelException e) {
            return;
        }

        if (beanName == null || beanName.isEmpty()) {
            return;
        }

        WebIndexService webIndex =
                Activator.getWebIndexService();

        if (webIndex == null) {
            return;
        }

        List<BeanUsage> usages =
                webIndex.findBeanUsages(
                        beanName,
                        declaringType
                                .getJavaProject()
                                .getElementName());

        Map<String, IFile> pages =
                new LinkedHashMap<String, IFile>();

        for (BeanUsage usage : usages) {
            IFile file = usage.getFile();

            if (file != null && file.exists()) {
                pages.put(
                        file.getFullPath()
                                .toPortableString(),
                        file);
            }
        }

        Pattern expression =
                Pattern.compile(
                        "[#\\$]\\{[^}]*?\\b"
                        + Pattern.quote(beanName)
                        + "\\s*\\.\\s*("
                        + Pattern.quote(
                                target.getElementName())
                        + ")\\b");

        for (IFile file : pages.values()) {
            String source = read(file);

            if (source == null) {
                continue;
            }

            Matcher matcher =
                    expression.matcher(source);

            while (matcher.find()) {
                int offset = matcher.start(1);

                WebNavigationTarget result =
                        new WebNavigationTarget(
                                file,
                                offset,
                                file.getProjectRelativePath()
                                        .toPortableString()
                                + "  [#{"
                                + beanName
                                + "."
                                + target.getElementName()
                                + "}]");

                unique.put(
                        result.getIdentity(),
                        result);
            }
        }
    }

    private static IMethod containingMethod(
            Object element) {

        if (element instanceof IMethod) {
            return (IMethod) element;
        }

        if (element instanceof IJavaElement) {
            IJavaElement ancestor =
                    ((IJavaElement) element)
                            .getAncestor(
                                    IJavaElement.METHOD);

            if (ancestor instanceof IMethod) {
                return (IMethod) ancestor;
            }
        }

        return null;
    }

    private static String read(IFile file) {
        BufferedReader reader = null;

        try {
            Charset charset =
                    Charset.forName(
                            file.getCharset());

            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    file.getContents(),
                                    charset));

            StringBuilder result =
                    new StringBuilder();

            char[] buffer =
                    new char[8192];

            int count;

            while ((count = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, count);
            }

            return result.toString();

        } catch (Exception e) {
            return null;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }
}
