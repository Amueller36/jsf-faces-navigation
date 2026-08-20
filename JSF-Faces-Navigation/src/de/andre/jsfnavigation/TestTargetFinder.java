package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

public final class TestTargetFinder {

    private static final int MAX_RESULTS = 12;

    private TestTargetFinder() {
    }

    public static List<TestTargetCandidate> find(
            TestHelperAnalysis analysis,
            IProgressMonitor monitor) {

        if (analysis == null
                || analysis.getDeclaringType()
                        .isEmpty()) {

            return Collections.emptyList();
        }

        final String productionSimple =
                analysis.getSimpleDeclaringType();

        final String productionPackage =
                packageName(
                        analysis.getDeclaringType());

        final Map<String, TestTargetCandidate>
                unique =
                        new LinkedHashMap<String, TestTargetCandidate>();

        IJavaSearchScope scope =
                SearchEngine.createWorkspaceScope();

        SearchEngine engine =
                new SearchEngine();

        TypeNameRequestor requestor =
                new TypeNameRequestor() {
                    @Override
                    public void acceptType(
                            int modifiers,
                            char[] packageName,
                            char[] simpleTypeName,
                            char[][] enclosingTypeNames,
                            String path) {

                        if (enclosingTypeNames != null
                                && enclosingTypeNames.length > 0) {

                            return;
                        }

                        String simple =
                                new String(
                                        simpleTypeName);

                        if (!looksLikeTestFor(
                                productionSimple,
                                simple)) {

                            return;
                        }

                        IType type =
                                sourceType(
                                        path,
                                        simple);

                        if (type == null
                                || !type.exists()) {

                            return;
                        }

                        try {
                            int classification =
                                    FlowTestClassifier
                                            .classify(
                                                    type);

                            if (classification
                                    == FlowTestClassifier.NOT_TEST) {

                                return;
                            }

                            int score =
                                    score(
                                            type,
                                            classification,
                                            productionSimple,
                                            productionPackage);

                            TestTargetCandidate candidate =
                                    new TestTargetCandidate(
                                            type,
                                            classification,
                                            score);

                            unique.put(
                                    type.getHandleIdentifier(),
                                    candidate);

                        } catch (Exception e) {
                            // Ignore malformed/incomplete candidate types.
                        }
                    }
                };

        try {
            engine.searchAllTypeNames(
                    null,
                    SearchPattern.R_EXACT_MATCH,
                    productionSimple
                            .toCharArray(),
                    SearchPattern.R_PREFIX_MATCH
                            | SearchPattern.R_CASE_SENSITIVE,
                    IJavaSearchConstants.TYPE,
                    scope,
                    requestor,
                    IJavaSearchConstants
                            .WAIT_UNTIL_READY_TO_SEARCH,
                    monitor);

            if (monitor == null
                    || !monitor.isCanceled()) {

                engine.searchAllTypeNames(
                        null,
                        SearchPattern.R_EXACT_MATCH,
                        ("Test"
                                + productionSimple)
                                .toCharArray(),
                        SearchPattern.R_EXACT_MATCH
                                | SearchPattern.R_CASE_SENSITIVE,
                        IJavaSearchConstants.TYPE,
                        scope,
                        requestor,
                        IJavaSearchConstants
                                .WAIT_UNTIL_READY_TO_SEARCH,
                        monitor);
            }

        } catch (JavaModelException e) {
            return Collections.emptyList();
        }

        List<TestTargetCandidate> result =
                new ArrayList<TestTargetCandidate>(
                        unique.values());

        Collections.sort(
                result,
                new Comparator<TestTargetCandidate>() {
                    @Override
                    public int compare(
                            TestTargetCandidate left,
                            TestTargetCandidate right) {

                        int scoreCompare =
                                right.getScore()
                                        - left.getScore();

                        if (scoreCompare != 0) {
                            return scoreCompare;
                        }

                        return left.getLabel()
                                .compareToIgnoreCase(
                                        right.getLabel());
                    }
                });

        if (result.size()
                > MAX_RESULTS) {

            return new ArrayList<TestTargetCandidate>(
                    result.subList(
                            0,
                            MAX_RESULTS));
        }

        return result;
    }

