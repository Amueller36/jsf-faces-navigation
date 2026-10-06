package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.swt.widgets.Display;

public final class JavaTypeFinder {

    private static final int MAX_CACHE_ENTRIES = 512;
    private static final long CACHE_TTL_MILLIS = 60000L;

    private static final Map<String, CachedTypeHandles> CACHE =
            new LinkedHashMap<String, CachedTypeHandles>(128, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CachedTypeHandles> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    private static final Map<String, Boolean> PENDING =
            new ConcurrentHashMap<String, Boolean>();

    private JavaTypeFinder() {
    }

    public static List<IType> findTypes(
            final String simpleName,
            final String preferredProjectName) {

        if (simpleName == null || simpleName.trim().isEmpty()) {
            return new ArrayList<IType>();
        }

        final String key = cacheKey(simpleName, preferredProjectName);
        List<IType> cached = cached(key);
        if (cached != null) {
            return cached;
        }

        if (Display.getCurrent() != null) {
            warmAsync(simpleName, preferredProjectName, key);
            return new ArrayList<IType>();
        }

        List<IType> result = findTypesUncached(simpleName, preferredProjectName);
        put(key, result);
        return result;
    }

    public static List<IType> findAllSourceTypes() {
        return searchTypes("*", SearchPattern.R_PATTERN_MATCH,
                SearchEngine.createWorkspaceScope());
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        PENDING.clear();
    }

    private static List<IType> findTypesUncached(
            String simpleName,
            String preferredProjectName) {

        if (preferredProjectName != null) {
            IJavaProject preferredProject = javaProject(preferredProjectName);
            if (preferredProject != null) {
                List<IType> projectMatches = searchTypes(
                        simpleName,
                        SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE,
                        SearchEngine.createJavaSearchScope(
                                new IJavaElement[] { preferredProject }, true));
                if (!projectMatches.isEmpty()) {
                    return projectMatches;
                }
            }
        }

        return searchTypes(simpleName,
                SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE,
                SearchEngine.createWorkspaceScope());
    }

    private static void warmAsync(
            final String simpleName,
            final String preferredProjectName,
            final String key) {

        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        Job job = new Job("Warm Java type lookup") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (!monitor.isCanceled()) {
                        put(key, findTypesUncached(simpleName, preferredProjectName));
                    }
                } finally {
                    PENDING.remove(key);
                }
                return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }

    private static String cacheKey(String simpleName, String preferredProjectName) {
        return (preferredProjectName == null ? "" : preferredProjectName)
                + "#" + simpleName;
    }

    private static List<IType> cached(String key) {
        CachedTypeHandles cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
        if (cached == null
                || System.currentTimeMillis() - cached.createdAt > CACHE_TTL_MILLIS) {
            return null;
        }
        List<IType> result = new ArrayList<IType>();
        for (String handle : cached.handles) {
            IJavaElement element = JavaCore.create(handle);
            if (element instanceof IType && element.exists()) {
                result.add((IType) element);
            }
        }
        return result;
    }

    private static void put(String key, List<IType> types) {
        List<String> handles = new ArrayList<String>();
        for (IType type : types) {
            handles.add(type.getHandleIdentifier());
        }
        synchronized (CACHE) {
            CACHE.put(key, new CachedTypeHandles(
                    handles.toArray(new String[handles.size()]),
                    System.currentTimeMillis()));
        }
    }

    private static IJavaProject javaProject(String projectName) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
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

        final Set<IType> result = new LinkedHashSet<IType>();
        SearchPattern pattern = SearchPattern.createPattern(
                namePattern,
                IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS,
                matchRule);
        if (pattern == null) {
            return new ArrayList<IType>();
        }

        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                Object element = match.getElement();
                if (element instanceof IType) {
                    IType type = (IType) element;
                    if (type.getCompilationUnit() != null) {
                        result.add(type);
                    }
                    return;
                }
                if (element instanceof IJavaElement) {
                    IJavaElement ancestor = ((IJavaElement) element)
                            .getAncestor(IJavaElement.TYPE);
                    if (ancestor instanceof IType
                            && ((IType) ancestor).getCompilationUnit() != null) {
                        result.add((IType) ancestor);
                    }
                }
            }
        };

        try {
            new SearchEngine().search(
                    pattern,
                    new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                    scope,
                    requestor,
                    new NullProgressMonitor());
        } catch (CoreException e) {
            // A rebuilding JDT index should degrade to no result, never a UI failure.
        }
        return new ArrayList<IType>(result);
    }

    private static final class CachedTypeHandles {
        final String[] handles;
        final long createdAt;
        CachedTypeHandles(String[] handles, long createdAt) {
            this.handles = handles;
            this.createdAt = createdAt;
        }
    }
}
