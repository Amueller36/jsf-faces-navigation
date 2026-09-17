package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowImpactMethodNode {

    private final String methodHandleIdentifier;
    private final String methodLabel;
    private final List<FlowImpactTestNode> tests =
            new ArrayList<FlowImpactTestNode>();

    public FlowImpactMethodNode(
            String methodHandleIdentifier,
            String methodLabel) {

        this.methodHandleIdentifier =
                methodHandleIdentifier;
        this.methodLabel = methodLabel;
    }

    void add(FlowImpactTestNode node) {
        tests.add(node);
    }

    void sort() {
        Collections.sort(
                tests,
                new java.util.Comparator<FlowImpactTestNode>() {
                    @Override
                    public int compare(
                            FlowImpactTestNode left,
                            FlowImpactTestNode right) {

                        int byDepth =
                                left.getDepth()
                                - right.getDepth();

                        if (byDepth != 0) {
                            return byDepth;
                        }

                        return left.getEntry()
                                .getResourcePath()
                                .compareToIgnoreCase(
                                        right.getEntry()
                                                .getResourcePath());
                    }
                });
    }

    public String getMethodHandleIdentifier() {
        return methodHandleIdentifier;
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    public List<FlowImpactTestNode> getTests() {
        return Collections.unmodifiableList(tests);
    }
}
