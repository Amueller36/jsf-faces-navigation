package de.andre.jsfnavigation;

import java.io.PrintWriter;
import java.io.StringWriter;
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
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.junit.JUnitCore;
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

    private static final long RESULT_WAIT_MILLIS =
            5000L;

    private static final int SCOPE_FLOW = 1;
    private static final int SCOPE_FILE = 2;
    private static final int SCOPE_CLASS = 3;
    private static final int SCOPE_METHOD = 4;

    private FlowJUnitRunner() {
    }

    public static void runCurrentFlowUnitTests() {
        schedule(
                new RunRequest(
                        SCOPE_FLOW,
                        null,
                        null,
                        null,
                        "Run current-flow unit tests"));
    }

    public static void runTestFile(
            IFile file) {

        if (file == null
                || !file.exists()) {

            return;
        }

        schedule(
                new RunRequest(
                        SCOPE_FILE,
                        file,
                        null,
                        null,
                        "Run "
                                + file.getName()));
    }

    public static void runTestClass(
            IFile file,
            String fullyQualifiedClassName) {

        if (file == null
                || !file.exists()) {

            return;
        }

        schedule(
                new RunRequest(
                        SCOPE_CLASS,
                        file,
                        fullyQualifiedClassName,
                        null,
                        "Run "
                                + simpleClassName(
                                        fullyQualifiedClassName,
                                        file.getName())));
    }


    public static void runExplicitTestMethod(
            IFile file,
            IMethod method) {

        if (file == null
                || !file.exists()
                || method == null
                || !method.exists()
                || !FlowTestClassifier
                        .isJUnitTestMethod(
                                method)) {

            return;
        }

        IType type =
                method.getDeclaringType();

        if (type == null
                || !type.exists()) {

            return;
        }

        schedule(
                new RunRequest(
                        SCOPE_METHOD,
                        file,
                        type.getFullyQualifiedName(),
                        method.getElementName(),
                        "Run "
                                + type.getElementName()
                                + "."
                                + method.getElementName()));
    }

    private static void schedule(
            final RunRequest request) {

        Job job =
                new Job(
                        request.jobName) {

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
                                request.discover(
                                        service);

                        if (discovery.unitTests.isEmpty()) {
                            show(
                                    request.emptyMessage()
                                    + skippedSuffix(
                                            discovery));

                            return Status.OK_STATUS;
                        }

                        return execute(
                                request,
                                discovery,
                                service,
                                monitor);
                    }
                };

        job.setUser(true);
        job.schedule();
    }

    private static IStatus execute(
            RunRequest request,
            Discovery discovery,
            FlowExplorerService service,
            IProgressMonitor monitor) {

        String flowName =
                service.getCurrentFlowName();

        long startedAt =
                System.currentTimeMillis();

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

        List<FlowTestCaseResult> allResults =
                new ArrayList<FlowTestCaseResult>();

        for (TestType test :
                discovery.unitTests.values()) {

            if (monitor.isCanceled()) {
                break;
            }

            ILaunchConfiguration configuration =
                    null;

            FlowJUnitSessionCollector collector =
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
                        test.testName);

                wc.setAttribute(
                        ATTR_TEST_KIND,
                        FlowTestClassifier
                                .junitKind(
                                        test.type));

                configuration =
                        wc.doSave();

                collector =
                        new FlowJUnitSessionCollector(
                                name,
                                test.file
                                        .getFullPath()
                                        .toPortableString());

                JUnitCore.addTestRunListener(
                        collector);

                ILaunch launch =
                        configuration.launch(
                                ILaunchManager.RUN_MODE,
                                monitor);

                while (!launch.isTerminated()
                        && !monitor.isCanceled()) {

                    try {
                        Thread.sleep(
                                150L);

                    } catch (InterruptedException e) {
                        Thread.currentThread()
                                .interrupt();
                        break;
                    }
                }

                collector.awaitFinished(
                        RESULT_WAIT_MILLIS);

                List<FlowTestCaseResult> classResults =
                        collector.snapshot();

                if (!collector.isMatched()) {
                    classResults.add(
                            unavailableResult(
                                    test,
                                    "Eclipse's JUnit result listener did not receive the test session."));
                }

                allResults.addAll(
                        classResults);

                completed++;

            } catch (Exception e) {
                allResults.add(
                        launchFailure(
                                test,
                                e));

            } finally {
                if (collector != null) {
                    JUnitCore.removeTestRunListener(
                            collector);
                }

                if (configuration != null) {
                    try {
                        configuration.delete();

                    } catch (Exception ignored) {
                        // Temporary launch config cleanup only.
                    }
                }
            }
        }

        FlowTestRunSummary summary =
                new FlowTestRunSummary(
                        flowName,
                        startedAt,
                        System.currentTimeMillis(),
                        completed,
                        discovery.arquillianSkipped,
                        discovery.jpaSkipped,
                        discovery.integrationSkipped,
                        monitor.isCanceled(),
                        allResults);

        boolean belongsToCurrentFlow =
                request.scope
                        != SCOPE_METHOD
                || service.containsFile(
                        request.file);

        if (belongsToCurrentFlow) {
            store(summary);
            FlowExplorerView.refreshIfOpen();
        }

        show(
                request.scopeLabel()
                + " "
                + summaryState(summary)
                + ": "
                + summary.getPassedCount()
                + " passed, "
                + summary.getFailedCount()
                + " failed, "
                + summary.getSkippedCount()
                + " skipped across "
                + summary.getClassesRun()
                + (summary.getClassesRun() == 1
                        ? " class."
                        : " classes.")
                + skippedSuffix(
                        discovery));

        return Status.OK_STATUS;
    }

    private static String summaryState(
            FlowTestRunSummary summary) {

        if (summary.isCanceled()) {
            return "CANCELED";
        }

        return summary.hasFailures()
                ? "FAILED"
                : "PASSED";
    }

    private static FlowTestCaseResult launchFailure(
            TestType test,
            Throwable error) {

        return new FlowTestCaseResult(
                test.file
                        .getFullPath()
                        .toPortableString(),
                test.type
                        .getFullyQualifiedName(),
                "<launch>",
                FlowTestCaseResult.ERROR,
                stackTrace(error),
                "",
                "",
                0.0d);
    }

    private static FlowTestCaseResult unavailableResult(
            TestType test,
            String message) {

        return new FlowTestCaseResult(
                test.file
                        .getFullPath()
                        .toPortableString(),
                test.type
                        .getFullyQualifiedName(),
                "<result unavailable>",
                FlowTestCaseResult.ERROR,
                message,
                "",
                "",
                0.0d);
    }

    private static String stackTrace(
            Throwable error) {

        if (error == null) {
            return "Unknown launch error.";
        }

        StringWriter buffer =
                new StringWriter();

        PrintWriter writer =
                new PrintWriter(buffer);

        error.printStackTrace(writer);
        writer.flush();

        return buffer.toString();
    }

    private static void store(
            FlowTestRunSummary summary) {

        FlowTestResultStore store =
                Activator.getFlowTestResultStore();

        if (store != null) {
            store.put(summary);
        }
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

            discoverFile(
                    file,
                    null,
                    result);
        }

        return result;
    }

    private static Discovery discoverFile(
            IFile file,
            String fullyQualifiedClassName) {

        Discovery result =
                new Discovery();

        discoverFile(
                file,
                fullyQualifiedClassName,
                result);

        return result;
    }

    private static void discoverFile(
            IFile file,
            String fullyQualifiedClassName,
            Discovery result) {

        if (file == null
                || !file.exists()
                || !"java".equalsIgnoreCase(
                        file.getFileExtension())) {

            return;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        file);

        if (unit == null
                || !unit.exists()) {

            return;
        }

        try {
            for (IType type :
                    unit.getAllTypes()) {

                if (type.getDeclaringType()
                        != null) {

                    continue;
                }

                if (fullyQualifiedClassName != null
                        && !fullyQualifiedClassName
                                .isEmpty()
                        && !fullyQualifiedClassName
                                .equals(
                                        type.getFullyQualifiedName())) {

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
                                    file,
                                    ""));

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

    private static String simpleClassName(
            String fullyQualifiedClassName,
            String fallback) {

        if (fullyQualifiedClassName == null
                || fullyQualifiedClassName
                        .isEmpty()) {

            return fallback;
        }

        int dot =
                fullyQualifiedClassName
                        .lastIndexOf('.');

        return dot >= 0
                ? fullyQualifiedClassName
                        .substring(
                                dot + 1)
                : fullyQualifiedClassName;
    }

    private static final class RunRequest {

        final int scope;
        final IFile file;
        final String fullyQualifiedClassName;
        final String methodName;
        final String jobName;

        RunRequest(
                int scope,
                IFile file,
                String fullyQualifiedClassName,
                String methodName,
                String jobName) {

            this.scope = scope;
            this.file = file;
            this.fullyQualifiedClassName =
                    fullyQualifiedClassName;
            this.methodName =
                    methodName;
            this.jobName = jobName;
        }

        Discovery discover(
                FlowExplorerService service) {

            if (scope == SCOPE_FLOW) {
                return FlowJUnitRunner
                        .discover(service);
            }

            if (scope == SCOPE_METHOD) {
                Discovery result =
                        new Discovery();

                ICompilationUnit unit =
                        JavaCore.createCompilationUnitFrom(
                                file);

                if (unit == null
                        || !unit.exists()) {

                    return result;
                }

                try {
                    for (IType type :
                            unit.getAllTypes()) {

                        if (type.getDeclaringType()
                                != null
                                || !fullyQualifiedClassName
                                        .equals(
                                                type.getFullyQualifiedName())) {

                            continue;
                        }

                        result.unitTests.put(
                                type.getHandleIdentifier()
                                        + "#"
                                        + methodName,
                                new TestType(
                                        type,
                                        file,
                                        methodName));

                        break;
                    }

                } catch (JavaModelException e) {
                    // Fall through with an empty discovery.
                }

                return result;
            }

            return discoverFile(
                    file,
                    scope == SCOPE_CLASS
                            ? fullyQualifiedClassName
                            : null);
        }

        String emptyMessage() {
            if (scope == SCOPE_METHOD) {
                return "Could not resolve this JUnit test method for launch.";
            }

            if (scope == SCOPE_CLASS) {
                String reason =
                        FlowTestLaunchSupport
                                .blockedReason(
                                        file,
                                        fullyQualifiedClassName);

                return reason.isEmpty()
                        ? "No safe JUnit unit-test class was found."
                        : reason;
            }

            if (scope == SCOPE_FILE) {
                String reason =
                        FlowTestLaunchSupport
                                .blockedReason(
                                        file,
                                        null);

                return reason.isEmpty()
                        ? "No safe JUnit unit-test class was found in "
                                + file.getName()
                                + "."
                        : reason;
            }

            return "No safe JUnit unit tests found in the current flow.";
        }

        String scopeLabel() {
            if (scope == SCOPE_METHOD) {
                return simpleClassName(
                        fullyQualifiedClassName,
                        file.getName())
                        + "."
                        + methodName;
            }

            if (scope == SCOPE_CLASS) {
                return simpleClassName(
                        fullyQualifiedClassName,
                        file.getName());
            }

            if (scope == SCOPE_FILE) {
                return file.getName();
            }

            return "Flow tests";
        }
    }

    private static final class TestType {

        final IType type;
        final IFile file;
        final String testName;

        TestType(
                IType type,
                IFile file,
                String testName) {

            this.type = type;
            this.file = file;
            this.testName =
                    testName == null
                            ? ""
                            : testName;
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
