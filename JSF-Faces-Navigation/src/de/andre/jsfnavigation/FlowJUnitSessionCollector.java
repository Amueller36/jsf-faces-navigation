package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestRunSession;

public final class FlowJUnitSessionCollector
        extends TestRunListener {

    private final String expectedRunName;
    private final String testFilePath;

    private final List<FlowTestCaseResult> results =
            Collections.synchronizedList(
                    new ArrayList<FlowTestCaseResult>());

    private final CountDownLatch finished =
            new CountDownLatch(1);

    private volatile boolean matched;

    public FlowJUnitSessionCollector(
            String expectedRunName,
            String testFilePath) {

        this.expectedRunName =
                expectedRunName == null
                        ? ""
                        : expectedRunName;

        this.testFilePath =
                testFilePath == null
                        ? ""
                        : testFilePath;
    }

    @Override
    public void sessionLaunched(
            ITestRunSession session) {

        if (matches(session)) {
            matched = true;
        }
    }

    @Override
    public void sessionStarted(
            ITestRunSession session) {

        if (matches(session)) {
            matched = true;
        }
    }

    @Override
    public void testCaseFinished(
            ITestCaseElement testCaseElement) {

        if (testCaseElement == null
                || !matches(
                        testCaseElement
                                .getTestRunSession())) {

            return;
        }

        matched = true;

        ITestElement.Result junitResult =
                testCaseElement
                        .getTestResult(false);

        int result =
                mapResult(junitResult);

        ITestElement.FailureTrace failure =
                testCaseElement
                        .getFailureTrace();

        String trace =
                failure == null
                        ? ""
                        : safe(
                                failure.getTrace());

        String expected =
                failure == null
                        ? ""
                        : safe(
                                failure.getExpected());

        String actual =
                failure == null
                        ? ""
                        : safe(
                                failure.getActual());

        results.add(
                new FlowTestCaseResult(
                        testFilePath,
                        safe(
                                testCaseElement
                                        .getTestClassName()),
                        safeMethodName(
                                testCaseElement
                                        .getTestMethodName()),
                        result,
                        trace,
                        expected,
                        actual,
                        testCaseElement
                                .getElapsedTimeInSeconds()));
    }

    @Override
    public void sessionFinished(
            ITestRunSession session) {

        if (!matches(session)) {
            return;
        }

        matched = true;
        finished.countDown();
    }

    public boolean awaitFinished(
            long timeoutMillis) {

        try {
            return finished.await(
                    timeoutMillis,
                    TimeUnit.MILLISECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread()
                    .interrupt();
            return false;
        }
    }

    public boolean isMatched() {
        return matched;
    }

    public List<FlowTestCaseResult> snapshot() {
        synchronized (results) {
            return new ArrayList<FlowTestCaseResult>(
                    results);
        }
    }

    private boolean matches(
            ITestRunSession session) {

        if (session == null) {
            return false;
        }

        String runName =
                session.getTestRunName();

        return expectedRunName.equals(
                runName);
    }

    private static int mapResult(
            ITestElement.Result result) {

        if (result
                == ITestElement.Result.OK) {

            return FlowTestCaseResult.PASS;
        }

        if (result
                == ITestElement.Result.FAILURE) {

            return FlowTestCaseResult.FAILURE;
        }

        if (result
                == ITestElement.Result.IGNORED) {

            return FlowTestCaseResult.SKIPPED;
        }

        /*
         * ERROR and any future/undefined result are deliberately treated as
         * errors rather than silently counted as skipped/passed.
         */
        return FlowTestCaseResult.ERROR;
    }

    private static String safeMethodName(
            String value) {

        String text =
                safe(value);

        return text.isEmpty()
                ? "<test>"
                : text;
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
