package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public final class MethodContext {

    private MethodContext() {
    }

    public static IMethod currentMethod() {
        ITextEditor editor = activeTextEditor();
        if (editor == null) {
            return null;
        }

        IEditorInput input = editor.getEditorInput();
        if (!(input instanceof IFileEditorInput)) {
            return null;
        }

        IFile file = ((IFileEditorInput) input).getFile();
        if (file == null || !"java".equalsIgnoreCase(file.getFileExtension())) {
            return null;
        }

        ISelection selection = editor.getSelectionProvider().getSelection();
        if (!(selection instanceof ITextSelection)) {
            return null;
        }

        int offset = ((ITextSelection) selection).getOffset();
        ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);

        if (unit == null || !unit.exists()) {
            return null;
        }

        try {
            IJavaElement element = unit.getElementAt(offset);

            if (element instanceof IMethod) {
                return (IMethod) element;
            }

            if (element != null) {
                IJavaElement ancestor =
                        element.getAncestor(IJavaElement.METHOD);

                if (ancestor instanceof IMethod) {
                    return (IMethod) ancestor;
                }
            }

        } catch (JavaModelException e) {
            return null;
        }

        return null;
    }

    public static ITextEditor activeTextEditor() {
        IWorkbenchWindow window =
                PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if (window == null) {
            return null;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            return null;
        }

        IEditorPart editor = page.getActiveEditor();
        return editor instanceof ITextEditor
                ? (ITextEditor) editor
                : null;
    }
}
