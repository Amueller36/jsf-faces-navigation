package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public final class BatchTestHelperGeneratorLauncher {

    private BatchTestHelperGeneratorLauncher() {
    }

    public static void open(
            final List<IMethod> methods) {

        if (methods == null
                || methods.isEmpty()) {

            return;
        }

        final List<IMethod> selected =
                new ArrayList<IMethod>();

        IType declaringType = null;

        for (IMethod method :
                methods) {

            if (method == null
                    || !method.exists()) {

                continue;
            }

            if (declaringType == null) {
                declaringType =
                        method.getDeclaringType();

            } else if (!declaringType.equals(
                    method.getDeclaringType())) {

                WebSphereStatusLine.show(
                        "Batch-Testgenerierung unterstützt aktuell Methoden aus genau einer Produktionsklasse gleichzeitig.");
                return;
            }

            selected.add(
                    method);
        }

        if (selected.isEmpty()) {
            return;
        }

        final String typeName =
                declaringType == null
                        ? "Tests"
                        : declaringType.getElementName();

        Job job =
                new Job(
                        "Generate test templates for "
                        + typeName) {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        final List<TestHelperAnalysis> analyses =
                                new ArrayList<TestHelperAnalysis>();

                        for (IMethod method :
                                selected) {

                            if (monitor.isCanceled()) {
                                return Status.CANCEL_STATUS;
                            }

                            analyses.add(
                                    TestHelperAnalyzer
                                            .analyze(
                                                    method,
                                                    monitor));
                        }

                        if (analyses.isEmpty()
                                || monitor.isCanceled()) {

                            return monitor.isCanceled()
                                    ? Status.CANCEL_STATUS
                                    : Status.OK_STATUS;
                        }

                        final TestHelperAnalysis first =
                                analyses.get(0);

                        final List<TestTargetCandidate> targets =
                                TestTargetFinder
                                        .find(
                                                first,
                                                monitor);

                        final TestHelperLearnedFixtureReport learnedFixtures =
                                TestHelperFixturePatternMiner
                                        .mine(
                                                analyses,
                                                targets,
                                                monitor);

                        final List<NewTestLocationCandidate> unitLocations =
                                NewTestLocationFinder
                                        .suggest(
                                                first,
                                                TestHelperSnippetGenerator.UNIT_TEST,
                                                monitor);

                        final List<NewTestLocationCandidate> jpaLocations =
                                NewTestLocationFinder
                                        .suggest(
                                                first,
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
                                                        ? display.getActiveShell()
                                                        : window.getShell();

                                        if (shell == null
                                                || shell.isDisposed()) {

                                            return;
                                        }

                                        new BatchTestHelperGeneratorDialog(
                                                shell,
                                                analyses,
                                                targets,
                                                unitLocations,
                                                jpaLocations,
                                                learnedFixtures)
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
