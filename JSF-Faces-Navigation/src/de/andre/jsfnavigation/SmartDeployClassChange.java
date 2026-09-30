package de.andre.jsfnavigation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;

public final class SmartDeployClassChange {

    private final IFile classFile;
    private final IPath outputRoot;
    private final IPath relativeClassPath;

    public SmartDeployClassChange(
            IFile classFile,
            IPath outputRoot,
            IPath relativeClassPath) {

        this.classFile = classFile;
        this.outputRoot = outputRoot;
        this.relativeClassPath = relativeClassPath;
    }

    public IFile getClassFile() {
        return classFile;
    }

    public IPath getOutputRoot() {
        return outputRoot;
    }

    public IPath getRelativeClassPath() {
        return relativeClassPath;
    }

    public String mappingKey() {
        return outputRoot.toPortableString();
    }
}
