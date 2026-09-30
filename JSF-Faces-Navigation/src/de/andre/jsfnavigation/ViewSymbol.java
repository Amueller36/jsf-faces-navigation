package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;

public final class ViewSymbol {

    public static final int COMPONENT_ID = 1;
    public static final int COMPONENT_REFERENCE = 2;
    public static final int WIDGET_VAR = 3;
    public static final int WIDGET_REFERENCE = 4;
    public static final int INCLUDE = 5;
    public static final int TEMPLATE = 6;
    public static final int COMPOSITE_TAG = 7;
    public static final int BUNDLE_KEY = 8;
    public static final int BUNDLE_VAR = 9;

    private final int kind;
    private final String name;
    private final String resourcePath;
    private final int offset;
    private final String attributeName;
    private final String extra;
    private final long modificationStamp;

    public ViewSymbol(
            int kind,
            String name,
            String resourcePath,
            int offset,
            String attributeName,
            String extra,
            long modificationStamp) {

        this.kind = kind;
        this.name = name;
        this.resourcePath = resourcePath;
        this.offset = offset;
        this.attributeName = attributeName;
        this.extra = extra;
        this.modificationStamp = modificationStamp;
    }

    public int getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public int getOffset() {
        return offset;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getExtra() {
        return extra;
    }

    public long getModificationStamp() {
        return modificationStamp;
    }

    public IFile getFile() {
        return ResourcesPlugin.getWorkspace()
                .getRoot()
                .getFile(new Path(resourcePath));
    }

    public boolean isCurrent() {
        IFile file = getFile();

        return file.exists()
                && file.getModificationStamp()
                        == modificationStamp;
    }
}
