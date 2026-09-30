package de.andre.jsfnavigation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class JavaPropertyResolver {

    private static final int MAX_MEMBER_CACHE_ENTRIES = 1024;

    private static final Map<String, JavaMemberTarget> MEMBER_CACHE =
            new LinkedHashMap<String, JavaMemberTarget>(256, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, JavaMemberTarget> eldest) {
                    return size() > MAX_MEMBER_CACHE_ENTRIES;
                }
            };

    private JavaPropertyResolver() {
    }

    public static JavaMemberTarget resolve(
            IType type,
            String property)
            throws JavaModelException {

        if (type == null
                || property == null
                || property.isEmpty()) {

            return null;
        }

        String cacheKey =
                type.getHandleIdentifier()
                + "#"
                + property;

        JavaMemberTarget cached;
        synchronized (MEMBER_CACHE) {
            cached = MEMBER_CACHE.get(cacheKey);
        }

        if (cached != null && cached.exists()) {
            return cached;
        }

        if (cached != null) {
            synchronized (MEMBER_CACHE) {
                MEMBER_CACHE.remove(cacheKey);
            }
        }

        JavaMemberTarget resolved =
                resolveUncached(type, property);

        if (resolved != null) {
            synchronized (MEMBER_CACHE) {
                MEMBER_CACHE.put(cacheKey, resolved);
            }
        }

        return resolved;
    }

    public static void clearCache() {
        synchronized (MEMBER_CACHE) {
            MEMBER_CACHE.clear();
        }
        JdtHierarchyCache.clear();
    }

    private static JavaMemberTarget resolveUncached(
            IType type,
            String property)
            throws JavaModelException {

        JavaMemberTarget direct =
                resolveOnType(type, property);

        if (direct != null) {
            return direct;
        }

        for (IType superType :
                JdtHierarchyCache.supertypes(type)) {

            JavaMemberTarget inherited =
                    resolveOnType(
                            superType,
                            property);

            if (inherited != null) {
                return inherited;
            }
        }

        return null;
    }

    private static JavaMemberTarget resolveOnType(
            IType type,
            String property)
            throws JavaModelException {

        String suffix =
                Character.toUpperCase(
                        property.charAt(0))
                + property.substring(1);

        String getter =
                "get" + suffix;

        String booleanGetter =
                "is" + suffix;

        for (IMethod method :
                type.getMethods()) {

            if (method.getNumberOfParameters() != 0) {
                continue;
            }

            if (getter.equals(
                    method.getElementName())
                    || booleanGetter.equals(
                            method.getElementName())) {

                return JavaMemberTarget
                        .forMethod(method);
            }
        }

        for (IField field :
                type.getFields()) {

            if (property.equals(
                    field.getElementName())) {

                return JavaMemberTarget
                        .forField(field);
            }
        }

        /*
         * Direct method fallback for common JSF/PrimeFaces attributes such as:
         * action="#{bean.save}"
         * listener="#{bean.changed}"
         * completeMethod="#{bean.completeUser}"
         */
        for (IMethod method :
                type.getMethods()) {

            if (property.equals(
                    method.getElementName())) {

                return JavaMemberTarget
                        .forMethod(method);
            }
        }

        return null;
    }
}
