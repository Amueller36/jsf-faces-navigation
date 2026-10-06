package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

public final class JpaTypeResolver {

    private JpaTypeResolver() {
    }

    public static IType resolvePropertyType(
            IType declaringType,
            JavaMemberTarget target) {

        if (declaringType == null || target == null) {
            return null;
        }

        try {
            String signature;

            if (target.getField() != null) {
                IField field = target.getField();
                signature = field.getTypeSignature();
                declaringType = field.getDeclaringType();
            } else {
                IMethod method = target.getMethod();
                signature = method.getReturnType();
                declaringType = method.getDeclaringType();
            }

            String readable = Signature.toString(signature);
            String candidate = collectionElementOrType(readable);

            while (candidate.endsWith("[]")) {
                candidate = candidate.substring(0, candidate.length() - 2);
            }

            String[][] resolved = declaringType.resolveType(candidate);

            if (resolved != null && resolved.length > 0) {
                String packageName = resolved[0][0];
                String className = resolved[0][1];
                String qualified = packageName == null || packageName.isEmpty()
                        ? className
                        : packageName + "." + className;

                IType type = declaringType.getJavaProject().findType(qualified);
                if (type != null && type.exists()) {
                    return type;
                }
            }

            List<IType> matches = JavaTypeFinder.findTypes(
                    simpleName(candidate),
                    declaringType.getJavaProject().getElementName());

            return matches.isEmpty() ? null : matches.get(0);

        } catch (JavaModelException e) {
            return null;
        }
    }

    public static String readableType(JavaMemberTarget target) {
        try {
            if (target.getField() != null) {
                return Signature.toString(target.getField().getTypeSignature());
            }

            if (target.getMethod() != null) {
                return Signature.toString(target.getMethod().getReturnType());
            }
        } catch (JavaModelException e) {
            return "?";
        }

        return "?";
    }

    private static String collectionElementOrType(String readable) {
        int lt = readable.indexOf('<');
        int gt = readable.lastIndexOf('>');

        if (lt > 0 && gt > lt) {
            String raw = readable.substring(0, lt);
            String rawSimple = simpleName(raw);

            if ("Collection".equals(rawSimple)
                    || "List".equals(rawSimple)
                    || "Set".equals(rawSimple)
                    || "Iterable".equals(rawSimple)) {

                String generic = readable.substring(lt + 1, gt).trim();
                int comma = generic.indexOf(',');
                if (comma >= 0) {
                    generic = generic.substring(0, comma).trim();
                }

                if (generic.startsWith("? extends ")) {
                    generic = generic.substring("? extends ".length()).trim();
                } else if (generic.startsWith("? super ")) {
                    generic = generic.substring("? super ".length()).trim();
                }

                return generic;
            }
        }

        return lt >= 0 ? readable.substring(0, lt) : readable;
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
