package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.PlatformUI;

public final class JavaEditorOpener {

    private JavaEditorOpener() {
    }

    public static void open(JavaMemberTarget target) {
        if (target != null) {
            open(target.getJavaElement());
        }
    }

    public static void open(IType type) {
        open((IJavaElement) type);
    }

    public static void open(final IJavaElement element) {
        if (element == null) {
            return;
        }

        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    JavaUI.openInEditor(element, true, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
