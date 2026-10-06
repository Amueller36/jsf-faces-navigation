package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;

public final class JavaNavigationTarget
        implements NavigationTarget {

    private final IMethod method;
    private final IFile file;
    private final int offset;
    private final String label;

    public JavaNavigationTarget(
            IMethod method,
            IFile file,
            int offset,
            String label) {

        this.method = method;
        this.file = file;
        this.offset = offset;
        this.label = label;
    }

    public static JavaNavigationTarget declaration(IMethod method) {
        int offset = 0;

        try {
            ISourceRange range = method.getNameRange();
            if (range != null) {
                offset = range.getOffset();
            }
        } catch (JavaModelException e) {
            // Fall back to JavaUI's element navigation.
        }

        IFile file = method.getResource() instanceof IFile
                ? (IFile) method.getResource()
                : null;

        return new JavaNavigationTarget(
                method,
                file,
                offset,
                labelFor(method));
    }

    public static JavaNavigationTarget callSite(
            IMethod containingMethod,
            IFile file,
            int offset) {

        String location = file == null
                ? ""
                : " — " + file.getProjectRelativePath().toPortableString();

        return new JavaNavigationTarget(
                containingMethod,
                file,
                offset,
                labelFor(containingMethod) + location);
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getIdentity() {
        return "java:"
                + method.getHandleIdentifier()
                + ":"
                + offset;
    }

    public IMethod getMethod() {
        return method;
    }

    @Override
    public void open() {
        if (file != null && file.exists()) {
            WebEditorOpener.open(file, offset);
        } else {
            JavaEditorOpener.open(method);
        }
    }

    private static String labelFor(IMethod method) {
        String typeName = method.getDeclaringType() == null
                ? ""
                : method.getDeclaringType().getElementName() + ".";

        return typeName + method.getElementName() + "(...)";
    }
}
