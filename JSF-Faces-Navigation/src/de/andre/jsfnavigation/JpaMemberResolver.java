package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

public final class JpaMemberResolver {

    private JpaMemberResolver() {
    }

    public static JavaMemberTarget resolve(
            IType type,
            String property)
            throws JavaModelException {

        if (type == null || property == null || property.isEmpty()) {
            return null;
        }

        JavaMemberTarget direct = resolveOnType(type, property);
        if (direct != null) {
            return direct;
        }

        ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);

        for (IType superType : hierarchy.getAllSupertypes(type)) {
            JavaMemberTarget inherited = resolveOnType(superType, property);
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

        IField field = type.getField(property);
        if (field != null && field.exists() && hasJpaMapping(field.getAnnotations())) {
            return JavaMemberTarget.forField(field);
        }

        String suffix = Character.toUpperCase(property.charAt(0))
                + property.substring(1);

        String getterName = "get" + suffix;
        String booleanGetterName = "is" + suffix;

        for (IMethod method : type.getMethods()) {
            if (method.getNumberOfParameters() != 0) {
                continue;
            }

            if ((getterName.equals(method.getElementName())
                    || booleanGetterName.equals(method.getElementName()))
                    && hasJpaMapping(method.getAnnotations())) {
                return JavaMemberTarget.forMethod(method);
            }
        }

        if (field != null && field.exists()) {
            return JavaMemberTarget.forField(field);
        }

        for (IMethod method : type.getMethods()) {
            if (method.getNumberOfParameters() == 0
                    && (getterName.equals(method.getElementName())
                            || booleanGetterName.equals(method.getElementName()))) {
                return JavaMemberTarget.forMethod(method);
            }
        }

        return null;
    }

    private static boolean hasJpaMapping(IAnnotation[] annotations) {
        if (annotations == null) {
            return false;
        }

        for (IAnnotation annotation : annotations) {
            String name = simpleName(annotation.getElementName());

            if ("Column".equals(name)
                    || "JoinColumn".equals(name)
                    || "JoinColumns".equals(name)
                    || "JoinTable".equals(name)
                    || "OneToOne".equals(name)
                    || "OneToMany".equals(name)
                    || "ManyToOne".equals(name)
                    || "ManyToMany".equals(name)
                    || "Enumerated".equals(name)
                    || "Embedded".equals(name)
                    || "EmbeddedId".equals(name)
                    || "Id".equals(name)
                    || "Basic".equals(name)
                    || "ElementCollection".equals(name)
                    || "Transient".equals(name)) {
                return true;
            }
        }

        return false;
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
