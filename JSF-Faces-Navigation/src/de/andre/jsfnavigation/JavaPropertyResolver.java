package de.andre.jsfnavigation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

public final class JavaPropertyResolver {

    private static final Map<String, JavaMemberTarget> MEMBER_CACHE =
            new ConcurrentHashMap<String, JavaMemberTarget>();

    private static final Map<String, ITypeHierarchy> HIERARCHY_CACHE =
            new ConcurrentHashMap<String, ITypeHierarchy>();

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

        JavaMemberTarget cached =
                MEMBER_CACHE.get(cacheKey);

        if (cached != null && cached.exists()) {
            return cached;
        }

        if (cached != null) {
            MEMBER_CACHE.remove(cacheKey, cached);
        }

        JavaMemberTarget resolved =
                resolveUncached(type, property);

        if (resolved != null) {
            MEMBER_CACHE.put(cacheKey, resolved);
        }

        return resolved;
    }

    public static void clearCache() {
        MEMBER_CACHE.clear();
        HIERARCHY_CACHE.clear();
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

        ITypeHierarchy hierarchy =
                hierarchyFor(type);

        for (IType superType :
                hierarchy.getAllSupertypes(type)) {

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

    private static ITypeHierarchy hierarchyFor(
            IType type)
            throws JavaModelException {

        String key =
                type.getHandleIdentifier();

        ITypeHierarchy hierarchy =
                HIERARCHY_CACHE.get(key);

        if (hierarchy != null) {
            return hierarchy;
        }

        hierarchy =
                type.newSupertypeHierarchy(null);

        HIERARCHY_CACHE.put(key, hierarchy);

        return hierarchy;
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
