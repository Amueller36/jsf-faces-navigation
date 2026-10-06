package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class WorkspaceFileHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final IFile file;
    private final String label;

    public WorkspaceFileHyperlink(
            IRegion region,
            IFile file,
            String label) {

        this.region = region;
        this.file = file;
        this.label =
                label == null
                        ? "Open file"
                        : label;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "Workspace file";
    }

    @Override
    public String getHyperlinkText() {
        return label;
    }

    @Override
    public void open() {
        if (file != null
                && file.exists()) {

            WebEditorOpener.open(
                    file,
                    0);
        }
    }
}