    public static int bestIndexForMode(
            List<TestTargetCandidate> candidates,
            int mode) {

        if (candidates == null
                || candidates.isEmpty()) {

            return -1;
        }

        int bestIndex = 0;
        int bestScore =
                Integer.MIN_VALUE;

        for (int i = 0;
                i < candidates.size();
                i++) {

            TestTargetCandidate candidate =
                    candidates.get(i);

            int score =
                    candidate.getScore();

            if (mode
                    == TestHelperSnippetGenerator.JPA_TEST) {

                if (candidate.getClassification()
                        == FlowTestClassifier.JPA_TEST) {

                    score += 120;
                }

                String project =
                        projectName(
                                candidate);

                if (project.contains(
                        "testjpa")) {

                    score += 90;
                }

            } else {
                if (candidate.getClassification()
                        == FlowTestClassifier.UNIT_TEST) {

                    score += 100;
                }

                String project =
                        projectName(
                                candidate);

                if (project.contains(
                        "junit")) {

                    score += 80;
                }

                if (project.contains(
                        "testjpa")
                        || project.contains(
                                "testejb")
                        || project.contains(
                                "regression")) {

                    score -= 70;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private static int score(
            IType type,
            int classification,
            String productionSimple,
            String productionPackage) {

        int score = 0;

        String simple =
                type.getElementName();

        if ((productionSimple
                + "Test").equals(
                        simple)) {

            score += 150;

        } else if ((productionSimple
                + "Tests").equals(
                        simple)) {

            score += 140;

        } else if ((productionSimple
                + "JPATest").equalsIgnoreCase(
                        simple)) {

            score += 130;

        } else if ((productionSimple
                + "IntegrationTest")
                .equalsIgnoreCase(
                        simple)) {

            score += 110;

        } else if (simple.toLowerCase(
                Locale.ENGLISH)
                .startsWith(
                        productionSimple
                                .toLowerCase(
                                        Locale.ENGLISH))) {

            score += 80;
        }

        String candidatePackage =
                type.getPackageFragment() == null
                        ? ""
                        : type.getPackageFragment()
                                .getElementName();

        if (!productionPackage.isEmpty()
                && productionPackage.equals(
                        candidatePackage)) {

            score += 65;

        } else if (!productionPackage.isEmpty()
                && commonPackagePrefix(
                        productionPackage,
                        candidatePackage)
                        >= 3) {

            score += 30;
        }

        String project =
                type.getJavaProject() == null
                        ? ""
                        : type.getJavaProject()
                                .getElementName()
                                .toLowerCase(
                                        Locale.ENGLISH);

        if (project.contains(
                "junit")) {

            score += 35;
        }

        if (project.contains(
                "testjpa")) {

            score += 30;
        }

        if (project.contains(
                "testejb")) {

            score += 20;
        }

        if (project.contains(
                "regression")) {

            score += 10;
        }

        if (classification
                == FlowTestClassifier.UNIT_TEST) {

            score += 25;

        } else if (classification
                == FlowTestClassifier.JPA_TEST) {

            score += 20;
        }

        return score;
    }

    private static boolean looksLikeTestFor(
            String productionSimple,
            String candidate) {

        String production =
                productionSimple.toLowerCase(
                        Locale.ENGLISH);

        String test =
                candidate.toLowerCase(
                        Locale.ENGLISH);

        return (test.startsWith(
                production)
                && (test.contains(
                        "test")
                        || test.endsWith(
                                "it")))
                || test.equals(
                        "test"
                        + production);
    }

    private static IType sourceType(
            String path,
            String simpleName) {

        if (path == null
                || path.isEmpty()) {

            return null;
        }

        IResource resource =
                ResourcesPlugin.getWorkspace()
                        .getRoot()
                        .findMember(
                                new org.eclipse.core.runtime.Path(
                                        path));

        if (!(resource
                instanceof IFile)) {

            return null;
        }

        ICompilationUnit unit =
                JavaCore.createCompilationUnitFrom(
                        (IFile)
                                resource);

        if (unit == null
                || !unit.exists()) {

            return null;
        }

        IType type =
                unit.getType(
                        simpleName);

        return type.exists()
                ? type
                : null;
    }

    private static String packageName(
            String qualifiedType) {

        int dot =
                qualifiedType == null
                        ? -1
                        : qualifiedType
                                .lastIndexOf('.');

        return dot > 0
                ? qualifiedType.substring(
                        0,
                        dot)
                : "";
    }

    private static int commonPackagePrefix(
            String left,
            String right) {

        String[] a =
                left.split(
                        "\\.");

        String[] b =
                right.split(
                        "\\.");

        int count = 0;

        while (count < a.length
                && count < b.length
                && a[count].equals(
                        b[count])) {

            count++;
        }

        return count;
    }

    private static String projectName(
            TestTargetCandidate candidate) {

        if (candidate == null
                || candidate.getType() == null
                || candidate.getType()
                        .getJavaProject() == null) {

            return "";
        }

        return candidate.getType()
                .getJavaProject()
                .getElementName()
                .toLowerCase(
                        Locale.ENGLISH);
    }
}
