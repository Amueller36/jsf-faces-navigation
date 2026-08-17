package de.andre.jsfnavigation;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class JsfSmartHyperlink
        implements IHyperlink {

    private final IRegion region;
    private final JsfCursorReference reference;
    private final IFile currentFile;

    public JsfSmartHyperlink(
            IRegion region,
            JsfCursorReference reference,
            IFile currentFile) {

        this.region = region;
        this.reference = reference;
        this.currentFile = currentFile;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "PrimeFaces / RichFaces / JSF";
    }

    @Override
    public String getHyperlinkText() {
        return "Open JSF target '"
                + reference.getName()
                + "'";
    }

    @Override
    public void open() {
        List<NavigationTarget> targets =
                JsfNavigationSupport.resolve(
                        reference,
                        currentFile);

        NavigationTarget selected =
                MethodNavigationChooser.choose(
                        "JSF Navigation",
                        "Select target for '"
                                + reference.getName()
                                + "':",
                        targets);

        if (selected != null) {
            selected.open();
        }
    }
}
