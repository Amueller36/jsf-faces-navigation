package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public final class JavaTypeFinder {

    private JavaTypeFinder() {
    }

    public static List<IType> findTypes(
            String simpleName,
            String preferredProjectName) {

        if (preferredProjectName != null) {
            IJavaProject preferredProject =
                    javaProject(preferredProjectName);

            if (preferredProject != null) {
                List<IType> projectMatches =
                        searchTypes(
                                simpleName,
                                SearchPattern.R_EXACT_MATCH
                                        | SearchPattern.R_CASE_SENSITIVE,
                                SearchEngine.createJavaSearchScope(
                                        new IJavaElement[] {
                                                preferredProject
                                        },
                                        true));

                if (!projectMatches.isEmpty()) {
                    return projectMatches;
                }
            }
        }

        return searchTypes(
                simpleName,
                SearchPattern.R_EXACT_MATCH
                        | SearchPattern.R_CASE_SENSITIVE,
                SearchEngine.createWorkspaceScope());
    }

    public static List<IType> findAllSourceTypes() {
        List<IType> matches =
                searchTypes(
                        "*",
                        SearchPattern.R_PATTERN_MATCH,
                        SearchEngine.createWorkspaceScope());

        List<IType> sourceTypes =
                new ArrayList<IType>();

        for (IType type : matches) {
            if (type.getCompilationUnit() != null) {
                sourceTypes.add(type);
            }
        }

        return sourceTypes;
    }

    private static IJavaProject javaProject(String projectName) {
        IProject project = ResourcesPlugin.getWorkspace()
                .getRoot()
                .getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return null;
        }

        try {
            if (!project.hasNature(JavaCore.NATURE_ID)) {
                return null;
            }
        } catch (CoreException e) {
            return null;
        }

        IJavaProject javaProject = JavaCore.create(project);
        return javaProject.exists() ? javaProject : null;
    }

    private static List<IType> searchTypes(
            String namePattern,
            int matchRule,
            IJavaSearchScope scope) {

        final Set<IType> result =
                new LinkedHashSet<IType>();

        SearchPattern pattern =
                SearchPattern.createPattern(
                        namePattern,
                        IJavaSearchConstants.TYPE,
                        IJavaSearchConstants.DECLARATIONS,
                        matchRule);

        if (pattern == null) {
            return new ArrayList<IType>();
        }

        SearchRequestor requestor =
                new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(
                            SearchMatch match)
                            throws CoreException {

                        Object element = match.getElement();

                        if (element instanceof IType) {
                            IType type = (IType) element;

                            if (type.getCompilationUnit() != null) {
                                result.add(type);
                            }

                            return;
                        }

                        if (element instanceof IJavaElement) {
                            IJavaElement javaElement =
                                    (IJavaElement) element;

                            IJavaElement ancestor =
                                    javaElement.getAncestor(
                                            IJavaElement.TYPE);

                            if (ancestor instanceof IType) {
                                IType type = (IType) ancestor;

                                if (type.getCompilationUnit() != null) {
                                    result.add(type);
                                }
                            }
                        }
                    }
                };

        try {
            new SearchEngine().search(
                    pattern,
                    new SearchParticipant[] {
                            SearchEngine.getDefaultSearchParticipant()
                    },
                    scope,
                    requestor,
                    new NullProgressMonitor());

        } catch (CoreException e) {
            e.printStackTrace();
        }

        return new ArrayList<IType>(result);
    }
}
