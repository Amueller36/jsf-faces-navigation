package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class BeanDescriptor {

    private final String beanName;
    private final String projectName;
    private final String qualifiedTypeName;
    private final String resourcePath;
    private final long modificationStamp;

    public BeanDescriptor(
            String beanName,
            String projectName,
            String qualifiedTypeName,
            String resourcePath,
            long modificationStamp) {

        this.beanName = beanName;
        this.projectName = projectName;
        this.qualifiedTypeName = qualifiedTypeName;
        this.resourcePath = resourcePath;
        this.modificationStamp = modificationStamp;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getQualifiedTypeName() {
        return qualifiedTypeName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public long getModificationStamp() {
        return modificationStamp;
    }

    public IType resolveType() {
        IFile sourceFile = ResourcesPlugin.getWorkspace()
                .getRoot()
                .getFile(new Path(resourcePath));

        if (!sourceFile.exists()) {
            return null;
        }

        long currentStamp = sourceFile.getModificationStamp();

        /*
         * This catches source files that changed while Eclipse/the plug-in was
         * closed. A stale disk-cache entry is discarded and rebuilt instead of
         * silently navigating to a bean name that no longer exists.
         */
        if (modificationStamp != IResource.NULL_STAMP
                && currentStamp != IResource.NULL_STAMP
                && modificationStamp != currentStamp) {

            return null;
        }

        IProject project = ResourcesPlugin.getWorkspace()
                .getRoot()
                .getProject(projectName);

        if (!project.exists() || !project.isOpen()) {
            return null;
        }

        IJavaProject javaProject = JavaCore.create(project);

        try {
            if (!javaProject.exists()) {
                return null;
            }

            IType type = javaProject.findType(qualifiedTypeName);

            return type != null && type.exists()
                    ? type
                    : null;

        } catch (JavaModelException e) {
            return null;
        }
    }
}
