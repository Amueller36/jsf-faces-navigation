package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowTestResultClassNode {

    private final int groupKind;
    private final String className;
    private final String testFilePath;
    private final List<FlowTestResultCaseNode> cases;

    public FlowTestResultClassNode(
            int groupKind,
            List<FlowTestCaseResult> results) {

        this.groupKind = groupKind;

        FlowTestCaseResult first =
                results.get(0);

        this.className =
                first.getClassName();

        this.testFilePath =
                first.getTestFilePath();

        List<FlowTestResultCaseNode> built =
                new ArrayList<FlowTestResultCaseNode>();

        for (FlowTestCaseResult result :
                results) {

            built.add(
                    new FlowTestResultCaseNode(
                            result));
        }

        Collections.sort(
                built,
                new java.util.Comparator<FlowTestResultCaseNode>() {
                    @Override
                    public int compare(
                            FlowTestResultCaseNode left,
                            FlowTestResultCaseNode right) {

                        return left.getResult()
                                .getMethodName()
                                .compareToIgnoreCase(
                                        right.getResult()
                                                .getMethodName());
                    }
                });

        this.cases =
                Collections.unmodifiableList(
                        built);
    }

    public int getGroupKind() {
        return groupKind;
    }

    public String getClassName() {
        return className;
    }

    public String getTestFilePath() {
        return testFilePath;
    }

    public String getSimpleClassName() {
        if (className == null
                || className.isEmpty()) {

            return "Test";
        }

        int dot =
                className.lastIndexOf('.');

        return dot >= 0
                ? className.substring(
                        dot + 1)
                : className;
    }

    public List<FlowTestResultCaseNode> getCases() {
        return cases;
    }
}
