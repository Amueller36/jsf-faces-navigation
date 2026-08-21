package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.IType;

public final class FeatureTestClassStatus {

    private final IType productionType;
    private final String architectureRole;
    private final List<TestTargetCandidate> tests;
    private final List<FeatureTestMethodStatus> methods;

    public FeatureTestClassStatus(
            IType productionType,
            String architectureRole,
            List<TestTargetCandidate> tests,
            List<FeatureTestMethodStatus> methods) {

        this.productionType = productionType;
        this.architectureRole =
                architectureRole == null
                        ? ""
                        : architectureRole;

        this.tests =
                Collections.unmodifiableList(
                        new ArrayList<TestTargetCandidate>(
                                tests));

        this.methods =
                Collections.unmodifiableList(
                        new ArrayList<FeatureTestMethodStatus>(
                                methods));
    }

    public IType getProductionType() {
        return productionType;
    }

    public String getArchitectureRole() {
        return architectureRole;
    }

    public List<TestTargetCandidate> getTests() {
        return tests;
    }

    public List<FeatureTestMethodStatus> getMethods() {
        return methods;
    }

    public int getUntestedCount() {
        int count = 0;

        for (FeatureTestMethodStatus method :
                methods) {

            if (!method.isTested()) {
                count++;
            }
        }

        return count;
    }

    public int getTestedCount() {
        return methods.size()
                - getUntestedCount();
    }

    public boolean hasTestClass() {
        return !tests.isEmpty();
    }

    public int getReferenceCoveragePercent() {
        if (methods.isEmpty()) {
            return 100;
        }

        return (int) Math.round(
                (getTestedCount()
                        * 100.0d)
                / methods.size());
    }

    public String getLabel() {
        StringBuilder out =
                new StringBuilder();

        if (!hasTestClass()) {
            out.append(
                    "[NO TEST CLASS] ");

        } else if (getUntestedCount() > 0) {
            out.append(
                    "[PARTIAL] ");

        } else {
            out.append(
                    "[OK] ");
        }

        out.append(
                productionType == null
                        ? ""
                        : productionType
                                .getElementName())
                .append(
                        "  — ")
                .append(
                        architectureRole)
                .append(
                        "  — ")
                .append(
                        getUntestedCount())
                .append('/')
                .append(
                        methods.size())
                .append(
                        " methods untested")
                .append(
                        "  — ")
                .append(
                        getReferenceCoveragePercent())
                .append(
                        "% static method-reference coverage");

        if (hasTestClass()) {
            out.append(
                    "  — ")
                    .append(
                            tests.size())
                    .append(
                            tests.size() == 1
                                    ? " test class"
                                    : " test classes");
        }

        return out.toString();
    }
}
