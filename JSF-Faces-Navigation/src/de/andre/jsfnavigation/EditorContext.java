package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public final class EditorContext {

    private EditorContext() {
    }

    public static IFile currentFile() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return null;
            }

            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return null;
            }

            IEditorPart editor = page.getActiveEditor();
            if (editor == null) {
                return null;
            }

            IEditorInput input = editor.getEditorInput();
            if (!(input instanceof IFileEditorInput)) {
                return null;
            }

            return ((IFileEditorInput) input).getFile();

        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String currentProjectName() {
        IFile file = currentFile();
        return file == null ? null : file.getProject().getName();
    }
}
