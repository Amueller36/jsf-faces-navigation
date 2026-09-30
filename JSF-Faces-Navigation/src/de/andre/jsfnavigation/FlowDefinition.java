package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowDefinition {

    private final String name;
    private final List<FlowEntry> entries =
            new ArrayList<FlowEntry>();

    public FlowDefinition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<FlowEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void addOrReplace(FlowEntry entry) {
        remove(entry.getResourcePath());
        entries.add(entry);
    }

    public void remove(String resourcePath) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (resourcePath.equals(
                    entries.get(i).getResourcePath())) {

                entries.remove(i);
            }
        }
    }

    public boolean contains(String resourcePath) {
        for (FlowEntry entry : entries) {
            if (resourcePath.equals(
                    entry.getResourcePath())) {

                return true;
            }
        }

        return false;
    }

    public void clear() {
        entries.clear();
    }
}
