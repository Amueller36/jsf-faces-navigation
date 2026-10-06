package de.andre.jsfnavigation;

import org.eclipse.jface.text.IRegion;

public final class XsdAttributeReference {

    private final String tagName;
    private final String attributeName;
    private final String value;
    private final IRegion region;

    public XsdAttributeReference(
            String tagName,
            String attributeName,
            String value,
            IRegion region) {

        this.tagName =
                tagName == null
                        ? ""
                        : tagName;

        this.attributeName =
                attributeName == null
                        ? ""
                        : attributeName;

        this.value =
                value == null
                        ? ""
                        : value;

        this.region = region;
    }

    public String getTagName() {
        return tagName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getValue() {
        return value;
    }

    public IRegion getRegion() {
        return region;
    }
}
