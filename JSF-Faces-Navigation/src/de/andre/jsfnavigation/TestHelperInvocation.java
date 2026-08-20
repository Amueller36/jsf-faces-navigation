package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestHelperInvocation {

    private final String methodName;
    private final String returnType;
    private final List<String> parameterTypes;
    private final boolean voidReturn;

    public TestHelperInvocation(
            String methodName,
            String returnType,
            List<String> parameterTypes,
            boolean voidReturn) {

        this.methodName =
                methodName == null
                        ? "call"
                        : methodName;

        this.returnType =
                returnType == null
                        ? "Object"
                        : returnType;

        this.parameterTypes =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                parameterTypes));

        this.voidReturn =
                voidReturn;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public boolean isVoidReturn() {
        return voidReturn;
    }

    public String signatureKey() {
        StringBuilder key =
                new StringBuilder(
                        methodName);

        key.append('(');

        for (String parameter :
                parameterTypes) {

            key.append(parameter)
                    .append(';');
        }

        return key.append(')')
                .toString();
    }
}
