package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public final class JavaReturnTypeResolver {

    private JavaReturnTypeResolver() {
    }

    public static IType resolve(IType declaringType, JavaMemberTarget target) {
        if (declaringType == null || target == null) {
            return null;
        }

        try {
            String signature;

            if (target.getMethod() != null) {
                IMethod method = target.getMethod();
                signature = method.getReturnType();
                declaringType = method.getDeclaringType();
            } else {
                IField field = target.getField();
                signature = field.getTypeSignature();
                declaringType = field.getDeclaringType();
            }

            String readable = Signature.toString(signature);

            int generic = readable.indexOf('<');
            if (generic >= 0) {
                readable = readable.substring(0, generic);
            }

            int array = readable.indexOf('[');
            if (array >= 0) {
                readable = readable.substring(0, array);
            }

            String[][] resolved = declaringType.resolveType(readable);

            if (resolved != null && resolved.length > 0) {
                String packageName = resolved[0][0];
                String className = resolved[0][1];

                String qualified =
                        packageName == null || packageName.isEmpty()
                                ? className
                                : packageName + "." + className;

                IType resolvedType = declaringType.getJavaProject().findType(qualified);
                if (resolvedType != null) {
                    return resolvedType;
                }
            }

            return JavaTypeFinder.findFirstType(simpleName(readable));

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot >= 0 ? typeName.substring(dot + 1) : typeName;
    }
}
