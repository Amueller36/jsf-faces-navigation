package de.andre.jsfnavigation;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

public final class NewTestClassCreator {

    private NewTestClassCreator() {
    }

    public static IType create(
            NewTestLocationCandidate location,
            IProgressMonitor monitor)
            throws JavaModelException {

        if (location == null
                || location.getSourceRoot() == null
                || location.getJavaProject() == null) {

            return null;
        }

        IPackageFragmentRoot root =
                location.getSourceRoot();

        IPackageFragment packageFragment =
                root.createPackageFragment(
                        location.getPackageName(),
                        true,
                        monitor);

        String unitName =
                location.getClassName()
                + ".java";

        ICompilationUnit existing =
                packageFragment
                        .getCompilationUnit(
                                unitName);

        if (existing.exists()) {
            IType type =
                    existing.getType(
                            location.getClassName());

            return type.exists()
                    ? type
                    : null;
        }

        String source =
                source(
                        location);

        ICompilationUnit unit =
                packageFragment
                        .createCompilationUnit(
                                unitName,
                                source,
                                false,
                                monitor);

        IType type =
                unit.getType(
                        location.getClassName());

        return type.exists()
                ? type
                : null;
    }

    private static String source(
            NewTestLocationCandidate location)
            throws JavaModelException {

        StringBuilder out =
                new StringBuilder();

        if (!location.getPackageName()
                .isEmpty()) {

            out.append(
                    "package ")
                    .append(
                            location.getPackageName())
                    .append(
                            ";\n\n");
        }

        IJavaProject project =
                location.getJavaProject();

        boolean junit5 =
                project.findType(
                        "org.junit.jupiter.api.Test")
                        != null;

        boolean junit4 =
                !junit5
                && project.findType(
                        "org.junit.Test")
                        != null;

        if (junit5) {
            out.append(
                    "import org.junit.jupiter.api.Test;\n\n");

        } else if (junit4) {
            out.append(
                    "import org.junit.Test;\n\n");
        }

        out.append(
                "public class ")
                .append(
                        location.getClassName())
                .append(
                        " {\n\n");

        if (junit5 || junit4) {
            out.append(
                    "    // Test class created by JSF / Java Navigation.\n")
                    .append(
                            "    // Use Generate Test Helper... on a production method to add a scaffold.\n\n");

        } else {
            out.append(
                    "    // TODO: No JUnit Test annotation type was resolved on this project's classpath.\n")
                    .append(
                            "    // Add the project's normal JUnit setup, then generate the test methods.\n\n");
        }

        out.append(
                "}\n");

        return out.toString();
    }
}
