package de.andre.jsfnavigation;

public final class FlowStackTraceNode {

    private final FlowTestCaseResult result;

    public FlowStackTraceNode(
            FlowTestCaseResult result) {

        this.result = result;
    }

    public FlowTestCaseResult getResult() {
        return result;
    }
}
