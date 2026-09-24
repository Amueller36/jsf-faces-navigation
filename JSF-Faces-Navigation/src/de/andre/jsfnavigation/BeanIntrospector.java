package de.andre.jsfnavigation;

import java.beans.Introspector;

import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class BeanIntrospector {

    private BeanIntrospector() {
    }

    public static String beanNameOf(IType type) throws JavaModelException {
        boolean beanAnnotationFound = false;

        for (IAnnotation annotation : type.getAnnotations()) {
            String annotationName = annotation.getElementName();

            if (!isBeanAnnotation(annotationName)) {
                continue;
            }

            beanAnnotationFound = true;

            String explicitName = explicitNameOf(annotation);
            if (explicitName != null && !explicitName.isEmpty()) {
                return explicitName;
            }
        }

        if (!beanAnnotationFound) {
            return null;
        }

        return Introspector.decapitalize(type.getElementName());
    }

    private static boolean isBeanAnnotation(String annotationName) {
        return "ManagedBean".equals(annotationName)
                || "Named".equals(annotationName)
                || annotationName.endsWith(".ManagedBean")
                || annotationName.endsWith(".Named");
    }

    private static String explicitNameOf(IAnnotation annotation)
            throws JavaModelException {

        for (IMemberValuePair pair : annotation.getMemberValuePairs()) {
            String memberName = pair.getMemberName();

            if ("value".equals(memberName)
                    || "name".equals(memberName)) {

                Object value = pair.getValue();

                if (value instanceof String) {
                    return (String) value;
                }
            }
        }

        return null;
    }
}
