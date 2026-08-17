package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
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

    public static List<IType> findTypes(String simpleName) {
        return searchTypes(simpleName, SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
    }

    public static List<IType> findAllSourceTypes() {
        List<IType> matches = searchTypes("*", SearchPattern.R_PATTERN_MATCH);
        List<IType> sourceTypes = new ArrayList<IType>();

        for (IType type : matches) {
            if (type.getCompilationUnit() != null) {
                sourceTypes.add(type);
            }
        }

        return sourceTypes;
    }

    public static IType findFirstType(String simpleName) {
        List<IType> matches = findTypes(simpleName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static List<IType> searchTypes(String namePattern, int matchRule) {
        final Set<IType> result = new LinkedHashSet<IType>();

        SearchPattern pattern = SearchPattern.createPattern(
                namePattern,
                IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS,
                matchRule);

        if (pattern == null) {
            return new ArrayList<IType>();
        }

        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();

        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                Object element = match.getElement();

                if (element instanceof IType) {
                    result.add((IType) element);
                    return;
                }

                if (element instanceof IJavaElement) {
                    IJavaElement javaElement = (IJavaElement) element;
                    IJavaElement ancestor = javaElement.getAncestor(IJavaElement.TYPE);

                    if (ancestor instanceof IType) {
                        result.add((IType) ancestor);
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
