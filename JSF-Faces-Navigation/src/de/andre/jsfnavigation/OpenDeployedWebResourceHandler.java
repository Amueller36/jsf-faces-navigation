package de.andre.jsfnavigation;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

public final class OpenDeployedWebResourceHandler
        extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        IFile source =
                EditorContext.currentFile();

        if (source == null) {
            return null;
        }

        if (!SmartDeploySettings.isEnabled()
                && WebSphereDeploymentChooser.ensureConfiguredRoot()
                        == null) {

            error(
                    "No deployed WebSphere web-module root is configured.");

            return null;
        }

        File target =
                WebSphereHotSyncPaths.resolveTarget(source);

        if (target == null || !target.isFile()) {
            error(
                    "The deployed copy does not exist yet:\n\n"
                    + (target == null
                            ? "(unresolved)"
                            : target.getAbsolutePath()));

            return null;
        }

        try {
            IWorkbenchWindow window =
                    PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow();

            IWorkbenchPage page =
                    window == null
                            ? null
                            : window.getActivePage();

            if (page == null) {
                return null;
            }

            IFileStore store =
                    EFS.getLocalFileSystem()
                            .fromLocalFile(target);

            IDE.openEditorOnFileStore(
                    page,
                    store);

        } catch (Exception e) {
            error(
                    "Could not open the deployed copy:\n\n"
                    + e.getMessage());
        }

        return null;
    }

    private static void error(
            final String message) {

        PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                        new Runnable() {
                            @Override
                            public void run() {
                                Shell shell =
                                        PlatformUI.getWorkbench()
                                                .getActiveWorkbenchWindow()
                                                .getShell();

                                MessageDialog.openError(
                                        shell,
                                        "Open WebSphere Deployed Copy",
                                        message);
                            }
                        });
    }
}
