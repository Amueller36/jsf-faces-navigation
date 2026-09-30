package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowCategoryNode {

    private final String name;
    private final List<FlowEntry> entries;
    private final List<Object> children;

    public FlowCategoryNode(
            String name,
            List<FlowEntry> entries) {

        this(
                name,
                entries,
                new ArrayList<Object>(entries));
    }

    public FlowCategoryNode(
            String name,
            List<FlowEntry> entries,
            List<Object> children) {

        this.name = name;
        this.entries =
                Collections.unmodifiableList(
                        new ArrayList<FlowEntry>(
                                entries));
        this.children =
                Collections.unmodifiableList(
                        new ArrayList<Object>(
                                children));
    }

    public String getName() {
        return name;
    }

    public List<FlowEntry> getEntries() {
        return entries;
    }

    public List<Object> getChildren() {
        return children;
    }
}
