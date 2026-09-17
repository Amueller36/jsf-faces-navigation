package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

public final class WebEditorOpener {

    private WebEditorOpener() {
    }

    public static void open(final IFile file, final int offset) {
        if (file == null || !file.exists()) {
            return;
        }

        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbenchPage page = PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow()
                            .getActivePage();

                    IEditorPart editor = IDE.openEditor(page, file);

                    if (editor instanceof ITextEditor) {
                        ((ITextEditor) editor).selectAndReveal(Math.max(0, offset), 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void revealInCurrentEditor(final int offset) {
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                IEditorPart editor = PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow()
                        .getActivePage()
                        .getActiveEditor();

                if (editor instanceof ITextEditor) {
                    ((ITextEditor) editor).selectAndReveal(Math.max(0, offset), 0);
                }
            }
        });
    }
}
