package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

public final class GenerateTestHelperHandler
        extends AbstractHandler {

    @Override
    public Object execute(
            ExecutionEvent event)
            throws ExecutionException {

        final IMethod method =
                selectedMethod(
                        event);

        if (method == null
                || !method.exists()) {

            WebSphereStatusLine.show(
                    "Place the caret in a Java method (or select a method in Outline/Package Explorer) before generating a test helper.");

            return null;
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

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        final List<NewTestLocationCandidate> jpaLocations =
                                NewTestLocationFinder
                                        .suggest(
                                                analysis,
                                                TestHelperSnippetGenerator.JPA_TEST,
                                                monitor);

                        if (monitor.isCanceled()) {
                            return Status.CANCEL_STATUS;
                        }

                        if (!PlatformUI
                                .isWorkbenchRunning()) {

                            return Status.OK_STATUS;
                        }

                        Display display =
                                PlatformUI.getWorkbench()
                                        .getDisplay();

                        if (display == null
                                || display.isDisposed()) {

                            return Status.OK_STATUS;
                        }

                        display.asyncExec(
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

                                        TestHelperGeneratorDialog dialog =
                                                new TestHelperGeneratorDialog(
                                                        shell,
                                                        analysis,
                                                        targets,
                                                        unitLocations,
                                                        jpaLocations);

                                        dialog.open();
                                    }
                                });

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();

        return null;
    }

    private static IMethod selectedMethod(
            ExecutionEvent event) {

        ISelection selection =
                HandlerUtil
                        .getCurrentSelection(
                                event);

        if (selection
                instanceof IStructuredSelection) {

            Object first =
                    ((IStructuredSelection)
                            selection)
                            .getFirstElement();

            if (first instanceof IMethod) {
                return (IMethod)
                        first;
            }

            if (first
                    instanceof IJavaElement) {

                IJavaElement method =
                        ((IJavaElement) first)
                                .getAncestor(
                                        IJavaElement.METHOD);

                if (method
                        instanceof IMethod) {

                    return (IMethod)
                            method;
                }
            }
        }

        return MethodContext
                .currentMethod();
    }
}
