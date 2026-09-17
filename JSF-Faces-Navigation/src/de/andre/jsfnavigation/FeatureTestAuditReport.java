package de.andre.jsfnavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureTestAuditReport {

    private final String feature;
    private final List<FeatureTestClassStatus> classes;
    private final boolean truncated;

    public FeatureTestAuditReport(
            String feature,
            List<FeatureTestClassStatus> classes,
            boolean truncated) {

        this.feature =
                feature == null
                        ? ""
                        : feature;

        this.classes =
                Collections.unmodifiableList(
                        new ArrayList<FeatureTestClassStatus>(
                                classes));

        this.truncated = truncated;
    }

    public String getFeature() {
        return feature;
    }

    public List<FeatureTestClassStatus> getClasses() {
        return classes;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int getMethodCount() {
        int count = 0;

        for (FeatureTestClassStatus clazz :
                classes) {

            count += clazz.getMethods()
                    .size();
        }

        return count;
    }

    public int getUntestedMethodCount() {
        int count = 0;

        for (FeatureTestClassStatus clazz :
                classes) {

            count += clazz.getUntestedCount();
        }

        return count;
    }

    public int getReferenceCoveragePercent() {
        int methods =
                getMethodCount();

        if (methods == 0) {
            return 100;
        }

        return (int) Math.round(
                ((methods
                        - getUntestedMethodCount())
                        * 100.0d)
                / methods);
    }

    public int getClassesWithoutTests() {
        int count = 0;

        for (FeatureTestClassStatus clazz :
                classes) {

            if (!clazz.hasTestClass()) {
                count++;
            }
        }

        return count;
    }
}
