package de.andre.jsfnavigation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public final class JavaReturnTypeResolver {

    private static final Map<String, IType> CACHE =
            new ConcurrentHashMap<String, IType>();

    private JavaReturnTypeResolver() {
    }

    public static IType resolve(
            IType declaringType,
            JavaMemberTarget target) {

        if (declaringType == null || target == null) {
            return null;
        }

        String cacheKey =
                target.getJavaElement()
                        .getHandleIdentifier();

        IType cached = CACHE.get(cacheKey);

        if (cached != null && cached.exists()) {
            return cached;
        }

        if (cached != null) {
            CACHE.remove(cacheKey, cached);
        }

        IType resolved =
                resolveUncached(
                        declaringType,
                        target);

        if (resolved != null) {
            CACHE.put(cacheKey, resolved);
        }

        return resolved;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static IType resolveUncached(
            IType declaringType,
            JavaMemberTarget target) {

        try {
            String signature;

            if (target.getMethod() != null) {
                IMethod method =
                        target.getMethod();

                signature =
                        method.getReturnType();

                declaringType =
                        method.getDeclaringType();

            } else {
                IField field =
                        target.getField();

                signature =
                        field.getTypeSignature();

                declaringType =
                        field.getDeclaringType();
            }

            String readable =
                    Signature.toString(signature);

            int generic =
                    readable.indexOf('<');

            if (generic >= 0) {
                readable =
                        readable.substring(
                                0,
                                generic);
            }

            while (readable.endsWith("[]")) {
                readable =
                        readable.substring(
                                0,
                                readable.length() - 2);
            }

            String[][] resolved =
                    declaringType.resolveType(
                            readable);

            if (resolved != null
                    && resolved.length > 0) {

                String packageName =
                        resolved[0][0];

                String className =
                        resolved[0][1];

                String qualified =
                        packageName == null
                                || packageName.isEmpty()
                                ? className
                                : packageName
                                        + "."
                                        + className;

                IType resolvedType =
                        declaringType
                                .getJavaProject()
                                .findType(qualified);

                if (resolvedType != null
                        && resolvedType.exists()) {

                    return resolvedType;
                }
            }

            /*
             * Fallback for unusual unresolved signatures. Prefer the current
             * project before widening to the workspace.
             */
            return first(
                    JavaTypeFinder.findTypes(
                            simpleName(readable),
                            declaringType
                                    .getJavaProject()
                                    .getElementName()));

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static IType first(
            java.util.List<IType> types) {

        return types == null || types.isEmpty()
                ? null
                : types.get(0);
    }

    private static String simpleName(
            String typeName) {

        int dot =
                typeName.lastIndexOf('.');

        return dot >= 0
                ? typeName.substring(dot + 1)
                : typeName;
    }
}
