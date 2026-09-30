package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FlowEntry {

    private final String resourcePath;
    private final String category;
    private final long addedAt;
    private final int legacyImpactDepth;
    private final List<FlowImpactOrigin> impactOrigins;

    public FlowEntry(
            String resourcePath,
            String category,
            long addedAt) {

        this(
                resourcePath,
                category,
                addedAt,
                0,
                Collections.<FlowImpactOrigin>emptyList());
    }

    public FlowEntry(
            String resourcePath,
            String category,
            long addedAt,
            int impactDepth) {

        this(
                resourcePath,
                category,
                addedAt,
                impactDepth,
                Collections.<FlowImpactOrigin>emptyList());
    }

    public FlowEntry(
            String resourcePath,
            String category,
            long addedAt,
            int legacyImpactDepth,
            List<FlowImpactOrigin> impactOrigins) {

        this.resourcePath = resourcePath;
        this.category = category;
        this.addedAt = addedAt;
        this.legacyImpactDepth =
                Math.max(0, legacyImpactDepth);
        this.impactOrigins =
                impactOrigins == null
                        ? Collections.<FlowImpactOrigin>emptyList()
                        : Collections.unmodifiableList(
                                new ArrayList<FlowImpactOrigin>(
                                        impactOrigins));
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getCategory() {
        return category;
    }

    public long getAddedAt() {
        return addedAt;
    }

    /**
     * Shortest known caller distance. 0 means no impact-distance metadata.
     */
    public int getImpactDepth() {
        int best = legacyImpactDepth;

        for (FlowImpactOrigin origin : impactOrigins) {
            if (best <= 0
                    || origin.getDepth() < best) {

                best = origin.getDepth();
            }
        }

        return best;
    }

    public List<FlowImpactOrigin> getImpactOrigins() {
        return impactOrigins;
    }
}
