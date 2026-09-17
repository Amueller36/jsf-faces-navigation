package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class SmartDeployOutputResolver {

    private SmartDeployOutputResolver() {
    }

    public static SmartDeployClassChange resolve(
            IFile classFile) {

        if (classFile == null
                || !"class".equalsIgnoreCase(
                        classFile.getFileExtension())) {

            return null;
        }

        IJavaProject javaProject =
                JavaCore.create(
                        classFile.getProject());

        if (javaProject == null
                || !javaProject.exists()) {

            return null;
        }

        try {
            List<IPath> outputs =
                    new ArrayList<IPath>();

            addUnique(
                    outputs,
                    javaProject.getOutputLocation());

            for (IClasspathEntry entry :
                    javaProject.getRawClasspath()) {

                if (entry.getEntryKind()
                        == IClasspathEntry.CPE_SOURCE
                        && entry.getOutputLocation()
                                != null) {

                    addUnique(
                            outputs,
                            entry.getOutputLocation());
                }
            }

            IPath fullPath =
                    classFile.getFullPath();

            IPath best = null;

            for (IPath output : outputs) {
                if (output != null
                        && output.isPrefixOf(fullPath)
                        && (best == null
                                || output.segmentCount()
                                        > best.segmentCount())) {

                    best = output;
                }
            }

            if (best == null) {
                return null;
            }

            IPath relative =
                    fullPath.removeFirstSegments(
                            best.segmentCount());

            if (relative.segmentCount() == 0) {
                return null;
            }

            return new SmartDeployClassChange(
                    classFile,
                    best,
                    relative);

        } catch (JavaModelException e) {
            return null;
        }
    }

    private static void addUnique(
            List<IPath> paths,
            IPath path) {

        if (path != null
                && !paths.contains(path)) {

            paths.add(path);
        }
    }
}
