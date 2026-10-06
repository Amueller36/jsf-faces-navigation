package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestHelperSnippetGenerator {

    public static final int MOCK_HELPER = 0;
    public static final int UNIT_TEST = 1;
    public static final int JPA_TEST = 2;

    private TestHelperSnippetGenerator() {
    }

    public static String[] modeNames() {
        return new String[] {
                "Mockito mock helper method",
                "Mockito unit test scaffold",
                "JPA test scaffold"
        };
    }

    public static int defaultMode(
            TestHelperAnalysis analysis) {

        return analysis != null
                && analysis.isJpaDetected()
                        ? JPA_TEST
                        : MOCK_HELPER;
    }

    public static String generate(
            TestHelperAnalysis analysis,
            int mode) {

        if (analysis == null) {
            return "// No method analysis available.";
        }

        switch (mode) {
            case UNIT_TEST:
                return unitTest(
                        analysis);

            case JPA_TEST:
                return jpaTest(
                        analysis);

            case MOCK_HELPER:
            default:
                return mockHelper(
                        analysis);
        }
    }

    public static String generateBatch(
            List<TestHelperAnalysis> analyses,
            int mode) {

        return generateBatch(
                analyses,
                mode,
                null,
                TestHelperLearnedFixtureReport.empty(),
                false);
    }

    public static String generateBatch(
            List<TestHelperAnalysis> analyses,
            int mode,
            TestTargetCandidate target,
            TestHelperLearnedFixtureReport learnedFixtures,
            boolean reuseLearnedGiven) {

        if (analyses == null
                || analyses.isEmpty()) {

            return "// No method analyses available.";
        }

        if (analyses.size() == 1) {
            if (mode == JPA_TEST
                    && reuseLearnedGiven
                    && target != null) {

                StringBuilder single =
                        new StringBuilder();

                appendJpaTestMethod(
                        single,
                        analyses.get(0),
                        true,
                        target,
                        learnedFixtures,
                        true);

                return single.toString();
            }

            return generate(
                    analyses.get(0),
                    mode);
        }

        StringBuilder out =
                new StringBuilder();

        TestHelperAnalysis first =
                analyses.get(0);

        out.append(
                "// Generated test templates for ")
                .append(
                        first.getSimpleDeclaringType())
                .append(
                        " — ")
                .append(
                        analyses.size())
                .append(
                        " selected methods.\n")
                .append(
                        "// Shared setup/dependency hints are emitted once; each test keeps only // Given / // When / // Then.\n\n");

        if (mode == UNIT_TEST) {
            appendSharedMockitoFields(
                    out,
                    analyses);

            for (TestHelperAnalysis analysis :
                    analyses) {

                appendUnitTestMethod(
                        out,
                        analysis);
                out.append('\n');
            }

        } else if (mode == JPA_TEST) {
            List<TestHelperFixtureDependency> fixtures =
                    mergedFixtureDependencies(
                            analyses);

            appendFixtureSummary(
                    out,
                    fixtures,
                    "// ");

            if (!fixtures.isEmpty()) {
                out.append('\n');
            }

            for (TestHelperAnalysis analysis :
                    analyses) {

                appendJpaTestMethod(
                        out,
                        analysis,
                        false,
                        target,
                        learnedFixtures,
                        reuseLearnedGiven);
                out.append('\n');
            }

        } else {
            for (TestHelperAnalysis analysis :
                    analyses) {

                out.append(
                        "// ")
                        .append(
                                analysis.getMethodName())
                        .append(
                                "(...)\n");

                appendMockHelperBody(
                        out,
                        analysis);
                out.append('\n');
            }
        }

        if (anyTruncated(
                analyses)) {

            out.append(
                    "// NOTE: at least one dependency traversal hit its safety bound; review deeper helper/query dependencies manually.\n");
        }

        return out.toString()
                .trim()
                + "\n";
    }

    private static String mockHelper(
            TestHelperAnalysis analysis) {

        StringBuilder out =
                new StringBuilder();

        header(
                out,
                analysis);

        if (analysis.getDependencies()
                .isEmpty()) {

            out.append(
                    "// No field-based collaborators were detected for this method.\n")
                    .append(
                            "// If the method delegates through dynamic lookups/static calls, add those manually.\n\n");
        } else {
            out.append(
                    "// Mocks required by the selected method path:\n");

            for (TestHelperDependency dependency :
                    analysis.getDependencies()) {

                out.append(
                        "@Mock\nprivate ")
                        .append(
                                dependency.getFieldType())
                        .append(' ')
                        .append(
                                dependency.getFieldName())
                        .append(
                                ";\n\n");
            }
        }

        out.append(
                "private void mock")
                .append(
                        capitalize(
                                analysis.getMethodName()))
                .append(
                        "Dependencies() {\n");

        boolean wroteStub = false;

        for (TestHelperDependency dependency :
                analysis.getDependencies()) {

            for (TestHelperInvocation invocation :
                    dependency.getInvocations()) {

                if (invocation.isVoidReturn()) {
                    out.append(
                            "    // ")
                            .append(
                                    dependency.getFieldName())
                            .append('.')
                            .append(
                                    invocation.getMethodName())
                            .append(
                                    '(')
                            .append(
                                    matchers(
                                            invocation.getParameterTypes()))
                            .append(
                                    "); is void; stub only if this test needs special behavior.\n");

                    continue;
                }

                wroteStub = true;

                out.append(
                        "    when(")
                        .append(
                                dependency.getFieldName())
                        .append('.')
                        .append(
                                invocation.getMethodName())
                        .append('(')
                        .append(
                                matchers(
                                        invocation.getParameterTypes()))
                        .append(
                                ")).thenReturn(")
                        .append(
                                todoReturnValue(
                                        invocation.getReturnType()))
                        .append(
                                ");\n");
            }
        }

        if (!wroteStub
                && analysis.getDependencies()
                        .isEmpty()) {

            out.append(
                    "    // TODO add project-specific mock behavior if needed.\n");
        }

        out.append(
                "}\n");

        footer(
                out,
                analysis);

        return out.toString();
    }

    private static String unitTest(
            TestHelperAnalysis analysis) {

        StringBuilder out =
                new StringBuilder();

        header(
                out,
                analysis);

        appendSharedMockitoFields(
                out,
                java.util.Collections.singletonList(
                        analysis));

        appendUnitTestMethod(
                out,
                analysis);

        footer(
                out,
                analysis);

        return out.toString();
    }

    private static void appendUnitTestMethod(
            StringBuilder out,
            TestHelperAnalysis analysis) {

        out.append(
                "@Test\npublic void ")
                .append(
                        analysis.getMethodName())
                .append(
                        "_shouldTODO() {\n")
                .append(
                        "    // Given\n");

        appendParameters(
                out,
                analysis.getParameters(),
                "    ");

        for (TestHelperDependency dependency :
                analysis.getDependencies()) {

            for (TestHelperInvocation invocation :
                    dependency.getInvocations()) {

                if (invocation.isVoidReturn()) {
                    continue;
                }

                out.append(
                        "    when(")
                        .append(
                                dependency.getFieldName())
                        .append('.')
                        .append(
                                invocation.getMethodName())
                        .append('(')
                        .append(
                                matchers(
                                        invocation.getParameterTypes()))
                        .append(
                                ")).thenReturn(")
                        .append(
                                todoReturnValue(
                                        invocation.getReturnType()))
                        .append(
                                ");\n");
            }
        }

        out.append(
                "\n    // When\n");

        appendSubjectCall(
                out,
                analysis,
                "    ");

        out.append(
                "\n    // Then\n");

        if (!isVoid(
                analysis.getReturnType())) {

            out.append(
                    "    assertNotNull(result); // TODO choose the assertion that proves the behavior\n");
        } else {
            out.append(
                    "    // TODO assert observable state/side effects\n");
        }

        for (TestHelperDependency dependency :
                analysis.getDependencies()) {

            for (TestHelperInvocation invocation :
                    dependency.getInvocations()) {

                out.append(
                        "    verify(")
                        .append(
                                dependency.getFieldName())
                        .append(").")
                        .append(
                                invocation.getMethodName())
                        .append('(')
                        .append(
                                matchers(
                                        invocation.getParameterTypes()))
                        .append(
                                "); // keep only if this interaction is part of the contract\n");
            }
        }

        out.append(
                "}\n");
    }

    private static String jpaTest(
            TestHelperAnalysis analysis) {

        StringBuilder out =
                new StringBuilder();

        header(
                out,
                analysis);

        appendJpaTestMethod(
                out,
                analysis,
                true);

        footer(
                out,
                analysis);

        return out.toString();
    }

    private static void appendJpaTestMethod(
            StringBuilder out,
            TestHelperAnalysis analysis,
            boolean includeFixtureSummary) {

        appendJpaTestMethod(
                out,
                analysis,
                includeFixtureSummary,
                null,
                TestHelperLearnedFixtureReport.empty(),
                false);
    }

    private static void appendJpaTestMethod(
            StringBuilder out,
            TestHelperAnalysis analysis,
            boolean includeFixtureSummary,
            TestTargetCandidate target,
            TestHelperLearnedFixtureReport learnedFixtures,
            boolean reuseLearnedGiven) {

        TestHelperLearnedFixtureReport learned =
                learnedFixtures == null
                        ? TestHelperLearnedFixtureReport.empty()
                        : learnedFixtures;

        TestHelperLearnedFixtureRecipe recipe =
                reuseLearnedGiven
                        ? learned.bestRecipe(
                                analysis,
                                target)
                        : null;

        TestHelperLearnedTargetStyle style =
                learned.styleFor(
                        target);

        out.append(
                "@Test\npublic void ")
                .append(
                        analysis.getMethodName())
                .append(
                        "_shouldTODO() {\n")
                .append(
                        "    // Given\n");

        if (includeFixtureSummary
                && recipe == null) {

            appendFixtureSummary(
                    out,
                    analysis.getFixtureDependencies(),
                    "    // ");
        }

        if (recipe != null) {
            appendLearnedGivenRecipe(
                    out,
                    recipe,
                    "    ");
        }

        appendParameters(
                out,
                analysis.getParameters(),
                "    ",
                recipe);

        if (recipe == null) {
            if (analysis.isJpaWriteDetected()) {
                out.append(
                        "    // TODO create/persist prerequisite entities through the project's cleanup-tracked helper (for example insertNewEntity/persistEntity).\n");
            } else {
                out.append(
                        "    // TODO create the prerequisite entity graph/input TOs listed above.\n");
            }

        } else if (recipe.coverageCount(
                analysis.getFixtureDependencies())
                < analysis.getFixtureDependencies()
                        .size()) {

            out.append(
                    "    // TODO complete any remaining fixture dependencies not covered by the learned Given recipe.\n");
        }

        if (analysis.isJpaReadDetected()
                && !recipeContainsPersistenceBoundary(
                        recipe)) {

            out.append(
                    "    // TODO flush + clear/renew the persistence context if the assertion must prove a real DB round-trip.\n");
        }

        out.append(
                "\n    // When\n");

        appendSubjectCall(
                out,
                analysis,
                "    ",
                style);

        out.append(
                "\n    // Then\n");

        if (!isVoid(
                analysis.getReturnType())) {

            appendNotNullAssertion(
                    out,
                    style,
                    "    ");
        } else {
            out.append(
                    "    // TODO query/read back state after the intended transaction/PC boundary\n");
        }

        out.append(
                "}\n");
    }

    private static void appendLearnedGivenRecipe(
            StringBuilder out,
            TestHelperLearnedFixtureRecipe recipe,
            String indent) {

        if (recipe == null) {
            return;
        }

        for (String statement :
                recipe.getStatements()) {

            appendIndentedMultiline(
                    out,
                    statement,
                    indent);

            if (!statement.endsWith(
                    "\n")) {

                out.append('\n');
            }
        }
    }

    private static void appendIndentedMultiline(
            StringBuilder out,
            String value,
            String indent) {

        String normalized =
                value == null
                        ? ""
                        : value.replace(
                                "\r\n",
                                "\n")
                                .replace(
                                        '\r',
                                        '\n');

        String[] lines =
                normalized.split(
                        "\\n",
                        -1);

        for (int i = 0;
                i < lines.length;
                i++) {

            if (!lines[i].isEmpty()) {
                out.append(
                        indent);
            }

            out.append(
                    lines[i]);

            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }
    }

    private static boolean recipeContainsPersistenceBoundary(
            TestHelperLearnedFixtureRecipe recipe) {

        if (recipe == null) {
            return false;
        }

        for (String statement :
                recipe.getStatements()) {

            String lower =
                    statement.toLowerCase();

            if (lower.contains(
                    "flush(")
                    || lower.contains(
                            "clear(")
                    || lower.contains(
                            "renewpersistencecontext")) {

                return true;
            }
        }

        return false;
    }

    private static void appendNotNullAssertion(
            StringBuilder out,
            TestHelperLearnedTargetStyle style,
            String indent) {

        int assertionStyle =
                style == null
                        ? TestHelperLearnedTargetStyle.ASSERT_JUNIT
                        : style.getAssertionStyle();

        if (assertionStyle
                == TestHelperLearnedTargetStyle.SOFT_ASSERTJ) {

            out.append(
                    indent)
                    .append(
                            "softly.assertThat(result).isNotNull();\n");

        } else if (assertionStyle
                == TestHelperLearnedTargetStyle.ASSERTJ) {

            out.append(
                    indent)
                    .append(
                            "assertThat(result).isNotNull();\n");

        } else {
            out.append(
                    indent)
                    .append(
                            "assertNotNull(result);\n");
        }
    }

    private static void appendFixtureSummary(
            StringBuilder out,
            List<TestHelperFixtureDependency> fixtures,
            String prefix) {

        if (fixtures == null
                || fixtures.isEmpty()) {

            return;
        }

        out.append(prefix)
                .append(
                        "Detected fixture/query dependencies (review):\n");

        for (TestHelperFixtureDependency fixture :
                fixtures) {

            out.append(prefix)
                    .append("- [")
                    .append(
                            fixture.getKind())
                    .append("] ")
                    .append(
                            fixture.getSimpleType());

            if (!fixture.getPrimaryReason()
                    .isEmpty()) {

                out.append(
                        " — ")
                        .append(
                                fixture.getPrimaryReason());
            }

            out.append('\n');
        }
    }

    private static void appendSharedMockitoFields(
            StringBuilder out,
            List<TestHelperAnalysis> analyses) {

        Map<String, TestHelperDependency> dependencies =
                new LinkedHashMap<String, TestHelperDependency>();

        for (TestHelperAnalysis analysis :
                analyses) {

            for (TestHelperDependency dependency :
                    analysis.getDependencies()) {

                dependencies.put(
                        dependency.getFieldName(),
                        dependency);
            }
        }

        for (TestHelperDependency dependency :
                dependencies.values()) {

            out.append(
                    "@Mock\nprivate ")
                    .append(
                            dependency.getFieldType())
                    .append(' ')
                    .append(
                            dependency.getFieldName())
                    .append(
                            ";\n\n");
        }

        TestHelperAnalysis first =
                analyses.get(0);

        out.append(
                "@InjectMocks\nprivate ")
                .append(
                        first.getSimpleDeclaringType())
                .append(
                        " subject;\n\n");
    }

    private static List<TestHelperFixtureDependency> mergedFixtureDependencies(
            List<TestHelperAnalysis> analyses) {

        Map<String, TestHelperFixtureDependency> unique =
                new LinkedHashMap<String, TestHelperFixtureDependency>();

        for (TestHelperAnalysis analysis :
                analyses) {

            for (TestHelperFixtureDependency fixture :
                    analysis.getFixtureDependencies()) {

                if (!unique.containsKey(
                        fixture.getQualifiedType())) {

                    unique.put(
                            fixture.getQualifiedType(),
                            fixture);
                }
            }
        }

        return new ArrayList<TestHelperFixtureDependency>(
                unique.values());
    }

    private static boolean anyTruncated(
            List<TestHelperAnalysis> analyses) {

        for (TestHelperAnalysis analysis :
                analyses) {

            if (analysis.isTruncated()) {
                return true;
            }
        }

        return false;
    }

    private static void appendMockHelperBody(
            StringBuilder out,
            TestHelperAnalysis analysis) {

        out.append(
                "private void mock")
                .append(
                        capitalize(
                                analysis.getMethodName()))
                .append(
                        "Dependencies() {\n");

        boolean wrote = false;

        for (TestHelperDependency dependency :
                analysis.getDependencies()) {

            for (TestHelperInvocation invocation :
                    dependency.getInvocations()) {

                if (invocation.isVoidReturn()) {
                    continue;
                }

                wrote = true;
                out.append(
                        "    when(")
                        .append(
                                dependency.getFieldName())
                        .append('.')
                        .append(
                                invocation.getMethodName())
                        .append('(')
                        .append(
                                matchers(
                                        invocation.getParameterTypes()))
                        .append(
                                ")).thenReturn(")
                        .append(
                                todoReturnValue(
                                        invocation.getReturnType()))
                        .append(
                                ");\n");
            }
        }

        if (!wrote) {
            out.append(
                    "    // TODO add project-specific mock behavior if needed.\n");
        }

        out.append(
                "}\n");
    }

    private static void header(
            StringBuilder out,
            TestHelperAnalysis analysis) {

        out.append(
                "// Generated from ")
                .append(
                        analysis.getSimpleDeclaringType())
                .append('.')
                .append(
                        analysis.getMethodName())
                .append(
                        "(...)\n")
                .append(
                        "// Copy/paste helper: keep your project's existing JUnit/Mockito imports and naming conventions.\n");

        if (analysis.isJpaDetected()) {
            out.append(
                    "// JPA usage detected on the selected method/helper path.\n");
        }

        out.append('\n');
    }

    private static void footer(
            StringBuilder out,
            TestHelperAnalysis analysis) {

        if (analysis.isTruncated()) {
            out.append(
                    "\n// NOTE: dependency-helper traversal hit its safety bound; deeper internal helper calls were not analyzed.\n");
        }

        out.append(
                "\n// Mockito matcher note: `any(...)` assumes the static matcher imports already used by your project.\n");
    }

    private static void appendParameters(
            StringBuilder out,
            List<TestHelperParameter> parameters,
            String indent) {

        appendParameters(
                out,
                parameters,
                indent,
                null);
    }

    private static void appendParameters(
            StringBuilder out,
            List<TestHelperParameter> parameters,
            String indent,
            TestHelperLearnedFixtureRecipe recipe) {

        for (TestHelperParameter parameter :
                parameters) {

            if (recipe != null
                    && recipe.declares(
                            parameter.getName())) {

                continue;
            }

            out.append(
                    indent)
                    .append(
                            parameter.getType()
                                    .replace(
                                            "...",
                                            "[]"))
                    .append(' ')
                    .append(
                            parameter.getName())
                    .append(
                            " = ")
                    .append(
                            parameterValue(
                                    parameter.getType()))
                    .append(
                            "; // TODO\n");
        }
    }

    private static void appendSubjectCall(
            StringBuilder out,
            TestHelperAnalysis analysis,
            String indent) {

        appendSubjectCall(
                out,
                analysis,
                indent,
                null);
    }

    private static void appendSubjectCall(
            StringBuilder out,
            TestHelperAnalysis analysis,
            String indent,
            TestHelperLearnedTargetStyle style) {

        if (!isVoid(
                analysis.getReturnType())) {

            out.append(
                    indent)
                    .append(
                            analysis.getReturnType())
                    .append(
                            " result = ");
        } else {
            out.append(
                    indent);
        }

        String subjectExpression =
                style == null
                        || style.getSubjectExpression()
                                .isEmpty()
                                ? "subject"
                                : style.getSubjectExpression();

        out.append(
                subjectExpression)
                .append('.')
                .append(
                        analysis.getMethodName())
                .append('(');

        for (int i = 0;
                i < analysis.getParameters()
                        .size();
                i++) {

            if (i > 0) {
                out.append(
                        ", ");
            }

            out.append(
                    analysis.getParameters()
                            .get(i)
                            .getName());
        }

        out.append(
                ");\n");
    }

    private static String matchers(
            List<String> parameterTypes) {

        StringBuilder out =
                new StringBuilder();

        for (int i = 0;
                i < parameterTypes.size();
                i++) {

            if (i > 0) {
                out.append(
                        ", ");
            }

            out.append(
                    matcher(
                            parameterTypes.get(i)));
        }

        return out.toString();
    }

    private static String matcher(
            String type) {

        String normalized =
                type == null
                        ? ""
                        : type.replace(
                                "...",
                                "[]");

        if ("boolean".equals(
                normalized)) {

            return "anyBoolean()";
        }

        if ("byte".equals(normalized)) {
            return "anyByte()";
        }

        if ("short".equals(normalized)) {
            return "anyShort()";
        }

        if ("int".equals(normalized)) {
            return "anyInt()";
        }

        if ("long".equals(normalized)) {
            return "anyLong()";
        }

        if ("float".equals(normalized)) {
            return "anyFloat()";
        }

        if ("double".equals(normalized)) {
            return "anyDouble()";
        }

        if ("char".equals(normalized)) {
            return "anyChar()";
        }

        if ("String".equals(normalized)) {
            return "anyString()";
        }

        if ("Object".equals(normalized)
                || normalized.isEmpty()) {

            return "any()";
        }

        return "any("
                + normalized
                + ".class)";
    }

    private static String todoReturnValue(
            String type) {

        return defaultValue(
                type,
                "/* TODO "
                        + type
                        + " */ ");
    }

    private static String parameterValue(
            String type) {

        return defaultValue(
                type,
                "");
    }

    private static String defaultValue(
            String type,
            String prefix) {

        String normalized =
                type == null
                        ? ""
                        : type.replace(
                                "...",
                                "[]");

        if ("boolean".equals(normalized)) {
            return prefix + "false";
        }

        if ("byte".equals(normalized)
                || "short".equals(normalized)
                || "int".equals(normalized)
                || "long".equals(normalized)
                || "float".equals(normalized)
                || "double".equals(normalized)) {

            return prefix + "0";
        }

        if ("char".equals(normalized)) {
            return prefix + "'\\0'";
        }

        if ("String".equals(normalized)) {
            return prefix + "\"TODO\"";
        }

        if (normalized.endsWith(
                "[]")) {

            String component =
                    normalized.substring(
                            0,
                            normalized.length() - 2);

            return prefix
                    + "new "
                    + component
                    + "[0]";
        }

        return prefix + "null";
    }

    private static boolean isVoid(
            String type) {

        return "void".equals(
                type);
    }

    private static String capitalize(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "Method";
        }

        return Character.toUpperCase(
                value.charAt(0))
                + value.substring(1);
    }
}
