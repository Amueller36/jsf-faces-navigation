package de.andre.jsfnavigation;

import java.util.Collections;
import java.util.List;

public final class FlowTestResultCaseNode {

    private final FlowTestCaseResult result;

    public FlowTestResultCaseNode(
            FlowTestCaseResult result) {

        this.result = result;
    }

    public FlowTestCaseResult getResult() {
        return result;
    }

    public List<FlowStackTraceNode> getChildren() {
        if (!result.isFailed()
                || result.getStackTrace()
                        .isEmpty()) {

            return Collections.emptyList();
        }

        return Collections.singletonList(
                new FlowStackTraceNode(
                        result));
    }
}
