package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public final class JsfComponentHelpHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        ITextEditor editor =
                MethodContext.activeTextEditor();

        if (editor == null) {
            return null;
        }

        ISelection selection =
                editor.getSelectionProvider()
                        .getSelection();

        if (!(selection
                instanceof ITextSelection)) {

            return null;
        }

        final int offset =
                ((ITextSelection) selection)
                        .getOffset();

        final IDocument document =
                editor.getDocumentProvider()
                        .getDocument(
                                editor.getEditorInput());

        if (document == null) {
            return null;
        }

        final JsfHelpContext context =
                JsfHelpContext.detect(
                        document,
                        offset);

        if (context == null) {
            WebSphereStatusLine.show(
                    "Put the caret on a JSF/PrimeFaces/RichFaces tag or attribute.");

            return null;
        }

        final IFile file =
                EditorContext.currentFile();

        Job job =
                new Job(
                        "Load JSF component help") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final JsfComponentHelp help;

                        try {
                            help =
                                    JsfTaglibCatalogService
                                            .help(
                                                    file,
                                                    document,
                                                    context);

                        } catch (RuntimeException e) {
                            WebSphereStatusLine.show(
                                    "JSF component help failed: "
                                    + safeMessage(e));

                            return Status.OK_STATUS;
                        }

                        if (help == null) {
                            WebSphereStatusLine.show(
                                    "No JSF component help found.");

                            return Status.OK_STATUS;
                        }

                        PlatformUI.getWorkbench()
                                .getDisplay()
                                .asyncExec(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (!PlatformUI
                                                        .isWorkbenchRunning()) {

                                                    return;
                                                }

                                                IWorkbenchWindow window =
                                                        PlatformUI
                                                                .getWorkbench()
                                                                .getActiveWorkbenchWindow();

                                                if (window == null) {
                                                    return;
                                                }

                                                Shell shell =
                                                        window.getShell();

                                                JsfComponentHelpDialog dialog =
                                                        new JsfComponentHelpDialog(
                                                                shell,
                                                                help);

                                                dialog.open();
                                            }
                                        });

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.schedule();

        return null;
    }

    private static String safeMessage(
            Throwable error) {

        if (error == null) {
            return "unknown error";
        }

        String message =
                error.getMessage();

        return message == null
                || message.trim().isEmpty()
                        ? error.getClass()
                                .getSimpleName()
                        : message;
    }


}
