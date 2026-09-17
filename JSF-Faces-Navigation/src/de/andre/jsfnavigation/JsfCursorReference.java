package de.andre.jsfnavigation;

public final class JsfCursorReference {

    public static final int COMPONENT = 1;
    public static final int WIDGET = 2;
    public static final int FILE = 3;
    public static final int COMPOSITE = 4;
    public static final int BUNDLE_KEY = 5;
    public static final int COMPOSITE_ATTRIBUTE = 6;
    public static final int ROLE = 7;

    private final int kind;
    private final String name;
    private final int offset;
    private final int length;
    private final String extra;

    public JsfCursorReference(
            int kind,
            String name,
            int offset,
            int length,
            String extra) {

        this.kind = kind;
        this.name = name;
        this.offset = offset;
        this.length = length;
        this.extra = extra;
    }

    public int getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getExtra() {
        return extra;
    }
}
