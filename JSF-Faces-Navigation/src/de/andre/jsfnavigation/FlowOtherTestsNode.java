package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowOtherTestsNode {

    private final List<FlowEntry> entries;

    public FlowOtherTestsNode(
            List<FlowEntry> entries) {

        this.entries =
                Collections.unmodifiableList(
                        new ArrayList<FlowEntry>(
                                entries));
    }

    public List<FlowEntry> getEntries() {
        return entries;
    }
}
