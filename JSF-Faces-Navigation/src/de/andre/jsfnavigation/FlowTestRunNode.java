package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowTestRunNode {

    private final FlowTestRunSummary summary;
    private final List<Object> children;

    public FlowTestRunNode(
            FlowTestRunSummary summary) {

        this.summary = summary;

        List<Object> result =
                new ArrayList<Object>();

        FlowTestResultGroupNode failed =
                FlowTestResultGroupNode.failed(
                        summary);

        if (failed != null) {
            result.add(failed);
        }

        FlowTestResultGroupNode skipped =
                FlowTestResultGroupNode.skipped(
                        summary);

        if (skipped != null) {
            result.add(skipped);
        }

        children =
                Collections.unmodifiableList(
                        result);
    }

    public FlowTestRunSummary getSummary() {
        return summary;
    }

    public List<Object> getChildren() {
        return children;
    }
}
