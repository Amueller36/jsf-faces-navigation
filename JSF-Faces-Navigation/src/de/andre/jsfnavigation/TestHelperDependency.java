package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestHelperDependency {

    private final String fieldName;
    private final String fieldType;
    private final List<TestHelperInvocation> invocations;

    public TestHelperDependency(
            String fieldName,
            String fieldType,
            List<TestHelperInvocation> invocations) {

        this.fieldName =
                fieldName == null
                        ? "dependency"
                        : fieldName;

        this.fieldType =
                fieldType == null
                        ? "Object"
                        : fieldType;

        this.invocations =
                Collections.unmodifiableList(
                        new ArrayList<TestHelperInvocation>(
                                invocations));
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public List<TestHelperInvocation> getInvocations() {
        return invocations;
    }
}
