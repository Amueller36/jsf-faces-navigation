package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TestHelperLearnedFixtureRecipe {

    private final String originTypeHandle;
    private final String originTypeName;
    private final String originMethodName;
    private final List<String> coveredFixtureTypes;
    private final List<String> statements;
    private final List<String> declaredVariables;
    private final int baseScore;

    public TestHelperLearnedFixtureRecipe(
            String originTypeHandle,
            String originTypeName,
            String originMethodName,
            List<String> coveredFixtureTypes,
            List<String> statements,
            List<String> declaredVariables,
            int baseScore) {

        this.originTypeHandle = safe(originTypeHandle);
        this.originTypeName = safe(originTypeName);
        this.originMethodName = safe(originMethodName);

        this.coveredFixtureTypes =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                coveredFixtureTypes == null
                                        ? Collections.<String>emptyList()
                                        : coveredFixtureTypes));

        this.statements =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                statements == null
                                        ? Collections.<String>emptyList()
                                        : statements));

        this.declaredVariables =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                declaredVariables == null
                                        ? Collections.<String>emptyList()
                                        : declaredVariables));

        this.baseScore = baseScore;
    }

    public String getOriginTypeHandle() {
        return originTypeHandle;
    }

    public String getOriginTypeName() {
        return originTypeName;
    }

    public String getOriginMethodName() {
        return originMethodName;
    }

    public List<String> getCoveredFixtureTypes() {
        return coveredFixtureTypes;
    }

    public List<String> getStatements() {
        return statements;
    }

    public List<String> getDeclaredVariables() {
        return declaredVariables;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public boolean isFromTargetHandle(
            String handle) {

        return handle != null
                && handle.equals(
                        originTypeHandle);
    }

    public int coverageCount(
            List<TestHelperFixtureDependency> required) {

        if (required == null
                || required.isEmpty()) {

            return 0;
        }

        Set<String> needed =
                new LinkedHashSet<String>();

        for (TestHelperFixtureDependency fixture :
                required) {

            if (fixture != null) {
                needed.add(
                        fixture.getQualifiedType());
            }
        }

        int count = 0;

        for (String covered :
                coveredFixtureTypes) {

            if (needed.contains(
                    covered)) {
                count++;
            }
        }

        return count;
    }

    public boolean declares(
            String variableName) {

        return variableName != null
                && declaredVariables.contains(
                        variableName);
    }

    public String getLabel() {
        return originTypeName
                + "."
                + originMethodName
                + "(...) — "
                + coveredFixtureTypes.size()
                + " fixture types, "
                + statements.size()
                + " setup statements";
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }
}
