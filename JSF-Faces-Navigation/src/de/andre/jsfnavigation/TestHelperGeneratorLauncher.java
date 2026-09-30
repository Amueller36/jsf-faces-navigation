package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public final class TestHelperGeneratorLauncher {

    private TestHelperGeneratorLauncher() {
    }

    public static void open(
            final IMethod method) {

        if (method == null
                || !method.exists()) {

            WebSphereStatusLine.show(
                    "Could not resolve the production method for test-helper generation.");

            return;
        }

        Job job =
                new Job(
                        "Generate test helper for "
                        + method.getElementName()) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final TestHelperAnalysis analysis =
                                TestHelperAnalyzer
                                        .analyze(
                                                method,
                                                monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        final List<TestTargetCandidate> targets =
                                TestTargetFinder
                                        .find(
                                                analysis,
                                                monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        final List<NewTestLocationCandidate> unitLocations =
                                NewTestLocationFinder
                                        .suggest(
                                                analysis,
                                                TestHelperSnippetGenerator.UNIT_TEST,
                                                monitor);

                        final List<NewTestLocationCandidate> jpaLocations =
                                NewTestLocationFinder
                                        .suggest(
                                                analysis,
                                                TestHelperSnippetGenerator.JPA_TEST,
                                                monitor);

                        if (monitor.isCanceled()
                                || !PlatformUI
                                        .isWorkbenchRunning()) {

                            return monitor.isCanceled()
                                    ? Status.CANCEL_STATUS
                                    : Status.OK_STATUS;
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

                                        IWorkbenchWindow window =
                                                PlatformUI
                                                        .getWorkbench()
                                                        .getActiveWorkbenchWindow();

                                        Shell shell =
                                                window == null
                                                        ? display
                                                                .getActiveShell()
                                                        : window
                                                                .getShell();

                                        if (shell == null
                                                || shell.isDisposed()) {

                                            return;
                                        }

                                        new TestHelperGeneratorDialog(
                                                shell,
                                                analysis,
                                                targets,
                                                unitLocations,
                                                jpaLocations)
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
