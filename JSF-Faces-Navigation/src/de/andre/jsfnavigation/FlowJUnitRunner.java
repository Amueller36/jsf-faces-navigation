package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

public final class FlowJUnitRunner {

    private static final String JUNIT_LAUNCH_TYPE =
            "org.eclipse.jdt.junit.launchconfig";

    private static final String ATTR_TEST_NAME =
            "org.eclipse.jdt.junit.TESTNAME";

    private static final String ATTR_TEST_KIND =
            "org.eclipse.jdt.junit.TEST_KIND";

    private FlowJUnitRunner() {
    }

    public static void runCurrentFlowUnitTests() {
        Job job =
                new Job(
                        "Run current-flow unit tests") {

                    @Override
                    protected IStatus run(
                            IProgressMonitor monitor) {

                        FlowExplorerService service =
                                Activator.getFlowExplorerService();

                        if (service == null) {
                            show(
                                    "Flow Explorer is not available.");
                            return Status.OK_STATUS;
                        }

                        Discovery discovery =
                                discover(service);

                        if (discovery.unitTests.isEmpty()) {
                            show(
                                    "No safe JUnit unit tests found in the current flow."
                                    + skippedSuffix(
                                            discovery));
                            return Status.OK_STATUS;
                        }

                        ILaunchManager manager =
                                DebugPlugin.getDefault()
                                        .getLaunchManager();

                        ILaunchConfigurationType type =
                                manager.getLaunchConfigurationType(
                                        JUNIT_LAUNCH_TYPE);

                        if (type == null) {
                            show(
                                    "Eclipse JUnit launch support is not installed.");
                            return Status.OK_STATUS;
                        }

                        int completed = 0;

                        for (TestType test :
                                discovery.unitTests.values()) {

                            if (monitor.isCanceled()) {
                                break;
                            }

                            ILaunchConfiguration configuration =
                                    null;

                            try {
                                String baseName =
                                        "JSF Flow - "
                                        + test.type
                                                .getElementName();

                                String name =
                                        manager.generateLaunchConfigurationName(
                                                baseName);

                                ILaunchConfigurationWorkingCopy wc =
                                        type.newInstance(
                                                null,
                                                name);

                                wc.setAttribute(
                                        IJavaLaunchConfigurationConstants
                                                .ATTR_PROJECT_NAME,
                                        test.type
                                                .getJavaProject()
                                                .getElementName());

                                wc.setAttribute(
                                        IJavaLaunchConfigurationConstants
                                                .ATTR_MAIN_TYPE_NAME,
                                        test.type
                                                .getFullyQualifiedName());

                                wc.setAttribute(
                                        ATTR_TEST_NAME,
                                        "");

                                wc.setAttribute(
                                        ATTR_TEST_KIND,
                                        FlowTestClassifier
                                                .junitKind(
                                                        test.type));

                                configuration =
                                        wc.doSave();

                                ILaunch launch =
                                        configuration.launch(
                                                ILaunchManager.RUN_MODE,
                                                monitor);

                                while (!launch.isTerminated()
                                        && !monitor.isCanceled()) {

                                    try {
                                        Thread.sleep(150L);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread()
                                                .interrupt();
                                        break;
                                    }
                                }

                                completed++;

                            } catch (Exception e) {
                                show(
                                        "Could not run "
                                        + test.type
                                                .getElementName()
                                        + ": "
                                        + safeMessage(e));

                            } finally {
                                if (configuration != null) {
                                    try {
                                        configuration.delete();
                                    } catch (Exception ignored) {
                                        // Temporary launch config cleanup only.
                                    }
                                }
                            }
                        }

                        show(
                                "Ran "
                                + completed
                                + (completed == 1
                                        ? " unit-test class"
                                        : " unit-test classes")
                                + " from the current flow."
                                + skippedSuffix(
                                        discovery));

                        return Status.OK_STATUS;
                    }
                };

        job.setUser(true);
        job.schedule();
    }

    private static Discovery discover(
            FlowExplorerService service) {

        Discovery result =
                new Discovery();

        for (FlowEntry entry :
                service.entriesForCategory(
                        FlowCategoryClassifier.TEST)) {

            IFile file =
                    service.resolve(entry);

            if (file == null
                    || !"java".equalsIgnoreCase(
                            file.getFileExtension())) {

                continue;
            }

            ICompilationUnit unit =
                    JavaCore.createCompilationUnitFrom(
                            file);

            if (unit == null
                    || !unit.exists()) {

                continue;
            }

            try {
                for (IType type :
                        unit.getAllTypes()) {

                    if (type.getDeclaringType()
                            != null) {

                        continue;
                    }

                    int classification =
                            FlowTestClassifier
                                    .classify(type);

                    if (classification
                            == FlowTestClassifier.UNIT_TEST) {

                        result.unitTests.put(
                                type.getHandleIdentifier(),
                                new TestType(
                                        type,
                                        file));

                    } else if (classification
                            == FlowTestClassifier.ARQUILLIAN_TEST) {

                        result.arquillianSkipped++;

                    } else if (classification
                            == FlowTestClassifier.JPA_TEST) {

                        result.jpaSkipped++;

                    } else if (classification
                            == FlowTestClassifier.INTEGRATION_TEST) {

                        result.integrationSkipped++;
                    }
                }

            } catch (JavaModelException e) {
                // Skip malformed/incomplete test files.
            }
        }

        return result;
    }

    private static String skippedSuffix(
            Discovery discovery) {

        List<String> parts =
                new ArrayList<String>();

        if (discovery.arquillianSkipped > 0) {
            parts.add(
                    discovery.arquillianSkipped
                    + " Arquillian integration "
                    + (discovery.arquillianSkipped == 1
                            ? "test was"
                            : "tests were")
                    + " skipped");
        }

        if (discovery.jpaSkipped > 0) {
            parts.add(
                    discovery.jpaSkipped
                    + " JPA "
                    + (discovery.jpaSkipped == 1
                            ? "test was"
                            : "tests were")
                    + " skipped");
        }

        if (discovery.integrationSkipped > 0) {
            parts.add(
                    discovery.integrationSkipped
                    + " integration "
                    + (discovery.integrationSkipped == 1
                            ? "test was"
                            : "tests were")
                    + " skipped");
        }

        if (parts.isEmpty()) {
            return "";
        }

        StringBuilder text =
                new StringBuilder(" (");

        for (int i = 0;
                i < parts.size();
                i++) {

            if (i > 0) {
                text.append(", ");
            }

            text.append(
                    parts.get(i));
        }

        text.append(").");
        return text.toString();
    }

    private static void show(
            final String message) {

        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }

        Display display =
                PlatformUI.getWorkbench()
                        .getDisplay();

        if (display == null
                || display.isDisposed()) {

            return;
        }

        display.asyncExec(
                new Runnable() {
                    @Override
                    public void run() {

                        WebSphereStatusLine.show(
                                message);
                    }
                });
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

    private static final class TestType {

        final IType type;
        final IFile file;

        TestType(
                IType type,
                IFile file) {

            this.type = type;
            this.file = file;
        }
    }

    private static final class Discovery {

        final Map<String, TestType> unitTests =
                new LinkedHashMap<String, TestType>();

        int arquillianSkipped;
        int jpaSkipped;
        int integrationSkipped;
    }
}
