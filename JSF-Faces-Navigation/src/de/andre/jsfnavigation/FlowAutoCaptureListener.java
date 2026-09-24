package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;

/**
 * Keeps the Flow Explorer in sync with the active editor without putting any
 * JDT/workspace scanning on Eclipse's editor-activation/UI path.
 */
public final class FlowAutoCaptureListener
        implements IPartListener2 {

    /**
     * Give Eclipse a short quiet period to finish opening/reconciling the
     * editor. Rapid tab switching therefore collapses into one background
     * Flow update for the file the user actually stayed on.
     */
    private static final long FLOW_IDLE_DELAY_MS = 850L;

    private final Object jobLock = new Object();

    private volatile long activationGeneration;
    private Job pendingFlowJob;

    @Override
    public void partActivated(
            IWorkbenchPartReference partRef) {

        IFile file = editorFile(partRef);

        if (file == null) {
            return;
        }

        /*
         * Highlighting is deliberately the only immediate action. It does not
         * inspect Java types, search the workspace or persist Flow state.
         */
        FlowExplorerView.activeEditorChanged(file);

        FlowExplorerService service =
                Activator.getFlowExplorerService();

        if (service != null
                && service.isAutoCapture()) {

            scheduleIdleFlowWork(file);
        }
    }

    @Override
    public void partOpened(
            IWorkbenchPartReference partRef) {

        /*
         * An opened editor is normally activated immediately afterwards.
         * Doing work here as well used to schedule duplicate capture jobs.
         */
    }

    private IFile editorFile(
            IWorkbenchPartReference partRef) {

        if (partRef == null) {
            return null;
        }

        Object part = partRef.getPart(false);

        if (!(part instanceof IEditorPart)) {
            return null;
        }

        IEditorInput input =
                ((IEditorPart) part)
                        .getEditorInput();

        if (!(input instanceof IFileEditorInput)) {
            return null;
        }

        IFile file =
                ((IFileEditorInput) input)
                        .getFile();

        return file != null && file.exists()
                ? file
                : null;
    }

    private void scheduleIdleFlowWork(
            final IFile file) {

        final long generation =
                ++activationGeneration;

        synchronized (jobLock) {
            if (pendingFlowJob != null) {
                pendingFlowJob.cancel();
            }

            pendingFlowJob =
                    new Job(
                            "Update JSF Flow after editor switch") {
                        @Override
                        protected IStatus run(
                                IProgressMonitor monitor) {

                            if (monitor.isCanceled()
                                    || generation
                                            != activationGeneration
                                    || file == null
                                    || !file.exists()) {

                                return Status.CANCEL_STATUS;
                            }

                            FlowExplorerService service =
                                    Activator
                                            .getFlowExplorerService();

                            if (service == null
                                    || !service.isAutoCapture()) {

                                return Status.OK_STATUS;
                            }

                            /*
                             * containsFile/addFile may classify Java source and
                             * persist state. This is intentionally background
                             * work and only happens after the editor has been
                             * stable for FLOW_IDLE_DELAY_MS.
                             */
                            boolean wasPresent =
                                    service.containsFile(file);

                            if (monitor.isCanceled()
                                    || generation
                                            != activationGeneration) {

                                return Status.CANCEL_STATUS;
                            }

                            /*
                             * Existing Flow entries need no work at all on an
                             * editor switch. Reclassification has its own
                             * background maintenance path; doing addFile()
                             * here used to re-run Java classification simply
                             * because a tab became active.
                             */
                            if (wasPresent) {
                                return Status.OK_STATUS;
                            }

                            service.addFile(file);
                            FlowExplorerView.refreshIfOpen();

                            /*
                             * Test discovery is workspace-search heavy.
                             * Run it only for a newly captured production
                             * file, never for every tab activation.
                             */
                            if (service.isAutoTestDiscovery()) {
                                FlowRelatedTestDiscoveryService
                                        .discoverForFlowFile(file);
                            }

                            return Status.OK_STATUS;
                        }
                    };

            pendingFlowJob.setSystem(true);
            pendingFlowJob.setPriority(Job.DECORATE);
            pendingFlowJob.setRule(PluginBackgroundSchedulingRule.INSTANCE);
            pendingFlowJob.schedule(
                    FLOW_IDLE_DELAY_MS);
        }
    }


    public void stop() {
        activationGeneration++;

        synchronized (jobLock) {
            if (pendingFlowJob != null) {
                pendingFlowJob.cancel();
                pendingFlowJob = null;
            }
        }
    }

    @Override
    public void partBroughtToTop(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partClosed(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partDeactivated(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partHidden(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partInputChanged(
            IWorkbenchPartReference partRef) {
    }

    @Override
    public void partVisible(
            IWorkbenchPartReference partRef) {
    }
}
