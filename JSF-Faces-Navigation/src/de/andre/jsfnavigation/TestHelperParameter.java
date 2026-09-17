package de.andre.jsfnavigation;

public final class TestHelperParameter {

    private final String type;
    private final String name;

    public TestHelperParameter(
            String type,
            String name) {

        this.type =
                type == null
                        ? "Object"
                        : type;

        this.name =
                name == null
                        ? "arg"
                        : name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
