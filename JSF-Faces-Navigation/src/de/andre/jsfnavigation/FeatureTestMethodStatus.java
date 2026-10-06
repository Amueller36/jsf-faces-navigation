package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.IMethod;

public final class FeatureTestMethodStatus {

    private final IMethod method;
    private final List<String> testReferences;

    public FeatureTestMethodStatus(
            IMethod method,
            List<String> testReferences) {

        this.method = method;
        this.testReferences =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                testReferences));
    }

    public IMethod getMethod() {
        return method;
    }

    public List<String> getTestReferences() {
        return testReferences;
    }

    public boolean isTested() {
        return !testReferences.isEmpty();
    }

    public String getLabel() {
        if (method == null) {
            return "";
        }

        StringBuilder out =
                new StringBuilder();

        out.append(
                isTested()
                        ? "✓ "
                        : "✗ ")
                .append(
                        method.getElementName())
                .append('(');

        String[] parameters =
                method.getParameterTypes();

        for (int i = 0;
                i < parameters.length;
                i++) {

            if (i > 0) {
                out.append(", ");
            }

            out.append(
                    org.eclipse.jdt.core.Signature
                            .toString(
                                    parameters[i]));
        }

        out.append(')');

        if (isTested()) {
            out.append(
                    "  ← ")
                    .append(
                            testReferences.get(0));

            if (testReferences.size() > 1) {
                out.append(
                        "  (+")
                        .append(
                                testReferences.size() - 1)
                        .append(
                                " more)");
            }

        } else {
            out.append(
                    "  [NOT REFERENCED BY TEST]");
        }

        return out.toString();
    }
}
