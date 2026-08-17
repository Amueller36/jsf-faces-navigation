package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

public final class JavaPropertyResolver {

    private JavaPropertyResolver() {
    }

    public static JavaMemberTarget resolve(IType type, String property)
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

    private static JavaMemberTarget resolveOnType(IType type, String property)
            throws JavaModelException {

        String suffix =
                Character.toUpperCase(property.charAt(0)) + property.substring(1);

        String getter = "get" + suffix;
        String booleanGetter = "is" + suffix;

        for (IMethod method : type.getMethods()) {
            if (method.getNumberOfParameters() != 0) {
                continue;
            }

            if (getter.equals(method.getElementName())
                    || booleanGetter.equals(method.getElementName())) {
                return JavaMemberTarget.forMethod(method);
            }
        }

        for (IField field : type.getFields()) {
            if (property.equals(field.getElementName())) {
                return JavaMemberTarget.forField(field);
            }
        }

        // Useful for component attributes such as completeMethod/action/listener.
        for (IMethod method : type.getMethods()) {
            if (property.equals(method.getElementName())) {
                return JavaMemberTarget.forMethod(method);
            }
        }

        return null;
    }
}
