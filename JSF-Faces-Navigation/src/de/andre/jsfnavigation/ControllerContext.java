package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class ControllerContext {

    private ControllerContext() {
    }

    public static String beanNameAt(IFile javaFile, int offset) {
        if (javaFile == null || !"java".equalsIgnoreCase(javaFile.getFileExtension())) {
            return null;
        }

        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(javaFile);
        if (unit == null || !unit.exists()) {
            return null;
        }

        try {
            IJavaElement element = unit.getElementAt(offset);
            IType type = null;

            if (element instanceof IType) {
                type = (IType) element;
            } else if (element != null) {
                IJavaElement ancestor = element.getAncestor(IJavaElement.TYPE);
                if (ancestor instanceof IType) {
                    type = (IType) ancestor;
                }
            }

            if (type == null) {
                IType[] types = unit.getTypes();
                if (types.length > 0) {
                    type = types[0];
                }
            }

            return type == null ? null : BeanIntrospector.beanNameOf(type);

        } catch (JavaModelException e) {
            return null;
        }
    }
}
