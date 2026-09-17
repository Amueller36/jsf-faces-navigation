package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;

public final class NewTestLocationCandidate {

    private final IJavaProject javaProject;
    private final IPackageFragmentRoot sourceRoot;
    private final String packageName;
    private final String className;
    private final int score;

    public NewTestLocationCandidate(
            IJavaProject javaProject,
            IPackageFragmentRoot sourceRoot,
            String packageName,
            String className,
            int score) {

        this.javaProject = javaProject;
        this.sourceRoot = sourceRoot;
        this.packageName =
                packageName == null
                        ? ""
                        : packageName;
        this.className =
                className == null
                        ? "GeneratedTest"
                        : className;
        this.score = score;
    }

    public IJavaProject getJavaProject() {
        return javaProject;
    }

    public IPackageFragmentRoot getSourceRoot() {
        return sourceRoot;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public int getScore() {
        return score;
    }

    public String getLabel() {
        String project =
                javaProject == null
                        ? ""
                        : javaProject
                                .getElementName();

        String root =
                sourceRoot == null
                        ? ""
                        : sourceRoot
                                .getPath()
                                .toPortableString();

        StringBuilder out =
                new StringBuilder();

        out.append(project)
                .append("  →  ")
                .append(root)
                .append("  →  ");

        if (!packageName.isEmpty()) {
            out.append(
                    packageName.replace(
                            '.',
                            '/'))
                    .append('/');
        }

        out.append(className)
                .append(".java");

        return out.toString();
    }
}
