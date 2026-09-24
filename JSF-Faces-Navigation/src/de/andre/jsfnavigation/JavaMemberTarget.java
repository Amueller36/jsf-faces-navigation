package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;

public final class JavaMemberTarget {

    private final IMethod method;
    private final IField field;

    private JavaMemberTarget(
            IMethod method,
            IField field) {

        this.method = method;
        this.field = field;
    }

    public static JavaMemberTarget forMethod(
            IMethod method) {

        return new JavaMemberTarget(
                method,
                null);
    }

    public static JavaMemberTarget forField(
            IField field) {

        return new JavaMemberTarget(
                null,
                field);
    }

    public IMethod getMethod() {
        return method;
    }

    public IField getField() {
        return field;
    }

    public IJavaElement getJavaElement() {
        return method != null ? method : field;
    }

    public boolean exists() {
        IJavaElement element = getJavaElement();
        return element != null && element.exists();
    }
}
