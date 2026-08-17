package de.andre.jsfnavigation;

public final class JavaScriptCall {

    private final String name;
    private final int offset;

    public JavaScriptCall(String name, int offset) {
        this.name = name;
        this.offset = offset;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }
}
