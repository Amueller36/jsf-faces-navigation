package de.andre.jsfnavigation;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public final class AddCurrentFileToFlowHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        final IFile file =
                EditorContext.currentFile();

        final FlowExplorerService service =
                Activator.getFlowExplorerService();

        if (file == null
                || service == null) {

            return null;
        }

        /*
         * Classification/persistence may touch JDT or disk. Keep the explicit
         * Add action off the SWT thread for the same reason as auto-capture.
         */
        Job job =
                new Job(
                        "Add file to JSF Flow") {
                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        if (monitor.isCanceled()
                                || !file.exists()) {

                            return Status.CANCEL_STATUS;
                        }

                        service.addFile(file);
                        FlowExplorerView.refreshIfOpen();

                        if (service.isAutoTestDiscovery()) {
                            FlowRelatedTestDiscoveryService
                                    .discoverForFlowFile(file);
                        }

                        WebSphereStatusLine.show(
                                "Added "
                                + file.getName()
                                + " to flow '"
                                + service.getCurrentFlowName()
                                + "'.");

                        return Status.OK_STATUS;
                    }
                };

        job.setSystem(true);
        job.setPriority(Job.DECORATE);
        job.schedule();

        return null;
    }
}
