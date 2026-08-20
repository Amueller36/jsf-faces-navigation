package de.andre.jsfnavigation;

public final class FlowImpactOrigin {

    private final String sourceResourcePath;
    private final String methodHandleIdentifier;
    private final String methodLabel;
    private final int depth;

    public FlowImpactOrigin(
            String sourceResourcePath,
            String methodHandleIdentifier,
            String methodLabel,
            int depth) {

        this.sourceResourcePath =
                sourceResourcePath == null
                        ? ""
                        : sourceResourcePath;
        this.methodHandleIdentifier =
                methodHandleIdentifier == null
                        ? ""
                        : methodHandleIdentifier;
        this.methodLabel =
                methodLabel == null
                        ? "method(...)"
                        : methodLabel;
        this.depth = Math.max(1, depth);
    }

    public String getSourceResourcePath() {
        return sourceResourcePath;
    }

    public String getMethodHandleIdentifier() {
        return methodHandleIdentifier;
    }

    public String getMethodLabel() {
        return methodLabel;
    }

    public int getDepth() {
        return depth;
    }

    public String getIdentity() {
        if (!methodHandleIdentifier.isEmpty()) {
            return methodHandleIdentifier;
        }

        return sourceResourcePath
                + "#"
                + methodLabel;
    }
}
