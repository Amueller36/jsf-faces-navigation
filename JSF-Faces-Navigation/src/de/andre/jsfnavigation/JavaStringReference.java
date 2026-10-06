package de.andre.jsfnavigation;

public final class JavaStringReference {

    public static final int COMPONENT_ID = 1;
    public static final int NAMED_QUERY = 2;
    public static final int ROLE = 3;
    public static final int OUTCOME = 4;

    private final int kind;
    private final String value;
    private final int offset;
    private final int length;

    public JavaStringReference(
            int kind,
            String value,
            int offset,
            int length) {

        this.kind = kind;
        this.value = value;
        this.offset = offset;
        this.length = length;
    }

    public int getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }
}
