package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;

public final class BeanUsage {

    private final String beanName;
    private final String resourcePath;
    private final int offset;
    private final long modificationStamp;

    public BeanUsage(String beanName, String resourcePath, int offset, long modificationStamp) {
        this.beanName = beanName;
        this.resourcePath = resourcePath;
        this.offset = offset;
        this.modificationStamp = modificationStamp;
    }

    public String getBeanName() {
        return beanName;
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
