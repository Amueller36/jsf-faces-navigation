package de.andre.jsfnavigation;

import java.util.List;

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

        out.append(
                "@InjectMocks\nprivate ")
                .append(
                        analysis.getSimpleDeclaringType())
                .append(
                        " subject;\n\n")
                .append(
                        "@Test\npublic void ")
                .append(
                        analysis.getMethodName())
                .append(
                        "_shouldTODO() {\n")
                .append(
                        "    // Arrange\n");

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
                "\n    // Act\n");

        appendSubjectCall(
                out,
                analysis,
                "    ");

        out.append(
                "\n    // Assert\n");

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

        footer(
                out,
                analysis);

        return out.toString();
    }

    private static String jpaTest(
            TestHelperAnalysis analysis) {

        StringBuilder out =
                new StringBuilder();

        header(
                out,
                analysis);

        out.append(
                "@Test\npublic void ")
                .append(
                        analysis.getMethodName())
                .append(
                        "_shouldTODO() {\n")
                .append(
                        "    // Arrange\n");

        appendParameters(
                out,
                analysis.getParameters(),
                "    ");

        if (analysis.isJpaWriteDetected()) {
            out.append(
                    "    // IMPORTANT: register rows created by this test with your cleanup mechanism.\n")
                    .append(
                            "    // Prefer the project's cleanup-tracked helper (for example persistEntity(...)) over raw EntityManager.persist(...).\n");
        } else {
            out.append(
                    "    // TODO persist prerequisite entities using the project's cleanup-tracked test helper.\n");
        }

        if (analysis.isJpaReadDetected()) {
            out.append(
                    "    // If this assertion must prove a real DB round-trip, flush + clear/renew the persistence context before the read.\n");
        }

        out.append(
                "\n    // Act\n");

        appendSubjectCall(
                out,
                analysis,
                "    ");

        out.append(
                "\n    // Assert\n");

        if (!isVoid(
                analysis.getReturnType())) {

            out.append(
                    "    assertNotNull(result); // TODO verify the DB-visible result\n");
        } else {
            out.append(
                    "    // TODO query/read back state after the intended transaction/PC boundary\n");
        }

        out.append(
                "}\n");

        footer(
                out,
                analysis);

        return out.toString();
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

        for (TestHelperParameter parameter :
                parameters) {

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

        out.append(
                "subject.")
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
