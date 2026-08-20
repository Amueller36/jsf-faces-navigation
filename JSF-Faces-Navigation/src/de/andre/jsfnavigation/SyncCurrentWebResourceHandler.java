package de.andre.jsfnavigation;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class SyncCurrentWebResourceHandler
        extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event)
            throws ExecutionException {

        final IFile source =
                EditorContext.currentFile();

        if (source == null) {
            return null;
        }

        if (WebSphereHotSyncPaths.relativeWebPath(source)
                == null) {

            showError(
                    "The current file is not below the configured/source-detected web root.");

            return null;
        }

        if (!WebSphereHotSyncPaths.isSyncable(source)) {
            showError(
                    "The current file type is not enabled for WebSphere hot sync.");

            return null;
        }

        /*
         * syncNow() performs path-based WAR discovery itself. Do not run the
         * legacy generic deployment chooser here.
         */

        final WebSphereHotSyncService service =
                Activator.getWebSphereHotSyncService();

        if (service == null) {
            return null;
        }

        Job job =
                new Job("Sync current resource to WebSphere") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        try {
                            File target =
                                    service.syncNow(source);

                            WebSphereStatusLine.show(
                                    "Synced "
                                    + source.getName()
                                    + " -> "
                                    + target.getAbsolutePath());

                            return Status.OK_STATUS;

                        } catch (IOException e) {
                            showError(
                                    "WebSphere hot sync failed:\n\n"
                                    + e.getMessage());

                            return new Status(
                                    IStatus.ERROR,
                                    Activator.PLUGIN_ID,
                                    e.getMessage(),
                                    e);
                        }
                    }
                };

        job.setUser(false);
        job.schedule();

        return null;
    }

    private static void showError(
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
                                        "WebSphere Hot Sync",
                                        message);
                            }
                        });
    }
}
