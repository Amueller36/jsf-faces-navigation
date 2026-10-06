package de.andre.jsfnavigation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FlowFocusResult {

    private final String rootResourcePath;
    private final Map<String, Integer> distances;

    public FlowFocusResult(
            String rootResourcePath,
            Map<String, Integer> distances) {

        this.rootResourcePath =
                rootResourcePath == null
                        ? ""
                        : rootResourcePath;

        this.distances =
                Collections.unmodifiableMap(
                        new LinkedHashMap<String, Integer>(
                                distances));
    }

    public String getRootResourcePath() {
        return rootResourcePath;
    }

    public boolean isRelated(
            String resourcePath) {

        return distances.containsKey(
                resourcePath);
    }

    public int getDistance(
            String resourcePath) {

        Integer value =
                distances.get(
                        resourcePath);

        return value == null
                ? -1
                : value.intValue();
    }

    public int getRelatedCount() {
        return distances.size();
    }

    public Set<String> getRelatedPaths() {
        return distances.keySet();
    }
}
