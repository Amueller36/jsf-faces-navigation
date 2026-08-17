package de.andre.jsfnavigation;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public final class WebSphereStatusLine {

    private WebSphereStatusLine() {
    }

    public static void show(final String message) {
        if (message == null) {
            return;
        }

        PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                IWorkbenchWindow window =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow();

                                if (window == null) {
                                    return;
                                }

                                IWorkbenchPage page =
                                        window.getActivePage();

                                if (page == null) {
                                    return;
                                }

                                IEditorPart editor =
                                        page.getActiveEditor();

                                if (editor != null
                                        && editor.getEditorSite() != null
                                        && editor.getEditorSite()
                                                .getActionBars() != null
                                        && editor.getEditorSite()
                                                .getActionBars()
                                                .getStatusLineManager() != null) {

                                    editor.getEditorSite()
                                            .getActionBars()
                                            .getStatusLineManager()
                                            .setMessage(message);
                                }
                            }
                        });
    }
}
