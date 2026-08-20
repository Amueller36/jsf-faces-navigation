package de.andre.jsfnavigation;

public final class FlowImpactTestNode {

    private final FlowEntry entry;
    private final FlowImpactOrigin origin;

    public FlowImpactTestNode(
            FlowEntry entry,
            FlowImpactOrigin origin) {

        this.entry = entry;
        this.origin = origin;
    }

    public FlowEntry getEntry() {
        return entry;
    }

    public FlowImpactOrigin getOrigin() {
        return origin;
    }

    public int getDepth() {
        return origin.getDepth();
    }
}
