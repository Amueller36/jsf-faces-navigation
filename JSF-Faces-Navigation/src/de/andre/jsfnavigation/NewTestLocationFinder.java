package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public final class NewTestLocationFinder {

    private static final int MAX_RESULTS = 6;

    private NewTestLocationFinder() {
    }

    public static List<NewTestLocationCandidate> suggest(
            TestHelperAnalysis analysis,
            int mode,
            IProgressMonitor monitor) {

        if (analysis == null) {
            return Collections.emptyList();
        }

        String packageName =
                packageName(
                        analysis.getDeclaringType());

        String className =
                analysis.getSimpleDeclaringType()
                + (mode
                        == TestHelperSnippetGenerator.JPA_TEST
                                ? "JPATest"
                                : "Test");

        List<NewTestLocationCandidate> result =
                new ArrayList<NewTestLocationCandidate>();

        for (IProject project :
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .getProjects()) {

            if (monitor != null
                    && monitor.isCanceled()) {

                break;
            }

            if (!project.isAccessible()) {
                continue;
            }

            IJavaProject javaProject =
                    JavaCore.create(
                            project);

            if (javaProject == null
                    || !javaProject.exists()) {

                continue;
            }

            try {
                for (IPackageFragmentRoot root :
                        javaProject
                                .getPackageFragmentRoots()) {

                    if (root.getKind()
                            != IPackageFragmentRoot.K_SOURCE
                            || root.isArchive()) {

                        continue;
                    }

                    int score =
                            scoreProject(
                                    javaProject,
                                    mode)
                            + scoreRoot(
                                    root);

                    if (score < 0) {
                        continue;
                    }

                    result.add(
                            new NewTestLocationCandidate(
                                    javaProject,
                                    root,
                                    packageName,
                                    className,
                                    score));
                }

            } catch (JavaModelException e) {
                // Ignore broken/non-Java project entries.
            }
        }

        Collections.sort(
                result,
                new Comparator<NewTestLocationCandidate>() {
                    @Override
                    public int compare(
                            NewTestLocationCandidate left,
                            NewTestLocationCandidate right) {

                        int score =
                                right.getScore()
                                        - left.getScore();

                        if (score != 0) {
                            return score;
                        }

                        return left.getLabel()
                                .compareToIgnoreCase(
                                        right.getLabel());
                    }
                });

        if (result.size()
                > MAX_RESULTS) {

            return new ArrayList<NewTestLocationCandidate>(
                    result.subList(
                            0,
                            MAX_RESULTS));
        }

        return result;
    }

    private static int scoreProject(
            IJavaProject project,
            int mode) {

        String name =
                project.getElementName()
                        .toLowerCase(
                                Locale.ENGLISH);

        int score = 0;

        if (mode
                == TestHelperSnippetGenerator.JPA_TEST) {

            if (name.contains(
                    "testjpa")) {

                score += 260;

            } else if (name.contains(
                    "jpa")
                    && name.contains(
                            "test")) {

                score += 210;

            } else if (name.contains(
                    "junit")) {

                score += 80;

            } else if (name.contains(
                    "testejb")) {

                score += 50;

            } else if (name.contains(
                    "regression")) {

                score += 20;

            } else if (!name.contains(
                    "test")) {

                score -= 100;
            }

        } else {
            if (name.contains(
                    "junit")) {

                score += 260;

            } else if (name.contains(
                    "unit")
                    && name.contains(
                            "test")) {

                score += 220;

            } else if (name.contains(
                    "testjpa")
                    || name.contains(
                            "testejb")
                    || name.contains(
                            "regression")) {

                score -= 80;

            } else if (name.contains(
                    "test")) {

                score += 70;

            } else {
                score -= 100;
            }
        }

        return score;
    }

    private static int scoreRoot(
            IPackageFragmentRoot root) {

        String path =
                root.getPath()
                        .toPortableString()
                        .toLowerCase(
                                Locale.ENGLISH);

        int score = 0;

        if (path.endsWith(
                "/src/test/java")) {

            score += 90;

        } else if (path.contains(
                "/src/test")) {

            score += 70;

        } else if (path.endsWith(
                "/src")) {

            score += 45;

        } else if (path.contains(
                "test")) {

            score += 30;
        }

        return score;
    }

    private static String packageName(
            String qualifiedType) {

        if (qualifiedType == null) {
            return "";
        }

        int dot =
                qualifiedType.lastIndexOf('.');

        String packageName =
                dot > 0
                        ? qualifiedType.substring(
                                0,
                                dot)
                        : "";

        return PackageMigrationRules
                .forNewTestPackage(
                        packageName);
    }
}
