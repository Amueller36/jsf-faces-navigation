package de.andre.jsfnavigation;

import org.eclipse.jdt.core.IType;

public final class TestTargetCandidate {

    private final IType type;
    private final int classification;
    private final int score;

    public TestTargetCandidate(
            IType type,
            int classification,
            int score) {

        this.type = type;
        this.classification =
                classification;
        this.score = score;
    }

    public IType getType() {
        return type;
    }

    public int getClassification() {
        return classification;
    }

    public int getScore() {
        return score;
    }

    public String getLabel() {
        if (type == null) {
            return "";
        }

        String project =
                type.getJavaProject() == null
                        ? ""
                        : type.getJavaProject()
                                .getElementName();

        String packageName =
                type.getPackageFragment() == null
                        ? ""
                        : type.getPackageFragment()
                                .getElementName();

        StringBuilder label =
                new StringBuilder();

        label.append(
                type.getElementName());

        if (!packageName.isEmpty()) {
            label.append(
                    " — ")
                    .append(
                            packageName);
        }

        if (!project.isEmpty()) {
            label.append(
                    "  [")
                    .append(
                            project)
                    .append(']');
        }

        label.append(
                "  ")
                .append(
                        classificationLabel(
                                classification));

        return label.toString();
    }

    public static String classificationLabel(
            int classification) {

        switch (classification) {
            case FlowTestClassifier.UNIT_TEST:
                return "UNIT";

            case FlowTestClassifier.JPA_TEST:
                return "JPA";

            case FlowTestClassifier.ARQUILLIAN_TEST:
                return "ARQUILLIAN";

            case FlowTestClassifier.INTEGRATION_TEST:
                return "INTEGRATION";

            default:
                return "TEST";
        }
    }
}
