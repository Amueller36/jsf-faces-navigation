package de.andre.jsfnavigation;

public final class XsdDefinition {

    private final String resourcePath;
    private final String namespace;
    private final String name;
    private final String kind;
    private final int offset;

    public XsdDefinition(
            String resourcePath,
            String namespace,
            String name,
            String kind,
            int offset) {

        this.resourcePath = safe(resourcePath);
        this.namespace = safe(namespace);
        this.name = safe(name);
        this.kind = safe(kind);
        this.offset = Math.max(0, offset);
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public int getOffset() {
        return offset;
    }

    public String getLabel() {
        return kind
                + " "
                + name
                + (namespace.isEmpty()
                        ? ""
                        : "  [" + namespace + "]")
                + " — "
                + resourcePath;
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
