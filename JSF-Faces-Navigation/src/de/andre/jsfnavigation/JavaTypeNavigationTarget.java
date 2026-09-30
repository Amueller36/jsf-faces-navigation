package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IType;

public final class JavaTypeNavigationTarget
        implements NavigationTarget {

    private final IType type;
    private final String label;

    public JavaTypeNavigationTarget(
            IType type,
            String label) {

        this.type = type;
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getIdentity() {
        return "type:"
                + type.getHandleIdentifier();
    }

    @Override
    public void open() {
        JavaEditorOpener.open(type);
    }
}
