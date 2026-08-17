package de.andre.jsfnavigation;

import java.beans.Introspector;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class JSFBeanResolver {

    private JSFBeanResolver() {
    }

    public static IType resolve(String beanName) {
        if (beanName == null || beanName.isEmpty()) {
            return null;
        }

        String expectedClassName =
                Character.toUpperCase(beanName.charAt(0)) + beanName.substring(1);

        for (IType type : JavaTypeFinder.findTypes(expectedClassName)) {
            if (matchesBeanName(type, beanName)) {
                return type;
            }
        }

        // Handles @Named("somethingDifferent") and
        // @ManagedBean(name = "somethingDifferent").
        for (IType type : JavaTypeFinder.findAllSourceTypes()) {
            if (matchesBeanName(type, beanName)) {
                return type;
            }
        }

        return null;
    }

    private static boolean matchesBeanName(IType type, String requestedName) {
        try {
            boolean beanAnnotationFound = false;

            for (IAnnotation annotation : type.getAnnotations()) {
                String annotationName = annotation.getElementName();

                if (!isBeanAnnotation(annotationName)) {
                    continue;
                }

                beanAnnotationFound = true;
                String explicitName = getExplicitName(annotation);

                if (explicitName != null && !explicitName.isEmpty()) {
                    return requestedName.equals(explicitName);
                }
            }

            if (!beanAnnotationFound) {
                return false;
            }

            return requestedName.equals(Introspector.decapitalize(type.getElementName()));

        } catch (JavaModelException e) {
            return false;
        }
    }

    private static boolean isBeanAnnotation(String annotationName) {
        return "ManagedBean".equals(annotationName)
                || "Named".equals(annotationName)
                || annotationName.endsWith(".ManagedBean")
                || annotationName.endsWith(".Named");
    }

    private static String getExplicitName(IAnnotation annotation)
            throws JavaModelException {

        for (IMemberValuePair pair : annotation.getMemberValuePairs()) {
            String memberName = pair.getMemberName();

            if ("value".equals(memberName) || "name".equals(memberName)) {
                Object value = pair.getValue();

                if (value instanceof String) {
                    return (String) value;
                }
            }
        }

        return null;
    }
}
