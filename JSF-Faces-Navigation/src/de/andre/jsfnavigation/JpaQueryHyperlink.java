package de.andre.jsfnavigation;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

public final class JpaQueryHyperlink implements IHyperlink {

    private final IRegion region;
    private final JpaResolvedReference resolved;

    public JpaQueryHyperlink(IRegion region, JpaResolvedReference resolved) {
        this.region = region;
        this.resolved = resolved;
    }

    @Override
    public IRegion getHyperlinkRegion() {
        return region;
    }

    @Override
    public String getTypeLabel() {
        return "JPA/JPQL mapping";
    }

    @Override
    public String getHyperlinkText() {
        if (resolved.getMember() == null) {
            return "Open entity " + resolved.getAliasType().getElementName();
        }

        return "Open JPA member "
                + resolved.getDeclaringType().getElementName()
                + "."
                + resolved.getReference().getSelectedSegment();
    }

    @Override
    public void open() {
        if (resolved.getMember() != null) {
            JavaEditorOpener.open(resolved.getMember());
        } else {
            JavaEditorOpener.open(resolved.getAliasType());
        }
    }
}
