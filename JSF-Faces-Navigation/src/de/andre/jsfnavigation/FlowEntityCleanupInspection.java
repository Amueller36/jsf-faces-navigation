package de.andre.jsfnavigation;

public final class FlowEntityCleanupInspection {

    private final boolean untrackedCreate;
    private final boolean possibleCreate;
    private final boolean trackedCreate;
    private final boolean directCleanup;
    private final boolean lifecycleCleanup;
    private final String lifecycleOwner;
    private final int inspectedMethods;
    private final boolean sameClassHelper;
    private final boolean superclassHelper;
    private final boolean truncated;

    public FlowEntityCleanupInspection(
            boolean untrackedCreate,
            boolean possibleCreate,
            boolean trackedCreate,
            boolean directCleanup,
            boolean lifecycleCleanup,
            String lifecycleOwner,
            int inspectedMethods,
            boolean sameClassHelper,
            boolean superclassHelper,
            boolean truncated) {

        this.untrackedCreate =
                untrackedCreate;
        this.possibleCreate =
                possibleCreate;
        this.trackedCreate =
                trackedCreate;
        this.directCleanup =
                directCleanup;
        this.lifecycleCleanup =
                lifecycleCleanup;
        this.lifecycleOwner =
                lifecycleOwner == null
                        ? ""
                        : lifecycleOwner;
        this.inspectedMethods =
                Math.max(
                        0,
                        inspectedMethods);
        this.sameClassHelper =
                sameClassHelper;
        this.superclassHelper =
                superclassHelper;
        this.truncated =
                truncated;
    }

    public boolean hasUntrackedCreate() {
        return untrackedCreate;
    }

    public boolean hasPossibleCreate() {
        return possibleCreate;
    }

    public boolean hasTrackedCreate() {
        return trackedCreate;
    }

    public boolean hasDirectCleanup() {
        return directCleanup;
    }

    public boolean hasLifecycleCleanup() {
        return lifecycleCleanup;
    }

    public String getLifecycleOwner() {
        return lifecycleOwner;
    }

    public int getInspectedMethods() {
        return inspectedMethods;
    }

    public boolean hasSameClassHelper() {
        return sameClassHelper;
    }

    public boolean hasSuperclassHelper() {
        return superclassHelper;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
