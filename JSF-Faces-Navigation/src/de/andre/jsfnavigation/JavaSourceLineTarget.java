package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;

public final class JavaSourceLineTarget
        implements NavigationTarget {

    private final IFile file;
    private final int offset;
    private final String label;

    public JavaSourceLineTarget(
            IFile file,
            int offset,
            String label) {

        this.file = file;
        this.offset = offset;
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getIdentity() {
        return "source-line:"
                + file.getFullPath()
                        .toPortableString()
                + ":"
                + offset;
    }

    @Override
    public void open() {
        WebEditorOpener.open(
                file,
                offset);
    }
}
