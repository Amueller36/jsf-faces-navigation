package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowTestRunSummary {

    private final String flowName;
    private final long startedAt;
    private final long finishedAt;
    private final int classesRun;
    private final int arquillianSkipped;
    private final int jpaSkipped;
    private final int integrationSkipped;
    private final boolean canceled;
    private final List<FlowTestCaseResult> results;

    public FlowTestRunSummary(
            String flowName,
            long startedAt,
            long finishedAt,
            int classesRun,
            int arquillianSkipped,
            int jpaSkipped,
            int integrationSkipped,
            boolean canceled,
            List<FlowTestCaseResult> results) {

        this.flowName =
                flowName == null
                        ? ""
                        : flowName;

        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.classesRun =
                Math.max(
                        0,
                        classesRun);

        this.arquillianSkipped =
                Math.max(
                        0,
                        arquillianSkipped);

        this.jpaSkipped =
                Math.max(
                        0,
                        jpaSkipped);

        this.integrationSkipped =
                Math.max(
                        0,
                        integrationSkipped);

        this.canceled = canceled;

        this.results =
                Collections.unmodifiableList(
                        new ArrayList<FlowTestCaseResult>(
                                results == null
                                        ? Collections
                                                .<FlowTestCaseResult>
                                                        emptyList()
                                        : results));
    }

    public String getFlowName() {
        return flowName;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public int getClassesRun() {
        return classesRun;
    }

    public int getArquillianSkipped() {
        return arquillianSkipped;
    }

    public int getJpaSkipped() {
        return jpaSkipped;
    }

    public int getIntegrationSkipped() {
        return integrationSkipped;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public List<FlowTestCaseResult> getResults() {
        return results;
    }

    public int getPassedCount() {
        return count(
                FlowTestCaseResult.PASS);
    }

    public int getFailureCount() {
        return count(
                FlowTestCaseResult.FAILURE);
    }

    public int getErrorCount() {
        return count(
                FlowTestCaseResult.ERROR);
    }

    public int getFailedCount() {
        return getFailureCount()
                + getErrorCount();
    }

    public int getSkippedCount() {
        return count(
                FlowTestCaseResult.SKIPPED);
    }

    public int getCaseCount() {
        return results.size();
    }

    public boolean hasFailures() {
        return getFailedCount() > 0;
    }

    public boolean isSuccessful() {
        return !canceled
                && classesRun > 0
                && !results.isEmpty()
                && !hasFailures();
    }

    private int count(
            int status) {

        int count = 0;

        for (FlowTestCaseResult result :
                results) {

            if (result.getStatus()
                    == status) {

                count++;
            }
        }

        return count;
    }
}
