package de.andre.jsfnavigation;

public final class FlowEntry {

    private final String resourcePath;
    private final String category;
    private final long addedAt;

    public FlowEntry(
            String resourcePath,
            String category,
            long addedAt) {

        this.resourcePath = resourcePath;
        this.category = category;
        this.addedAt = addedAt;
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
}
