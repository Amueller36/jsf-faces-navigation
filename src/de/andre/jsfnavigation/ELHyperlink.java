package de.andre.jsfnavigation;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class ELHyperlink implements IHyperlink {

    private final IRegion region;
    private final ELSelection selection;

    public ELHyperlink(IRegion region, ELSelection selection) {
        this.region = region;
        this.selection = selection;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JSF EL declaration";
    }

    @Override
    public String getHyperlinkText() {
        return "Open declaration of " + selection.getSelectedPart();
    }

    @Override
    public void open() {
        ELNavigationService.navigate(selection);
    }
}
