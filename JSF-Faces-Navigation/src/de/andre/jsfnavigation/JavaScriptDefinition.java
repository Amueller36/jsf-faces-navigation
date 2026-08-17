package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;

public final class JavaScriptDefinition {

    private final String functionName;
    private final String resourcePath;
    private final int offset;
    private final long modificationStamp;

    public JavaScriptDefinition(
            String functionName,
            String resourcePath,
            int offset,
            long modificationStamp) {

        this.functionName = functionName;
        this.resourcePath = resourcePath;
        this.offset = offset;
        this.modificationStamp = modificationStamp;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public int getOffset() {
        return offset;
    }

    public String getProjectName() {
        IFile file = getFile();
        return file == null ? null : file.getProject().getName();
    }

    public IFile getFile() {
        return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(resourcePath));
    }

    public boolean isCurrent() {
        IFile file = getFile();

        if (file == null || !file.exists()) {
            return false;
        }

        long current = file.getModificationStamp();

        return modificationStamp == IResource.NULL_STAMP
                || current == IResource.NULL_STAMP
                || modificationStamp == current;
    }
}
