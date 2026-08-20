package de.andre.jsfnavigation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class FlowJavaSemantics {

    private static final int MAX_CACHE_ENTRIES = 512;

    private static final Map<String, CachedSemantics>
            CACHE =
                    new LinkedHashMap<String, CachedSemantics>(
                            128,
                            0.75f,
                            true) {

                        private static final long serialVersionUID =
                                1L;

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, CachedSemantics>
                                        eldest) {

                            return size()
                                    > MAX_CACHE_ENTRIES;
                        }
                    };

    private FlowJavaSemantics() {
    }

    public static boolean isEntity(
            IFile file) {

        return semantics(file).entity;
    }

    public static boolean isManagedBean(
            IFile file) {

        return semantics(file).managedBean;
    }

    public static boolean isService(
            IFile file) {

        return semantics(file).service;
    }

    public static boolean isRepository(
            IFile file) {

        return semantics(file).repository;
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static Semantics semantics(
            IFile file) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return Semantics.EMPTY;
        }

        String path =
                file.getFullPath()
                        .toPortableString();

        long stamp =
                file.getModificationStamp();

        synchronized (CACHE) {
            CachedSemantics cached =
                    CACHE.get(path);

            if (cached != null
                    && cached.stamp == stamp) {

                return cached.semantics;
            }
        }

        Semantics computed =
                inspect(file);

        synchronized (CACHE) {
            CACHE.put(
                    path,
                    new CachedSemantics(
                            stamp,
                            computed));
        }

        return computed;
    }

    private static Semantics inspect(
            IFile file) {

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return Semantics.EMPTY;
        }

        boolean entity = false;
        boolean managedBean = false;
        boolean service = false;
        boolean repository = false;

        try {
            for (IType type :
                    unit.getAllTypes()) {

                for (IAnnotation annotation :
                        type.getAnnotations()) {

                    String name =
                            simpleName(
                                    annotation
                                            .getElementName());

                    if ("Entity".equals(name)
                            || "Embeddable".equals(
                                    name)
                            || "MappedSuperclass".equals(
                                    name)) {

                        entity = true;
                    }

                    if ("ManagedBean".equals(name)
                            || "Named".equals(name)) {

                        managedBean = true;
                    }

                    if ("Service".equals(name)) {
                        service = true;
                    }

                    if ("Repository".equals(name)) {
                        repository = true;
                    }
                }
            }

        } catch (JavaModelException e) {
            return Semantics.EMPTY;
        }

        return new Semantics(
                entity,
                managedBean,
                service,
                repository);
    }

    private static String simpleName(
            String name) {

        if (name == null) {
            return "";
        }

        int dot =
                name.lastIndexOf('.');

        return dot >= 0
                ? name.substring(
                        dot + 1)
                : name;
    }

    private static final class CachedSemantics {

        final long stamp;
        final Semantics semantics;

        CachedSemantics(
                long stamp,
                Semantics semantics) {

            this.stamp = stamp;
            this.semantics = semantics;
        }
    }

    private static final class Semantics {

        static final Semantics EMPTY =
                new Semantics(
                        false,
                        false,
                        false,
                        false);

        final boolean entity;
        final boolean managedBean;
        final boolean service;
        final boolean repository;

        Semantics(
                boolean entity,
                boolean managedBean,
                boolean service,
                boolean repository) {

            this.entity = entity;
            this.managedBean = managedBean;
            this.service = service;
            this.repository = repository;
        }
    }
}
