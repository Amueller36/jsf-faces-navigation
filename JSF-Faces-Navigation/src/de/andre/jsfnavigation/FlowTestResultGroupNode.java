package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowTestResultGroupNode {

    public static final int FAILED = 1;
    public static final int SKIPPED = 2;

    private final int kind;
    private final List<FlowTestResultClassNode> classes;
    private final int caseCount;

    private FlowTestResultGroupNode(
            int kind,
            List<FlowTestCaseResult> results) {

        this.kind = kind;
        this.caseCount =
                results.size();

        Map<String, List<FlowTestCaseResult>> grouped =
                new LinkedHashMap<String, List<FlowTestCaseResult>>();

        for (FlowTestCaseResult result :
                results) {

            String key =
                    result.getClassName()
                    + "\n"
                    + result.getTestFilePath();

            List<FlowTestCaseResult> current =
                    grouped.get(key);

            if (current == null) {
                current =
                        new ArrayList<FlowTestCaseResult>();

                grouped.put(
                        key,
                        current);
            }

            current.add(result);
        }

        List<FlowTestResultClassNode> built =
                new ArrayList<FlowTestResultClassNode>();

        for (List<FlowTestCaseResult> current :
                grouped.values()) {

            built.add(
                    new FlowTestResultClassNode(
                            kind,
                            current));
        }

        Collections.sort(
                built,
                new java.util.Comparator<FlowTestResultClassNode>() {
                    @Override
                    public int compare(
                            FlowTestResultClassNode left,
                            FlowTestResultClassNode right) {

                        return left.getSimpleClassName()
                                .compareToIgnoreCase(
                                        right.getSimpleClassName());
                    }
                });

        this.classes =
                Collections.unmodifiableList(
                        built);
    }

    public static FlowTestResultGroupNode failed(
            FlowTestRunSummary summary) {

        List<FlowTestCaseResult> results =
                new ArrayList<FlowTestCaseResult>();

        for (FlowTestCaseResult result :
                summary.getResults()) {

            if (result.isFailed()) {
                results.add(result);
            }
        }

        return results.isEmpty()
                ? null
                : new FlowTestResultGroupNode(
                        FAILED,
                        results);
    }

    public static FlowTestResultGroupNode skipped(
            FlowTestRunSummary summary) {

        List<FlowTestCaseResult> results =
                new ArrayList<FlowTestCaseResult>();

        for (FlowTestCaseResult result :
                summary.getResults()) {

            if (result.isSkipped()) {
                results.add(result);
            }
        }

        return results.isEmpty()
                ? null
                : new FlowTestResultGroupNode(
                        SKIPPED,
                        results);
    }

    public int getKind() {
        return kind;
    }

    public int getCaseCount() {
        return caseCount;
    }

    public List<FlowTestResultClassNode> getClasses() {
        return classes;
    }
}
