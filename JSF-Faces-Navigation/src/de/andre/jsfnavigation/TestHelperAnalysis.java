package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestHelperAnalysis {

    private final String declaringType;
    private final String methodName;
    private final String returnType;
    private final List<TestHelperParameter> parameters;
    private final List<TestHelperDependency> dependencies;
    private final boolean jpaDetected;
    private final boolean jpaWriteDetected;
    private final boolean jpaReadDetected;
    private final boolean truncated;

    public TestHelperAnalysis(
            String declaringType,
            String methodName,
            String returnType,
            List<TestHelperParameter> parameters,
            List<TestHelperDependency> dependencies,
            boolean jpaDetected,
            boolean jpaWriteDetected,
            boolean jpaReadDetected,
            boolean truncated) {

        this.declaringType = safe(declaringType);
        this.methodName = safe(methodName);
        this.returnType = safe(returnType);

        this.parameters =
                Collections.unmodifiableList(
                        new ArrayList<TestHelperParameter>(
                                parameters));

        this.dependencies =
                Collections.unmodifiableList(
                        new ArrayList<TestHelperDependency>(
                                dependencies));

        this.jpaDetected = jpaDetected;
        this.jpaWriteDetected =
                jpaWriteDetected;
        this.jpaReadDetected =
                jpaReadDetected;
        this.truncated = truncated;
    }

    public String getDeclaringType() {
        return declaringType;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<TestHelperParameter> getParameters() {
        return parameters;
    }

    public List<TestHelperDependency> getDependencies() {
        return dependencies;
    }

    public boolean isJpaDetected() {
        return jpaDetected;
    }

    public boolean isJpaWriteDetected() {
        return jpaWriteDetected;
    }

    public boolean isJpaReadDetected() {
        return jpaReadDetected;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getSimpleDeclaringType() {
        return simpleType(
                declaringType);
    }

    public static String simpleType(
            String qualified) {

        if (qualified == null
                || qualified.isEmpty()) {

            return "Object";
        }

        String value =
                qualified.replace(
                        '$',
                        '.');

        int dot =
                value.lastIndexOf('.');

        return dot >= 0
                ? value.substring(
                        dot + 1)
                : value;
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
