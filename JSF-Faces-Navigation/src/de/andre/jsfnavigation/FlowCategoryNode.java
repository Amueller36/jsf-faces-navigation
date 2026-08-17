package de.andre.jsfnavigation;

import java.util.List;

public final class FlowCategoryNode {

    private final String name;
    private final List<FlowEntry> entries;

    public FlowCategoryNode(
            String name,
            List<FlowEntry> entries) {

        this.name = name;
        this.entries = entries;
    }

    public String getName() {
        return name;
    }

    public List<FlowEntry> getEntries() {
        return entries;
    }
}
