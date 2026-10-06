package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.widgets.Display;

public final class JdtHierarchyCache {

    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long CACHE_TTL_MILLIS = 30000L;

    private static final Map<String, CachedHierarchy> CACHE =
            new LinkedHashMap<String, CachedHierarchy>(32, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CachedHierarchy> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    private static final Map<String, Boolean> PENDING =
            new ConcurrentHashMap<String, Boolean>();

    private JdtHierarchyCache() {
    }

    public static IType[] supertypes(final IType type) {
        if (type == null || !type.exists()) {
            return new IType[0];
        }

        final String key = type.getHandleIdentifier();
        CachedHierarchy cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }

        if (cached != null
                && System.currentTimeMillis() - cached.createdAt
                        <= CACHE_TTL_MILLIS) {
            return resolve(cached.typeHandles);
        }

        if (Display.getCurrent() != null) {
            warmAsync(type, key);
            return new IType[0];
        }

        return build(type, key);
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        PENDING.clear();
    }

    private static void warmAsync(final IType type, final String key) {
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        Job job = new Job("Warm Java type hierarchy") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (!monitor.isCanceled() && type.exists()) {
                        build(type, key);
                    }
                } finally {
                    PENDING.remove(key);
                }
                return monitor.isCanceled()
                        ? Status.CANCEL_STATUS
                        : Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }

    private static IType[] build(IType type, String key) {
        try {
            ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
            IType[] supers = orderedSupertypes(type, hierarchy);
            List<String> handles = new ArrayList<String>();
            for (IType superType : supers) {
                handles.add(superType.getHandleIdentifier());
            }
            synchronized (CACHE) {
                CACHE.put(key, new CachedHierarchy(
                        handles.toArray(new String[handles.size()]),
                        System.currentTimeMillis()));
            }
            return supers;
        } catch (JavaModelException e) {
            return new IType[0];
        }
    }

    /*
     * ITypeHierarchy#getAllSupertypes does not promise the declaration order
     * that is most useful for navigation.  For an EL call we want the nearest
     * inherited class implementation first (Concrete -> Base -> Core), then
     * inherited interfaces.  This also keeps an overridden method in a nearer
     * superclass from being shadowed by an arbitrary farther hierarchy entry.
     */
    private static IType[] orderedSupertypes(
            IType type,
            ITypeHierarchy hierarchy) {

        List<IType> classLineage = new ArrayList<IType>();
        Set<String> seen = new LinkedHashSet<String>();

        IType current = type;
        while (current != null) {
            IType superClass = hierarchy.getSuperclass(current);
            if (superClass == null) {
                break;
            }

            if (seen.add(superClass.getHandleIdentifier())) {
                classLineage.add(superClass);
            }

            current = superClass;
        }

        List<IType> result = new ArrayList<IType>(classLineage);

        addInterfaces(type, hierarchy, seen, result);
        for (IType classType : classLineage) {
            addInterfaces(classType, hierarchy, seen, result);
        }

        return result.toArray(new IType[result.size()]);
    }

    private static void addInterfaces(
            IType owner,
            ITypeHierarchy hierarchy,
            Set<String> seen,
            List<IType> result) {

        for (IType iface : hierarchy.getSuperInterfaces(owner)) {
            if (!seen.add(iface.getHandleIdentifier())) {
                continue;
            }

            result.add(iface);
            addInterfaces(iface, hierarchy, seen, result);
        }
    }

    private static IType[] resolve(String[] handles) {
        List<IType> result = new ArrayList<IType>();
        for (String handle : handles) {
            IJavaElement element = JavaCore.create(handle);
            if (element instanceof IType && element.exists()) {
                result.add((IType) element);
            }
        }
        return result.toArray(new IType[result.size()]);
    }

    private static final class CachedHierarchy {
        final String[] typeHandles;
        final long createdAt;
        CachedHierarchy(String[] typeHandles, long createdAt) {
            this.typeHandles = typeHandles;
            this.createdAt = createdAt;
        }
    }
}
