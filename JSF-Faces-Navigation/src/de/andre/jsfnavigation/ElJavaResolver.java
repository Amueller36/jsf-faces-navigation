package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class ElJavaResolver {

    private ElJavaResolver() {
    }

    public static IType resolveBean(
            String beanName,
            String projectName) {

        BeanIndexService index =
                Activator.getBeanIndexService();

        return index == null
                ? null
                : index.resolve(
                        beanName,
                        projectName);
    }

    public static JavaMemberTarget resolveChain(
            IType beanType,
            List<String> properties)
            throws JavaModelException {

        IType current = beanType;
        JavaMemberTarget target = null;

        for (int i = 0; i < properties.size(); i++) {
            target =
                    JavaPropertyResolver.resolve(
                            current,
                            properties.get(i));

            if (target == null) {
                return null;
            }

            if (i + 1 < properties.size()) {
                current =
                        JavaReturnTypeResolver.resolve(
                                current,
                                target);

                if (current == null) {
                    return null;
                }
            }
        }

        return target;
    }

    public static List<String> splitSimpleChain(
            String expression) {

        List<String> result =
                new ArrayList<String>();

        if (expression == null) {
            return result;
        }

        String[] parts =
                expression.trim().split("\\.");

        for (String part : parts) {
            String clean =
                    part.trim();

            int paren =
                    clean.indexOf('(');

            if (paren >= 0) {
                clean =
                        clean.substring(0, paren);
            }

            if (!isIdentifier(clean)) {
                result.clear();
                return result;
            }

            result.add(clean);
        }

        return result;
    }

    public static IMethod findMethod(
            IType type,
            String name)
            throws JavaModelException {

        IType current = type;

        while (current != null) {
            for (IMethod method :
                    current.getMethods()) {

                if (name.equals(
                        method.getElementName())) {

                    return method;
                }
            }

            String superclass =
                    current.getSuperclassName();

            if (superclass == null) {
                break;
            }

            String[][] resolved =
                    current.resolveType(superclass);

            if (resolved == null
                    || resolved.length == 0) {

                break;
            }

            String qn =
                    resolved[0][0]
                    + "."
                    + resolved[0][1];

            current =
                    current.getJavaProject()
                            .findType(qn);
        }

        return null;
    }

    private static boolean isIdentifier(
            String value) {

        if (value == null
                || value.isEmpty()
                || !Character.isJavaIdentifierStart(
                        value.charAt(0))) {

            return false;
        }

        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(
                    value.charAt(i))) {

                return false;
            }
        }

        return true;
    }
}
