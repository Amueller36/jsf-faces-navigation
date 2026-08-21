package de.andre.jsfnavigation;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public final class FeatureTestAuditLauncher {

    private static String lastFeature = "";

    private FeatureTestAuditLauncher() {
    }

    public static void open(
            Shell shell) {

        if (shell == null
                || shell.isDisposed()) {

            return;
        }

        InputDialog input =
                new InputDialog(
                        shell,
                        "Feature Test Audit",
                        "Feature/class-name fragment (for example Postbuch):",
                        lastFeature,
                        null);

        if (input.open()
                != Window.OK) {

            return;
        }

        final String feature =
                input.getValue() == null
                        ? ""
                        : input.getValue()
                                .trim();

        if (feature.isEmpty()) {
            return;
        }

        lastFeature = feature;

        Job job =
                new Job(
                        "Audit tests for feature "
                        + feature) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final FeatureTestAuditReport report =
                                FeatureTestAuditService
                                        .scan(
                                                feature,
                                                monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        if (!PlatformUI
                                .isWorkbenchRunning()) {

                            return Status.OK_STATUS;
                        }

                        final Display display =
                                PlatformUI
                                        .getWorkbench()
                                        .getDisplay();

                        if (display == null
                                || display.isDisposed()) {

                            return Status.OK_STATUS;
                        }

                        display.asyncExec(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        Shell active =
                                                display
                                                        .getActiveShell();

                                        if (active == null
                                                || active.isDisposed()) {

                                            return;
                                        }

                                        new FeatureTestAuditDialog(
                                                active,
                                                report)
                                                .open();
                                    }
                                });

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();
    }
}
