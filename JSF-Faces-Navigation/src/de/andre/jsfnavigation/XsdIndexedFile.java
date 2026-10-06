package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XsdIndexedFile {

    private final String resourcePath;
    private final long modificationStamp;
    private final String targetNamespace;
    private final List<XsdDefinition> definitions;

    public XsdIndexedFile(
            String resourcePath,
            long modificationStamp,
            String targetNamespace,
            List<XsdDefinition> definitions) {

        this.resourcePath =
                resourcePath == null
                        ? ""
                        : resourcePath;

        this.modificationStamp =
                modificationStamp;

        this.targetNamespace =
                targetNamespace == null
                        ? ""
                        : targetNamespace;

        this.definitions =
                Collections.unmodifiableList(
                        new ArrayList<XsdDefinition>(
                                definitions));
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public long getModificationStamp() {
        return modificationStamp;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public List<XsdDefinition> getDefinitions() {
        return definitions;
    }
}
